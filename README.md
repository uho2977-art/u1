# OpenClaw Voice

一个 Android 语音助手应用，连接到 OpenClaw Gateway，支持语音唤醒和连续对话。

## 功能特性

- 🎙️ **语音唤醒**: 说 "Hey OpenClaw" 唤醒助手
- 🔄 **连续对话**: 唤醒后可持续对话，无需重复唤醒
- 🔊 **语音播报**: 回复自动朗读出来
- 🤖 **多 Agent 支持**: 从服务器动态获取 Agent 列表
- 🌐 **局域网连接**: 连接本地 Gateway 服务
- 🌍 **中英文支持**: 支持中英文语音交互

## 架构

```
┌─────────────────────────────────────────────────┐
│                 Android App                     │
├─────────────────────────────────────────────────┤
│  Wake Word Engine │  Android SpeechRecognizer │
│  (离线唤醒)         │  (语音识别)               │
├─────────────────────────────────────────────────┤
│            HTTP / WebSocket Client             │
│         ↓ OpenClaw Gateway API                  │
├─────────────────────────────────────────────────┤
│         Android TTS / Server TTS                │
│         (语音播报)                               │
└─────────────────────────────────────────────────┘
```

## 交互流程

```
[唤醒态] 等待 "Hey OpenClaw"
    ↓
[监听态] 用户说话 → 语音识别
    ↓
[处理态] 发送到 Gateway → 显示"正在处理"
    ↓
[播报态] 收到回复 → TTS 朗读
    ↓
[监听态] 自动返回监听（连续对话）
    ↓ 超时10秒
[唤醒态] 返回等待唤醒词
```

## 构建

### 前置要求

- Android Studio Hedgehog 或更高版本
- JDK 17+
- Android SDK 34

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/uho2977-art/u1.git
cd u1

# 构建 Debug APK
./gradlew assembleDebug

# 或在 Android Studio 中打开项目构建
```

## 配置

### Gateway 连接

首次启动时会提示输入：
- **Gateway URL**: OpenClaw Gateway 地址（如 `http://192.168.1.100:18789`）
- **Token**: 认证令牌（可选，如果 Gateway 需要认证）

### Agent 选择

连接成功后，点击底部的机器人图标选择 Agent。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **网络**: OkHttp + Kotlin Coroutines
- **语音识别**: Android SpeechRecognizer
- **语音播报**: Android TextToSpeech
- **唤醒词**: 自定义幅度检测（可替换为 Porcupine/Vosk）

## 唤醒词引擎选项

当前使用简单的语音活动检测。生产环境推荐：

| 引擎 | 优点 | 缺点 |
|------|------|------|
| **Porcupine** | 高准确度，低延迟，完全离线 | 免费版有限制，商业需授权 |
| **Vosk** | 开源，离线，多语言 | 资源占用较高 |
| **Snowboy** | 开源 | 已停止维护 |

### 集成 Porcupine（推荐）

```kotlin
// build.gradle.kts
implementation("ai.picovoice:porcupine-android:3.0.1")

// 使用
import ai.picovoice.porcupine.*

val porcupine = Porcupine.Builder()
    .setAccessKey("YOUR_ACCESS_KEY")
    .setKeyword("hey openclaw")
    .build(context)
```

## API 参考

应用使用 OpenAI 兼容的 HTTP API：

```http
POST /v1/chat/completions
Authorization: Bearer <token>
Content-Type: application/json

{
  "model": "openclaw:<agentId>",
  "messages": [{"role": "user", "content": "..."}]
}
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！