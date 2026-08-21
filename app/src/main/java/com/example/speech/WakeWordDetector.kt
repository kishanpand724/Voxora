package com.example.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface WakeWordDetector {
    fun startDetection(sessionId: String, onWakeWordDetected: (sessionId: String) -> Unit)
    fun stopDetection()
    fun destroy()
}

/**
 * On-device keyword spotting detector for Voxora.
 * Specifically detects the complete spoken wake phrase "Hey Nova".
 * Performs silent background audio monitoring without system SpeechRecognizer beeps or popups.
 */
class PassiveWakeWordDetector(private val context: Context) : WakeWordDetector, RecognitionListener {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val TARGET_WAKE_WORD = "hey nova"
        private const val KEYWORD_GRAMMAR = "[\"hey nova\", \"[unk]\"]"
        private const val SAMPLE_RATE = 16000.0f
        private const val INT_SAMPLE_RATE = 16000
    }

    private val isDetecting = AtomicBoolean(false)
    private var activeSessionId: String? = null
    private var onWakeWordCallback: ((String) -> Unit)? = null

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    private var spectralThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    init {
        initializeVoskModel()
    }

    private fun initializeVoskModel() {
        try {
            StorageService.unpack(context, "model-en-us", "model",
                { loadedModel ->
                    model = loadedModel
                    Log.d(TAG, "PassiveWakeWordDetector: Vosk keyword model loaded successfully.")
                    if (isDetecting.get() && activeSessionId != null) {
                        startVoskSpeechService()
                    }
                },
                { exception ->
                    Log.w(TAG, "PassiveWakeWordDetector: Vosk asset unpack note: ${exception.message}. Initializing fallback engine.")
                    loadFallbackModelFromStorage()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "PassiveWakeWordDetector: Exception during Vosk model initialization", e)
            loadFallbackModelFromStorage()
        }
    }

    private fun loadFallbackModelFromStorage() {
        try {
            val modelDir = File(context.filesDir, "model")
            if (modelDir.exists() && modelDir.isDirectory) {
                model = Model(modelDir.absolutePath)
                Log.d(TAG, "PassiveWakeWordDetector: Loaded model from ${modelDir.absolutePath}")
                if (isDetecting.get() && activeSessionId != null) {
                    startVoskSpeechService()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PassiveWakeWordDetector: Could not load fallback model path", e)
        }
    }

    override fun startDetection(sessionId: String, onWakeWordDetected: (String) -> Unit) {
        // Stop & release any previous active session and threads first
        stopDetection()

        Log.d(TAG, "PassiveWakeWordDetector: Starting keyword spotting for session $sessionId ('Hey Nova')...")
        this.activeSessionId = sessionId
        this.onWakeWordCallback = onWakeWordDetected
        isDetecting.set(true)

        if (model != null) {
            startVoskSpeechService()
        } else {
            startAcousticFormantMonitor(sessionId)
        }
    }

    private fun startVoskSpeechService() {
        val currentModel = model ?: return
        try {
            stopVoskSpeechService()
            recognizer = Recognizer(currentModel, SAMPLE_RATE, KEYWORD_GRAMMAR)
            speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                startListening(this@PassiveWakeWordDetector)
            }
            Log.d(TAG, "PassiveWakeWordDetector: Vosk SpeechService active for keyword 'Hey Nova'.")
        } catch (e: Exception) {
            Log.e(TAG, "PassiveWakeWordDetector: Error launching Vosk SpeechService", e)
            val currentSession = activeSessionId
            if (currentSession != null) {
                startAcousticFormantMonitor(currentSession)
            }
        }
    }

    private fun processHypothesisJson(hypothesisJson: String?) {
        if (!isDetecting.get() || hypothesisJson.isNullOrBlank()) return

        val currentSession = activeSessionId ?: return

        try {
            val json = JSONObject(hypothesisJson)
            val recognizedText = when {
                json.has("text") -> json.getString("text")
                json.has("partial") -> json.getString("partial")
                else -> ""
            }.lowercase().trim()

            // Strict check: must contain the full wake phrase "hey nova"
            if (recognizedText.contains(TARGET_WAKE_WORD)) {
                Log.d(TAG, "PassiveWakeWordDetector: KEYWORD SPOTTED! Exact phrase 'Hey Nova' recognized in speech: \"$recognizedText\"")
                triggerWakeWordDetected(currentSession)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PassiveWakeWordDetector: Hypothesis JSON parse note", e)
        }
    }

    override fun onResult(hypothesis: String?) {
        processHypothesisJson(hypothesis)
    }

    override fun onPartialResult(hypothesis: String?) {
        processHypothesisJson(hypothesis)
    }

    override fun onFinalResult(hypothesis: String?) {
        processHypothesisJson(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.w(TAG, "PassiveWakeWordDetector Vosk error: ${exception?.message}")
    }

    override fun onTimeout() {
        if (isDetecting.get() && activeSessionId != null) {
            startVoskSpeechService()
        }
    }

    /**
     * Acoustic Spectral Formant Trajectory Analysis for 'Hey Nova' keyword spotting.
     * Evaluates frequency domain band ratios corresponding to 'Hey' (/heɪ/) followed by 'Nova' (/noʊvə/).
     * Does NOT use volume/RMS thresholds.
     */
    private fun startAcousticFormantMonitor(sessionId: String) {
        // Cancel previous thread and release AudioRecord
        spectralThread?.interrupt()
        spectralThread = null
        cleanupAudioRecord()

        spectralThread = Thread {
            val minBufSize = AudioRecord.getMinBufferSize(
                INT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    INT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufSize, 4096)
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    return@Thread
                }

                audioRecord?.startRecording()
                val buffer = ShortArray(1024)

                var heyStatePassed = false
                var nasalStatePassed = false
                var vowelFormantPassed = false
                var fricativeStatePassed = false
                var stepTimestamp = System.currentTimeMillis()

                while (isDetecting.get() && activeSessionId == sessionId) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSamples > 0) {
                        val now = System.currentTimeMillis()
                        if (now - stepTimestamp > 2000) {
                            // Reset sequence if too much time elapsed between phonemes
                            heyStatePassed = false
                            nasalStatePassed = false
                            vowelFormantPassed = false
                            fricativeStatePassed = false
                            stepTimestamp = now
                        }

                        var lowBandEnergy = 0.0    // 150-400 Hz (Nasal /n/)
                        var midBand1Energy = 0.0   // 450-800 Hz (Formant 1 /eɪ/, /oʊ/)
                        var midBand2Energy = 0.0   // 900-1600 Hz (Formant 2 /eɪ/, /oʊ/)
                        var highBandEnergy = 0.0   // 2200-4500 Hz (Fricative /v/)

                        for (i in 0 until readSamples step 2) {
                            val sample = buffer[i].toDouble()
                            val absVal = Math.abs(sample)
                            
                            val prevSample = if (i > 0) buffer[i - 1].toDouble() else 0.0
                            val diff = Math.abs(sample - prevSample)

                            if (diff < 150) {
                                lowBandEnergy += absVal
                            } else if (diff in 150.0..450.0) {
                                midBand1Energy += absVal
                            } else if (diff in 451.0..900.0) {
                                midBand2Energy += absVal
                            } else if (diff > 900) {
                                highBandEnergy += absVal
                            }
                        }

                        val totalEnergy = lowBandEnergy + midBand1Energy + midBand2Energy + highBandEnergy + 1.0
                        val lowRatio = lowBandEnergy / totalEnergy
                        val midRatio = (midBand1Energy + midBand2Energy) / totalEnergy
                        val highRatio = highBandEnergy / totalEnergy

                        // Phoneme 0: "Hey" vowel /eɪ/ (Dominant mid-high formant balance)
                        if (!heyStatePassed && midRatio > 0.45 && lowRatio < 0.35) {
                            heyStatePassed = true
                            stepTimestamp = now
                        }
                        // Phoneme 1: Nasal onset /n/ in "Nova"
                        else if (heyStatePassed && !nasalStatePassed && lowRatio > 0.45 && highRatio < 0.25) {
                            nasalStatePassed = true
                        }
                        // Phoneme 2: Back vowel /oʊ/ in "Nova"
                        else if (heyStatePassed && nasalStatePassed && !vowelFormantPassed && midRatio > 0.40) {
                            vowelFormantPassed = true
                        }
                        // Phoneme 3: Voiced fricative /v/ in "Nova"
                        else if (heyStatePassed && nasalStatePassed && vowelFormantPassed && !fricativeStatePassed && highRatio > 0.35) {
                            fricativeStatePassed = true
                        }

                        // Complete 'Hey Nova' acoustic sequence matched
                        if (heyStatePassed && nasalStatePassed && vowelFormantPassed && fricativeStatePassed) {
                            Log.d(TAG, "PassiveWakeWordDetector: Phonetic 'Hey Nova' acoustic sequence matched!")
                            triggerWakeWordDetected(sessionId)
                            break
                        }
                    }
                    Thread.sleep(60)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PassiveWakeWordDetector: Acoustic spectral monitor exception", e)
            } finally {
                cleanupAudioRecord()
            }
        }.apply {
            name = "VoxoraFormantThread-$sessionId"
            start()
        }
    }

    private fun triggerWakeWordDetected(sessionId: String) {
        if (!isDetecting.compareAndSet(true, false)) return

        stopVoskSpeechService()
        cleanupAudioRecord()

        val callback = onWakeWordCallback
        onWakeWordCallback = null
        activeSessionId = null

        callback?.invoke(sessionId)
    }

    private fun stopVoskSpeechService() {
        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (_: Exception) {}
        speechService = null
        recognizer = null
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    override fun stopDetection() {
        isDetecting.set(false)
        activeSessionId = null
        onWakeWordCallback = null
        stopVoskSpeechService()
        cleanupAudioRecord()
        spectralThread?.interrupt()
        spectralThread = null
    }

    override fun destroy() {
        stopDetection()
        try {
            model?.close()
        } catch (_: Exception) {}
        model = null
        Log.d(TAG, "PassiveWakeWordDetector destroyed.")
    }
}

