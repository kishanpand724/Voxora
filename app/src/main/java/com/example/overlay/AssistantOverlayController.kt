package com.example.overlay

import com.example.speech.SpeechListeningState

/**
 * Architectural contract for future floating assistant overlay.
 *
 * When wake-word ("Hey Nova") is detected while Voxora is backgrounded:
 * 1. Overlay shows "Nova Activated / Listening" state without opening the full app UI.
 * 2. Speech recognition converts user speech to text.
 * 3. Overlay transitions to "Processing" state.
 * 4. Overlay automatically disappears upon command execution completion.
 */
interface AssistantOverlayController {
    fun showOverlay()
    fun updateState(state: SpeechListeningState, recognizedText: String?)
    fun hideOverlay()
}

/**
 * Default stub controller preparing the architecture for future overlay integration.
 */
class NoOpAssistantOverlayController : AssistantOverlayController {
    override fun showOverlay() {}
    override fun updateState(state: SpeechListeningState, recognizedText: String?) {}
    override fun hideOverlay() {}
}
