package com.example.audio

import com.example.data.database.entities.NovelEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NowPlayingSource {
    NONE,
    TTS,
    AUDIOBOOK
}

data class NowPlayingState(
    val source: NowPlayingSource = NowPlayingSource.NONE,
    val title: String = "",
    val subtitle: String = "",
    val coverUri: String? = null,
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val novelId: String? = null,
    val chapterId: String? = null,
    val bookId: String? = null,
    val speedLabel: String = ""
) {
    val isActive: Boolean get() = source != NowPlayingSource.NONE
}

/**
 * Pure resolution logic turning current TTS state, Audiobook state, and the most recently active
 * source into a single [NowPlayingState] for the docked mini-player.
 */
internal fun resolveNowPlayingState(
    tts: TtsPlaybackInfo,
    ttsNovel: NovelEntity?,
    ttsRate: Float,
    audio: AudioPlayerUiState,
    lastActiveSource: NowPlayingSource
): NowPlayingState {
    val ttsLoaded = tts.state in setOf(
        TtsState.PLAYING,
        TtsState.PAUSED,
        TtsState.INITIALIZING,
        TtsState.ERROR
    ) && !tts.novelId.isNullOrBlank() && !tts.chapterId.isNullOrBlank()

    val ttsRunning = ttsLoaded && (tts.state == TtsState.PLAYING || tts.state == TtsState.INITIALIZING)
    val audioLoaded = audio.hasBook
    val audioRunning = audioLoaded && (audio.isPlaying || audio.status == PlaybackStatus.LOADING)

    val chosenSource = when {
        ttsRunning -> NowPlayingSource.TTS
        audioRunning -> NowPlayingSource.AUDIOBOOK
        ttsLoaded && audioLoaded -> {
            if (lastActiveSource == NowPlayingSource.TTS) NowPlayingSource.TTS
            else NowPlayingSource.AUDIOBOOK
        }
        ttsLoaded -> NowPlayingSource.TTS
        audioLoaded -> NowPlayingSource.AUDIOBOOK
        else -> NowPlayingSource.NONE
    }

    return when (chosenSource) {
        NowPlayingSource.NONE -> NowPlayingState()
        NowPlayingSource.TTS -> {
            val title = ttsNovel?.title?.takeIf { it.isNotBlank() }
                ?: tts.chapterTitle.takeIf { it.isNotBlank() }
                ?: "Novel Narration"
            val progress = if (tts.totalChunks > 0) {
                ((tts.currentChunkIndex + 1).toFloat() / tts.totalChunks.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val subtitle = when {
                tts.state == TtsState.ERROR -> tts.errorMessage?.takeIf { it.isNotBlank() }
                    ?: "Narration error · Tap to open"
                tts.chapterTitle.isNotBlank() && tts.totalChunks > 0 ->
                    "${tts.chapterTitle}  ·  ${tts.currentChunkIndex + 1}/${tts.totalChunks}"
                tts.chapterTitle.isNotBlank() -> tts.chapterTitle
                tts.totalChunks > 0 -> "Paragraph ${tts.currentChunkIndex + 1} of ${tts.totalChunks}"
                else -> "Reading aloud"
            }
            NowPlayingState(
                source = NowPlayingSource.TTS,
                title = title,
                subtitle = subtitle,
                coverUri = ttsNovel?.coverUri,
                progress = progress,
                isPlaying = tts.state == TtsState.PLAYING,
                isLoading = tts.state == TtsState.INITIALIZING,
                hasError = tts.state == TtsState.ERROR,
                novelId = tts.novelId,
                chapterId = tts.chapterId,
                speedLabel = AudioFormat.speed(ttsRate)
            )
        }
        NowPlayingSource.AUDIOBOOK -> {
            val hasError = audio.status == PlaybackStatus.ERROR
            val subtitle = when {
                hasError -> "Can't play · Tap to fix"
                audio.chapterTitle.isNotBlank() -> audio.chapterTitle
                audio.author.isNotBlank() -> audio.author
                else -> "Audiobook"
            }
            NowPlayingState(
                source = NowPlayingSource.AUDIOBOOK,
                title = audio.title.ifBlank { "Audiobook" },
                subtitle = subtitle,
                coverUri = audio.coverUri,
                progress = audio.bookProgress,
                isPlaying = audio.isPlaying,
                isLoading = audio.status == PlaybackStatus.LOADING,
                hasError = hasError,
                bookId = audio.bookId,
                speedLabel = AudioFormat.speed(audio.speed)
            )
        }
    }
}

/**
 * Coordinates Novel TTS and Audiobook playback so only one audio source speaks at a time,
 * and exposes a single unified [nowPlaying] flow for the app-wide mini-player.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinator(
    private val ttsManager: TtsManager,
    private val audioController: AudioPlayerController,
    private val novelRepository: NovelRepository,
    private val settingsDataStore: SettingsDataStore,
    scope: CoroutineScope
) {
    private val lastActiveSource = MutableStateFlow(NowPlayingSource.NONE)

    private val activeTtsNovelFlow = ttsManager.playbackInfo
        .map { it.novelId }
        .distinctUntilChanged()
        .flatMapLatest { novelId ->
            if (novelId.isNullOrBlank()) flowOf(null)
            else novelRepository.getNovelById(novelId)
        }

    private val ttsRateFlow = settingsDataStore.settingsFlow
        .map { it.ttsRate }
        .distinctUntilChanged()

    val nowPlaying: StateFlow<NowPlayingState> = combine(
        ttsManager.playbackInfo,
        activeTtsNovelFlow,
        ttsRateFlow,
        audioController.state,
        lastActiveSource
    ) { tts, novel, rate, audio, preferredSource ->
        resolveNowPlayingState(
            tts = tts,
            ttsNovel = novel,
            ttsRate = rate,
            audio = audio,
            lastActiveSource = preferredSource
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = NowPlayingState()
    )

    init {
        // Enforce mutual exclusion: starting Audiobook playback pauses TTS.
        scope.launch {
            audioController.state
                .map { it.isPlaying to it.hasBook }
                .distinctUntilChanged()
                .collect { (audioPlaying, hasBook) ->
                    if (audioPlaying) {
                        lastActiveSource.value = NowPlayingSource.AUDIOBOOK
                        if (ttsManager.isPlaying()) {
                            ttsManager.pause()
                        }
                    } else if (hasBook && !ttsManager.isPlaying() && lastActiveSource.value == NowPlayingSource.NONE) {
                        lastActiveSource.value = NowPlayingSource.AUDIOBOOK
                    }
                }
        }

        // Enforce mutual exclusion: starting Novel TTS pauses Audiobook playback.
        scope.launch {
            ttsManager.playbackInfo
                .map { it.state }
                .distinctUntilChanged()
                .collect { ttsState ->
                    if (ttsState == TtsState.PLAYING || ttsState == TtsState.INITIALIZING) {
                        lastActiveSource.value = NowPlayingSource.TTS
                        if (audioController.state.value.isPlaying) {
                            audioController.pause()
                        }
                    } else if (ttsState == TtsState.PAUSED &&
                        !audioController.state.value.isPlaying &&
                        lastActiveSource.value == NowPlayingSource.NONE
                    ) {
                        lastActiveSource.value = NowPlayingSource.TTS
                    }
                }
        }
    }

    fun togglePlayPause() {
        when (nowPlaying.value.source) {
            NowPlayingSource.TTS -> {
                if (ttsManager.isPlaying()) {
                    ttsManager.pause()
                } else {
                    if (audioController.state.value.isPlaying) {
                        audioController.pause()
                    }
                    lastActiveSource.value = NowPlayingSource.TTS
                    ttsManager.resume()
                }
            }
            NowPlayingSource.AUDIOBOOK -> {
                if (audioController.state.value.isPlaying) {
                    audioController.pause()
                } else {
                    if (ttsManager.isPlaying()) {
                        ttsManager.pause()
                    }
                    lastActiveSource.value = NowPlayingSource.AUDIOBOOK
                    audioController.play()
                }
            }
            NowPlayingSource.NONE -> Unit
        }
    }

    /**
     * Stops the active playback session and dismisses the mini-player cleanly. If the secondary
     * source was only sitting paused in the background, it is also closed/stopped so the bar
     * disappears immediately as expected.
     */
    fun stopAndDismiss() {
        when (nowPlaying.value.source) {
            NowPlayingSource.TTS -> {
                ttsManager.stop()
                if (audioController.state.value.hasBook && !audioController.state.value.isPlaying) {
                    audioController.close()
                }
                lastActiveSource.value = NowPlayingSource.NONE
            }
            NowPlayingSource.AUDIOBOOK -> {
                audioController.close()
                val ttsState = ttsManager.playbackInfo.value.state
                if (ttsState == TtsState.PAUSED || ttsState == TtsState.ERROR) {
                    ttsManager.stop()
                }
                lastActiveSource.value = NowPlayingSource.NONE
            }
            NowPlayingSource.NONE -> Unit
        }
    }
}
