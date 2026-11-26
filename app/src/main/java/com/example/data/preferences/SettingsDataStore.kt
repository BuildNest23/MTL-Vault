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
import com.example.ui.theme.AppAppearance
import com.example.ui.theme.AppFontPreset
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MattePalette
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
        val PHONE_ENGINE = stringPreferencesKey("phone_engine")
        val EDGE_VOICE = stringPreferencesKey("edge_voice")
        val EDGE_WIFI_ONLY = booleanPreferencesKey("edge_wifi_only")
        val CONTINUE_READING = booleanPreferencesKey("continue_reading")
        val REPLACEMENTS_ENABLED = booleanPreferencesKey("replacements_enabled")
        val DETAILED_LOGGING = booleanPreferencesKey("detailed_logging")
        val SPLIT_LONG_PARAGRAPHS = stringPreferencesKey("split_long_paragraphs")
        val HIDE_READ_CHAPTERS = booleanPreferencesKey("hide_read_chapters")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val LIBRARY_GRID_COLUMNS = intPreferencesKey("library_grid_columns")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val APP_MATTE_PALETTE = stringPreferencesKey("app_matte_palette")
        val APP_FONT_PRESET = stringPreferencesKey("app_font_preset")
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
                phoneEngine = preferences[PreferencesKeys.PHONE_ENGINE] ?: "",
                edgeVoice = preferences[PreferencesKeys.EDGE_VOICE] ?: "",
                edgeWifiOnly = preferences[PreferencesKeys.EDGE_WIFI_ONLY] ?: false,
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

    val libraryGridColumnsFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            (preferences[PreferencesKeys.LIBRARY_GRID_COLUMNS] ?: 2).coerceIn(2, 3)
        }

    val appAppearanceFlow: Flow<AppAppearance> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppAppearance(
                themeMode = AppThemeMode.fromKey(preferences[PreferencesKeys.APP_THEME_MODE]),
                mattePalette = MattePalette.fromKey(preferences[PreferencesKeys.APP_MATTE_PALETTE]),
                fontPreset = AppFontPreset.fromKey(preferences[PreferencesKeys.APP_FONT_PRESET]),
                dynamicColor = preferences[PreferencesKeys.DYNAMIC_COLOR] ?: false
            )
        }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateAppThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[PreferencesKeys.APP_THEME_MODE] = mode.key }
    }

    suspend fun updateMattePalette(palette: MattePalette) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.APP_MATTE_PALETTE] = palette.key
            // Turning off Material You lets the chosen matte palette take effect immediately
            prefs[PreferencesKeys.DYNAMIC_COLOR] = false
            // If the user explicitly forced Light mode, switch to Matte Dark so their choice is visible right away
            if (prefs[PreferencesKeys.APP_THEME_MODE] == AppThemeMode.LIGHT.key) {
                prefs[PreferencesKeys.APP_THEME_MODE] = AppThemeMode.DARK.key
            }
        }
    }

    suspend fun updateAppFontPreset(preset: AppFontPreset) {
        context.dataStore.edit { it[PreferencesKeys.APP_FONT_PRESET] = preset.key }
    }

    suspend fun updateLibraryGridColumns(columns: Int) {
        context.dataStore.edit { it[PreferencesKeys.LIBRARY_GRID_COLUMNS] = columns.coerceIn(2, 3) }
    }

    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME] = theme }
    }

    suspend fun updateFontSize(size: Int) {
        context.dataStore.edit { it[PreferencesKeys.FONT_SIZE] = size }
    }

    suspend fun updateFontFamily(fontFamily: String) {
        context.dataStore.edit { it[PreferencesKeys.FONT_FAMILY] = fontFamily }
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

    suspend fun updatePhoneEngine(packageName: String) {
        context.dataStore.edit {
            it[PreferencesKeys.PHONE_ENGINE] = packageName
            // A voice name belongs to one engine, so forget the old one
            it[PreferencesKeys.TTS_VOICE] = ""
        }
    }

    suspend fun updateEdgeVoice(voice: String) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_VOICE] = voice }
    }

    suspend fun updateEdgeWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.EDGE_WIFI_ONLY] = enabled }
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
            prefs[PreferencesKeys.PHONE_ENGINE] = settings.phoneEngine
            prefs[PreferencesKeys.EDGE_VOICE] = settings.edgeVoice
            prefs[PreferencesKeys.EDGE_WIFI_ONLY] = settings.edgeWifiOnly
            prefs[PreferencesKeys.CONTINUE_READING] = settings.continueReading
            prefs[PreferencesKeys.REPLACEMENTS_ENABLED] = settings.replacementsEnabled
            prefs[PreferencesKeys.DETAILED_LOGGING] = settings.detailedLogging
            prefs[PreferencesKeys.SPLIT_LONG_PARAGRAPHS] = settings.splitLongParagraphs
        }
    }
}
