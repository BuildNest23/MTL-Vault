package com.example.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ChapterSummary
import com.example.data.model.NovelSearchHit
import com.example.data.model.ReaderSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import com.example.tts.edge.EdgeProgress
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsHighlightState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ReaderEvent {
    data class ShowToast(val message: String) : ReaderEvent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    val novelId: String,
    initialChapterId: String,
    private val repository: NovelRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ttsManager: TtsManager,
    val autoPlayTtsOnLoad: Boolean = false,
    private val initialParagraphOverride: Int? = null
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

    val novelAnnotations: StateFlow<List<NovelAnnotationEntity>> = repository.observeAnnotationsForNovel(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chapterAnnotations: StateFlow<List<NovelAnnotationEntity>> = _currentChapterId
        .flatMapLatest { chId -> repository.observeAnnotationsForChapter(chId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val ttsPlaybackInfo: StateFlow<TtsPlaybackInfo> = ttsManager.playbackInfo
    val ttsHighlightState: StateFlow<TtsHighlightState> = ttsManager.highlightState
    val edgeProgress: StateFlow<EdgeProgress> = ttsManager.edgeProgress

    private val _events = MutableSharedFlow<ReaderEvent>()
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    private val _scrollToParagraphEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToParagraphEvent: SharedFlow<Int> = _scrollToParagraphEvent.asSharedFlow()

    private val _initialParagraphIndex = MutableStateFlow<Int?>(null)
    val initialParagraphIndex: StateFlow<Int?> = _initialParagraphIndex.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NovelSearchHit>>(emptyList())
    val searchResults: StateFlow<List<NovelSearchHit>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

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

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // IMPORTANT: keep this init block AFTER every property declaration above. viewModelScope runs on
    // Dispatchers.Main.immediate, so loadChapter() can touch those properties before they are
    // initialized if this block is placed earlier (caused a NullPointerException on opening a chapter).

    private var loadChapterJob: Job? = null

    init {
        loadChapter(initialChapterId, targetParagraphOverride = initialParagraphOverride)
        ttsManager.onNextChapterRequested = onNextRequested
        ttsManager.onPrevChapterRequested = onPrevRequested
        if (ttsManager.isPlayingChapter(novelId, initialChapterId)) {
            ttsManager.onChapterCompleteCallback = onTtsChapterComplete
        }

        // When the novel's rules change (e.g. edited on the Rules screen), re-render the open chapter.
        viewModelScope.launch {
            var lastRulesKey: String? = null
            novel.collect { n ->
                if (n != null) {
                    val key = n.replacements + "|" + n.replacementsEnabled
                    if (lastRulesKey != null && lastRulesKey != key) refreshRenderedContent()
                    lastRulesKey = key
                }
            }
        }
    }

    fun loadChapter(chapterId: String, targetParagraphOverride: Int? = null) {
        if (chapterId == _currentChapter.value?.id && targetParagraphOverride != null) {
            _scrollToParagraphEvent.tryEmit(targetParagraphOverride.coerceAtLeast(0))
            return
        }
        saveCurrentPositionNow()
        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
            loadChapterInternal(
                chapterId = chapterId,
                autoPlayIfTtsActive = false,
                targetParagraphOverride = targetParagraphOverride
            )
        }
    }

    private suspend fun loadChapterInternal(
        chapterId: String,
        autoPlayIfTtsActive: Boolean = false,
        targetParagraphOverride: Int? = null
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
                targetParagraphOverride != null -> targetParagraphOverride.coerceAtLeast(0)
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

    fun toggleBookmarkAt(paragraphIndex: Int, paragraphPreview: String) {
        val chId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            val added = repository.toggleParagraphBookmark(
                novelId = novelId,
                chapterId = chId,
                paragraphIndex = paragraphIndex,
                quotePreview = paragraphPreview
            )
            _events.emit(
                ReaderEvent.ShowToast(
                    if (added) "Bookmarked paragraph ${paragraphIndex + 1}" else "Bookmark removed"
                )
            )
        }
    }

    fun addHighlight(
        paragraphIndex: Int,
        quoteText: String,
        startOffset: Int,
        endOffset: Int,
        colorKey: String,
        note: String = ""
    ) {
        val chId = _currentChapter.value?.id ?: return
        viewModelScope.launch {
            repository.addHighlight(
                novelId = novelId,
                chapterId = chId,
                paragraphIndex = paragraphIndex,
                quoteText = quoteText,
                startOffset = startOffset,
                endOffset = endOffset,
                colorKey = colorKey,
                note = note
            )
            _events.emit(
                ReaderEvent.ShowToast(if (note.isNotBlank()) "Note saved" else "Passage highlighted")
            )
        }
    }

    fun updateAnnotation(annotation: NovelAnnotationEntity) {
        viewModelScope.launch {
            repository.updateAnnotation(annotation)
            _events.emit(ReaderEvent.ShowToast("Annotation updated"))
        }
    }

    fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotationId)
            _events.emit(ReaderEvent.ShowToast("Removed"))
        }
    }

    fun jumpToAnnotation(annotation: NovelAnnotationEntity) {
        loadChapter(annotation.chapterId, targetParagraphOverride = annotation.paragraphIndex)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(250)
            try {
                _searchResults.value = repository.searchInNovel(novelId, trimmed)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun jumpToSearchHit(hit: NovelSearchHit) {
        loadChapter(hit.chapterId, targetParagraphOverride = hit.paragraphIndex)
    }

    /**
     * Adds "find = replace" to this novel's replacement rules (same format as the Rules screen)
     * and re-renders the open chapter so the change shows up straight away.
     */
    fun addReplacementRule(find: String, replace: String, caseSensitive: Boolean, partialMatch: Boolean) {
        val trimmedFind = find.trim()
        if (trimmedFind.isEmpty()) return
        val trimmedReplace = replace.trim()
        viewModelScope.launch {
            val n = repository.getNovelByIdSync(novelId) ?: return@launch
            val flags = buildString {
                if (caseSensitive) append("[cs] ")
                if (partialMatch) append("[nww] ")
            }
            val line = "$flags$trimmedFind = $trimmedReplace"
            val updated = if (n.replacements.isBlank()) line else n.replacements.trimEnd() + "\n" + line
            repository.updateReplacements(novelId, updated, n.replacementsEnabled)
            refreshRenderedContent()
            val note = if (n.replacementsEnabled) "" else " (replacements are switched off for this novel)"
            _events.emit(ReaderEvent.ShowToast("Rule added: $trimmedFind ➔ $trimmedReplace$note"))
        }
    }

    /**
     * Re-renders the open chapter with the latest rules and shows it straight away. When TTS is
     * reading (or paused on) this chapter, the speech is restarted on the same paragraph with the
     * new text, and stays paused if it was paused, so what is heard matches what is shown.
     */
    private suspend fun refreshRenderedContent() {
        val chapter = _currentChapter.value ?: return
        val rendered = repository.getRenderedChapterContent(novelId, chapter)
        if (rendered == _renderedContent.value) return
        val info = ttsManager.playbackInfo.value
        val ttsActiveHere = info.chapterId == chapter.id &&
            (info.state == com.example.tts.TtsState.PLAYING || info.state == com.example.tts.TtsState.PAUSED)
        _renderedContent.value = rendered
        if (ttsActiveHere) {
            val wasPaused = info.state == com.example.tts.TtsState.PAUSED
            val paragraphCount = TtsManager.parseParagraphs(rendered).size
            val index = info.currentChunkIndex.coerceIn(0, (paragraphCount - 1).coerceAtLeast(0))
            startTtsForChapter(chapter, rendered, index, startPaused = wasPaused)
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

    fun saveCurrentPositionNow() {
        val chapter = _currentChapter.value ?: return
        val paraToSave = lastScrollParagraphIndex
        val scrollToSave = lastScrollIndex
        val progressToSave = lastScrollProgress
        if (lastSavedParagraph == paraToSave && kotlin.math.abs(lastSavedProgress - progressToSave) < 0.01f) {
            return
        }
        lastSavedParagraph = paraToSave
        lastSavedIndex = scrollToSave
        lastSavedProgress = progressToSave
        saveScope.launch {
            com.example.logging.AppLogger.d("ReaderViewModel", "saveCurrentPositionNow: id=${chapter.id}, para=$paraToSave, scrollY=$scrollToSave")
            repository.updateReadingPosition(chapter.id, paraToSave, scrollToSave)
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
        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
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
        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
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

    fun playFromSentence(paragraphIndex: Int, startCharOffset: Int) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            var content = _renderedContent.value
            if (content.isBlank()) {
                content = repository.getRenderedChapterContent(novelId, chapter)
                _renderedContent.value = content
            }
            if (!ttsManager.isPlayingChapter(novelId, chapter.id)) {
                ttsManager.prepareChapter(
                    novelId = novelId,
                    chapterId = chapter.id,
                    chapterTitle = chapter.title,
                    content = content,
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

    /** Jump to a paragraph (used by the player's seek bar). */
    fun seekTtsToParagraph(index: Int) {
        ttsManager.seekToChunk(index)
    }

    fun ttsRemainingSeconds(): Int = ttsManager.estimatedRemainingSeconds()

    val ttsSleepTimer: StateFlow<com.example.tts.SleepTimerState> = ttsManager.sleepTimer
    fun setSleepTimerMinutes(minutes: Int) = ttsManager.setSleepTimerMinutes(minutes)
    fun setSleepAtChapterEnd() = ttsManager.setSleepAtChapterEnd()
    fun cancelSleepTimer() = ttsManager.cancelSleepTimer()

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

    private fun startTtsForChapter(chapter: ChapterEntity, content: String, startFromChunkIndex: Int, startPaused: Boolean = false) {
        ttsManager.playChapter(
            novelId = novelId,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            content = content,
            startFromChunkIndex = startFromChunkIndex,
            onChapterComplete = onTtsChapterComplete,
            startPaused = startPaused
        )
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

    fun setUseEdgeVoices(useEdge: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateTtsEngine(if (useEdge) "edge" else "")
        }
    }

    fun setEdgeWifiOnly(enabled: Boolean) {
        ttsManager.setEdgeWifiOnly(enabled)
        viewModelScope.launch {
            settingsDataStore.updateEdgeWifiOnly(enabled)
        }
    }

    fun clearEdgeChapter() {
        ttsManager.clearEdgeChapter()
    }

    fun clearEdgeCache() {
        ttsManager.clearEdgeCache()
    }

    /** Total size of the downloaded Edge audio in bytes (reads the disk, so it runs off the main thread). */
    suspend fun edgeCacheSize(): Long = withContext(Dispatchers.IO) { ttsManager.edgeCacheSize() }

    fun setEdgeVoice(voice: String) {
        viewModelScope.launch {
            settingsDataStore.updateEdgeVoice(voice)
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch {
            settingsDataStore.saveAllSettings(newSettings)
        }
    }

    override fun onCleared() {
        saveCurrentPositionNow()
        super.onCleared()
        // Only detach callbacks that still belong to this ViewModel (a newer reader may own them now).
        // Leaving the chapter-complete callback empty lets TtsManager keep going in the background.
        if (ttsManager.onNextChapterRequested === onNextRequested) ttsManager.onNextChapterRequested = null
        if (ttsManager.onPrevChapterRequested === onPrevRequested) ttsManager.onPrevChapterRequested = null
        if (ttsManager.onChapterCompleteCallback === onTtsChapterComplete) ttsManager.onChapterCompleteCallback = null
    }
}
