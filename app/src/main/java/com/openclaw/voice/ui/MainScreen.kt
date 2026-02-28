package com.openclaw.voice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openclaw.voice.ui.theme.OpenClawVoiceTheme

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        MainContent(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onWakeWordDetected = { viewModel.onWakeWordDetected() },
            onSpeechResult = { viewModel.onSpeechResult(it) },
            onSpeechError = { viewModel.onSpeechError(it) }
        )
        
        // Connection status overlay
        if (!uiState.isConnected && !uiState.showSettings) {
            ConnectionOverlay(
                uiState = uiState,
                onEvent = viewModel::onEvent
            )
        }
    }
}

@Composable
private fun MainContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onWakeWordDetected: () -> Unit,
    onSpeechResult: (String?) -> Unit,
    onSpeechError: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status indicator
        VoiceStateIndicator(
            voiceState = uiState.voiceState,
            modifier = Modifier.size(200.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Voice state text
        Text(
            text = when (uiState.voiceState) {
                VoiceState.IDLE -> "说 \"Hey OpenClaw\" 开始"
                VoiceState.WAKE_WORD_READY -> "请说..."
                VoiceState.LISTENING -> "正在听..."
                VoiceState.PROCESSING -> "正在处理..."
                VoiceState.SPEAKING -> "回复中..."
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Selected agent
        uiState.selectedAgent?.let { agent ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Response text (when speaking)
        if (uiState.responseText.isNotEmpty() && uiState.voiceState == VoiceState.SPEAKING) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.responseText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        
        // Bottom action bar
        Spacer(modifier = Modifier.weight(1f))
        
        BottomActionBar(
            uiState = uiState,
            onEvent = onEvent
        )
    }
}

@Composable
private fun VoiceStateIndicator(
    voiceState: VoiceState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (voiceState) {
            VoiceState.LISTENING, VoiceState.PROCESSING, VoiceState.SPEAKING -> 1.2f
            VoiceState.WAKE_WORD_READY -> 1.15f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val color = when (voiceState) {
        VoiceState.IDLE -> MaterialTheme.colorScheme.outline
        VoiceState.WAKE_WORD_READY -> MaterialTheme.colorScheme.tertiary
        VoiceState.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceState.PROCESSING -> MaterialTheme.colorScheme.secondary
        VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = modifier
            .scale(if (voiceState != VoiceState.IDLE) scale else 1f)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.2f),
                        color.copy(alpha = 0.05f)
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (voiceState) {
                VoiceState.IDLE -> Icons.Default.MicNone
                VoiceState.WAKE_WORD_READY -> Icons.Default.Mic
                VoiceState.LISTENING -> Icons.Default.Mic
                VoiceState.PROCESSING -> Icons.Default.HourglassTop
                VoiceState.SPEAKING -> Icons.Default.VolumeUp
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(80.dp)
        )
    }
}

@Composable
private fun BottomActionBar(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Agent selector button
        IconButton(
            onClick = { onEvent(UiEvent.ToggleAgentSelector) },
            enabled = uiState.isConnected && uiState.agents.isNotEmpty()
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = "选择 Agent",
                tint = if (uiState.selectedAgent != null) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.outline
            )
        }
        
        // Settings button
        IconButton(onClick = { onEvent(UiEvent.ToggleSettings) }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
    
    // Agent selector dialog
    if (uiState.showAgentSelector) {
        AgentSelectorDialog(
            agents = uiState.agents,
            selectedAgent = uiState.selectedAgent,
            onSelect = { agent -> onEvent(UiEvent.SelectAgent(agent)) },
            onDismiss = { onEvent(UiEvent.ToggleAgentSelector) }
        )
    }
    
    // Settings dialog
    if (uiState.showSettings) {
        SettingsDialog(
            currentUrl = uiState.gatewayUrl,
            currentToken = uiState.gatewayToken,
            onSave = { url, token -> onEvent(UiEvent.UpdateSettings(url, token)) },
            onDismiss = { onEvent(UiEvent.ToggleSettings) }
        )
    }
    
    // Error dialog
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { onEvent(UiEvent.DismissError) },
            title = { Text("错误") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { onEvent(UiEvent.DismissError) }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun ConnectionOverlay(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit
) {
    var url by remember { mutableStateOf(uiState.gatewayUrl) }
    var token by remember { mutableStateOf(uiState.gatewayToken) }
    
    AlertDialog(
        onDismissRequest = { },
        title = { Text("连接 OpenClaw Gateway") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Gateway URL") },
                    placeholder = { Text("http://192.168.1.100:18789") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token (可选)") },
                    placeholder = { Text("Gateway 认证 token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(UiEvent.Connect(url, token)) },
                enabled = url.isNotBlank()
            ) {
                Text("连接")
            }
        }
    )
}

@Composable
private fun AgentSelectorDialog(
    agents: List<AgentInfo>,
    selectedAgent: AgentInfo?,
    onSelect: (AgentInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 Agent") },
        text = {
            LazyColumn {
                items(agents) { agent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = agent.id == selectedAgent?.id,
                            onClick = { onSelect(agent) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            agent.description?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun SettingsDialog(
    currentUrl: String,
    currentToken: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }
    var token by remember { mutableStateOf(currentToken) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Gateway URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url, token) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}