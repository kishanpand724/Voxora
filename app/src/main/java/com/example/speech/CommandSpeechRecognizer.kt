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

class CommandSpeechRecognizer(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onErrorOrTimeout: () -> Unit
) : RecognitionListener {

    companion object {
        private const val TAG = "VoxoraSpeech"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false

    fun startListeningForCommand() {
        mainHandler.post {
            if (isListening) {
                Log.d(TAG, "CommandSpeechRecognizer is already listening.")
                return@post
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition unavailable for command recognizer.")
                onErrorOrTimeout()
                return@post
            }

            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                        setRecognitionListener(this@CommandSpeechRecognizer)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d(TAG, "CommandSpeechRecognizer: Activated ONE command listening session.")
            } catch (e: Exception) {
                Log.e(TAG, "CommandSpeechRecognizer: Error starting speech listening session", e)
                isListening = false
                onErrorOrTimeout()
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
                Log.w(TAG, "CommandSpeechRecognizer: Error stopping speech recognizer", e)
            }
            Log.d(TAG, "CommandSpeechRecognizer: Stopped command listening.")
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
                Log.w(TAG, "CommandSpeechRecognizer: Error destroying speech recognizer", e)
            }
            speechRecognizer = null
            Log.d(TAG, "CommandSpeechRecognizer: Destroyed and resources released.")
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "CommandSpeechRecognizer: Ready for command speech input...")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "CommandSpeechRecognizer: Command speech detected...")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "CommandSpeechRecognizer: Command speech segment ended.")
        isListening = false
    }

    override fun onError(error: Int) {
        Log.w(TAG, "CommandSpeechRecognizer error code $error. Handled gracefully.")
        isListening = false
        destroy()
        onErrorOrTimeout()
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val recognizedCommand = matches?.firstOrNull()
        destroy()
        if (!recognizedCommand.isNullOrBlank()) {
            Log.d(TAG, "CommandSpeechRecognizer final command result: \"$recognizedCommand\"")
            onCommandRecognized(recognizedCommand)
        } else {
            Log.d(TAG, "CommandSpeechRecognizer returned empty command result.")
            onErrorOrTimeout()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            Log.d(TAG, "CommandSpeechRecognizer partial command result: \"$text\"")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
