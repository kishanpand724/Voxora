package com.example.data

import android.app.ActivityManager
import android.content.Context
import com.example.service.VoiceAssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AssistantStateRepository {

    private val _isAssistantActive = MutableStateFlow(false)
    val isAssistantActive: StateFlow<Boolean> = _isAssistantActive.asStateFlow()

    fun setAssistantActive(active: Boolean) {
        _isAssistantActive.value = active
    }

    fun syncServiceState(context: Context) {
        val running = isServiceRunning(context)
        if (running) {
            _isAssistantActive.value = true
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
