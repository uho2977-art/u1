package com.openclaw.voice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openclaw.voice.ui.MainScreen
import com.openclaw.voice.ui.MainViewModel
import com.openclaw.voice.ui.theme.OpenClawVoiceTheme
import com.openclaw.voice.voice.VoiceService

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            OpenClawVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestPermissions()
    }
    
    private fun checkAndRequestPermissions() {
        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }
    
    private fun startVoiceService() {
        val intent = Intent(this, VoiceService::class.java)
        startForegroundService(intent)
    }
}