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
}