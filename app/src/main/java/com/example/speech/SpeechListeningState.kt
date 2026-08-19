package com.example.speech

enum class SpeechListeningState {
    STOPPED,
    WAITING_FOR_WAKE_WORD,
    LISTENING_FOR_COMMAND,
    PROCESSING,
    ERROR
}

