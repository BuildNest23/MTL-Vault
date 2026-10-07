package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager

import com.example.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MtlVaultApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var novelRepository: NovelRepository
        private set

    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        novelRepository = NovelRepository(
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            settingsDataStore = settingsDataStore,
            database = database
        )
        ttsManager = TtsManager(this)

        CoroutineScope(Dispatchers.Default).launch {
            settingsDataStore.settingsFlow.collect { settings ->
                AppLogger.isDetailedLogging = settings.detailedLogging
                ttsManager.setSpeechRate(settings.ttsRate)
                ttsManager.setSpeechPitch(settings.ttsPitch)
                if (settings.ttsVoice.isNotBlank()) {
                    ttsManager.setVoice(settings.ttsVoice)
                }
            }
        }
    }
}
