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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
    private val ttsManager: TtsManager
) : ViewModel() {

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

    private val _events = MutableSharedFlow<ReaderEvent>()
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    init {
        loadChapter(initialChapterId)
        ttsManager.onNextChapterRequested = { nextChapter(autoPlay = true) }
        ttsManager.onPrevChapterRequested = { prevChapter(autoPlay = true) }
    }

    fun loadChapter(chapterId: String) {
        _currentChapterId.value = chapterId
        viewModelScope.launch {
            val chapter = repository.getChapterByIdSync(chapterId)
            _currentChapter.value = chapter
            if (chapter != null) {
                _renderedContent.value = repository.getRenderedChapterContent(novelId, chapter)
                repository.updateLastChapter(novelId, chapter.id)
            }
        }
    }

    fun updateScrollPosition(scrollY: Int, scrollPercent: Float) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            repository.updateScrollPosition(chapter.id, scrollY)
            if (scrollPercent >= 0.85f && !chapter.read) {
                repository.setChapterRead(chapter.id, true)
                _currentChapter.value = _currentChapter.value?.copy(read = true)
            }
        }
    }

    fun prevChapter(autoPlay: Boolean = false) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            val prev = repository.getPrevChapter(novelId, chapter.orderIndex)
            if (prev != null) {
                val shouldPlay = autoPlay || ttsManager.isPlaying()
                _currentChapterId.value = prev.id
                val loadedChapter = repository.getChapterByIdSync(prev.id) ?: return@launch
                _currentChapter.value = loadedChapter
                val rendered = repository.getRenderedChapterContent(novelId, loadedChapter)
                _renderedContent.value = rendered
                repository.updateLastChapter(novelId, loadedChapter.id)

                if (shouldPlay) {
                    startTtsForChapter(loadedChapter, rendered, 0)
                }
            } else {
                _events.emit(ReaderEvent.ShowToast("First chapter reached."))
            }
        }
    }

    fun nextChapter(autoPlay: Boolean = false) {
        val chapter = _currentChapter.value ?: return
        viewModelScope.launch {
            val next = repository.getNextChapter(novelId, chapter.orderIndex)
            if (next != null) {
                val shouldPlay = autoPlay || ttsManager.isPlaying()
                _currentChapterId.value = next.id
                val loadedChapter = repository.getChapterByIdSync(next.id) ?: return@launch
                _currentChapter.value = loadedChapter
                val rendered = repository.getRenderedChapterContent(novelId, loadedChapter)
                _renderedContent.value = rendered
                repository.updateLastChapter(novelId, loadedChapter.id)

                if (shouldPlay) {
                    startTtsForChapter(loadedChapter, rendered, 0)
                }
            } else {
                ttsManager.stop()
                _events.emit(ReaderEvent.ShowToast("Last chapter reached."))
            }
        }
    }

    fun toggleTts() {
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
                    playCurrentChapterTts()
                }
            }
            else -> {
                val startIndex = if (info.chapterId == chapter.id) info.currentChunkIndex else 0
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
        ttsManager.playChapter(
            novelId = novelId,
            chapterId = chapter.id,
            chapterTitle = chapter.title,
            content = content,
            startFromChunkIndex = startFromChunkIndex,
            onChapterComplete = {
                viewModelScope.launch {
                    val currentSettings = settingsDataStore.settingsFlow.first()
                    if (currentSettings.continueReading) {
                        nextChapter(autoPlay = true)
                    } else {
                        ttsManager.stop()
                    }
                }
            }
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
        ttsManager.onNextChapterRequested = null
        ttsManager.onPrevChapterRequested = null
    }
}
