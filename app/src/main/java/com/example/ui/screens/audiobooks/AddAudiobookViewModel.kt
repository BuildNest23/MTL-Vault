package com.example.ui.screens.audiobooks

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioImportRules
import com.example.audio.AudioImporter
import com.example.audio.ChapterOptions
import com.example.audio.DraftFile
import com.example.audio.ImportDraft
import com.example.data.repository.AudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the import screen is in its flow. */
enum class ImportPhase {
    /** Nothing picked yet. The screen offers "Choose a folder" and "Choose files". */
    PICK,

    /** Looking through the picked folder or files. Can take a few seconds for big books. */
    SCANNING,

    /** The draft is ready. The user checks the title, the order of files and the chapter choice. */
    REVIEW,

    /** Saving the book. */
    SAVING
}

/**
 * Everything the import screen shows.
 *
 * Chapters: by default the audio is kept as it is, one chapter per file (or one chapter for a
 * one-file book). A one-file book can instead be split every [splitMinutes] minutes by turning
 * [splitByTime] on. Chapter marks stored inside the audio are used when found, unless
 * [useEmbeddedChapters] is turned off.
 */
data class AddAudiobookUiState(
    val phase: ImportPhase = ImportPhase.PICK,
    val draft: ImportDraft? = null,
    val useEmbeddedChapters: Boolean = true,
    val splitByTime: Boolean = false,
    val splitMinutes: Int = DEFAULT_SPLIT_MINUTES,
    val error: String? = null
) {
    val files: List<DraftFile> get() = draft?.files.orEmpty()

    /** True when the book is one audio file, the only case where splitting by time applies. */
    val isSingleFile: Boolean get() = files.size == 1

    /** Chapters found inside a one-file book (or its .cue sheet). 0 when there are none. */
    val embeddedChapterCount: Int get() = if (isSingleFile) draft?.embeddedChapterCount ?: 0 else 0

    /** The "use the chapters found in the file" switch only matters when some were found. */
    val showEmbeddedSwitch: Boolean get() = isSingleFile && embeddedChapterCount >= 2

    /** The split option is offered for a one-file book that will not use its own chapters. */
    val showSplitOption: Boolean get() = isSingleFile && !(useEmbeddedChapters && embeddedChapterCount >= 2)

    /** Files for which no length could be read. They may be damaged, and the book total will be short. */
    val filesWithoutLength: Int get() = files.count { it.durationMs <= 0L }

    /** How many chapters the book will get, as far as is known before saving. */
    val chapterCount: Int
        get() {
            val d = draft ?: return 0
            return when {
                d.files.size != 1 -> d.files.size
                useEmbeddedChapters && d.embeddedChapterCount >= 2 -> d.embeddedChapterCount
                splitByTime && d.totalDurationMs > 0L -> {
                    val each = splitMinutes * 60_000L
                    ((d.totalDurationMs + each - 1) / each).toInt().coerceAtLeast(1)
                }
                else -> 1
            }
        }

    val canSave: Boolean
        get() = phase == ImportPhase.REVIEW && draft != null && draft.files.isNotEmpty() && draft.title.isNotBlank()

    companion object {
        const val DEFAULT_SPLIT_MINUTES = 15
        val SPLIT_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60)
    }
}

/**
 * The link between the "Add audiobook" screen and the importer.
 *
 * Flow: the screen hands over the folder or files the user picked, this class scans them and
 * keeps the draft, the user edits it, and [save] stores the book. Nothing is saved before
 * that. If the user leaves without saving, the read permission Android kept for the picked
 * folder or files is given back.
 */
