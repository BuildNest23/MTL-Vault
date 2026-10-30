package com.example.ui.screens.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NovelSummary
import com.example.data.remote.AniListClient
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    private var fetchRunning = false

    /**
     * Looks up AniList for every novel that has no cover or no description and fills in
     * whatever is missing. Existing covers/descriptions/authors are never overwritten.
     */
    fun fetchMissingInfo(context: Context) {
        if (fetchRunning) return
        val targets = novels.value.filter { it.coverUri.isNullOrBlank() || it.description.isBlank() }
        if (targets.isEmpty()) {
            viewModelScope.launch { _events.emit(LibraryEvent.ShowToast("All novels already have a cover and description.")) }
            return
        }
        fetchRunning = true
        val appContext = context.applicationContext
        viewModelScope.launch {
            var updated = 0
            try {
                _events.emit(LibraryEvent.ShowToast("Fetching info for ${targets.size} novel(s)..."))
                for ((index, novel) in targets.withIndex()) {
                    val meta = AniListClient.search(novel.title)
                    if (meta != null) {
                        var coverUri = novel.coverUri
                        if (coverUri.isNullOrBlank() && meta.coverUrl != null) {
                            val file = File(appContext.filesDir, "covers/${novel.id}_${System.currentTimeMillis()}.jpg")
                            if (AniListClient.downloadImage(meta.coverUrl, file)) {
                                coverUri = Uri.fromFile(file).toString()
                            }
                        }
                        val description = novel.description.ifBlank { meta.description }
                        val author = novel.author.ifBlank { meta.author }
                        if (coverUri != novel.coverUri || description != novel.description || author != novel.author) {
                            repository.updateNovelMetadata(novel.id, description, coverUri, author)
                            updated++
                        }
                    }
                    if (index < targets.lastIndex) kotlinx.coroutines.delay(800) // stay under AniList rate limit
                }
                _events.emit(LibraryEvent.ShowToast(
                    if (updated > 0) "Updated $updated novel(s)." else "No matches found online."
                ))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(LibraryEvent.ShowToast(
                    if (updated > 0) "Updated $updated novel(s), then stopped: ${e.localizedMessage}"
                    else "Fetch failed: ${e.localizedMessage}"
                ))
            } finally {
                fetchRunning = false
            }
        }
    }

    // ------------------------------------------------------------ Long-press menu actions

    /**
     * Finds the chapter to continue reading: the one you stopped at, else the first unread one,
     * else the first chapter. Calls [onResult] with its id, or null when the novel has no chapters.
     */
    fun continueReading(novel: NovelSummary, onResult: (chapterId: String?) -> Unit) {
        viewModelScope.launch {
            val chapters = repository.getChapterSummaries(novel.id).first()
            val chapterId = novel.lastChapterId?.takeIf { last -> chapters.any { it.id == last } }
                ?: chapters.firstOrNull { !it.read }?.id
                ?: chapters.firstOrNull()?.id
            if (chapterId == null) {
                _events.emit(LibraryEvent.ShowToast("This novel has no chapters yet."))
            }
            onResult(chapterId)
        }
    }

    /** Marks every chapter of a novel read or unread. */
    fun setAllChaptersRead(novel: NovelSummary, read: Boolean) {
        viewModelScope.launch {
            repository.setAllChaptersRead(novel.id, read)
            _events.emit(
                LibraryEvent.ShowToast(
                    if (read) "\"${novel.title}\": all chapters marked as read."
                    else "\"${novel.title}\": all chapters marked as unread."
                )
            )
        }
    }

    /** Deletes a novel with all its chapters, and the cover file the app saved for it. */
    fun deleteNovel(context: Context, novel: NovelSummary) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            repository.deleteNovel(novel.id)
            withContext(Dispatchers.IO) { deleteOwnCover(appContext, novel.coverUri) }
            _events.emit(LibraryEvent.ShowToast("\"${novel.title}\" deleted."))
        }
    }

    /**
     * Saves the Edit novel window opened from the long-press menu. Same behaviour as on the novel
     * page: a chosen cover is copied into the app and shrunk to at most 1000 pixels, and the old
     * saved cover is deleted. If the image can't be read, the text changes are still saved.
     */
    fun saveNovelDetails(
        context: Context,
        novelId: String,
        title: String,
        author: String,
        translator: String,
        description: String,
        newCover: Uri?,
        removeCover: Boolean
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val current = repository.getNovelByIdSync(novelId) ?: return@launch
            var coverUri = current.coverUri
            var coverProblem = false

            if (newCover != null) {
                val saved = withContext(Dispatchers.IO) { copyCoverIntoApp(appContext, novelId, newCover) }
                if (saved != null) {
                    withContext(Dispatchers.IO) { deleteOwnCover(appContext, current.coverUri) }
                    coverUri = saved
                } else {
                    coverProblem = true
                }
            } else if (removeCover) {
                withContext(Dispatchers.IO) { deleteOwnCover(appContext, current.coverUri) }
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
                LibraryEvent.ShowToast(
                    if (coverProblem) "Saved, but that image couldn't be read." else "Novel updated."
                )
            )
        }
    }

    /** Copies a picked picture into the app's covers folder. Returns the new file address, or null if it can't be read. */
    private fun copyCoverIntoApp(context: Context, novelId: String, source: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1000) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val longest = maxOf(decoded.width, decoded.height)
            val bitmap = if (longest > 1000) {
                val scale = 1000f / longest
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }

            val file = File(context.filesDir, "covers/${novelId}_${System.currentTimeMillis()}.jpg")
            file.parentFile?.mkdirs()
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            if (file.length() > 0L) Uri.fromFile(file).toString() else null
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Deletes a cover file the app saved itself. Pictures that live elsewhere are never touched. */
    private fun deleteOwnCover(context: Context, coverUri: String?) {
        if (coverUri.isNullOrBlank()) return
        try {
            val path = Uri.parse(coverUri).path ?: return
            val file = File(path)
            val coversDir = File(context.filesDir, "covers")
            if (file.exists() && file.parentFile?.canonicalPath == coversDir.canonicalPath) file.delete()
        } catch (e: Exception) {
            // The old file stays. That only wastes a little space.
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
