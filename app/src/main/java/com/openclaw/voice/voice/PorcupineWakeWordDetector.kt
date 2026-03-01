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
 * - Custom wake word model or use built-in keywords
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
    private val keyword: String = "hey google", // Built-in keyword or path to .ppn file
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "PorcupineWakeWord"
        
        // Built-in keywords (lowercase)
        val BUILT_IN_KEYWORDS = listOf(
            "hey google",
            "ok google",
            "hey siri",
            "alexa"
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
        
        try {
            // Initialize Porcupine
            porcupine = if (BUILT_IN_KEYWORDS.contains(keyword.lowercase())) {
                // Use built-in keyword
                Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(Porcupine.BuiltInKeyword.valueOf(keyword.uppercase().replace(" ", "_")))
                    .build(context)
            } else {
                // Use custom keyword path (.ppn file)
                // For custom wake word, provide path to .ppn file
                Porcupine.Builder()
                    .setAccessKey(accessKey)
                    .setCustomKeywordPath(keyword)
                    .build(context)
            }
            
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
            val audioBuffer = ShortArray(porcupine?.frameLength ?: 512)
            val audioSource = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                Porcupine.SAMPLE_RATE,
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
                                Log.d(TAG, "Wake word detected!")
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
                audioSource.stop()
                audioSource.release()
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
}

/**
 * Alternative wake word detector using amplitude-based detection.
 * Use this when Porcupine AccessKey is not available.
 * 
 * This is a simplified implementation that detects speech activity
 * but cannot recognize specific wake words. The app should use
 * Android's SpeechRecognizer to check if the detected speech
 * contains the wake word.
 */
class AmplitudeWakeWordDetector(
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "AmplitudeWakeWord"
        private const val SAMPLE_RATE = 16000
        private const val AMPLITUDE_THRESHOLD = 2000.0
        private const val SPEECH_DURATION_MS = 500L
    }
    
    private var job: Job? = null
    private var isRunning = false
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private var speechStartTime = 0L
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        job = CoroutineScope(Dispatchers.IO).launch {
            val bufferSize = android.media.AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            
            val audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            try {
                if (audioRecord.state != android.media.AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord not initialized")
                    return@launch
                }
                
                audioRecord.startRecording()
                val buffer = ShortArray(bufferSize / 2)
                
                while (isRunning) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateAmplitude(buffer, read)
                        
                        if (amplitude > AMPLITUDE_THRESHOLD) {
                            if (speechStartTime == 0L) {
                                speechStartTime = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - speechStartTime > SPEECH_DURATION_MS) {
                                // Speech detected for sufficient duration
                                // Trigger wake word callback
                                onWakeWordDetected()
                                _isListening.value = true
                            }
                        } else {
                            speechStartTime = 0L
                            _isListening.value = false
                        }
                    }
                    
                    delay(100)
                }
                
                audioRecord.stop()
            } catch (e: SecurityException) {
                Log.e(TAG, "Microphone permission not granted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection: ${e.message}")
            } finally {
                audioRecord.release()
            }
        }
    }
    
    fun stop() {
        isRunning = false
        _isListening.value = false
        job?.cancel()
        job = null
    }
    
    private fun calculateAmplitude(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += kotlin.math.abs(buffer[i].toDouble())
        }
        return sum / size
    }
}