package com.example.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.AssistantStateRepository

class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val DISPLAY_COMMAND_DELAY_MS = 2500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWordDetector: WakeWordDetector? = null
    private var commandRecognizer: CommandSpeechRecognizer? = null

    fun startListening() {
        mainHandler.post {
            if (!AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Assistant is not active. Skipping startListening.")
                return@post
            }

            Log.d(TAG, "Starting Voxora passive wake-word monitoring flow...")
            AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_NOVA)

            startWakeWordMonitoring()
        }
    }

    private fun startWakeWordMonitoring() {
        stopCommandRecognizer()

        if (wakeWordDetector == null) {
            wakeWordDetector = PassiveWakeWordDetector(context)
        }

        wakeWordDetector?.startDetection {
            mainHandler.post {
                onWakeWordDetected()
            }
        }
    }

    private fun onWakeWordDetected() {
        Log.d(TAG, "Wake word 'Nova' detected! Transitioning state to LISTENING_FOR_COMMAND...")
        
        // Stop passive monitoring while listening for command
        wakeWordDetector?.stopDetection()
        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING_FOR_COMMAND)

        // Activate single-turn command speech recognizer
        stopCommandRecognizer()
        commandRecognizer = CommandSpeechRecognizer(
            context = context,
            onCommandRecognized = { commandText ->
                onCommandReceived(commandText)
            },
            onErrorOrTimeout = {
                onCommandErrorOrTimeout()
            }
        ).apply {
            startListeningForCommand()
        }
    }

    private fun onCommandReceived(commandText: String) {
        Log.d(TAG, "Command captured: \"$commandText\". Transitioning to PROCESSING state...")
        AssistantStateRepository.updateRecognizedText(commandText)
        AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)

        stopCommandRecognizer()

        // Display command and return to passive wake word monitoring
        mainHandler.postDelayed({
            if (AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Returning to WAITING_FOR_NOVA passive state...")
                AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_NOVA)
                startWakeWordMonitoring()
            }
        }, DISPLAY_COMMAND_DELAY_MS)
    }

    private fun onCommandErrorOrTimeout() {
        Log.w(TAG, "Command listening timed out or failed. Returning to WAITING_FOR_NOVA state...")
        stopCommandRecognizer()

        if (AssistantStateRepository.isAssistantActive.value) {
            AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_NOVA)
            startWakeWordMonitoring()
        }
    }

    private fun stopCommandRecognizer() {
        commandRecognizer?.destroy()
        commandRecognizer = null
    }

    fun stopListening() {
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(null)
            wakeWordDetector?.stopDetection()
            stopCommandRecognizer()
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Voxora voice assistant listening stopped.")
        }
    }

    fun destroy() {
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(null)
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            stopCommandRecognizer()
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Voxora speech resources released and destroyed.")
        }
    }
}
