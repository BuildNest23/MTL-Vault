package com.example.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.ChapterEntity
import com.example.data.repository.NovelRepository
import com.example.importexport.ChapterSplitter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class EditorEvent {
    data class ShowToast(val message: String) : EditorEvent()
    object ChapterSaved : EditorEvent()
}

class ChapterEditorViewModel(
    val novelId: String,
    val chapterId: String?,
    private val repository: NovelRepository
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var initialEntity: ChapterEntity? = null
    private var initialTitle: String = ""
    private var initialContent: String = ""
    private var wordCountJob: Job? = null

    private val _events = MutableSharedFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (chapterId != null) {
                val existing = repository.getChapterByIdSync(chapterId)
                if (existing != null) {
                    initialEntity = existing
                    initialTitle = existing.title
                    initialContent = existing.content
                    _title.value = existing.title
                    _content.value = existing.content
                    _wordCount.value = existing.wordCount
                    _hasUnsavedChanges.value = false
                }
            } else {
                val nextTitle = repository.getNextDefaultChapterTitle(novelId)
                initialTitle = nextTitle
                initialContent = ""
                _title.value = nextTitle
                _hasUnsavedChanges.value = false
            }
        }
    }

    private fun updateDirtyState() {
        _hasUnsavedChanges.value = _title.value != initialTitle || _content.value != initialContent
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
        updateDirtyState()
    }

    fun onContentChange(newContent: String) {
        _content.value = newContent
        updateDirtyState()
        wordCountJob?.cancel()
        wordCountJob = viewModelScope.launch {
            delay(150)
            val count = withContext(Dispatchers.Default) {
                ChapterSplitter.countWords(newContent)
            }
            _wordCount.value = count
        }
    }

    fun saveChapter() {
        if (_isSaving.value) return
        _isSaving.value = true
        val curTitle = _title.value.trim()
        val curContent = _content.value

        viewModelScope.launch {
            try {
                if (initialEntity != null) {
                    val original = initialEntity!!
                    repository.updateChapterText(
                        chapter = original,
                        title = curTitle.ifBlank { original.title },
                        content = curContent
                    )
                    _events.emit(EditorEvent.ShowToast("Chapter updated."))
                } else {
                    repository.addChapter(
                        novelId = novelId,
                        title = curTitle.ifBlank { "Chapter" },
                        content = curContent
                    )
                    _events.emit(EditorEvent.ShowToast("Chapter added."))
                }
                _hasUnsavedChanges.value = false
                _events.emit(EditorEvent.ChapterSaved)
            } catch (e: CancellationException) {
                _isSaving.value = false
                throw e
            } catch (e: Exception) {
                _isSaving.value = false
                _events.emit(EditorEvent.ShowToast("Failed to save chapter: ${e.localizedMessage}"))
            }
        }
    }
}
