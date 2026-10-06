package com.example.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ReaderSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.logging.AppLogger

sealed class SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent()
}

class SettingsViewModel(
    private val repository: NovelRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val _logSizeText = MutableStateFlow("0 KB")
    val logSizeText: StateFlow<String> = _logSizeText.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun refreshLogSize(context: Context) {
        _logSizeText.value = AppLogger.getFormattedLogSize(context)
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            settingsDataStore.updateTheme(theme)
        }
    }

    fun updateFontSize(size: Int) {
        viewModelScope.launch {
            settingsDataStore.updateFontSize(size)
        }
    }

    fun updateFontFamily(family: String) {
        viewModelScope.launch {
            settingsDataStore.updateFontFamily(family)
        }
    }

    fun updateTtsRate(rate: Float) {
        viewModelScope.launch {
            settingsDataStore.updateTtsRate(rate)
        }
    }

    fun updateTtsPitch(pitch: Float) {
        viewModelScope.launch {
            settingsDataStore.updateTtsPitch(pitch)
        }
    }

    fun updateContinueReading(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateContinueReading(enabled)
        }
    }

    fun updateReplacementsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateReplacementsEnabled(enabled)
            _events.emit(SettingsEvent.ShowToast(if (enabled) "Replacements enabled." else "Replacements disabled."))
        }
    }

    fun updateDetailedLogging(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateDetailedLogging(enabled)
            AppLogger.isDetailedLogging = enabled
            AppLogger.i("Settings", "Detailed logging updated: $enabled")
        }
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            AppLogger.i("Backup", "exportBackup started")
            try {
                val json = repository.getFullBackupJson()
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val dir = File(context.cacheDir, "exports")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "mtl_vault_backup_$dateStr.json")
                file.writeText(json, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, "MTL Vault Backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Export Backup JSON")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i("Backup", "exportBackup completed in ${duration}ms, size=${file.length()} bytes")
                _events.emit(SettingsEvent.ShowToast("Backup generated."))
            } catch (e: Exception) {
                AppLogger.e("Backup", "exportBackup failed", e)
                _events.emit(SettingsEvent.ShowToast("Export failed: ${e.localizedMessage}"))
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            AppLogger.i("Backup", "importBackup started with uri=$uri")
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val json = reader.readText()
                reader.close()
                inputStream?.close()

                if (json.isBlank()) {
                    AppLogger.w("Backup", "importBackup failed: backup file is blank")
                    _events.emit(SettingsEvent.ShowToast("Backup file is empty."))
                    return@launch
                }

                repository.restoreFromBackupJson(json)
                val duration = System.currentTimeMillis() - startTime
                AppLogger.i("Backup", "importBackup completed successfully in ${duration}ms")
                _events.emit(SettingsEvent.ShowToast("Backup restored successfully!"))
            } catch (e: Exception) {
                AppLogger.e("Backup", "importBackup failed", e)
                _events.emit(SettingsEvent.ShowToast("Import failed: ${e.localizedMessage}"))
            }
        }
    }

    fun shareLog(context: Context) {
        viewModelScope.launch {
            try {
                AppLogger.i("Diagnostics", "Building log file to share")
                val logFile = AppLogger.buildExportFile(context, settings.value)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, "MTL Vault Diagnostic Log")
                    putExtra(Intent.EXTRA_SUBJECT, "MTL Vault Diagnostic Log")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Share Diagnostic Log")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                AppLogger.i("Diagnostics", "Share log intent launched")
            } catch (e: Exception) {
                AppLogger.e("Diagnostics", "Failed to share log", e)
                _events.emit(SettingsEvent.ShowToast("Failed to share log: ${e.localizedMessage}"))
            }
        }
    }

    fun saveLogToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                AppLogger.i("Diagnostics", "Saving diagnostic log to selected file")
                val fullText = AppLogger.buildFullExportText(context, settings.value)
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(fullText.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                AppLogger.i("Diagnostics", "Diagnostic log saved successfully")
                _events.emit(SettingsEvent.ShowToast("Log saved successfully."))
            } catch (e: Exception) {
                AppLogger.e("Diagnostics", "Failed to save log to URI", e)
                _events.emit(SettingsEvent.ShowToast("Failed to save log: ${e.localizedMessage}"))
            }
        }
    }

    fun clearLogs(context: Context) {
        viewModelScope.launch {
            val cleared = AppLogger.clearLogs(context)
            refreshLogSize(context)
            if (cleared) {
                _events.emit(SettingsEvent.ShowToast("Diagnostic logs cleared."))
            } else {
                _events.emit(SettingsEvent.ShowToast("Failed to clear logs."))
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            AppLogger.w("Settings", "resetAllData initiated by user")
            repository.resetAllData()
            AppLogger.i("Settings", "resetAllData finished")
            _events.emit(SettingsEvent.ShowToast("All data erased and reset."))
        }
    }
}
