package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AssistantStateRepository
import com.example.service.VoiceAssistantService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoxoraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VoxoraUiState(isAssistantActive = AssistantStateRepository.isAssistantActive.value))
    val uiState: StateFlow<VoxoraUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            AssistantStateRepository.isAssistantActive.collect { active ->
                _uiState.update { currentState ->
                    currentState.copy(isAssistantActive = active)
                }
            }
        }
        viewModelScope.launch {
            AssistantStateRepository.listeningState.collect { state ->
                _uiState.update { currentState ->
                    currentState.copy(listeningState = state)
                }
            }
        }
        viewModelScope.launch {
            AssistantStateRepository.lastRecognizedText.collect { text ->
                _uiState.update { currentState ->
                    currentState.copy(lastRecognizedText = text)
                }
            }
        }
    }

    fun syncState(context: Context) {
        AssistantStateRepository.syncServiceState(context)
        _uiState.update { 
            it.copy(
                isAssistantActive = AssistantStateRepository.isAssistantActive.value,
                listeningState = AssistantStateRepository.listeningState.value,
                lastRecognizedText = AssistantStateRepository.lastRecognizedText.value
            )
        }
    }


    fun startAssistant(context: Context) {
        VoiceAssistantService.startService(context)
    }

    fun stopAssistant(context: Context) {
        VoiceAssistantService.stopService(context)
    }

    fun toggleAssistant(context: Context) {
        if (uiState.value.isAssistantActive) {
            stopAssistant(context)
        } else {
            startAssistant(context)
        }
    }
}


