package com.example.command

import android.content.Context
import android.util.Log

class CommandProcessor(
    private val context: Context,
    private val handlers: List<CommandHandler> = listOf(
        AppLauncherCommandHandler()
    )
) {
    companion object {
        private const val TAG = "CommandProcessor"
    }

    /**
     * Normalizes input text and routes to appropriate handler.
     */
    fun processCommand(rawCommand: String): CommandResult {
        val normalized = rawCommand
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")

        Log.d(TAG, "Processing command. Raw: \"$rawCommand\", Normalized: \"$normalized\"")

        for (handler in handlers) {
            if (handler.canHandle(normalized)) {
                val result = handler.execute(context, rawCommand, normalized)
                Log.d(TAG, "Command result from ${handler.javaClass.simpleName}: $result")
                return result
            }
        }

        Log.d(TAG, "No handler matched for command: \"$rawCommand\"")
        return CommandResult.Unknown(
            rawCommand = rawCommand,
            resultMessage = "Command not recognized"
        )
    }
}
