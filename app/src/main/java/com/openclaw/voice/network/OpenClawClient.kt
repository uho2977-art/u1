package com.openclaw.voice.network

import com.openclaw.voice.ui.AgentInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenClawClient(private val settingsRepository: com.openclaw.voice.data.SettingsRepository) {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var webSocket: WebSocket? = null
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()
    
    fun connect(url: String, token: String?) {
        // For now, we'll use HTTP API. WebSocket can be added later for real-time features.
        _connectionState.value = true
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Disconnected")
        webSocket = null
        _connectionState.value = false
        _agents.value = emptyList()
    }
    
    suspend fun fetchAgents(): List<AgentInfo> {
        val url = settingsRepository.gatewayUrl.value
        val token = settingsRepository.gatewayToken.value
        
        // Try to fetch agents via HTTP API
        // OpenClaw Gateway exposes agent list via WebSocket or HTTP
        // For simplicity, we'll use a default "main" agent if fetch fails
        
        try {
            val request = Request.Builder()
                .url("${url}/rpc")
                .addHeader("Authorization", "Bearer $token")
                .post("""{"method":"agents.list","params":{}}""".toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return getDefaultAgents()
                val agentsResponse = json.decodeFromString<AgentsListResponse>(body)
                val agentList = agentsResponse.agents?.map { 
                    AgentInfo(
                        id = it.id ?: "main",
                        name = it.name ?: it.id ?: "Main",
                        description = it.description
                    )
                } ?: getDefaultAgents()
                _agents.value = agentList
                return agentList
            }
        } catch (e: Exception) {
            // Fallback to default agents if fetch fails
        }
        
        return getDefaultAgents()
    }
    
    private fun getDefaultAgents(): List<AgentInfo> {
        return listOf(
            AgentInfo(
                id = "main",
                name = "Main",
                description = "Default agent"
            )
        )
    }
    
    suspend fun sendMessage(message: String, agentId: String): String {
        val url = settingsRepository.gatewayUrl.value
        val token = settingsRepository.gatewayToken.value
        
        // Use OpenAI-compatible chat completions endpoint
        val requestBody = ChatRequest(
            model = "openclaw:$agentId",
            messages = listOf(
                ChatMessage(role = "user", content = message)
            ),
            stream = false
        )
        
        val request = Request.Builder()
            .url("${url.trimEnd('/')}/v1/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("x-openclaw-agent-id", agentId)
            .post(json.encodeToString(ChatRequest.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }
        
        val body = response.body?.string() ?: throw Exception("Empty response")
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        
        return chatResponse.choices?.firstOrNull()?.message?.content 
            ?: throw Exception("No response content")
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatResponse(
    val choices: List<ChatChoice>? = null
)

@Serializable
data class ChatChoice(
    val message: ChatMessage? = null
)

@Serializable
data class AgentsListResponse(
    val agents: List<AgentResponse>? = null
)

@Serializable
data class AgentResponse(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null
)