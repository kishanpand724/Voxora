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

class SpeechRecognitionManager(private val context: Context) : RecognitionListener {

    companion object {
        private const val TAG = "VoxoraSpeech"
        private const val RESTART_DELAY_MS = 600L
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

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device.")
                AssistantStateRepository.updateListeningState(SpeechListeningState.ERROR)
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@SpeechRecognitionManager)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING)
                Log.d(TAG, "Started speech recognition listening.")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognizer", e)
                AssistantStateRepository.updateListeningState(SpeechListeningState.ERROR)
                scheduleRestart()
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
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer", e)
            }
            speechRecognizer = null
            AssistantStateRepository.updateListeningState(SpeechListeningState.STOPPED)
            Log.d(TAG, "Destroyed speech recognizer.")
        }
    }

    private fun scheduleRestart() {
        mainHandler.removeCallbacksAndMessages(null)
        if (AssistantStateRepository.isAssistantActive.value) {
            mainHandler.postDelayed({
                if (AssistantStateRepository.isAssistantActive.value) {
                    startListening()
                }
            }, RESTART_DELAY_MS)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech")
        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING)
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
        AssistantStateRepository.updateListeningState(SpeechListeningState.LISTENING)
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
        AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)
    }

    override fun onError(error: Int) {
        Log.w(TAG, "onError code: $error")
        AssistantStateRepository.updateListeningState(SpeechListeningState.ERROR)
        scheduleRestart()
    }

    override fun onResults(results: Bundle?) {
        AssistantStateRepository.updateListeningState(SpeechListeningState.PROCESSING)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedText = matches?.firstOrNull()
        if (!recognizedText.isNullOrBlank()) {
            Log.d(TAG, "Recognized text: \"$recognizedText\"")
            AssistantStateRepository.updateRecognizedText(recognizedText)
        }
        scheduleRestart()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            AssistantStateRepository.updateRecognizedText(text)
        }
    }


    override fun onEvent(eventType: Int, params: Bundle?) {}
}
