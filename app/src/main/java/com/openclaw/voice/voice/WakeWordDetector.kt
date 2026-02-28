package com.openclaw.voice.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Wake word detector using amplitude-based detection.
 * For production, consider integrating a proper wake word engine like:
 * - Porcupine (Picovoice) - https://picovoice.ai/
 * - Vosk - https://alphacephei.com/vosk/
 * - Snowboy (deprecated but open source)
 * 
 * This is a simplified implementation that detects speech activity
 * and triggers on the phrase "hey openclaw" using pattern matching
 * on recognized speech.
 */
class WakeWordDetector(
    private val wakeWord: String = "hey openclaw",
    private val onWakeWordDetected: () -> Unit
) {
    private var job: Job? = null
    private var isRunning = false
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    // Audio configuration for simple amplitude detection
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // Threshold for detecting speech activity
    private val amplitudeThreshold = 1500
    
    fun start() {
        if (isRunning) return
        isRunning = true
        
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    return@launch
                }
                
                audioRecord.startRecording()
                val buffer = ShortArray(bufferSize / 2)
                
                while (isRunning) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateAmplitude(buffer, read)
                        
                        if (amplitude > amplitudeThreshold) {
                            // Speech activity detected
                            // In a real implementation, this would trigger the speech recognizer
                            // which would then check for the wake word
                            _isListening.value = true
                        } else {
                            _isListening.value = false
                        }
                    }
                    
                    delay(100) // Check every 100ms
                }
                
                audioRecord.stop()
                audioRecord.release()
            } catch (e: SecurityException) {
                // Microphone permission not granted
            } catch (e: Exception) {
                // Handle other errors
            }
        }
    }
    
    fun stop() {
        isRunning = false
        job?.cancel()
        job = null
        _isListening.value = false
    }
    
    private fun calculateAmplitude(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        return sum / size
    }
    
    /**
     * Check if the recognized text contains the wake word.
     * This should be called from the SpeechRecognizer callback.
     */
    fun checkForWakeWord(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        
        val normalizedText = text.lowercase().trim()
        val normalizedWakeWord = wakeWord.lowercase()
        
        // Check for exact match or containing the wake word
        if (normalizedText == normalizedWakeWord || 
            normalizedText.contains(normalizedWakeWord)) {
            onWakeWordDetected()
            return true
        }
        
        return false
    }
}

/**
 * Wake word detection modes:
 * 
 * 1. Always-on mode (consumes more battery):
 *    - Continuously listens for wake word
 *    - Requires foreground service with microphone type
 * 
 * 2. Tap-to-talk mode (saves battery):
 *    - User taps button to start listening
 *    - No need for always-on wake word detection
 * 
 * 3. Hybrid mode:
 *    - Use amplitude detection to detect speech activity
 *    - Only activate speech recognizer when speech is detected
 *    - Balance between battery and convenience
 * 
 * For production apps, consider these alternatives:
 * 
 * Porcupine (recommended):
 * - Pros: High accuracy, low latency, runs fully offline
 * - Cons: Free tier limited, commercial license required
 * - Integration: Add picovoice-android dependency
 * 
 * Vosk:
 * - Pros: Open source, runs offline, supports multiple languages
 * - Cons: Higher resource usage, slightly slower
 * - Integration: Add vosk-android dependency
 */