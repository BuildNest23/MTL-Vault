package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager

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
        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        novelRepository = NovelRepository(
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            settingsDataStore = settingsDataStore
        )
        ttsManager = TtsManager(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}
