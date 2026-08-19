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
            listeningState == SpeechListeningState.WAITING_FOR_NOVA -> "WAITING FOR 'NOVA'"
            listeningState == SpeechListeningState.LISTENING_FOR_COMMAND -> "LISTENING FOR COMMAND"
            listeningState == SpeechListeningState.PROCESSING -> "PROCESSING COMMAND"
            listeningState == SpeechListeningState.ERROR -> "LISTENING ERROR"
            else -> "ASSISTANT ACTIVE"
        }

    val buttonText: String
        get() = if (isAssistantActive) "STOP ASSISTANT" else "START ASSISTANT"

    val infoMessage: String
        get() = when {
            !isAssistantActive -> "Start Voxora to activate your voice assistant."
            listeningState == SpeechListeningState.WAITING_FOR_NOVA -> "Say 'Nova' to activate..."
            listeningState == SpeechListeningState.LISTENING_FOR_COMMAND -> "Listening for your command..."
            listeningState == SpeechListeningState.PROCESSING -> "Processing command..."
            listeningState == SpeechListeningState.ERROR -> "Listening error occurred. Retrying..."
            else -> "Voxora is active and listening in background."
        }
}



