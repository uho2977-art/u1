package com.openclaw.voice.voice

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

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
                
                while (isActive && isRunning) {
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
            sum += abs(buffer[i].toDouble())
        }
        return sum / size
    }
}