package com.openclaw.voice

import android.app.Application
import android.speech.tts.TextToSpeech
import com.openclaw.voice.data.SettingsRepository
import com.openclaw.voice.network.OpenClawClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

class App : Application(), TextToSpeech.OnInitListener {

    private val applicationScope = CoroutineScope(Dispatchers.Default)
    
    lateinit var settingsRepository: SettingsRepository
        private set
    
    lateinit var openClawClient: OpenClawClient
        private set
    
    var tts: TextToSpeech? = null
        private set
    
    var ttsReady = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        settingsRepository = SettingsRepository(this)
        openClawClient = OpenClawClient(settingsRepository)
        
        initTts()
    }
    
    private fun initTts() {
        tts = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            // Set default language, will be updated based on settings
            updateTtsLanguage()
        }
    }
    
    fun updateTtsLanguage() {
        applicationScope.launch {
            val lang = settingsRepository.language.first()
            val locale = when (lang) {
                "zh" -> Locale.CHINESE
                else -> Locale.ENGLISH
            }
            tts?.language = locale
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}