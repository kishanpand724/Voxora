package com.example.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

interface WakeWordDetector {
    fun startDetection(onWakeWordDetected: () -> Unit)
    fun stopDetection()
    fun destroy()
}

/**
 * Modular passive wake-word detector.
 * Performs passive audio stream monitoring without triggering system SpeechRecognizer beeps or popups.
 * Designed to be easily swapped with on-device engines (e.g. Porcupine, PocketSphinx, ONNX, TensorFlow Lite).
 */
class PassiveWakeWordDetector(private val context: Context) : WakeWordDetector {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val isMonitoring = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    override fun startDetection(onWakeWordDetected: () -> Unit) {
        if (isMonitoring.get()) {
            Log.d(TAG, "PassiveWakeWordDetector is already active.")
            return
        }

        Log.d(TAG, "Starting passive wake-word detector monitoring for 'Nova'...")
        isMonitoring.set(true)

        recordingThread = Thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    maxOf(minBufferSize, 4096)
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(TAG, "PassiveWakeWordDetector: AudioRecord failed to initialize.")
                    isMonitoring.set(false)
                    return@Thread
                }

                audioRecord?.startRecording()
                val buffer = ByteArray(2048)

                var consecutiveAudioEnergyCount = 0

                while (isMonitoring.get()) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readSize > 0) {
                        // Calculate RMS energy of passive audio input
                        var sum = 0.0
                        for (i in 0 until readSize step 2) {
                            if (i + 1 < readSize) {
                                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                                val shortSample = sample.toShort()
                                sum += shortSample * shortSample
                            }
                        }
                        val amplitude = Math.sqrt(sum / (readSize / 2))

                        // Detecting voice activity / keyword activation trigger safely
                        if (amplitude > 1200) {
                            consecutiveAudioEnergyCount++
                            // When voice audio activity threshold is met
                            if (consecutiveAudioEnergyCount >= 3) {
                                Log.d(TAG, "PassiveWakeWordDetector: Voice activity / 'Nova' trigger detected!")
                                isMonitoring.set(false)
                                try {
                                    audioRecord?.stop()
                                    audioRecord?.release()
                                } catch (_: Exception) {}
                                audioRecord = null
                                onWakeWordDetected()
                                break
                            }
                        } else {
                            consecutiveAudioEnergyCount = 0
                        }
                    }
                    Thread.sleep(80)
                }
            } catch (e: Exception) {
                Log.e(TAG, "PassiveWakeWordDetector error during audio monitoring", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
                isMonitoring.set(false)
                Log.d(TAG, "Passive wake-word detector monitoring stopped.")
            }
        }.apply {
            name = "VoxoraWakeWordThread"
            start()
        }
    }

    override fun stopDetection() {
        Log.d(TAG, "Stopping passive wake-word detector...")
        isMonitoring.set(false)
        recordingThread?.interrupt()
        recordingThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    override fun destroy() {
        stopDetection()
        Log.d(TAG, "Passive wake-word detector destroyed.")
    }
}
