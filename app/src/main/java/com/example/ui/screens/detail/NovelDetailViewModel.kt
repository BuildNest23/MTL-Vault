package com.example.ui.screens.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ChapterSummary
import com.example.data.repository.NovelRepository
import com.example.importexport.ChapterSplitter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

import kotlinx.coroutines.flow.onEach
import com.example.data.preferences.SettingsDataStore
import com.example.data.model.ReaderSettings

sealed class DetailEvent {
    data class ShowToast(val message: String) : DetailEvent()
    object NovelDeleted : DetailEvent()
    data class OpenReader(val chapterId: String) : DetailEvent()
    data class OpenEditor(val chapterId: String?) : DetailEvent()
    object OpenReplacements : DetailEvent()
}

class NovelDetailViewModel(
    val novelId: String,
    private val repository: NovelRepository,
    private val settingsDataStore: SettingsDataStore? = null
) : ViewModel() {

    private val _chaptersLoaded = MutableStateFlow(false)
    val chaptersLoaded: StateFlow<Boolean> = _chaptersLoaded.asStateFlow()

    val novel: StateFlow<NovelEntity?> = repository.getNovelById(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterSummary>> = repository.getChapterSummaries(novelId)
        .onEach { _chaptersLoaded.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<ReaderSettings> = settingsDataStore?.settingsFlow
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())
        ?: MutableStateFlow(ReaderSettings()).asStateFlow()

    val hideReadChapters: StateFlow<Boolean> = settingsDataStore?.hideReadChaptersFlow
        ?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        ?: MutableStateFlow(false).asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    private val _selectedChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedChapterIds: StateFlow<Set<String>> = _selectedChapterIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _pendingImportText = MutableStateFlow<String?>(null)
    val pendingImportText: StateFlow<String?> = _pendingImportText.asStateFlow()

    fun toggleHideReadChapters() {
        viewModelScope.launch {
            val current = hideReadChapters.value
            settingsDataStore?.updateHideReadChapters(!current)
        }
    }

    fun setHideReadChapters(hide: Boolean) {
        viewModelScope.launch {
            settingsDataStore?.updateHideReadChapters(hide)
        }
    }

    fun toggleSelectionMode() {
        if (_isSelectionMode.value) {
            _isSelectionMode.value = false
            _selectedChapterIds.value = emptySet()
        } else {
            _isSelectionMode.value = true
        }
    }

    fun enterSelectionMode(initialChapterId: String) {
        _isSelectionMode.value = true
        _selectedChapterIds.value = setOf(initialChapterId)
    }

    fun toggleChapterSelection(chapterId: String) {
        val current = _selectedChapterIds.value.toMutableSet()
        if (current.contains(chapterId)) {
            current.remove(chapterId)
        } else {
            current.add(chapterId)
        }
        _selectedChapterIds.value = current
    }

    fun selectAllChapters(visibleIds: Collection<String>? = null) {
        val ids = if (visibleIds != null) {
            visibleIds.toSet()
        } else {
            val list = if (hideReadChapters.value) {
                chapters.value.filter { !it.read }.map { it.id }
            } else {
                chapters.value.map { it.id }
            }
            list.toSet()
        }
        _selectedChapterIds.value = ids
    }

    fun clearChapterSelection() {
        _selectedChapterIds.value = emptySet()
    }

    fun toggleChapterRead(chapterId: String) {
        viewModelScope.launch {
            repository.toggleChapterRead(chapterId)
        }
    }

    fun updateNovelInfo(title: String, author: String, translator: String, description: String) {
        viewModelScope.launch {
            val current = novel.value ?: return@launch
            repository.updateNovel(
                current.copy(
                    title = title.trim().ifBlank { "Untitled" },
                    author = author.trim(),
                    translator = translator.trim(),
                    description = description.trim()
                )
            )
            _events.emit(DetailEvent.ShowToast("Novel updated."))
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            repository.deleteChapter(novelId, chapterId)
            _events.emit(DetailEvent.ShowToast("Chapter deleted."))
        }
    }

    fun deleteSelectedChapters() {
        val selected = _selectedChapterIds.value.toList()
        if (selected.isEmpty()) return
        val count = selected.size
        viewModelScope.launch {
            repository.deleteChapters(novelId, selected)
            _selectedChapterIds.value = emptySet()
            _isSelectionMode.value = false
            _events.emit(DetailEvent.ShowToast("Deleted $count chapters"))
        }
    }

    fun addSampleChapters() {
        viewModelScope.launch {
            val chapters = com.example.data.sample.SampleDataGenerator.generateSampleChapters()
            repository.importChapters(novelId, chapters)
            _events.emit(DetailEvent.ShowToast("Added 2 sample chapters!"))
        }
    }

    fun deleteNovel() {
        viewModelScope.launch {
            repository.deleteNovel(novelId)
            _events.emit(DetailEvent.ShowToast("Novel deleted."))
            _events.emit(DetailEvent.NovelDeleted)
        }
    }

    fun startTextImport(text: String) {
        _pendingImportText.value = text
    }

    fun startFileImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri)

                if (com.example.importexport.EpubParser.isEpubFile(fileName)) {
                    val epub = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.importexport.EpubParser.parseEpub(context, uri)
                    }
                    if (epub.chapters.isEmpty()) {
                        _events.emit(DetailEvent.ShowToast("No readable chapters found in EPUB."))
                        return@launch
                    }
                    val count = repository.importChapters(novelId, epub.chapters)
                    _events.emit(DetailEvent.ShowToast("Imported $count chapter(s) from EPUB."))
                } else {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    val content = reader.readText()
                    reader.close()
                    inputStream?.close()

                    if (content.isBlank()) {
                        _events.emit(DetailEvent.ShowToast("The selected file is empty."))
                        return@launch
                    }
                    _pendingImportText.value = content
                }
            } catch (e: Exception) {
                _events.emit(DetailEvent.ShowToast("Failed to read file: ${e.localizedMessage}"))
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file.txt"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        if (name == "file.txt") {
            name = uri.path?.substringAfterLast('/') ?: "file.txt"
        }
        return name
    }

    fun completeImport(mode: ChapterSplitter.SplitMode, customMarker: String) {
        val text = _pendingImportText.value ?: return
        viewModelScope.launch {
            val chunks = ChapterSplitter.splitText(
                text = text,
                mode = mode,
                customMarker = customMarker
            )
            if (chunks.isEmpty()) {
                _events.emit(DetailEvent.ShowToast("No chapters detected."))
                _pendingImportText.value = null
                return@launch
            }
            val count = repository.importChapters(novelId, chunks)
            _events.emit(DetailEvent.ShowToast("Imported $count chapter(s)."))
            _pendingImportText.value = null
        }
    }

    fun cancelImport() {
        _pendingImportText.value = null
    }

    fun exportToEpubUri(context: Context, destinationUri: Uri, selectedOnly: Boolean = false) {
        viewModelScope.launch {
            try {
                val n = novel.value ?: return@launch
                val ids = if (selectedOnly) _selectedChapterIds.value.toList() else null
                val chaptersList = withContext(Dispatchers.IO) {
                    repository.getRenderedChapters(novelId, ids)
                }
                if (chaptersList.isEmpty()) {
                    _events.emit(DetailEvent.ShowToast("No chapters to export."))
                    return@launch
                }
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    withContext(Dispatchers.IO) {
                        com.example.importexport.EpubExporter.exportToEpub(n, chaptersList, outputStream)
                    }
                } ?: throw java.lang.IllegalStateException("Could not open destination file for writing.")

                val countMsg = if (selectedOnly) "${chaptersList.size} chapter(s)" else "${chaptersList.size} chapter(s) of \"${n.title}\""
                _events.emit(DetailEvent.ShowToast("Exported $countMsg to EPUB!"))
                if (selectedOnly) {
                    toggleSelectionMode()
                }
            } catch (e: Exception) {
                _events.emit(DetailEvent.ShowToast("Export failed: ${e.localizedMessage}"))
            }
        }
    }
}
