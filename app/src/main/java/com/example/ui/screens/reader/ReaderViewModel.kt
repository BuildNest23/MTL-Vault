package com.example.ui.screens.reader

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ChapterSummary
import com.example.data.model.ReaderSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsHighlightState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class ReaderEvent {
    data class ShowToast(val message: String) : ReaderEvent()
    data class NavigateToChapter(val chapterId: String) : ReaderEvent()
}

class ReaderViewModel(
    val novelId: String,
    initialChapterId: String,
    private val repository: NovelRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    val autoPlayTtsOnLoad: Boolean = false
) : ViewModel() {

    private var hasAutoPlayed = false

    private val _currentChapterId = MutableStateFlow(initialChapterId)
    val currentChapterId: StateFlow<String> = _currentChapterId.asStateFlow()

    private val _currentChapter = MutableStateFlow<ChapterEntity?>(null)
    val currentChapter: StateFlow<ChapterEntity?> = _currentChapter.asStateFlow()

    private val _renderedContent = MutableStateFlow("")
    val renderedContent: StateFlow<String> = _renderedContent.asStateFlow()

    val novel: StateFlow<NovelEntity?> = repository.getNovelById(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chaptersList: StateFlow<List<ChapterSummary>> = repository.getChapterSummaries(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val ttsPlaybackInfo: StateFlow<TtsPlaybackInfo> = ttsManager.playbackInfo
    val ttsHighlightState: StateFlow<TtsHighlightState> = ttsManager.highlightState

    private val _events = MutableSharedFlow<ReaderEvent>()
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    private val _initialParagraphIndex = MutableStateFlow<Int?>(null)
    val initialParagraphIndex: StateFlow<Int?> = _initialParagraphIndex.asStateFlow()

    private var lastScrollParagraphIndex: Int = 0
    private var lastScrollIndex: Int = 0
    private var lastScrollProgress: Float = 0f
    private var lastSavedParagraph: Int = -1
    private var lastSavedIndex: Int = -1
    private var lastSavedProgress: Float = -1f

    // Single instances so onCleared() can tell whether the TtsManager callbacks are still ours.
    private val onTtsChapterComplete: () -> Unit = {
        viewModelScope.launch {
            val latestSettings = settingsDataStore.settingsFlow.first()
            if (latestSettings.continueReading) {
                nextChapter(autoPlay = true)
            } else {
                ttsManager.stop()
            }
        }
    }
    private val onNextRequested: () -> Unit = { nextChapter(autoPlay = true) }
    private val onPrevRequested: () -> Unit = { prevChapter(autoPlay = true) }

    // IMPORTANT: keep this init block AFTER every property declaration above. viewModelScope runs on
    // Dispatchers.Main.immediate, so loadChapter() can touch those properties before they are
    // initialized if this block is placed earlier (caused a NullPointerException on opening a chapter).

    init {
        loadChapter(initialChapterId)
        ttsManager.onNextChapterRequested = onNextRequested
        ttsManager.onPrevChapterRequested = onPrevRequested

        viewModelScope.launch {
            var previousSplitMode: String? = null
            settingsDataStore.settingsFlow.collect { newSettings ->
                val currentMode = newSettings.splitLongParagraphs
                if (previousSplitMode != null && previousSplitMode != currentMode) {
                    onSplitModeChanged(previousSplitMode!!, currentMode)
                }
                previousSplitMode = currentMode
            }
        }
    }

    fun loadChapter(chapterId: String) {
        saveCurrentPositionNow()
        viewModelScope.launch {
            loadChapterInternal(chapterId, autoPlayIfTtsActive = false)
        }
    }

    private suspend fun loadChapterInternal(
        chapterId: String,
        autoPlayIfTtsActive: Boolean = false
    ) {
        com.example.logging.AppLogger.i("ReaderViewModel", "loadChapter: novelId=$novelId, chapterId=$chapterId")
        _currentChapterId.value = chapterId
        lastSavedIndex = -1
        lastSavedParagraph = -1
        lastSavedProgress = -1f
        lastScrollIndex = 0
        lastScrollParagraphIndex = 0
        lastScrollProgress = 0f

        val chapter = repository.getChapterByIdSync(chapterId)
        if (chapter != null) {
            val ttsInfo = ttsManager.playbackInfo.value
            val ttsActiveHere = ttsInfo.chapterId == chapter.id &&
                (ttsInfo.state == com.example.tts.TtsState.PLAYING || ttsInfo.state == com.example.tts.TtsState.PAUSED)
            val targetParagraph = when {
                // TTS auto-advanced into this chapter and starts at paragraph 0: open at the top instead of
                // an old saved position (that caused "opens in the middle, then scrolls up")
                autoPlayIfTtsActive -> 0
                // Reader re-opened (e.g. from the media notification) while TTS is reading this chapter:
                // open on the paragraph being spoken, not on the last manually saved position
                ttsActiveHere -> ttsInfo.currentChunkIndex.coerceAtLeast(0)
                else -> chapter.readingParagraphIndex.coerceAtLeast(0)
            }
            // Render FIRST, then publish content + chapter together. Publishing the chapter before its
            // text made the screen restore the scroll position against the previous chapter's paragraphs.
            val rendered = repository.getRenderedChapterContent(novelId, chapter)
            lastScrollParagraphIndex = targetParagraph
            lastScrollIndex = targetParagraph
            _initialParagraphIndex.value = targetParagraph
            _renderedContent.value = rendered
            _currentChapter.value = chapter
            repository.updateLastChapter(novelId, chapter.id)
            com.example.logging.AppLogger.i("ReaderViewModel", "Chapter loaded: id=${chapter.id}, targetParagraph=$targetParagraph, renderedLen=${rendered.length}")

            if (autoPlayIfTtsActive) {
                startTtsForChapter(chapter, rendered, 0)
            } else if (autoPlayTtsOnLoad && !hasAutoPlayed) {
                hasAutoPlayed = true
                playCurrentChapterTts(startFromChunkIndex = targetParagraph)
            }
        } else {
            _currentChapter.value = null
            com.example.logging.AppLogger.w("ReaderViewModel", "loadChapter: chapter not found for id=$chapterId")
        }
    }

    fun updateReadingPosition(paragraphIndex: Int, scrollY: Int, scrollPercent: Float) {
        lastScrollParagraphIndex = paragraphIndex
        lastScrollIndex = scrollY
        lastScrollProgress = scrollPercent

        val chapter = _currentChapter.value ?: return

        // Only write when the paragraph changed or progress changed by >= 1% (0.01f)
        val isMeaningfulChange = lastSavedParagraph != paragraphIndex || kotlin.math.abs(lastSavedProgress - scrollPercent) >= 0.01f
        if (!isMeaningfulChange) return

        lastSavedParagraph = paragraphIndex
        lastSavedIndex = scrollY
        lastSavedProgress = scrollPercent

        viewModelScope.launch(Dispatchers.IO) {
            com.example.logging.AppLogger.d("ReaderViewModel", "updateReadingPosition: id=${chapter.id}, para=$paragraphIndex, scrollY=$scrollY, prog=$scrollPercent")
            repository.updateReadingPosition(chapter.id, paragraphIndex, scrollY)
        }
    }

    fun updateScrollPosition(scrollY: Int, scrollPercent: Float) {
        val paragraphIndex = maxOf(0, scrollY - 1)
        updateReadingPosition(paragraphIndex, scrollY, scrollPercent)
    }

    fun saveCurrentPositionNow() {
        val chapter = _currentChapter.value ?: return
        if (lastSavedParagraph == lastScrollParagraphIndex && kotlin.math.abs(lastSavedProgress - lastScrollProgress) < 0.01f) {
            return
        }
        lastSavedParagraph = lastScrollParagraphIndex
        lastSavedIndex = lastScrollIndex
        lastSavedProgress = lastScrollProgress
        viewModelScope.launch(Dispatchers.IO) {
            com.example.logging.AppLogger.d("ReaderViewModel", "saveCurrentPositionNow: id=${chapter.id}, para=$lastScrollParagraphIndex, scrollY=$lastScrollIndex")
            repository.updateReadingPosition(chapter.id, lastScrollParagraphIndex, lastScrollIndex)
        }
    }

    fun toggleReadStatus() {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !chapter.read
            com.example.logging.AppLogger.i("ReaderViewModel", "toggleReadStatus: id=${chapter.id}, newStatus=$newStatus")
            repository.setChapterRead(chapter.id, newStatus)
            _currentChapter.value = _currentChapter.value?.copy(read = newStatus)
        }
    }

    fun prevChapter(autoPlay: Boolean = false) {
        val chapter = _currentChapter.value ?: return
        saveCurrentPositionNow()
        com.example.logging.AppLogger.i("ReaderViewModel", "prevChapter requested: currentOrder=${chapter.orderIndex}, autoPlay=$autoPlay")
        viewModelScope.launch {
            val prev = repository.getPrevChapter(novelId, chapter.orderIndex)
            if (prev != null) {
                val shouldPlay = autoPlay || ttsManager.isPlaying()
                loadChapterInternal(prev.id, autoPlayIfTtsActive = shouldPlay)
            } else {
                com.example.logging.AppLogger.i("ReaderViewModel", "prevChapter: first chapter reached")
                _events.emit(ReaderEvent.ShowToast("First chapter reached."))
            }
        }
    }

    fun nextChapter(autoPlay: Boolean = false) {
        val chapter = _currentChapter.value ?: return
        saveCurrentPositionNow()
        com.example.logging.AppLogger.i("ReaderViewModel", "nextChapter requested: currentOrder=${chapter.orderIndex}, autoPlay=$autoPlay")
        viewModelScope.launch {
            val next = repository.getNextChapter(novelId, chapter.orderIndex)
            if (next != null) {
                repository.setChapterRead(chapter.id, true)
                _currentChapter.value = _currentChapter.value?.copy(read = true)
                val shouldPlay = autoPlay || ttsManager.isPlaying()
                loadChapterInternal(next.id, autoPlayIfTtsActive = shouldPlay)
            } else {
                com.example.logging.AppLogger.i("ReaderViewModel", "nextChapter: last chapter reached")
                ttsManager.stop()
                _events.emit(ReaderEvent.ShowToast("Last chapter reached."))
            }
        }
    }

    fun toggleTts(preferredParagraphIndex: Int? = null) {
        val info = ttsManager.playbackInfo.value
        val chapter = _currentChapter.value ?: return

        when (info.state) {
            com.example.tts.TtsState.PLAYING -> {
                ttsManager.pause()
            }
            com.example.tts.TtsState.PAUSED -> {
                if (info.chapterId == chapter.id) {
                    ttsManager.resume()
                } else {
                    val startIndex = preferredParagraphIndex ?: lastScrollParagraphIndex.coerceAtLeast(0)
                    playCurrentChapterTts(startFromChunkIndex = startIndex)
                }
            }
            else -> {
                val startIndex = if (info.chapterId == chapter.id && info.currentChunkIndex > 0) {
                    info.currentChunkIndex
                } else {
                    preferredParagraphIndex ?: lastScrollParagraphIndex.coerceAtLeast(0)
                }
                playCurrentChapterTts(startFromChunkIndex = startIndex)
            }
        }
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun playFromParagraph(paragraphIndex: Int) {
        val chapter = _currentChapter.value ?: return
        if (ttsManager.isPlayingChapter(novelId, chapter.id)) {
            ttsManager.seekToChunk(paragraphIndex)
        } else {
            playCurrentChapterTts(startFromChunkIndex = paragraphIndex)
        }
    }

    fun playFromSentence(paragraphIndex: Int, startCharOffset: Int) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            var content = _renderedContent.value
            if (content.isBlank()) {
                content = repository.getRenderedChapterContent(novelId, chapter)
                _renderedContent.value = content
            }
            if (!ttsManager.isPlayingChapter(novelId, chapter.id)) {
                val currentSettings = settings.value
                val splitMode = com.example.data.model.ParagraphSplitMode.fromString(currentSettings.splitLongParagraphs)
                ttsManager.prepareChapter(
                    novelId = novelId,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    content = content,
                    maxChars = splitMode.maxChars,
                    onChapterComplete = onTtsChapterComplete
                )
            }
            ttsManager.playFromSentence(paragraphIndex, startCharOffset)
            saveCurrentPositionNow()
        }
    }

    fun prevParagraph() {
        ttsManager.previousChunk()
    }

    fun nextParagraph() {
        ttsManager.nextChunk()
    }

    private fun playCurrentChapterTts(startFromChunkIndex: Int = 0) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            var content = _renderedContent.value
            if (content.isBlank()) {
                content = repository.getRenderedChapterContent(novelId, chapter)
                _renderedContent.value = content
            }
            startTtsForChapter(chapter, content, startFromChunkIndex)
        }
    }

    private fun startTtsForChapter(chapter: ChapterEntity, content: String, startFromChunkIndex: Int) {
        val currentSettings = settings.value
        val splitMode = com.example.data.model.ParagraphSplitMode.fromString(currentSettings.splitLongParagraphs)
        ttsManager.playChapter(
            novelId = novelId,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            content = content,
            startFromChunkIndex = startFromChunkIndex,
            maxChars = splitMode.maxChars,
            onChapterComplete = onTtsChapterComplete
        )
    }

    private fun onSplitModeChanged(oldMode: String, newMode: String) {
        val chapter = _currentChapter.value ?: return
        val content = _renderedContent.value
        if (content.isBlank()) return

        val oldSplit = com.example.data.model.ParagraphSplitMode.fromString(oldMode)
        val newSplit = com.example.data.model.ParagraphSplitMode.fromString(newMode)

        val oldChunks = com.example.tts.ParagraphSplitter.parseParagraphChunks(content, oldSplit.maxChars)
        val newChunks = com.example.tts.ParagraphSplitter.parseParagraphChunks(content, newSplit.maxChars)

        val isTtsActive = ttsManager.isPlaying() || ttsManager.playbackInfo.value.state == com.example.tts.TtsState.PAUSED
        val activeChunkIndex = if (isTtsActive) {
            ttsManager.playbackInfo.value.currentChunkIndex
        } else {
            lastScrollParagraphIndex
        }

        val safeOldIdx = activeChunkIndex.coerceIn(0, (oldChunks.size - 1).coerceAtLeast(0))
        val origIndex = if (oldChunks.isNotEmpty()) oldChunks[safeOldIdx].originalParagraphIndex else 0

        val mappedNewIndex = newChunks.indexOfFirst { it.originalParagraphIndex == origIndex }.let {
            if (it >= 0) it else 0
        }.coerceIn(0, (newChunks.size - 1).coerceAtLeast(0))

        lastScrollParagraphIndex = mappedNewIndex
        _initialParagraphIndex.value = mappedNewIndex
        viewModelScope.launch {
            repository.updateReadingPosition(chapter.id, mappedNewIndex, 0)
        }

        if (isTtsActive) {
            val wasPaused = ttsManager.playbackInfo.value.state == com.example.tts.TtsState.PAUSED
            startTtsForChapter(chapter, content, mappedNewIndex)
            if (wasPaused) {
                ttsManager.pause()
            }
        }
    }

    fun setTtsRate(rate: Float) {
        ttsManager.setSpeechRate(rate)
        viewModelScope.launch {
            settingsDataStore.updateTtsRate(rate)
        }
    }

    fun setTtsPitch(pitch: Float) {
        ttsManager.setSpeechPitch(pitch)
        viewModelScope.launch {
            settingsDataStore.updateTtsPitch(pitch)
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch {
            settingsDataStore.saveAllSettings(newSettings)
        }
    }

    fun shareToVoice(context: Context) {
        val chapter = _currentChapter.value ?: return
        val n = novel.value ?: return
        val text = "${chapter.title}\n\n${_renderedContent.value}"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, "${n.title} - ${chapter.title}")
            putExtra(Intent.EXTRA_SUBJECT, "${n.title} - ${chapter.title}")
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Play in @Voice / Share")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    override fun onCleared() {
        super.onCleared()
        saveCurrentPositionNow()
        // Only detach callbacks that still belong to this ViewModel (a newer reader may own them now).
        // Leaving the chapter-complete callback empty lets TtsManager keep going in the background.
        if (ttsManager.onNextChapterRequested === onNextRequested) ttsManager.onNextChapterRequested = null
        if (ttsManager.onPrevChapterRequested === onPrevRequested) ttsManager.onPrevChapterRequested = null
        if (ttsManager.onChapterCompleteCallback === onTtsChapterComplete) ttsManager.onChapterCompleteCallback = null
    }
}
