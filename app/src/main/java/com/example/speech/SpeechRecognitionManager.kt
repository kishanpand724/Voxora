package com.example.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.AssistantStateRepository
import java.util.concurrent.atomic.AtomicLong

class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val DISPLAY_COMMAND_DELAY_MS = 2500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWordDetector: WakeWordDetector? = null
    private var commandRecognizer: CommandSpeechRecognizer? = null

    private val sessionCounter = AtomicLong(0)
    @Volatile
    private var activeWakeWordSessionId: String? = null

    fun startListening() {
        mainHandler.post {
            if (!AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Assistant is not active. Skipping startListening.")
                return@post
            }

            Log.d(TAG, "Starting Voxora wake-word state machine...")
            AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_HEY_NOVA)

            startWakeWordMonitoring()
        }
    }

    private fun startWakeWordMonitoring() {
        stopCommandRecognizer()

        if (!AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Assistant inactive. Cannot start wake-word monitoring.")
            return
        }

        // Generate a new Session ID token
        val newSessionId = "session_${System.currentTimeMillis()}_${sessionCounter.incrementAndGet()}"
        activeWakeWordSessionId = newSessionId

        Log.d(TAG, "Wake word session started: $newSessionId")
        AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_HEY_NOVA)

        if (wakeWordDetector == null) {
            wakeWordDetector = PassiveWakeWordDetector(context)
        }

        wakeWordDetector?.startDetection(newSessionId) { detectedSessionId ->
            mainHandler.post {
                onWakeWordDetected(detectedSessionId)
            }
        }
    }

    private fun onWakeWordDetected(detectedSessionId: String) {
        val isActive = AssistantStateRepository.isAssistantActive.value
        val currentState = AssistantStateRepository.listeningState.value
        val expectedSessionId = activeWakeWordSessionId

        Log.d(TAG, "Wake word detected: $detectedSessionId")

        // Strictly validate against all required conditions
        when {
            !isActive -> {
                Log.d(TAG, "Wake word callback rejected")
                Log.d(TAG, "Reason for rejection: Assistant is not active")
                return
            }
            currentState != SpeechListeningState.WAITING_FOR_HEY_NOVA -> {
                Log.d(TAG, "Wake word callback rejected")
                Log.d(TAG, "Reason for rejection: Current state is $currentState, expected WAITING_FOR_HEY_NOVA")
                return
            }
            detectedSessionId != expectedSessionId || expectedSessionId == null -> {
                Log.d(TAG, "Wake word callback rejected")
                Log.d(TAG, "Reason for rejection: Session ID mismatch or expired (detected=$detectedSessionId, active=$expectedSessionId)")
                return
            }
        }

        // Callback accepted!
        Log.d(TAG, "Wake word callback accepted for session: $detectedSessionId")

        // Immediately invalidate session token so the same session cannot trigger again
        activeWakeWordSessionId = null

        // Stop wake word detector for current session
        wakeWordDetector?.stopDetection()

        // Transition state to LISTENING_FOR_COMMAND
        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING_FOR_COMMAND)
        Log.d(TAG, "Command listening started")

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
        Log.d(TAG, "Command listening stopped")

        // Display command and return to WAITING_FOR_HEY_NOVA state
        mainHandler.postDelayed({
            returnToWaitingState()
        }, DISPLAY_COMMAND_DELAY_MS)
    }

    private fun onCommandErrorOrTimeout() {
        Log.w(TAG, "Command listening timed out or failed.")
        stopCommandRecognizer()
        Log.d(TAG, "Command listening stopped")

        returnToWaitingState()
    }

    private fun returnToWaitingState() {
        if (AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Returning to WAITING_FOR_HEY_NOVA")
            AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_HEY_NOVA)
            startWakeWordMonitoring()
        } else {
            Log.d(TAG, "Assistant stopped. Remaining in STOPPED state.")
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
        }
    }

    private fun stopCommandRecognizer() {
        commandRecognizer?.destroy()
        commandRecognizer = null
    }

    fun stopListening() {
        mainHandler.post {
            activeWakeWordSessionId = null
            mainHandler.removeCallbacksAndMessages(null)
            wakeWordDetector?.stopDetection()
            stopCommandRecognizer()
            Log.d(TAG, "Command listening stopped")
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Returning to WAITING_FOR_HEY_NOVA")
            Log.d(TAG, "Voxora voice assistant listening stopped.")
        }
    }

    fun destroy() {
        mainHandler.post {
            activeWakeWordSessionId = null
            mainHandler.removeCallbacksAndMessages(null)
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            stopCommandRecognizer()
            Log.d(TAG, "Command listening stopped")
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Voxora speech resources released and destroyed.")
        }
    }
}
