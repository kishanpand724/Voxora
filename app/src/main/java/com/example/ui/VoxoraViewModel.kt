package com.example.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class VoxoraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VoxoraUiState())
    val uiState: StateFlow<VoxoraUiState> = _uiState.asStateFlow()

    fun toggleAssistantState() {
        _uiState.update { currentState ->
            currentState.copy(isAssistantActive = !currentState.isAssistantActive)
        }
    }

    fun setAssistantActive(active: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(isAssistantActive = active)
        }
    }
}
