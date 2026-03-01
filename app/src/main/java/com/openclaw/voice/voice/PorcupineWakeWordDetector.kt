package com.openclaw.voice.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineActivationException
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineInvalidArgumentException
import ai.picovoice.porcupine.PorcupineStopIterationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Porcupine-based wake word detector.
 * 
 * Requirements:
 * - Picovoice AccessKey (free tier available at https://picovoice.ai/)
 * - Built-in keywords or custom wake word model (.ppn file)
 * 
 * Built-in keywords available:
 * - "hey siri", "ok google", "alexa", "hey google"
 * - For custom wake word "hey openclaw", use Porcupine Console to create
 * 
 * Usage:
 * 1. Get free AccessKey from https://console.picovoice.ai/
 * 2. Create custom wake word or use built-in
 * 3. Call start() to begin listening
 * 4. onWakeWordDetected callback will be triggered when wake word is detected
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val keywordPath: String? = null, // Path to .ppn file for custom keyword, or null for built-in
    private val builtInKeyword: Porcupine.BuiltInKeyword? = null, // Built-in keyword to use
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "PorcupineWakeWord"
        
        // Porcupine's fixed sample rate
        const val SAMPLE_RATE = 16000
        
        // Built-in keywords mapping
        val BUILT_IN_KEYWORDS_MAP = mapOf(
            "hey google" to Porcupine.BuiltInKeyword.HEY_GOOGLE,
            "ok google" to Porcupine.BuiltInKeyword.OK_GOOGLE,
            "hey siri" to Porcupine.BuiltInKeyword.HEY_SIRI,
            "alexa" to Porcupine.BuiltInKeyword.ALEXA,
            "hey spot" to Porcupine.BuiltInKeyword.HEY_SPOT,
            "jarvis" to Porcupine.BuiltInKeyword.JARVIS,
            "computer" to Porcupine.BuiltInKeyword.COMPUTER,
            "grasshopper" to Porcupine.BuiltInKeyword.GRASSHOPPER
        )
    }
    
    private var porcupine: Porcupine? = null
    private var recordingJob: Job? = null
    private var isRunning = false
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Initialize and start wake word detection.
     * Call this from a background service.
     */
    fun start(): Boolean {
        if (isRunning) return true
        
        if (accessKey.isBlank()) {
            _error.value = "AccessKey 不能为空"
            return false
        }
        
        try {
            val builder = Porcupine.Builder()
                .setAccessKey(accessKey)
            
            // Configure keyword - either built-in or custom
            when {
                builtInKeyword != null -> {
                    builder.setKeyword(builtInKeyword)
                }
                keywordPath != null -> {
                    builder.setKeywordPath(keywordPath)
                }
                else -> {
                    // Default to Hey Google as fallback
                    builder.setKeyword(Porcupine.BuiltInKeyword.HEY_GOOGLE)
                }
            }
            
            porcupine = builder.build(context)
            
            isRunning = true
            startListening()
            _isListening.value = true
            _error.value = null
            Log.d(TAG, "Porcupine initialized successfully")
            return true
            
        } catch (e: PorcupineActivationException) {
            Log.e(TAG, "AccessKey activation failed: ${e.message}")
            _error.value = "激活失败: 请检查 AccessKey"
            return false
        } catch (e: PorcupineInvalidArgumentException) {
            Log.e(TAG, "Invalid argument: ${e.message}")
            _error.value = "参数错误: ${e.message}"
            return false
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine error: ${e.message}")
            _error.value = "启动失败: ${e.message}"
            return false
        }
    }
    
    private fun startListening() {
        recordingJob = scope.launch {
            val frameLength = porcupine?.frameLength ?: 512
            val audioBuffer = ShortArray(frameLength)
            
            val audioSource = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                audioBuffer.size * 2
            )
            
            try {
                audioSource.startRecording()
                
                while (isActive && isRunning) {
                    val numSamples = audioSource.read(audioBuffer, 0, audioBuffer.size)
                    if (numSamples > 0) {
                        try {
                            val result = porcupine?.process(audioBuffer) ?: -1
                            if (result >= 0) {
                                Log.d(TAG, "Wake word detected! Index: $result")
                                onWakeWordDetected()
                            }
                        } catch (e: PorcupineStopIterationException) {
                            // Stop iteration, break loop
                            break
                        }
                    }
                    delay(10) // Small delay to prevent busy loop
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission not granted")
                _error.value = "需要麦克风权限"
            } finally {
                try {
                    audioSource.stop()
                    audioSource.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Stop wake word detection and release resources.
     */
    fun stop() {
        isRunning = false
        _isListening.value = false
        
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            porcupine?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting Porcupine: ${e.message}")
        }
        porcupine = null
    }
    
    /**
     * Check if Porcupine is available with the given access key.
     */
    fun isAvailable(): Boolean {
        return porcupine != null && isRunning
    }
    
    /**
     * Get the frame length required by Porcupine.
     */
    fun getFrameLength(): Int {
        return porcupine?.frameLength ?: 512
    }
}