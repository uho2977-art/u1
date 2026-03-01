package com.openclaw.voice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

data class SettingsState(
    val gatewayUrl: String = "",
    val gatewayToken: String = "",
    val porcupineAccessKey: String = "",
    val wakeWordMode: String = "amplitude",
    val language: String = "zh",
    val isConnecting: Boolean = false,
    val error: String? = null
)

sealed interface SettingsEvent {
    data class GatewayUrlChanged(val url: String) : SettingsEvent
    data class GatewayTokenChanged(val token: String) : SettingsEvent
    data class PorcupineKeyChanged(val key: String) : SettingsEvent
    data class WakeWordModeChanged(val mode: String) : SettingsEvent
    data class LanguageChanged(val language: String) : SettingsEvent
    data object Connect : SettingsEvent
    data object Save : SettingsEvent
    data object DismissError : SettingsEvent
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit
) {
    var tokenVisible by remember { mutableStateOf(false) }
    var accessKeyVisible by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gateway Configuration Section
            Text(
                text = "OpenClaw Gateway 配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedTextField(
                value = state.gatewayUrl,
                onValueChange = { onEvent(SettingsEvent.GatewayUrlChanged(it)) },
                label = { Text("Gateway URL") },
                placeholder = { Text("http://192.168.1.100:18789") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("OpenClaw Gateway 地址和端口") }
            )
            
            OutlinedTextField(
                value = state.gatewayToken,
                onValueChange = { onEvent(SettingsEvent.GatewayTokenChanged(it)) },
                label = { Text("Gateway Token") },
                placeholder = { Text("可选，如果 Gateway 需要认证") },
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (tokenVisible) "隐藏" else "显示"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Wake Word Configuration Section
            Text(
                text = "唤醒词配置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Wake Word Mode Selection
            Text(
                text = "唤醒检测模式",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = state.wakeWordMode == "amplitude",
                        onClick = { onEvent(SettingsEvent.WakeWordModeChanged("amplitude")) }
                    )
                    Column {
                        Text("简易模式", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "基于音量检测，无需配置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = state.wakeWordMode == "porcupine",
                        onClick = { onEvent(SettingsEvent.WakeWordModeChanged("porcupine")) }
                    )
                    Column {
                        Text("Porcupine 引擎", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "高精度，需要 AccessKey",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            
            // Porcupine Configuration (only shown when Porcupine mode is selected)
            if (state.wakeWordMode == "porcupine") {
                OutlinedTextField(
                    value = state.porcupineAccessKey,
                    onValueChange = { onEvent(SettingsEvent.PorcupineKeyChanged(it)) },
                    label = { Text("Picovoice AccessKey") },
                    placeholder = { Text("从 console.picovoice.ai 获取") },
                    singleLine = true,
                    visualTransformation = if (accessKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { accessKeyVisible = !accessKeyVisible }) {
                            Icon(
                                imageVector = if (accessKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (accessKeyVisible) "隐藏" else "显示"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("免费 AccessKey 每月可使用") }
                )
                
                Text(
                    text = "提示：获取免费 AccessKey 请访问 console.picovoice.ai",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Language Configuration
            Text(
                text = "语言设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.language == "zh",
                    onClick = { onEvent(SettingsEvent.LanguageChanged("zh")) }
                )
                Text("中文", modifier = Modifier.padding(start = 8.dp))
                
                Spacer(modifier = Modifier.width(24.dp))
                
                RadioButton(
                    selected = state.language == "en",
                    onClick = { onEvent(SettingsEvent.LanguageChanged("en")) }
                )
                Text("English", modifier = Modifier.padding(start = 8.dp))
            }
            
            // Error Message
            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onEvent(SettingsEvent.DismissError) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                
                Button(
                    onClick = { onEvent(SettingsEvent.Save) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
            
            // Connection Test Button
            Button(
                onClick = { onEvent(SettingsEvent.Connect) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.gatewayUrl.isNotBlank() && !state.isConnecting
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.isConnecting) "连接中..." else "测试连接")
            }
        }
    }
}