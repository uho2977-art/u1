package com.openclaw.voice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openclaw_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        val SELECTED_AGENT_ID = stringPreferencesKey("selected_agent_id")
        val LANGUAGE = stringPreferencesKey("language")
        val WAKE_WORD = stringPreferencesKey("wake_word")
        val PORCUPINE_ACCESS_KEY = stringPreferencesKey("porcupine_access_key")
        val WAKE_WORD_MODEL_PATH = stringPreferencesKey("wake_word_model_path")
        val WAKE_WORD_MODE = stringPreferencesKey("wake_word_mode") // "porcupine" or "amplitude"
    }

    val gatewayUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.GATEWAY_URL] ?: ""
        }

    val gatewayToken: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.GATEWAY_TOKEN] ?: ""
        }

    val selectedAgentId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_AGENT_ID]
        }

    val language: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LANGUAGE] ?: "zh"
        }

    val wakeWord: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.WAKE_WORD] ?: "hey openclaw"
        }

    val porcupineAccessKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PORCUPINE_ACCESS_KEY] ?: ""
        }

    val wakeWordModelPath: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.WAKE_WORD_MODEL_PATH] ?: ""
        }

    val wakeWordMode: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.WAKE_WORD_MODE] ?: "amplitude" // Default to amplitude mode (no Porcupine key needed)
        }

    suspend fun setGatewayUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GATEWAY_URL] = url
        }
    }

    suspend fun setGatewayToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GATEWAY_TOKEN] = token
        }
    }

    suspend fun setSelectedAgentId(agentId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_AGENT_ID] = agentId
        }
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language
        }
    }

    suspend fun setWakeWord(wakeWord: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_WORD] = wakeWord
        }
    }

    suspend fun setPorcupineAccessKey(accessKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PORCUPINE_ACCESS_KEY] = accessKey
        }
    }

    suspend fun setWakeWordModelPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_WORD_MODEL_PATH] = path
        }
    }

    suspend fun setWakeWordMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_WORD_MODE] = mode
        }
    }
}