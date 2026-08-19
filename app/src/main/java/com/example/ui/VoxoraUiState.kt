package com.example.ui

data class VoxoraUiState(
    val isAssistantActive: Boolean = false
) {
    val statusText: String
        get() = if (isAssistantActive) "ASSISTANT ACTIVE" else "ASSISTANT STOPPED"

    val buttonText: String
        get() = if (isAssistantActive) "STOP ASSISTANT" else "START ASSISTANT"

    val infoMessage: String
        get() = if (isAssistantActive) "Voxora is currently active." else "Start Voxora to activate your voice assistant."
}
