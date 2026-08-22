package com.example.command

sealed interface CommandResult {
    val rawCommand: String
    val resultMessage: String
    val isSuccess: Boolean

    data class Success(
        override val rawCommand: String,
        override val resultMessage: String
    ) : CommandResult {
        override val isSuccess: Boolean = true
    }

    data class Error(
        override val rawCommand: String,
        override val resultMessage: String
    ) : CommandResult {
        override val isSuccess: Boolean = false
    }

    data class Unknown(
        override val rawCommand: String,
        override val resultMessage: String = "Command not recognized"
    ) : CommandResult {
        override val isSuccess: Boolean = false
    }
}
