package com.example.ui.screens.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

sealed class DetailEvent {
    data class ShowToast(val message: String) : DetailEvent()
    object NovelDeleted : DetailEvent()
    data class OpenReader(val chapterId: String) : DetailEvent()
    data class OpenEditor(val chapterId: String?) : DetailEvent()
    object OpenReplacements : DetailEvent()
}

class NovelDetailViewModel(
    val novelId: String,
    private val repository: NovelRepository
) : ViewModel() {

    val novel: StateFlow<NovelEntity?> = repository.getNovelById(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chapters: StateFlow<List<ChapterSummary>> = repository.getChapterSummaries(novelId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    private val _selectedChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedChapterIds: StateFlow<Set<String>> = _selectedChapterIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _pendingImportText = MutableStateFlow<String?>(null)
    val pendingImportText: StateFlow<String?> = _pendingImportText.asStateFlow()

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

    fun selectAllChapters() {
        _selectedChapterIds.value = chapters.value.map { it.id }.toSet()
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

    fun shareNovel(context: Context) {
        viewModelScope.launch {
            val text = repository.exportNovelAsText(novelId)
            val n = novel.value ?: return@launch
            shareTextOrFile(context, n.title, text)
        }
    }

    fun shareSelected(context: Context) {
        viewModelScope.launch {
            val ids = _selectedChapterIds.value.toList()
            if (ids.isEmpty()) return@launch
            val text = repository.exportChaptersAsText(novelId, ids)
            val n = novel.value ?: return@launch
            shareTextOrFile(context, "${n.title} - Selected (${ids.size})", text)
        }
    }

    fun exportNovelToFile(context: Context) {
        viewModelScope.launch {
            val text = repository.exportNovelAsText(novelId)
            val n = novel.value ?: return@launch
            val file = saveToCacheFile(context, "${safeFileName(n.title)}.txt", text)
            shareFileIntent(context, file, "text/plain", "Export Novel")
        }
    }

    fun exportSelectedToFile(context: Context) {
        viewModelScope.launch {
            val ids = _selectedChapterIds.value.toList()
            if (ids.isEmpty()) return@launch
            val text = repository.exportChaptersAsText(novelId, ids)
            val n = novel.value ?: return@launch
            val file = saveToCacheFile(context, "${safeFileName(n.title)}_selected_${ids.size}.txt", text)
            shareFileIntent(context, file, "text/plain", "Export Selected Chapters")
        }
    }

    private fun shareTextOrFile(context: Context, title: String, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun saveToCacheFile(context: Context, filename: String, content: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, filename)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun shareFileIntent(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun safeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.-]+"), "_").take(50)
    }
}