class AddAudiobookViewModel(
    private val repository: AudioRepository,
    private val importer: AudioImporter
) : ViewModel() {

    private val _state = MutableStateFlow(AddAudiobookUiState())
    val state: StateFlow<AddAudiobookUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the id of the new book once it is saved. The screen then opens the player. */
    val saved: SharedFlow<String> = _saved.asSharedFlow()

    private var job: Job? = null
    private var finished = false

    // ------------------------------------------------------------------ Picking

    fun scanFolder(treeUri: Uri) = scan { importer.scanFolder(treeUri) }

    fun scanFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scan { importer.scanFiles(uris) }
    }

    private fun scan(work: suspend () -> ImportDraft) {
        if (_state.value.phase == ImportPhase.SCANNING || _state.value.phase == ImportPhase.SAVING) return
        _state.update { AddAudiobookUiState(phase = ImportPhase.SCANNING) }
        job = viewModelScope.launch {
            try {
                val draft = work()
                if (draft.files.isEmpty()) {
                    discardDraft(draft)
                    val skipped = draft.skippedNames
                    _state.value = AddAudiobookUiState(
                        error = if (skipped.isEmpty()) {
                            "No audio files found here."
                        } else {
                            "No playable audio files found. These formats can't be played: ${skipped.take(3).joinToString()}."
                        }
                    )
                } else {
                    _state.value = AddAudiobookUiState(phase = ImportPhase.REVIEW, draft = draft)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AddAudiobookUiState(error = "Couldn't read that: ${e.message ?: "unknown problem"}")
            }
        }
    }

    // ------------------------------------------------------------------ Editing the draft

    fun setTitle(title: String) = editDraft { it.copy(title = title) }
    fun setAuthor(author: String) = editDraft { it.copy(author = author) }
    fun setNarrator(narrator: String) = editDraft { it.copy(narrator = narrator) }

    /** Moves a file up (delta -1) or down (delta 1) in the playing order. */
    fun moveFile(index: Int, delta: Int) = editDraft { d ->
        val target = index + delta
        if (index !in d.files.indices || target !in d.files.indices) {
            d
        } else {
            val list = d.files.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            d.copy(files = list)
        }
    }

    /** Puts the files back in natural name order (Chapter 2 before Chapter 10). */
    fun sortByName() = editDraft { it.copy(files = AudioImportRules.sortNaturally(it.files)) }

    /** Leaves a file out of the book. At least one file always stays. */
    fun removeFile(index: Int) {
        val d = _state.value.draft ?: return
        if (d.files.size <= 1 || index !in d.files.indices) return
        val removed = d.files[index]
        // Files picked one by one each hold their own permission. A folder holds one for all.
        if (d.sourceTreeUri == null) discardDraft(d.copy(files = listOf(removed)))
        editDraft { current ->
            val list = current.files.toMutableList().also { it.removeAt(index) }
            // The chapter count and .cue sheet belonged to the original single file
            current.copy(
                files = list,
                embeddedChapterCount = if (list.size == 1) current.embeddedChapterCount else 0,
                cueText = if (list.size == 1) current.cueText else null
            )
        }
    }

    private fun editDraft(change: (ImportDraft) -> ImportDraft) {
        _state.update { s ->
            val d = s.draft ?: return@update s
            s.copy(draft = change(d))
        }
    }

    // ------------------------------------------------------------------ Chapter choices

    fun setUseEmbeddedChapters(use: Boolean) = _state.update { it.copy(useEmbeddedChapters = use) }

    fun setSplitByTime(split: Boolean) = _state.update { it.copy(splitByTime = split) }

    fun setSplitMinutes(minutes: Int) = _state.update { it.copy(splitMinutes = minutes.coerceIn(1, 600)) }

    // ------------------------------------------------------------------ Saving and leaving

    fun save() {
        val s = _state.value
        val d = s.draft ?: return
        if (!s.canSave) return
        _state.update { it.copy(phase = ImportPhase.SAVING, error = null) }
        job = viewModelScope.launch {
            try {
                val options = ChapterOptions(
                    useEmbeddedChapters = s.useEmbeddedChapters,
                    splitEveryMinutes = if (s.splitByTime && d.files.size == 1) s.splitMinutes else 0
                )
                val imported = importer.build(d, options)
                repository.addBook(imported.book, imported.files, imported.chapters)
                finished = true
                _saved.tryEmit(imported.book.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = ImportPhase.REVIEW, error = "Couldn't save the book: ${e.message ?: "unknown problem"}")
                }
            }
        }
    }

    /** Throws the draft away and goes back to the first step. */
    fun cancel() {
        // A book that is being saved must not be cancelled half-way
        if (_state.value.phase == ImportPhase.SAVING) return
        job?.cancel()
        _state.value.draft?.let { discardDraft(it) }
        _state.value = AddAudiobookUiState()
    }

    /**
     * Gives back the read permission of a draft that was not saved, unless a book that is
     * already in the app still uses the same folder or file.
     */
    private fun discardDraft(draft: ImportDraft) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val used = HashSet<String>()
                for (book in repository.getAllBooks()) {
                    book.sourceUri?.let { used.add(it) }
                    repository.getFiles(book.id).forEach { used.add(it.uri) }
                }
                val tree = draft.sourceTreeUri
                if (tree != null) {
                    if (tree !in used) importer.releasePermissions(tree, emptyList())
                } else {
                    importer.releasePermissions(null, draft.files.map { it.uri }.filter { it !in used })
                }
            } catch (e: Exception) {
                // Nothing more can be done. The permission just stays until the app is cleared.
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    override fun onCleared() {
        // Leaving the screen without saving gives the folder permission back
        if (!finished) _state.value.draft?.let { discardDraft(it) }
        super.onCleared()
    }
}
