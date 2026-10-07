package com.example.ui.screens.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NovelSummary
import com.example.data.repository.NovelRepository
import com.example.importexport.ChapterSplitter
import com.example.importexport.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class LibraryEvent {
    data class ShowToast(val message: String) : LibraryEvent()
    data class OpenNovel(val novelId: String) : LibraryEvent()
}

class LibraryViewModel(
    private val repository: NovelRepository
) : ViewModel() {

    val novels: StateFlow<List<NovelSummary>> = repository.getNovelSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private val _pendingImportText = MutableStateFlow<String?>(null)
    val pendingImportText: StateFlow<String?> = _pendingImportText.asStateFlow()

    private val _targetNovelId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.ensureSampleDataIfEmpty()
        }
    }

    fun loadSampleNovel() {
        viewModelScope.launch {
            val novelId = repository.createSampleNovel()
            _events.emit(LibraryEvent.ShowToast("Sample novel added!"))
            _events.emit(LibraryEvent.OpenNovel(novelId))
        }
    }

    fun createNovel(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val novelId = repository.createNovel(title = title.trim())
            _events.emit(LibraryEvent.ShowToast("Novel created."))
            _events.emit(LibraryEvent.OpenNovel(novelId))
        }
    }

    fun startTextImport(novelId: String?, text: String) {
        _targetNovelId.value = novelId
        _pendingImportText.value = text
    }

    fun startFileImport(context: Context, novelId: String?, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = withContext(Dispatchers.IO) { getFileName(context, uri) }

                if (EpubParser.isEpubFile(fileName)) {
                    // EPUB Import Flow: Extracts metadata & chapters automatically!
                    val epub = withContext(Dispatchers.IO) {
                        EpubParser.parseEpub(context, uri)
                    }

                    if (epub.chapters.isEmpty()) {
                        _events.emit(LibraryEvent.ShowToast("No readable chapters found in EPUB."))
                        return@launch
                    }

                    val effectiveNovelId = if (novelId != null) {
                        novelId
                    } else {
                        repository.createNovel(
                            title = epub.title,
                            author = epub.author,
                            description = epub.description
                        )
                    }

                    val count = repository.importChapters(effectiveNovelId, epub.chapters)
                    _events.emit(LibraryEvent.ShowToast("Imported \"${epub.title}\" with $count chapters."))
                    _events.emit(LibraryEvent.OpenNovel(effectiveNovelId))
                } else {
                    // Plain text / TXT Import Flow
                    // Read off the main thread; strip a UTF-8 BOM so the first heading is still detected
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).readText()
                        } ?: ""
                    }.removePrefix("\uFEFF")

                    if (content.isBlank()) {
                        _events.emit(LibraryEvent.ShowToast("The selected file is empty."))
                        return@launch
                    }

                    _targetNovelId.value = novelId
                    _pendingImportText.value = content
                }
            } catch (e: Exception) {
                _events.emit(LibraryEvent.ShowToast("Failed to read file: ${e.localizedMessage}"))
            }
        }
    }

    private var importRunning = false

    fun completeImport(mode: ChapterSplitter.SplitMode, customMarker: String) {
        val text = _pendingImportText.value ?: return
        val targetId = _targetNovelId.value
        if (importRunning) return // ignore double taps
        importRunning = true

        viewModelScope.launch {
            try {
                // Split first (off the main thread) so a failed split never leaves an empty novel behind
                val chunks = withContext(Dispatchers.Default) {
                    ChapterSplitter.splitText(
                        text = text,
                        mode = mode,
                        customMarker = customMarker
                    )
                }

                if (chunks.isEmpty()) {
                    _events.emit(LibraryEvent.ShowToast("No chapters detected."))
                    _pendingImportText.value = null
                    _targetNovelId.value = null
                    return@launch
                }

                val effectiveNovelId = if (targetId != null) {
                    targetId
                } else {
                    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: "Imported Novel"
                    repository.createNovel(title = firstLine.take(40))
                }

                val importedCount = repository.importChapters(effectiveNovelId, chunks)
                _events.emit(LibraryEvent.ShowToast("Imported $importedCount chapter(s)."))
                _pendingImportText.value = null
                _targetNovelId.value = null
                _events.emit(LibraryEvent.OpenNovel(effectiveNovelId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _pendingImportText.value = null
                _targetNovelId.value = null
                _events.emit(LibraryEvent.ShowToast("Import failed: ${e.localizedMessage}"))
            } finally {
                importRunning = false
            }
        }
    }

    fun cancelImport() {
        _pendingImportText.value = null
        _targetNovelId.value = null
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "file.txt"
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }
        if (name == "file.txt") {
            name = uri.path?.substringAfterLast('/') ?: "file.txt"
        }
        return name
    }
}
