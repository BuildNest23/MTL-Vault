package com.example.ui.screens.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CoverStorage
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.database.entities.ShelfEntity
import com.example.data.model.ChapterSummary
import com.example.data.model.NovelSearchHit
import com.example.data.model.ReaderSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.importexport.ChapterSplitter
import com.example.tts.VoiceCastStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DetailEvent {
    data class ShowToast(val message: String) : DetailEvent()
    object NovelDeleted : DetailEvent()
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

    val annotations: StateFlow<List<NovelAnnotationEntity>> = repository.observeAnnotationsForNovel(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfEntity>> = repository.observeShelves()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val novelShelfIds: StateFlow<List<String>> = repository.observeShelfIdsForNovel(novelId)
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<NovelSearchHit>>(emptyList())
    val searchResults: StateFlow<List<NovelSearchHit>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

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

    fun toggleShelf(shelfId: String) {
        viewModelScope.launch {
            repository.toggleNovelShelf(novelId, shelfId)
        }
    }

    fun createShelfAndAssign(name: String) {
        viewModelScope.launch {
            val created = repository.createShelf(name)
            if (created != null) {
                repository.toggleNovelShelf(novelId, created.id)
                _events.emit(DetailEvent.ShowToast("Added to \"${created.name}\"."))
            }
        }
    }

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

    fun updateAnnotation(annotation: NovelAnnotationEntity) {
        viewModelScope.launch {
            repository.updateAnnotation(annotation)
            _events.emit(DetailEvent.ShowToast("Note saved"))
        }
    }

    fun deleteAnnotation(annotationId: String) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotationId)
            _events.emit(DetailEvent.ShowToast("Annotation removed"))
        }
    }

    /**
     * Saves everything from the Edit novel window: title, author, translator, description and cover.
     *
     * A cover chosen from the gallery is copied into the app (shrunk to at most 1000 pixels) so it
     * keeps working when the original is moved or deleted. The previous saved cover is deleted.
     * If the chosen image can't be read, the text changes are still saved.
     *
     * @param newCover the image the user picked, or null to keep the current cover
     * @param removeCover true to remove the cover (ignored when [newCover] is given)
     */
    fun saveNovelDetails(
        context: Context,
        title: String,
        author: String,
        translator: String,
        description: String,
        newCover: Uri?,
        removeCover: Boolean
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val current = novel.value ?: return@launch
            var coverUri = current.coverUri
            var coverProblem = false

            if (newCover != null) {
                val saved = withContext(Dispatchers.IO) {
                    CoverStorage.copyCoverIntoApp(appContext, novelId, newCover)
                }
                if (saved != null) {
                    withContext(Dispatchers.IO) { CoverStorage.deleteOwnCover(appContext, current.coverUri) }
                    com.example.ui.components.evictNovelCoverCache(current.coverUri)
                    coverUri = saved
                } else {
                    coverProblem = true
                }
            } else if (removeCover) {
                withContext(Dispatchers.IO) { CoverStorage.deleteOwnCover(appContext, current.coverUri) }
                com.example.ui.components.evictNovelCoverCache(current.coverUri)
                coverUri = null
            }

            repository.updateNovel(
                current.copy(
                    title = title.trim().ifBlank { "Untitled" },
                    author = author.trim(),
                    translator = translator.trim(),
                    description = description.trim(),
                    coverUri = coverUri
                )
            )
            _events.emit(
                DetailEvent.ShowToast(
                    if (coverProblem) "Saved, but that image couldn't be read." else "Novel updated."
                )
            )
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

    fun deleteNovel(context: Context? = null) {
        val appContext = context?.applicationContext
        val currentCoverUri = novel.value?.coverUri
        viewModelScope.launch {
            val effectiveCoverUri = currentCoverUri ?: repository.getNovelByIdSync(novelId)?.coverUri
            repository.deleteNovel(novelId)
            if (appContext != null) {
                withContext(Dispatchers.IO) {
                    CoverStorage.deleteOwnCover(appContext, effectiveCoverUri)
                    VoiceCastStore(appContext).delete(novelId)
                }
            }
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
                val fileName = withContext(Dispatchers.IO) { getFileName(context, uri) }

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
                    // Read off the main thread; strip a UTF-8 BOM so the first heading is still detected
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            com.example.importexport.TextFileDecoder.decode(input.readBytes())
                        } ?: ""
                    }.removePrefix("\uFEFF")

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

    private var importRunning = false

    fun completeImport(mode: ChapterSplitter.SplitMode, customMarker: String) {
        val text = _pendingImportText.value ?: return
        if (importRunning) return // ignore double taps
        importRunning = true
        viewModelScope.launch {
            try {
                val chunks = withContext(Dispatchers.Default) {
                    ChapterSplitter.splitText(
                        text = text,
                        mode = mode,
                        customMarker = customMarker
                    )
                }
                if (chunks.isEmpty()) {
                    _events.emit(DetailEvent.ShowToast("No chapters detected."))
                    _pendingImportText.value = null
                    return@launch
                }
                val count = repository.importChapters(novelId, chunks)
                _events.emit(DetailEvent.ShowToast("Imported $count chapter(s)."))
                _pendingImportText.value = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(DetailEvent.ShowToast("Import failed: ${e.localizedMessage}"))
            } finally {
                importRunning = false
            }
        }
    }

    fun cancelImport() {
        _pendingImportText.value = null
    }

    fun exportToEpubUri(context: Context, destinationUri: Uri, selectedOnly: Boolean = false) {
        val appContext = context.applicationContext
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
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        com.example.importexport.EpubExporter.exportToEpub(n, chaptersList, outputStream)
                    } ?: throw IllegalStateException("Could not open destination file for writing.")
                }

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
