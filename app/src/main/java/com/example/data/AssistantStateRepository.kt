package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import com.example.command.CommandResult
import com.example.service.VoiceAssistantService
import com.example.speech.SpeechListeningState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AssistantStateRepository {

    private const val PREFS_NAME = "voxora_assistant_prefs"
    private const val KEY_EXPLICITLY_STOPPED = "key_explicitly_stopped"

    private val _isAssistantActive = MutableStateFlow(false)
    val isAssistantActive: StateFlow<Boolean> = _isAssistantActive.asStateFlow()

    private val _listeningState = MutableStateFlow(SpeechListeningState.STOPPED)
    val listeningState: StateFlow<SpeechListeningState> = _listeningState.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow<String?>(null)
    val lastRecognizedText: StateFlow<String?> = _lastRecognizedText.asStateFlow()

    private val _lastExecutionResult = MutableStateFlow<CommandResult?>(null)
    val lastExecutionResult: StateFlow<CommandResult?> = _lastExecutionResult.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setExplicitlyStopped(context: Context, stopped: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_EXPLICITLY_STOPPED, stopped).apply()
        _isAssistantActive.value = !stopped
        if (stopped) {
            _listeningState.value = SpeechListeningState.STOPPED
        }
    }

    fun isExplicitlyStopped(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_EXPLICITLY_STOPPED, true)
    }

    fun setAssistantActive(active: Boolean) {
        _isAssistantActive.value = active
        if (!active) {
            _listeningState.value = SpeechListeningState.STOPPED
        } else if (_listeningState.value == SpeechListeningState.STOPPED) {
            _listeningState.value = SpeechListeningState.LISTENING_FOR_COMMAND
        }
    }

    fun updateListeningState(state: SpeechListeningState) {
        _listeningState.value = state
    }

    fun updateRecognizedText(text: String?) {
        _lastRecognizedText.value = text
    }

    fun updateExecutionResult(result: CommandResult?) {
        _lastExecutionResult.value = result
    }

    fun syncServiceState(context: Context) {
        val running = isServiceRunning(context)
        if (running) {
            _isAssistantActive.value = true
            if (_listeningState.value == SpeechListeningState.STOPPED) {
                _listeningState.value = SpeechListeningState.LISTENING_FOR_COMMAND
            }
        } else if (isExplicitlyStopped(context)) {
            _isAssistantActive.value = false
            _listeningState.value = SpeechListeningState.STOPPED
        }
    }



    private fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (VoiceAssistantService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}


