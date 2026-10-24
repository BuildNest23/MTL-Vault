package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mtl_vault_settings")

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("reader_theme")
        val FONT_SIZE = intPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val MAX_WIDTH = intPreferencesKey("max_width")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val CONTINUE_READING = booleanPreferencesKey("continue_reading")
        val REPLACEMENTS_ENABLED = booleanPreferencesKey("replacements_enabled")
        val DETAILED_LOGGING = booleanPreferencesKey("detailed_logging")
        val SPLIT_LONG_PARAGRAPHS = stringPreferencesKey("split_long_paragraphs")
        val HIDE_READ_CHAPTERS = booleanPreferencesKey("hide_read_chapters")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val settingsFlow: Flow<ReaderSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ReaderSettings(
                theme = preferences[PreferencesKeys.THEME] ?: "light",
                fontSize = preferences[PreferencesKeys.FONT_SIZE] ?: 18,
                lineHeight = preferences[PreferencesKeys.LINE_HEIGHT] ?: 1.75f,
                fontFamily = preferences[PreferencesKeys.FONT_FAMILY] ?: "serif",
                maxWidth = preferences[PreferencesKeys.MAX_WIDTH] ?: 720,
                ttsRate = preferences[PreferencesKeys.TTS_RATE] ?: 1.0f,
                ttsPitch = preferences[PreferencesKeys.TTS_PITCH] ?: 1.0f,
                ttsVoice = preferences[PreferencesKeys.TTS_VOICE] ?: "",
                ttsEngine = preferences[PreferencesKeys.TTS_ENGINE] ?: "",
                continueReading = preferences[PreferencesKeys.CONTINUE_READING] ?: true,
                replacementsEnabled = preferences[PreferencesKeys.REPLACEMENTS_ENABLED] ?: true,
                detailedLogging = preferences[PreferencesKeys.DETAILED_LOGGING] ?: false,
                splitLongParagraphs = preferences[PreferencesKeys.SPLIT_LONG_PARAGRAPHS] ?: "medium"
            )
        }

    val hideReadChaptersFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HIDE_READ_CHAPTERS] ?: false
        }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DYNAMIC_COLOR] ?: false
        }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME] = theme }
    }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[PreferencesKeys.FONT_SIZE] = size }
    }

    suspend fun updateLineHeight(lineHeight: Float) {
        context.dataStore.edit { it[PreferencesKeys.LINE_HEIGHT] = lineHeight }
    }

    suspend fun updateFontFamily(fontFamily: String) {
        context.dataStore.edit { it[PreferencesKeys.FONT_FAMILY] = fontFamily }
    }

    suspend fun updateMaxWidth(width: Int) {
        context.dataStore.edit { it[PreferencesKeys.MAX_WIDTH] = width }
    }

    suspend fun updateTtsRate(rate: Float) {
        context.dataStore.edit { it[PreferencesKeys.TTS_RATE] = rate }
    }

    suspend fun updateTtsPitch(pitch: Float) {
        context.dataStore.edit { it[PreferencesKeys.TTS_PITCH] = pitch }
    }

    suspend fun updateTtsVoice(voice: String) {
        context.dataStore.edit { it[PreferencesKeys.TTS_VOICE] = voice }
    }

    suspend fun updateTtsEngine(engine: String) {
        context.dataStore.edit { it[PreferencesKeys.TTS_ENGINE] = engine }
    }

    suspend fun updateContinueReading(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.CONTINUE_READING] = enabled }
    }

    suspend fun updateReplacementsEnabled(enabled: Boolean) {
        com.example.replacement.ReplacementEngine.invalidateCache()
        context.dataStore.edit { it[PreferencesKeys.REPLACEMENTS_ENABLED] = enabled }
    }

    suspend fun updateDetailedLogging(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DETAILED_LOGGING] = enabled }
    }

    suspend fun updateSplitLongParagraphs(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.SPLIT_LONG_PARAGRAPHS] = mode.lowercase() }
    }

    suspend fun updateHideReadChapters(hide: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.HIDE_READ_CHAPTERS] = hide }
    }

    suspend fun saveAllSettings(settings: ReaderSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME] = settings.theme
            prefs[PreferencesKeys.FONT_SIZE] = settings.fontSize
            prefs[PreferencesKeys.LINE_HEIGHT] = settings.lineHeight
            prefs[PreferencesKeys.FONT_FAMILY] = settings.fontFamily
            prefs[PreferencesKeys.MAX_WIDTH] = settings.maxWidth
            prefs[PreferencesKeys.TTS_RATE] = settings.ttsRate
            prefs[PreferencesKeys.TTS_PITCH] = settings.ttsPitch
            prefs[PreferencesKeys.TTS_VOICE] = settings.ttsVoice
            prefs[PreferencesKeys.TTS_ENGINE] = settings.ttsEngine
            prefs[PreferencesKeys.CONTINUE_READING] = settings.continueReading
            prefs[PreferencesKeys.REPLACEMENTS_ENABLED] = settings.replacementsEnabled
            prefs[PreferencesKeys.DETAILED_LOGGING] = settings.detailedLogging
            prefs[PreferencesKeys.SPLIT_LONG_PARAGRAPHS] = settings.splitLongParagraphs
        }
    }
}
