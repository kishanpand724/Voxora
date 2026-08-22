package com.example.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.command.CommandProcessor
import com.example.data.AssistantStateRepository
import java.util.concurrent.atomic.AtomicLong

/**
 * SpeechRecognitionManager manages voice interaction flows for Voxora.
 *
 * Direct Flow (Wake-word disabled for current version):
 * - START ASSISTANT -> Starts foreground service -> Direct command speech recognition
 * -> Speech to text -> Process command -> Continue active command listening flow.
 *
 * Prepared Architecture for Future Wake-Word Overlay:
 * - The WakeWordDetector architecture and WAITING_FOR_HEY_NOVA state remain preserved in code.
 * - Future overlay flow: WakeWordDetector detects "Hey Nova" -> Show floating overlay ->
 *   Command speech recognition -> Processing -> Hide overlay.
 */
class SpeechRecognitionManager(private val context: Context) {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val DISPLAY_COMMAND_DELAY_MS = 2500L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val commandProcessor = CommandProcessor(context)
    private var wakeWordDetector: WakeWordDetector? = null
    private var commandRecognizer: CommandSpeechRecognizer? = null

    private val sessionCounter = AtomicLong(0)
    @Volatile
    private var activeWakeWordSessionId: String? = null

    private var pendingDisplayRunnable: Runnable? = null

    /**
     * Starts voice recognition flow. Directly starts command speech recognition for current version.
     */
    fun startListening() {
        mainHandler.post {
            if (!AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Assistant is not active. Skipping startListening.")
                return@post
            }

            Log.d(TAG, "Starting direct command speech recognition flow...")
            startCommandListeningDirectly()
        }
    }

    /**
     * Direct command recognition flow (bypassing passive wake-word monitoring).
     */
    private fun startCommandListeningDirectly() {
        cancelPendingDisplayRunnable()
        stopCommandRecognizer()

        if (!AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Assistant inactive. Skipping command listening.")
            return
        }

        Log.d(TAG, "Starting direct command speech recognition session...")
        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING_FOR_COMMAND)

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
        Log.d(TAG, "Command captured: \"$commandText\". Processing command...")
        AssistantStateRepository.updateRecognizedText(commandText)
        AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)

        val result = commandProcessor.processCommand(commandText)
        AssistantStateRepository.updateExecutionResult(result)

        stopCommandRecognizer()
        Log.d(TAG, "Command listening stopped")

        cancelPendingDisplayRunnable()
        val runnable = Runnable {
            pendingDisplayRunnable = null
            if (AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Command processing complete. Continuing direct command listening flow...")
                startCommandListeningDirectly()
            } else {
                AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            }
        }
        pendingDisplayRunnable = runnable
        mainHandler.postDelayed(runnable, DISPLAY_COMMAND_DELAY_MS)
    }

    private fun onCommandErrorOrTimeout() {
        Log.w(TAG, "Command listening timed out or completed with error.")
        stopCommandRecognizer()
        Log.d(TAG, "Command listening stopped")

        if (AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Restarting direct command listening session...")
            startCommandListeningDirectly()
        } else {
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
        }
    }

    /* =========================================================================
     * MODULAR WAKE-WORD ARCHITECTURE (PREPARED FOR FUTURE OVERLAY IMPLEMENTATION)
     * ========================================================================= */

    @Suppress("unused")
    private fun startWakeWordMonitoring() {
        cancelPendingDisplayRunnable()
        stopCommandRecognizer()

        if (!AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Assistant inactive. Cannot start wake-word monitoring.")
            return
        }

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

    @Suppress("unused")
    private fun onWakeWordDetected(detectedSessionId: String) {
        val isActive = AssistantStateRepository.isAssistantActive.value
        val currentState = AssistantStateRepository.listeningState.value
        val expectedSessionId = activeWakeWordSessionId

        Log.d(TAG, "Wake word detected: $detectedSessionId")

        when {
            !isActive -> {
                Log.d(TAG, "Wake word callback rejected: Assistant not active")
                return
            }
            currentState != SpeechListeningState.WAITING_FOR_HEY_NOVA -> {
                Log.d(TAG, "Wake word callback rejected: Current state is $currentState")
                return
            }
            detectedSessionId != expectedSessionId || expectedSessionId == null -> {
                Log.d(TAG, "Wake word callback rejected: Session ID mismatch or expired")
                return
            }
        }

        Log.d(TAG, "Wake word callback accepted for session: $detectedSessionId")
        activeWakeWordSessionId = null
        wakeWordDetector?.stopDetection()

        // Transition to direct command listening
        startCommandListeningDirectly()
    }

    /* ========================================================================= */

    private fun cancelPendingDisplayRunnable() {
        pendingDisplayRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingDisplayRunnable = null
        }
    }

    private fun stopCommandRecognizer() {
        commandRecognizer?.destroy()
        commandRecognizer = null
    }

    fun stopListening() {
        mainHandler.post {
            activeWakeWordSessionId = null
            cancelPendingDisplayRunnable()
            wakeWordDetector?.stopDetection()
            stopCommandRecognizer()
            Log.d(TAG, "Command listening stopped")
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Voxora voice assistant listening stopped.")
        }
    }

    fun destroy() {
        mainHandler.post {
            activeWakeWordSessionId = null
            cancelPendingDisplayRunnable()
            wakeWordDetector?.destroy()
            wakeWordDetector = null
            stopCommandRecognizer()
            Log.d(TAG, "Command listening stopped")
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Voxora speech resources released and destroyed.")
        }
    }
}
