package com.example.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.ChapterEntity
import com.example.data.repository.NovelRepository
import com.example.importexport.ChapterSplitter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private var initialEntity: ChapterEntity? = null

    private val _events = MutableSharedFlow<EditorEvent>()
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (chapterId != null) {
                val existing = repository.getChapterByIdSync(chapterId)
                if (existing != null) {
                    initialEntity = existing
                    _title.value = existing.title
                    _content.value = existing.content
                    _wordCount.value = existing.wordCount
                }
            } else {
                // Auto-suggest next chapter title
                val novel = repository.getNovelByIdSync(novelId)
                val chapters = repository.getNovelByIdSync(novelId)?.let {
                    // Next title logic
                    "Chapter " + (it.lastChapterId?.let { 1 } ?: 1)
                } ?: "Chapter 1"
                _title.value = chapters
            }
        }
    }

    fun onTitleChange(newTitle: String) {
        _title.value = newTitle
    }

    fun onContentChange(newContent: String) {
        _content.value = newContent
        _wordCount.value = ChapterSplitter.countWords(newContent)
    }

    fun saveChapter() {
        val curTitle = _title.value.trim()
        val curContent = _content.value

        viewModelScope.launch {
            if (initialEntity != null) {
                repository.updateChapter(
                    initialEntity!!.copy(
                        title = curTitle.ifBlank { initialEntity!!.title },
                        content = curContent
                    )
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
            _events.emit(EditorEvent.ChapterSaved)
        }
    }
}
