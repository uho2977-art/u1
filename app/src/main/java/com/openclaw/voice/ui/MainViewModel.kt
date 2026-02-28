package com.openclaw.voice.ui

import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.voice.App
import com.openclaw.voice.data.SettingsRepository
import com.openclaw.voice.network.OpenClawClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val settingsRepository: SettingsRepository = App.instance.settingsRepository
    private val openClawClient: OpenClawClient = App.instance.openClawClient
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private var connectionJob: Job? = null
    private var silenceDetectionJob: Job? = null
    
    init {
        loadSettings()
        observeConnectionState()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.gatewayUrl.collect { url ->
                _uiState.update { it.copy(gatewayUrl = url) }
            }
        }
        viewModelScope.launch {
            settingsRepository.gatewayToken.collect { token ->
                _uiState.update { it.copy(gatewayToken = token) }
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedAgentId.collect { agentId ->
                if (agentId != null && _uiState.value.agents.isNotEmpty()) {
                    val agent = _uiState.value.agents.find { it.id == agentId }
                    _uiState.update { it.copy(selectedAgent = agent) }
                }
            }
        }
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            openClawClient.connectionState.collect { state ->
                _uiState.update { it.copy(isConnected = state) }
            }
        }
        viewModelScope.launch {
            openClawClient.agents.collect { agents ->
                _uiState.update { uiState ->
                    val updatedState = uiState.copy(agents = agents)
                    // Auto-select first agent if none selected
                    if (uiState.selectedAgent == null && agents.isNotEmpty()) {
                        val savedAgentId = settingsRepository.selectedAgentId.value
                        val agent = if (savedAgentId != null) {
                            agents.find { it.id == savedAgentId } ?: agents.first()
                        } else {
                            agents.first()
                        }
                        updatedState.copy(selectedAgent = agent)
                    } else {
                        updatedState
                    }
                }
            }
        }
    }
    
    fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Connect -> connect(event.url, event.token)
            is UiEvent.Disconnect -> disconnect()
            is UiEvent.SelectAgent -> selectAgent(event.agent)
            is UiEvent.StartListening -> startListening()
            is UiEvent.MessageReceived -> handleMessage(event.message)
            is UiEvent.DismissError -> dismissError()
            is UiEvent.ToggleSettings -> toggleSettings()
            is UiEvent.ToggleAgentSelector -> toggleAgentSelector()
            is UiEvent.UpdateSettings -> updateSettings(event.url, event.token)
        }
    }
    
    private fun connect(url: String, token: String) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            try {
                settingsRepository.setGatewayUrl(url)
                settingsRepository.setGatewayToken(token)
                openClawClient.connect(url, token)
                openClawClient.fetchAgents()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "连接失败: ${e.message}") }
            }
        }
    }
    
    private fun disconnect() {
        connectionJob?.cancel()
        openClawClient.disconnect()
        _uiState.update { 
            it.copy(
                isConnected = false,
                voiceState = VoiceState.IDLE,
                responseText = ""
            )
        }
    }
    
    private fun selectAgent(agent: AgentInfo) {
        settingsRepository.setSelectedAgentId(agent.id)
        _uiState.update { 
            it.copy(
                selectedAgent = agent,
                showAgentSelector = false
            )
        }
    }
    
    private fun startListening() {
        _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
    }
    
    fun onSpeechResult(text: String?) {
        if (text.isNullOrBlank()) {
            _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            return
        }
        
        _uiState.update { 
            it.copy(
                userInput = text,
                voiceState = VoiceState.PROCESSING
            )
        }
        
        sendMessage(text)
    }
    
    fun onSpeechError(errorCode: Int) {
        val errorMessage = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
            SpeechRecognizer.ERROR_AUDIO -> "音频错误"
            SpeechRecognizer.ERROR_NETWORK -> "网络错误"
            SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
            else -> "语音识别错误: $errorCode"
        }
        _uiState.update { 
            it.copy(
                voiceState = VoiceState.IDLE,
                error = errorMessage
            )
        }
    }
    
    private fun sendMessage(message: String) {
        val agentId = _uiState.value.selectedAgent?.id ?: "main"
        
        viewModelScope.launch {
            try {
                val response = openClawClient.sendMessage(message, agentId)
                handleMessage(response)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        voiceState = VoiceState.IDLE,
                        error = "发送失败: ${e.message}"
                    )
                }
            }
        }
    }
    
    private fun handleMessage(message: String) {
        _uiState.update { it.copy(responseText = message) }
        speakAndReturnToListening(message)
    }
    
    private fun speakAndReturnToListening(text: String) {
        _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
        
        val tts = App.instance.tts
        if (tts == null || !App.instance.ttsReady) {
            // TTS not ready, just show text and return to listening
            startSilenceTimer()
            return
        }
        
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "openclaw_tts")
        
        // Wait for TTS to finish, then check for silence to return to wake word mode
        // This is a simplified approach - in production we'd use UtteranceProgressListener
        viewModelScope.launch {
            delay(text.length * 80L + 500) // Rough estimate: ~80ms per character
            // Return to listening state for continuous dialog
            _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
            startSilenceTimer()
        }
    }
    
    private fun startSilenceTimer() {
        silenceDetectionJob?.cancel()
        silenceDetectionJob = viewModelScope.launch {
            delay(SILENCE_TIMEOUT_MS)
            // If still in listening state after timeout, go back to idle (wake word mode)
            if (_uiState.value.voiceState == VoiceState.LISTENING) {
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }
    
    private fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }
    
    private fun toggleAgentSelector() {
        _uiState.update { it.copy(showAgentSelector = !it.showAgentSelector) }
    }
    
    private fun updateSettings(url: String, token: String) {
        settingsRepository.setGatewayUrl(url)
        settingsRepository.setGatewayToken(token)
        _uiState.update { 
            it.copy(
                gatewayUrl = url,
                gatewayToken = token,
                showSettings = false
            )
        }
    }
    
    fun onWakeWordDetected() {
        silenceDetectionJob?.cancel()
        _uiState.update { it.copy(voiceState = VoiceState.WAKE_WORD_READY) }
        
        // Auto-transition to listening after a short delay
        viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(voiceState = VoiceState.LISTENING) }
        }
    }
    
    companion object {
        private const val SILENCE_TIMEOUT_MS = 10000L // 10 seconds of silence returns to wake word mode
    }
}