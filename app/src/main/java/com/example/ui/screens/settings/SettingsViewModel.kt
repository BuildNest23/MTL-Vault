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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent()
}

class SettingsViewModel(
    private val repository: NovelRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

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

    fun exportBackup(context: Context) {
        viewModelScope.launch {
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
                _events.emit(SettingsEvent.ShowToast("Backup generated."))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Export failed: ${e.localizedMessage}"))
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val json = reader.readText()
                reader.close()
                inputStream?.close()

                if (json.isBlank()) {
                    _events.emit(SettingsEvent.ShowToast("Backup file is empty."))
                    return@launch
                }

                repository.restoreFromBackupJson(json)
                _events.emit(SettingsEvent.ShowToast("Backup restored successfully!"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.ShowToast("Import failed: ${e.localizedMessage}"))
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            repository.resetAllData()
            _events.emit(SettingsEvent.ShowToast("All data erased and reset."))
        }
    }
}
