package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DeiAiSettings
import com.example.data.model.PersonalityStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.deiaiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "deiai_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOLUME = floatPreferencesKey("volume")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val PERSONALITY_STYLE = stringPreferencesKey("personality_style")
        val EMOTIONS_ENABLED = booleanPreferencesKey("emotions_enabled")
        val REMEMBER_NAME = booleanPreferencesKey("remember_name")
        val REMEMBER_PREFERENCES = booleanPreferencesKey("remember_preferences")
        val REMEMBER_FACTS = booleanPreferencesKey("remember_facts")
        val ASK_BEFORE_REMEMBERING = booleanPreferencesKey("ask_before_remembering")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val settingsFlow: Flow<DeiAiSettings> = context.deiaiSettingsDataStore.data.map { prefs ->
        val styleRaw = prefs[Keys.PERSONALITY_STYLE]
        val style = PersonalityStyle.entries.firstOrNull { it.name == styleRaw }
            ?: PersonalityStyle.AMIGABLE

        DeiAiSettings(
            voiceName = prefs[Keys.VOICE_NAME] ?: "",
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.8f),
            volume = (prefs[Keys.VOLUME] ?: 0.9f).coerceIn(0.0f, 1.0f),
            autoSpeak = prefs[Keys.AUTO_SPEAK] ?: true,
            continuousConversation = prefs[Keys.CONTINUOUS_CONVERSATION] ?: false,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            personalityStyle = style,
            emotionsEnabled = prefs[Keys.EMOTIONS_ENABLED] ?: true,
            rememberName = prefs[Keys.REMEMBER_NAME] ?: true,
            rememberPreferences = prefs[Keys.REMEMBER_PREFERENCES] ?: true,
            rememberFacts = prefs[Keys.REMEMBER_FACTS] ?: true,
            askBeforeRemembering = prefs[Keys.ASK_BEFORE_REMEMBERING] ?: false,
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "es-ES",
            darkMode = prefs[Keys.DARK_MODE] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
        )
    }

    suspend fun updateSettings(transform: (DeiAiSettings) -> DeiAiSettings) {
        context.deiaiSettingsDataStore.edit { prefs ->
            val currentStyleRaw = prefs[Keys.PERSONALITY_STYLE]
            val currentStyle = PersonalityStyle.entries.firstOrNull { it.name == currentStyleRaw }
                ?: PersonalityStyle.AMIGABLE
            val current = DeiAiSettings(
                voiceName = prefs[Keys.VOICE_NAME] ?: "",
                speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.8f),
                volume = (prefs[Keys.VOLUME] ?: 0.9f).coerceIn(0.0f, 1.0f),
                autoSpeak = prefs[Keys.AUTO_SPEAK] ?: true,
                continuousConversation = prefs[Keys.CONTINUOUS_CONVERSATION] ?: false,
                wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
                personalityStyle = currentStyle,
                emotionsEnabled = prefs[Keys.EMOTIONS_ENABLED] ?: true,
                rememberName = prefs[Keys.REMEMBER_NAME] ?: true,
                rememberPreferences = prefs[Keys.REMEMBER_PREFERENCES] ?: true,
                rememberFacts = prefs[Keys.REMEMBER_FACTS] ?: true,
                askBeforeRemembering = prefs[Keys.ASK_BEFORE_REMEMBERING] ?: false,
                languageTag = prefs[Keys.LANGUAGE_TAG] ?: "es-ES",
                darkMode = prefs[Keys.DARK_MODE] ?: true,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true
            )
            val updated = transform(current)
            prefs[Keys.VOICE_NAME] = updated.voiceName
            prefs[Keys.SPEECH_RATE] = updated.speechRate.coerceIn(0.6f, 1.8f)
            prefs[Keys.VOLUME] = updated.volume.coerceIn(0.0f, 1.0f)
            prefs[Keys.AUTO_SPEAK] = updated.autoSpeak
            prefs[Keys.CONTINUOUS_CONVERSATION] = updated.continuousConversation
            prefs[Keys.WAKE_WORD_ENABLED] = updated.wakeWordEnabled
            prefs[Keys.PERSONALITY_STYLE] = updated.personalityStyle.name
            prefs[Keys.EMOTIONS_ENABLED] = updated.emotionsEnabled
            prefs[Keys.REMEMBER_NAME] = updated.rememberName
            prefs[Keys.REMEMBER_PREFERENCES] = updated.rememberPreferences
            prefs[Keys.REMEMBER_FACTS] = updated.rememberFacts
            prefs[Keys.ASK_BEFORE_REMEMBERING] = updated.askBeforeRemembering
            prefs[Keys.LANGUAGE_TAG] = updated.languageTag
            prefs[Keys.DARK_MODE] = updated.darkMode
            prefs[Keys.NOTIFICATIONS_ENABLED] = updated.notificationsEnabled
        }
    }
}
