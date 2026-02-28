package com.openclaw.voice.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.openclaw.voice.App
import com.openclaw.voice.MainActivity
import com.openclaw.voice.R
import com.openclaw.voice.ui.VoiceState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    private var wakeWordDetector: WakeWordDetector? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _serviceState = MutableStateFlow(VoiceState.IDLE)
    val serviceState: StateFlow<VoiceState> = _serviceState.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onSpeechResult: ((String?) -> Unit)? = null
    var onSpeechError: ((Int) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): VoiceService = this@VoiceService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWakeWordDetector()
        initSpeechRecognizer()
        initTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        return START_STICKY
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            updateTtsLanguage()
        }
    }
    
    private fun updateTtsLanguage() {
        val lang = runBlocking { App.instance.settingsRepository.language.first() }
        val locale = when (lang) {
            "zh" -> Locale.CHINESE
            else -> Locale.ENGLISH
        }
        tts?.language = locale
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "OpenClaw Voice",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Voice assistant background service"
        }
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenClaw Voice")
            .setContentText(getStatusText())
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun getStatusText(): String {
        return when (_serviceState.value) {
            VoiceState.IDLE -> "等待唤醒词..."
            VoiceState.WAKE_WORD_READY -> "准备就绪"
            VoiceState.LISTENING -> "正在聆听..."
            VoiceState.PROCESSING -> "正在处理..."
            VoiceState.SPEAKING -> "正在回复..."
        }
    }

    private fun initWakeWordDetector() {
        wakeWordDetector = WakeWordDetector(
            wakeWord = "hey openclaw",
            onWakeWordDetected = {
                _serviceState.value = VoiceState.WAKE_WORD_READY
                onWakeWordDetected?.invoke()
                startListening()
            }
        )
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    onListeningStateChanged?.invoke(true)
                }
                
                override fun onBeginningOfSpeech() {}
                
                override fun onRmsChanged(rmsdB: Float) {}
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    _isListening.value = false
                    onListeningStateChanged?.invoke(false)
                }
                
                override fun onError(error: Int) {
                    _isListening.value = false
                    onListeningStateChanged?.invoke(false)
                    onSpeechError?.invoke(error)
                }
                
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    onListeningStateChanged?.invoke(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    onSpeechResult?.invoke(matches?.firstOrNull())
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Can be used for real-time feedback
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    private fun initTts() {
        tts = TextToSpeech(this, this)
    }

    fun startListening() {
        if (_isListening.value) return
        
        _serviceState.value = VoiceState.LISTENING
        
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        speechRecognizer?.startListening(intent)
        updateNotification()
    }
    
    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
        onListeningStateChanged?.invoke(false)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            onDone?.invoke()
            return
        }
        
        _serviceState.value = VoiceState.SPEAKING
        updateNotification()
        
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "openclaw_tts")
        
        // Approximate completion callback
        serviceScope.launch {
            delay(text.length * 80L + 500)
            onDone?.invoke()
        }
    }
    
    fun setProcessing() {
        _serviceState.value = VoiceState.PROCESSING
        updateNotification()
    }
    
    fun setIdle() {
        _serviceState.value = VoiceState.IDLE
        updateNotification()
    }
    
    fun startWakeWordDetection() {
        wakeWordDetector?.start()
        setIdle()
    }
    
    fun stopWakeWordDetection() {
        wakeWordDetector?.stop()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        tts?.shutdown()
        wakeWordDetector?.stop()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "openclaw_voice_channel"
        const val NOTIFICATION_ID = 1001
    }
}