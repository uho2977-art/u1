package com.openclaw.voice.ui

data class UiState(
    // Connection state
    val isConnected: Boolean = false,
    val gatewayUrl: String = "",
    val gatewayToken: String = "",
    
    // Agent state
    val agents: List<AgentInfo> = emptyList(),
    val selectedAgent: AgentInfo? = null,
    
    // Voice state
    val voiceState: VoiceState = VoiceState.IDLE,
    val userInput: String = "",
    val responseText: String = "",
    
    // Settings
    val showSettings: Boolean = false,
    val showAgentSelector: Boolean = false,
    
    // Error
    val error: String? = null
)

data class AgentInfo(
    val id: String,
    val name: String,
    val description: String? = null
)

enum class VoiceState {
    IDLE,           // Waiting for wake word
    WAKE_WORD_READY, // Just woke up, ready to listen
    LISTENING,      // User is speaking
    PROCESSING,     // Sending to server, waiting for response
    SPEAKING        // TTS is reading the response
}

sealed interface UiEvent {
    data class Connect(val url: String, val token: String) : UiEvent
    object Disconnect : UiEvent
    data class SelectAgent(val agent: AgentInfo) : UiEvent
    object StartListening : UiEvent
    data class MessageReceived(val message: String) : UiEvent
    object DismissError : UiEvent
    object ToggleSettings : UiEvent
    object ToggleAgentSelector : UiEvent
    data class UpdateSettings(val url: String, val token: String) : UiEvent
}