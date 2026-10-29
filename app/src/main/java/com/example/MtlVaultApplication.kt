package com.example

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.audio.AudioImporter
import com.example.audio.AudioPlaybackService
import com.example.audio.AudioPlayerController
import com.example.data.database.AppDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AudioRepository
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import com.example.tts.TtsState

import com.example.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    lateinit var audioRepository: AudioRepository
        private set

    lateinit var audioImporter: AudioImporter
        private set

    lateinit var audioController: AudioPlayerController
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

        audioRepository = AudioRepository(database.audioDao())
        audioImporter = AudioImporter(this)
        audioController = AudioPlayerController(this, audioRepository) {
            try {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, AudioPlaybackService::class.java)
                )
            } catch (e: Exception) {
                AppLogger.e("MtlVaultApplication", "Failed to start audio service", e)
            }
        }
        keepOnlyOneVoicePlaying()

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

    /**
     * An audiobook and text-to-speech never play together: starting one pauses the other.
     */
    private fun keepOnlyOneVoicePlaying() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        scope.launch {
            audioController.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { audioPlaying ->
                    if (audioPlaying && ttsManager.isPlaying()) ttsManager.pause()
                }
        }
        scope.launch {
            ttsManager.playbackInfo
                .map { it.state == TtsState.PLAYING }
                .distinctUntilChanged()
                .collect { ttsPlaying ->
                    if (ttsPlaying && audioController.state.value.isPlaying) audioController.pause()
                }
        }
    }
}
