package com.example.audio

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioFileEntity
import com.example.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The audiobook player. One instance lives for the whole app (created in the Application
 * class) and is shared by the screens, the mini-player and the background service.
 *
 * It owns the ExoPlayer, which plays one media item per audio file, and publishes everything
 * the screens need as [state]. It does the work around playback: resuming where you stopped,
 * smart rewind, speed, the sleep timer, saving your position, and counting listening time.
 * The rules themselves live in AudioPlayerLogic.kt.
 *
 * Everything runs on the main thread. [startService] is called when playback begins, so the
 * background service (which shows the notification and handles headset buttons) is running;
 * the Application class provides it.
 */
class AudioPlayerController(
    context: Context,
    private val repository: AudioRepository,
    private val startService: () -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ Published state

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val state: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AudioPlayerSettings> = _settings.asStateFlow()

    // ------------------------------------------------------------------ The engine

    /** The ExoPlayer. Created the first time it is needed, so the app starts without loading it. */
    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true                                   // pause for calls, duck for other apps
            )
            .setHandleAudioBecomingNoisy(true)         // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL)            // keep playing with the screen off
            .build()
            .also { exo ->
                exo.addListener(playerListener)
                exo.skipSilenceEnabled = _settings.value.skipSilence
            }
    }

    // ------------------------------------------------------------------ Loaded book

    private var book: AudioBookEntity? = null
    private var files: List<AudioFileEntity> = emptyList()
    private var spans: List<ChapterSpan> = emptyList()
    private var title = ""
    private var author = ""
    private var coverUri: String? = null

    private var speed = 1.0f
    private var error: String? = null
    private var missingFileName: String? = null
    private var everReady = false

    private val sleep = SleepTimerLogic()
    private val tracker = ListeningTracker()

    private var observeJob: Job? = null
    private var tickJob: Job? = null
    private var lastTickAt = 0L
    private var lastSaveAt = 0L
    private var pausedAtWall = 0L          // wall-clock time of the last pause, 0 if unknown
    private var enhancer: LoudnessEnhancer? = null
    private val checkedDurations = HashSet<String>()

    private data class BookMeta(val title: String, val author: String, val coverUri: String?)

    // ================================================================== Opening a book

    /**
     * Loads a book and gets it ready at the place the user stopped. If the book is already
     * loaded it is left alone. Pass [play] = true to start playing.
     */
    suspend fun open(bookId: String, play: Boolean = false) {
        if (book?.id == bookId && files.isNotEmpty()) {
            if (play) play()
            return
        }

        val loadedBook = repository.getBook(bookId) ?: return
        val loadedFiles = repository.getFiles(bookId)
        if (loadedFiles.isEmpty()) return

        closeCurrent()
        book = loadedBook
        files = loadedFiles
        spans = ChapterNavigator.spansOf(repository.getChapters(bookId), loadedFiles)
        title = loadedBook.title
        author = loadedBook.author
        coverUri = loadedBook.coverUri
        speed = AudioFormat.clampSpeed(loadedBook.playbackSpeed)
        pausedAtWall = loadedBook.lastPlayedAt ?: 0L
        error = null
        missingFileName = null
        everReady = false
        checkedDurations.clear()

        loadMediaItems(loadedBook.lastFileIndex, loadedBook.lastPositionMs)
        startObserving(bookId)
        publish()
        if (play) play()
    }

    /** Loads the files again, for example after a file was re-linked. Keeps the current position. */
    suspend fun reload() {
        val current = book ?: return
        val fileIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex else current.lastFileIndex
        val position = if (player.mediaItemCount > 0) player.currentPosition else current.lastPositionMs
        files = repository.getFiles(current.id)
        error = null
        missingFileName = null
        everReady = false
        loadMediaItems(fileIndex, position)
        publish()
    }

    private fun loadMediaItems(startFileIndex: Int, startPositionMs: Long) {
        val items = files.map { file ->
            MediaItem.Builder()
                .setMediaId(file.id)
                .setUri(Uri.parse(file.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(author.ifBlank { null })
                        .setArtworkUri(coverUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        val exo = player
        exo.setMediaItems(items, startFileIndex.coerceIn(0, items.lastIndex), startPositionMs.coerceAtLeast(0L))
        exo.setPlaybackSpeed(speed)
        exo.volume = 1f
        exo.prepare()
    }

    /** Keeps chapters, files and the book's name up to date while the book is open. */
    private fun startObserving(bookId: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            combine(
                repository.observeBook(bookId).map { it?.let { b -> BookMeta(b.title, b.author, b.coverUri) } }
                    .distinctUntilChanged(),
                repository.observeFiles(bookId),
                repository.observeChapters(bookId)
            ) { meta, newFiles, chapters -> Triple(meta, newFiles, chapters) }
                .collect { (meta, newFiles, chapters) ->
                    if (meta != null) {
                        title = meta.title
                        author = meta.author
                        coverUri = meta.coverUri
                    }
                    if (newFiles.size == files.size) files = newFiles
                    spans = ChapterNavigator.spansOf(chapters, files)
                    publish()
                }
        }
    }

    /** Saves the position and unloads the current book. */
    private suspend fun closeCurrent() {
        if (book == null) return
        saveProgressNow()
        flushListening()
        tickJob?.cancel()
        observeJob?.cancel()
        sleep.cancel()
        player.stop()
        player.clearMediaItems()
        book = null
        files = emptyList()
        spans = emptyList()
    }

    /** Stops playback and clears the player. The mini-player disappears. */
    fun close() {
        scope.launch {
            closeCurrent()
            _state.value = AudioPlayerUiState()
        }
    }

    // ================================================================== Playback controls

    fun play() {
        val current = book ?: return
        val exo = player
        if (exo.mediaItemCount == 0) return

        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0, 0L)
            scope.launch { repository.restart(current.id) }
        } else if (!exo.playWhenReady && _settings.value.rewindOnResume && pausedAtWall > 0L) {
            val rewind = SmartRewind.rewindMs(System.currentTimeMillis() - pausedAtWall)
            if (rewind > 0L) {
                val target = ChapterNavigator.moveBy(
                    files.map { it.durationMs }, exo.currentMediaItemIndex, exo.currentPosition, -rewind
                )
                exo.seekTo(target.fileIndex, target.positionMs)
            }
        }
        error = null
        missingFileName = null
        if (exo.playerError != null) exo.prepare()     // try again after an earlier error
        startService()
        exo.play()
        publish()
    }

    fun pause() {
        player.pause()
        pausedAtWall = System.currentTimeMillis()
        publish()
    }

    fun toggle() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) pause() else play()
    }

    fun skipBack() = seekBy(-_settings.value.skipBackSeconds * 1000L)

    fun skipForward() = seekBy(_settings.value.skipForwardSeconds * 1000L)

    /** Moves by [deltaMs], crossing into the next or previous file when needed. */
    fun seekBy(deltaMs: Long) {
        if (book == null || player.mediaItemCount == 0) return
        val target = ChapterNavigator.moveBy(
            files.map { it.durationMs }, player.currentMediaItemIndex, player.currentPosition, deltaMs
        )
        seekTo(target)
    }

    fun nextChapter() {
        val index = currentChapterIndex()
        ChapterNavigator.next(spans, index)?.let { seekTo(it) }
    }

    fun previousChapter() {
        val index = currentChapterIndex()
        ChapterNavigator.previous(spans, index, player.currentMediaItemIndex, player.currentPosition)
            ?.let { seekTo(it) }
    }

    /** Jumps to the start of a chapter by its position in the chapter list. */
    fun playChapter(chapterIndex: Int) {
        spans.getOrNull(chapterIndex)?.let { seekTo(PlayTarget(it.fileIndex, it.startMs)) }
    }

    /** Moves inside the current chapter. [elapsedMs] counts from the start of the chapter. */
    fun seekInChapter(elapsedMs: Long) {
        val span = spans.getOrNull(currentChapterIndex()) ?: return
        val clamped = elapsedMs.coerceIn(0L, span.durationMs)
        seekTo(PlayTarget(span.fileIndex, span.startMs + clamped))
    }

    /** Jumps to a bookmark or any spot given as file index and position in that file. */
    fun seekToFilePosition(fileIndex: Int, positionMs: Long) = seekTo(PlayTarget(fileIndex, positionMs))

    private fun seekTo(target: PlayTarget) {
        if (player.mediaItemCount == 0) return
        player.seekTo(target.fileIndex.coerceIn(0, player.mediaItemCount - 1), target.positionMs)
        publish()
        scope.launch { saveProgressNow() }
    }

    fun setSpeed(newSpeed: Float) {
        val current = book ?: return
        speed = AudioFormat.clampSpeed(newSpeed)
        player.setPlaybackSpeed(speed)
        scope.launch { repository.setSpeed(current.id, speed) }
        publish()
    }

    // ================================================================== Sleep timer

    fun startSleepTimer(minutes: Int) {
        sleep.startTimed(minutes)
        publish()
    }

    fun startSleepAtChapterEnd() {
        sleep.startEndOfChapter()
        publish()
    }

    fun cancelSleepTimer() {
        sleep.cancel()
        player.volume = 1f
        publish()
    }

    /** Adds the "shake to extend" time to a running timer. Returns false if no timer is running. */
    fun extendSleepTimer(): Boolean {
        val extended = sleep.extend(_settings.value.sleepExtendMinutes)
        if (extended) {
            player.volume = 1f
            publish()
        }
        return extended
    }

    // ================================================================== Bookmarks and chapters

    /** Saves a bookmark at the current spot. */
    fun addBookmarkHere(note: String = "") {
        val current = book ?: return
        val fileIndex = player.currentMediaItemIndex
        val position = player.currentPosition
        scope.launch {
            repository.addBookmark(
                bookId = current.id,
                fileIndex = fileIndex,
                positionMs = position,
                bookPositionMs = AudioRepository.progressMsFor(files, fileIndex, position),
                note = note
            )
        }
    }

    /** Starts a new chapter at the current spot. Returns false if it is too close to a chapter edge. */
    suspend fun addChapterHere(chapterTitle: String): Boolean {
        val current = book ?: return false
        val file = files.getOrNull(player.currentMediaItemIndex) ?: return false
        return repository.addChapterAt(current.id, file.id, player.currentPosition, chapterTitle)
    }

    // ================================================================== Settings

    fun updateSettings(newSettings: AudioPlayerSettings) {
        _settings.value = newSettings
        prefs.edit()
            .putInt(KEY_SKIP_BACK, newSettings.skipBackSeconds)
            .putInt(KEY_SKIP_FORWARD, newSettings.skipForwardSeconds)
            .putBoolean(KEY_REWIND, newSettings.rewindOnResume)
            .putBoolean(KEY_SKIP_SILENCE, newSettings.skipSilence)
            .putBoolean(KEY_BOOST, newSettings.volumeBoost)
            .putBoolean(KEY_SHAKE, newSettings.shakeToExtendSleep)
            .putInt(KEY_EXTEND, newSettings.sleepExtendMinutes)
            .apply()
        if (book != null) {
            player.skipSilenceEnabled = newSettings.skipSilence
            applyVolumeBoost()
        }
    }

    private fun loadSettings(): AudioPlayerSettings {
        val defaults = AudioPlayerSettings()
        return AudioPlayerSettings(
            skipBackSeconds = prefs.getInt(KEY_SKIP_BACK, defaults.skipBackSeconds),
            skipForwardSeconds = prefs.getInt(KEY_SKIP_FORWARD, defaults.skipForwardSeconds),
            rewindOnResume = prefs.getBoolean(KEY_REWIND, defaults.rewindOnResume),
            skipSilence = prefs.getBoolean(KEY_SKIP_SILENCE, defaults.skipSilence),
            volumeBoost = prefs.getBoolean(KEY_BOOST, defaults.volumeBoost),
            shakeToExtendSleep = prefs.getBoolean(KEY_SHAKE, defaults.shakeToExtendSleep),
            sleepExtendMinutes = prefs.getInt(KEY_EXTEND, defaults.sleepExtendMinutes)
        )
    }

    /** Makes quiet recordings about 8 dB louder when the option is on. */
    private fun applyVolumeBoost() {
        try {
            val sessionId = player.audioSessionId
            if (_settings.value.volumeBoost && sessionId != C.AUDIO_SESSION_ID_UNSET) {
                val effect = enhancer ?: LoudnessEnhancer(sessionId).also { enhancer = it }
                effect.setTargetGain(BOOST_MILLIBELS)
                effect.enabled = true
            } else {
                enhancer?.enabled = false
            }
        } catch (e: Exception) {
            // Some phones don't allow this effect. Playback carries on at normal volume.
        }
    }

    // ================================================================== Engine events

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                lastTickAt = SystemClock.elapsedRealtime()
                lastSaveAt = lastTickAt
                startTicking()
            } else {
                tracker.pause()
                tickJob?.cancel()
                if (!player.playWhenReady) pausedAtWall = System.currentTimeMillis()
                scope.launch {
                    flushListening()
                    saveProgressNow()
                }
            }
            publish()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                everReady = true
                checkFileDuration()
            }
            if (playbackState == Player.STATE_ENDED) onBookEnded()
            publish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            checkFileDuration()
            publish()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            enhancer?.release()
            enhancer = null
            applyVolumeBoost()
        }

        override fun onPlayerError(playbackError: PlaybackException) {
            val name = files.getOrNull(player.currentMediaItemIndex)?.displayName ?: "the audio file"
            missingFileName = name
            error = "Can't play $name. It may have been moved, renamed or deleted."
            publish()
        }
    }

    private fun onBookEnded() {
        val current = book ?: return
        val last = files.lastOrNull() ?: return
        tracker.pause()
        scope.launch {
            flushListening()
            repository.saveProgress(
                current.id, last.orderIndex, last.durationMs,
                AudioRepository.progressMsFor(files, last.orderIndex, last.durationMs)
            )
            repository.setFinished(current.id, true)
        }
    }

    /** If the engine learns a file's real length and it differs from what is stored, correct it once. */
    private fun checkFileDuration() {
        val current = book ?: return
        val index = player.currentMediaItemIndex
        val file = files.getOrNull(index) ?: return
        val real = player.duration
        if (real == C.TIME_UNSET || real <= 0L) return
        if (kotlin.math.abs(real - file.durationMs) < 1_000L) return
        if (!checkedDurations.add(file.id)) return
        scope.launch { repository.correctFileDuration(current.id, file.id, real) }
    }

    // ================================================================== Ticking

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                onTick()
                delay(TICK_MS)
            }
        }
    }

    private suspend fun onTick() {
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickAt).coerceIn(0L, 2_000L)
        lastTickAt = now

        if (player.isPlaying) {
            tracker.tick(now, speed)
            if (sleep.tick(delta)) {
                pause()
                player.volume = 1f
            }
        }

        // "End of chapter" timer: stop just before the chapter ends, parked at the start of the next one.
        if (sleep.state.kind == SleepKind.END_OF_CHAPTER && player.isPlaying) {
            val index = currentChapterIndex()
            val span = spans.getOrNull(index)
            if (span != null && span.durationMs > 0L) {
                val remaining = ChapterNavigator.remainingInChapter(span, player.currentMediaItemIndex, player.currentPosition)
                if (remaining <= END_OF_CHAPTER_MARGIN_MS && sleep.chapterEnded()) {
                    pause()
                    player.volume = 1f
                    ChapterNavigator.next(spans, index)?.let { player.seekTo(it.fileIndex, it.startMs) }
                }
            }
        }

        if (sleep.isActive) {
            val span = spans.getOrNull(currentChapterIndex())
            val remaining = if (span != null) {
                ChapterNavigator.remainingInChapter(span, player.currentMediaItemIndex, player.currentPosition)
            } else {
                Long.MAX_VALUE
            }
            player.volume = sleep.volumeFactor(remaining)
        }

        if (tracker.hasPending()) flushListening()
        if (now - lastSaveAt >= SAVE_EVERY_MS) {
            lastSaveAt = now
            saveProgressNow()
        }
        publish()
    }

    // ================================================================== Saving

    private suspend fun saveProgressNow() {
        val current = book ?: return
        if (player.mediaItemCount == 0) return
        val fileIndex = player.currentMediaItemIndex
        val position = player.currentPosition
        repository.saveProgress(
            current.id, fileIndex, position,
            AudioRepository.progressMsFor(files, fileIndex, position)
        )
    }

    private suspend fun flushListening() {
        val current = book ?: return
        val (listened, bookMs) = tracker.take()
        if (listened > 0L || bookMs > 0L) repository.addListening(current.id, title, listened, bookMs)
    }

    // ================================================================== Publishing state

    private fun currentChapterIndex(): Int =
        ChapterNavigator.indexAt(spans, player.currentMediaItemIndex, player.currentPosition)

    private fun publish() {
        val current = book
        if (current == null || files.isEmpty()) {
            _state.value = AudioPlayerUiState()
            return
        }
        val exo = player
        val fileIndex = exo.currentMediaItemIndex.coerceIn(0, files.lastIndex)
        val position = exo.currentPosition.coerceAtLeast(0L)
        val chapterIndex = ChapterNavigator.indexAt(spans, fileIndex, position)
        val span = spans.getOrNull(chapterIndex)

        val status = when {
            error != null -> PlaybackStatus.ERROR
            exo.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            exo.playbackState == Player.STATE_BUFFERING && !everReady -> PlaybackStatus.LOADING
            exo.playWhenReady -> PlaybackStatus.PLAYING
            else -> PlaybackStatus.PAUSED
        }

        _state.value = AudioPlayerUiState(
            bookId = current.id,
            title = title,
            author = author,
            coverUri = coverUri,
            status = status,
            fileIndex = fileIndex,
            positionMs = position,
            bookPositionMs = AudioRepository.progressMsFor(files, fileIndex, position),
            bookDurationMs = files.sumOf { it.durationMs },
            chapterIndex = chapterIndex,
            chapterCount = spans.size,
            chapterTitle = span?.title.orEmpty(),
            chapterElapsedMs = if (span != null) ChapterNavigator.elapsedInChapter(span, fileIndex, position) else 0L,
            chapterDurationMs = span?.durationMs ?: 0L,
            speed = speed,
            sleep = sleep.state,
            missingFileName = missingFileName,
            error = error
        )
    }

    private companion object {
        const val PREFS_NAME = "audio_player_prefs"
        const val KEY_SKIP_BACK = "skip_back_seconds"
        const val KEY_SKIP_FORWARD = "skip_forward_seconds"
        const val KEY_REWIND = "rewind_on_resume"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_BOOST = "volume_boost"
        const val KEY_SHAKE = "shake_to_extend"
        const val KEY_EXTEND = "sleep_extend_minutes"

        const val TICK_MS = 500L
        const val SAVE_EVERY_MS = 5_000L
        const val END_OF_CHAPTER_MARGIN_MS = 600L
        const val BOOST_MILLIBELS = 800
    }
}
