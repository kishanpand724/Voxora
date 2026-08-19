package com.example.ui

import com.example.speech.SpeechListeningState

data class VoxoraUiState(
    val isAssistantActive: Boolean = false,
    val listeningState: SpeechListeningState = SpeechListeningState.STOPPED,
    val lastRecognizedText: String? = null
) {
    val statusText: String
        get() = when {
            !isAssistantActive -> "ASSISTANT STOPPED"
            listeningState == SpeechListeningState.LISTENING -> "LISTENING"
            listeningState == SpeechListeningState.PROCESSING -> "PROCESSING"
            listeningState == SpeechListeningState.ERROR -> "LISTENING ERROR"
            else -> "ASSISTANT ACTIVE"
        }

    val buttonText: String
        get() = if (isAssistantActive) "STOP ASSISTANT" else "START ASSISTANT"

    val infoMessage: String
        get() = when {
            !isAssistantActive -> "Start Voxora to activate your voice assistant."
            listeningState == SpeechListeningState.LISTENING -> "Listening for spoken commands..."
            listeningState == SpeechListeningState.PROCESSING -> "Processing speech input..."
            listeningState == SpeechListeningState.ERROR -> "Listening error occurred. Retrying..."
            else -> "Voxora is active and listening in background."
        }
}

