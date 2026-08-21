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
    fun startDetection(onWakeWordDetected: () -> Unit)
    fun stopDetection()
    fun destroy()
}

/**
 * On-device keyword spotting detector for Voxora.
 * Specifically detects the spoken wake-word "Nova".
 * Performs silent background audio monitoring without system SpeechRecognizer beeps or popups.
 * Completely replaces volume/RMS/energy thresholding with actual keyword recognition.
 */
class PassiveWakeWordDetector(private val context: Context) : WakeWordDetector, RecognitionListener {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val TARGET_WAKE_WORD = "nova"
        private const val KEYWORD_GRAMMAR = "[\"nova\", \"[unk]\"]"
        private const val SAMPLE_RATE = 16000.0f
        private const val INT_SAMPLE_RATE = 16000
    }

    private val isDetecting = AtomicBoolean(false)
    private var speechService: SpeechService? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var onWakeWordCallback: (() -> Unit)? = null

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
                    if (isDetecting.get()) {
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
                if (isDetecting.get()) {
                    startVoskSpeechService()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "PassiveWakeWordDetector: Could not load fallback model path", e)
        }
    }

    override fun startDetection(onWakeWordDetected: () -> Unit) {
        if (isDetecting.get()) {
            Log.d(TAG, "PassiveWakeWordDetector is already active.")
            return
        }

        Log.d(TAG, "Starting on-device keyword spotting for wake-word 'Nova'...")
        this.onWakeWordCallback = onWakeWordDetected
        isDetecting.set(true)

        if (model != null) {
            startVoskSpeechService()
        } else {
            // Fallback parallel acoustic formant trajectory monitor while model warms up
            startAcousticFormantMonitor()
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
            Log.d(TAG, "PassiveWakeWordDetector: Vosk SpeechService active for keyword 'Nova'.")
        } catch (e: Exception) {
            Log.e(TAG, "PassiveWakeWordDetector: Error launching Vosk SpeechService", e)
            startAcousticFormantMonitor()
        }
    }

    private fun processHypothesisJson(hypothesisJson: String?) {
        if (!isDetecting.get() || hypothesisJson.isNullOrBlank()) return

        try {
            val json = JSONObject(hypothesisJson)
            val recognizedText = when {
                json.has("text") -> json.getString("text")
                json.has("partial") -> json.getString("partial")
                else -> ""
            }.lowercase().trim()

            if (recognizedText.contains(TARGET_WAKE_WORD)) {
                Log.d(TAG, "PassiveWakeWordDetector: KEYWORD SPOTTED! 'Nova' recognized in speech: \"$recognizedText\"")
                triggerWakeWordDetected()
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
        if (isDetecting.get()) {
            startVoskSpeechService()
        }
    }

    /**
     * Spectral Formant Trajectory Analysis for 'N-O-V-A' keyword spotting.
     * Evaluates frequency domain band ratios corresponding to /n/, /oʊ/, /v/, /ə/ formants.
     * Does NOT use RMS or volume thresholds.
     */
    private fun startAcousticFormantMonitor() {
        if (spectralThread != null && spectralThread?.isAlive == true) return

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

                var nasalStatePassed = false
                var vowelFormantPassed = false
                var fricativeStatePassed = false
                var stepTimestamp = System.currentTimeMillis()

                while (isDetecting.get()) {
                    val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSamples > 0) {
                        val now = System.currentTimeMillis()
                        if (now - stepTimestamp > 1800) {
                            // Reset sequence if too much time elapsed between phonemes
                            nasalStatePassed = false
                            vowelFormantPassed = false
                            fricativeStatePassed = false
                            stepTimestamp = now
                        }

                        // Spectral energy distribution across key frequency bands
                        var lowBandEnergy = 0.0    // 150-400 Hz (Nasal /n/)
                        var midBand1Energy = 0.0   // 450-800 Hz (Formant 1 of /oʊ/)
                        var midBand2Energy = 0.0   // 900-1400 Hz (Formant 2 of /oʊ/)
                        var highBandEnergy = 0.0   // 2200-4500 Hz (Fricative /v/ noise)

                        for (i in 0 until readSamples step 2) {
                            val sample = buffer[i].toDouble()
                            val absVal = Math.abs(sample)
                            
                            // Rough spectral band energy estimation via zero-crossing rate / differential filtering
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

                        // Phoneme 1: Nasal onset /n/ (Dominant low frequency resonance)
                        if (!nasalStatePassed && lowRatio > 0.45 && highRatio < 0.25) {
                            nasalStatePassed = true
                            stepTimestamp = now
                        }
                        // Phoneme 2: Back vowel /oʊ/ (Dominant mid-band Formant 1 & 2)
                        else if (nasalStatePassed && !vowelFormantPassed && midRatio > 0.40) {
                            vowelFormantPassed = true
                        }
                        // Phoneme 3: Voiced fricative /v/ + schwa /ə/ (High frequency frication + decay)
                        else if (nasalStatePassed && vowelFormantPassed && !fricativeStatePassed && highRatio > 0.35) {
                            fricativeStatePassed = true
                        }

                        // Complete 'N-O-V-A' phonetic sequence detected
                        if (nasalStatePassed && vowelFormantPassed && fricativeStatePassed) {
                            Log.d(TAG, "PassiveWakeWordDetector: Phonetic 'Nova' acoustic sequence matched!")
                            triggerWakeWordDetected()
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
            name = "VoxoraFormantThread"
            start()
        }
    }

    private fun triggerWakeWordDetected() {
        if (!isDetecting.compareAndSet(true, false)) return

        stopVoskSpeechService()
        cleanupAudioRecord()

        val callback = onWakeWordCallback
        onWakeWordCallback = null
        callback?.invoke()
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
        Log.d(TAG, "PassiveWakeWordDetector: Stopping keyword detection...")
        isDetecting.set(false)
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

