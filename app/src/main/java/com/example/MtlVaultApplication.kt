package com.example

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.audio.AudioImporter
import com.example.audio.AudioPlaybackService
import com.example.audio.AudioPlayerController
import com.example.audio.PlaybackCoordinator
import com.example.data.database.AppDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.AudioRepository
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager

import com.example.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Which voice each character of each novel uses. */
    val voiceCastStore: com.example.tts.VoiceCastStore by lazy { com.example.tts.VoiceCastStore(this) }

    lateinit var audioRepository: AudioRepository
        private set

    lateinit var audioImporter: AudioImporter
        private set

    lateinit var audioController: AudioPlayerController
        private set

    lateinit var playbackCoordinator: PlaybackCoordinator
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        database = AppDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        novelRepository = NovelRepository(
            novelDao = database.novelDao(),
            chapterDao = database.chapterDao(),
            settingsDataStore = settingsDataStore,
            database = database,
            annotationDao = database.annotationDao(),
            shelfDao = database.shelfDao()
        )
        ttsManager = TtsManager(this)
        AppLogger.voiceCountProvider = { ttsManager.getPhoneVoices().size }

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
        playbackCoordinator = PlaybackCoordinator(
            ttsManager = ttsManager,
            audioController = audioController,
            novelRepository = novelRepository,
            settingsDataStore = settingsDataStore,
            scope = appScope
        )

        appScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                AppLogger.isDetailedLogging = settings.detailedLogging
                ttsManager.setSpeechRate(settings.ttsRate)
                ttsManager.setSpeechPitch(settings.ttsPitch)
                ttsManager.setEdgeMode(settings.ttsEngine == "edge", settings.edgeVoice)
                ttsManager.setEdgeWifiOnly(settings.edgeWifiOnly)
                ttsManager.setPhoneEngine(settings.phoneEngine)
                if (settings.ttsVoice.isNotBlank()) {
                    ttsManager.setVoice(settings.ttsVoice)
                }
            }
        }
    }
}
