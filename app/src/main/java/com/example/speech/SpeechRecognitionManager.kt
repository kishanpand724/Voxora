package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.AssistantStateRepository
import java.util.Locale

class SpeechRecognitionManager(private val context: Context) : RecognitionListener {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val WAKE_WORD = "nova"
        private const val RESTART_DELAY_MS = 400L
        private const val COMMAND_DISPLAY_DELAY_MS = 2500L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    fun startListening() {
        mainHandler.post {
            if (!AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Assistant is not active. Skipping startListening.")
                return@post
            }

            if (isListening) {
                Log.d(TAG, "SpeechRecognizer is already listening. Skipping duplicate start.")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device.")
                AssistantStateRepository.updateListeningState(SpeechListeningState.ERROR)
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(this@SpeechRecognitionManager)
                    }
                }

                val currentListeningState = AssistantStateRepository.listeningState.value
                if (currentListeningState == SpeechListeningState.STOPPED) {
                    AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_WAKE_WORD)
                }

                val defaultLocale = Locale.getDefault().toLanguageTag().ifEmpty { "en-IN" }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "Started speech recognition listening session ($defaultLocale / state=${AssistantStateRepository.listeningState.value}).")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                isListening = false
                AssistantStateRepository.updateListeningState(SpeechListeningState.ERROR)
                recreateRecognizerAndRestart()
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            isListening = false
            mainHandler.removeCallbacksAndMessages(null)
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognizer", e)
            }
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Stopped speech recognition listening.")
        }
    }

    fun destroy() {
        mainHandler.post {
            isListening = false
            mainHandler.removeCallbacksAndMessages(null)
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer", e)
            }
            speechRecognizer = null
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "SpeechRecognizer destroyed and resources released.")
        }
    }

    private fun recreateRecognizerAndRestart() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false

        if (AssistantStateRepository.isAssistantActive.value) {
            Log.d(TAG, "Restarting speech recognition listening loop in ${RESTART_DELAY_MS}ms...")
            mainHandler.postDelayed({
                if (AssistantStateRepository.isAssistantActive.value) {
                    startListening()
                }
            }, RESTART_DELAY_MS)
        }
    }

    private fun scheduleNextListeningTurn(delayMs: Long = RESTART_DELAY_MS) {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        if (AssistantStateRepository.isAssistantActive.value) {
            mainHandler.postDelayed({
                if (AssistantStateRepository.isAssistantActive.value) {
                    startListening()
                }
            }, delayMs)
        }
    }

    private fun scheduleReturnToWakeWord(delayMs: Long = COMMAND_DISPLAY_DELAY_MS) {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (AssistantStateRepository.isAssistantActive.value) {
                Log.d(TAG, "Returning to WAITING_FOR_WAKE_WORD state after command processing.")
                AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_WAKE_WORD)
                startListening()
            }
        }, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "SpeechRecognizer: Ready for speech input (state=${AssistantStateRepository.listeningState.value}).")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "SpeechRecognizer: Speech detected (state=${AssistantStateRepository.listeningState.value}).")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "SpeechRecognizer: Speech segment finished.")
        isListening = false
    }

    override fun onError(error: Int) {
        val errorMsg = getErrorMessage(error)
        Log.w(TAG, "SpeechRecognizer error code $error: $errorMsg (state=${AssistantStateRepository.listeningState.value})")
        isListening = false

        val currentState = AssistantStateRepository.listeningState.value
        if (currentState == SpeechListeningState.LISTENING_FOR_COMMAND) {
            Log.d(TAG, "No command received after wake word. Resetting state to WAITING_FOR_WAKE_WORD.")
            AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_WAKE_WORD)
        }

        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            recreateRecognizerAndRestart()
        } else {
            scheduleNextListeningTurn(RESTART_DELAY_MS)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull()
        if (!recognizedText.isNullOrBlank()) {
            handleSpeechResult(recognizedText, isFinal = true)
        } else {
            Log.d(TAG, "Speech recognition returned empty final result.")
            if (AssistantStateRepository.listeningState.value == SpeechListeningState.LISTENING_FOR_COMMAND) {
                AssistantStateRepository.updateListeningState(SpeechListeningState.WAITING_FOR_WAKE_WORD)
            }
            scheduleNextListeningTurn(RESTART_DELAY_MS)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            val currentState = AssistantStateRepository.listeningState.value
            if (currentState == SpeechListeningState.WAITING_FOR_WAKE_WORD) {
                val lower = text.lowercase()
                if (lower.contains(WAKE_WORD)) {
                    Log.d(TAG, "Wake word 'Nova' detected in partial result: \"$text\"")
                    handleSpeechResult(text, isFinal = false)
                }
            }
        }
    }

    private fun handleSpeechResult(text: String, isFinal: Boolean) {
        val currentState = AssistantStateRepository.listeningState.value
        val lowerText = text.lowercase().trim()

        when (currentState) {
            SpeechListeningState.WAITING_FOR_WAKE_WORD -> {
                if (lowerText.contains(WAKE_WORD)) {
                    Log.d(TAG, "Wake word 'Nova' confirmed in speech: \"$text\"")
                    val wakeWordIndex = lowerText.indexOf(WAKE_WORD)
                    val remainder = text.substring(wakeWordIndex + WAKE_WORD.length)
                        .trim()
                        .removePrefix(",")
                        .removePrefix(".")
                        .removePrefix(":")
                        .trim()

                    if (remainder.isNotEmpty()) {
                        Log.d(TAG, "Command extracted directly after 'Nova': \"$remainder\"")
                        AssistantStateRepository.updateRecognizedText(remainder)
                        AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)
                        scheduleReturnToWakeWord(COMMAND_DISPLAY_DELAY_MS)
                    } else {
                        Log.d(TAG, "'Nova' detected alone. State changing to LISTENING_FOR_COMMAND.")
                        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING_FOR_COMMAND)
                        scheduleNextListeningTurn(100L)
                    }
                } else {
                    Log.d(TAG, "Speech ignored (no 'Nova' wake word): \"$text\"")
                    if (isFinal) {
                        scheduleNextListeningTurn(RESTART_DELAY_MS)
                    }
                }
            }

            SpeechListeningState.LISTENING_FOR_COMMAND -> {
                Log.d(TAG, "Command captured in LISTENING_FOR_COMMAND state: \"$text\"")
                AssistantStateRepository.updateRecognizedText(text)
                AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)
                scheduleReturnToWakeWord(COMMAND_DISPLAY_DELAY_MS)
            }

            SpeechListeningState.PROCESSING -> {
                Log.d(TAG, "Speech ignored during PROCESSING state: \"$text\"")
            }

            else -> {
                if (isFinal) {
                    scheduleNextListeningTurn(RESTART_DELAY_MS)
                }
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
            else -> "Unknown error ($errorCode)"
        }
    }
}
