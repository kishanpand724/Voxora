package com.example.command

import android.content.Context

interface CommandHandler {
    /**
     * Returns true if this handler can process the normalized command text.
     */
    fun canHandle(normalizedText: String): Boolean

    /**
     * Executes the command and returns a CommandResult.
     */
    fun execute(context: Context, rawCommand: String, normalizedText: String): CommandResult
}
