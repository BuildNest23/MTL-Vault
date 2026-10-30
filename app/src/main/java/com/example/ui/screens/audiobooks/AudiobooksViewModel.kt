package com.example.ui.screens.audiobooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioImporter
import com.example.audio.AudioPlayerController
import com.example.data.database.entities.AudioBookEntity
import com.example.data.repository.AudioRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The shelf as the screen sees it. [loaded] is false until the database has answered once. */
data class AudiobooksUiState(
    val books: List<AudioBookEntity> = emptyList(),
    val loaded: Boolean = false
)

/**
 * The link between the Audiobooks shelf screen and the data.
 *
 * It supplies the list of books, the book to "continue listening" to, and the player's current
 * state (so the shelf can mark the book that is playing). It also removes books. Adding a book
 * is handled by AddAudiobookViewModel.
 */
class AudiobooksViewModel(
    private val repository: AudioRepository,
    private val controller: AudioPlayerController,
    private val importer: AudioImporter
) : ViewModel() {

    val uiState: StateFlow<AudiobooksUiState> = repository.observeBooks()
        .map { AudiobooksUiState(books = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudiobooksUiState())

    /** The book listened to last that is not finished yet, or null. */
    val continueBook: StateFlow<AudioBookEntity?> = repository.observeContinueBook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class NowPlaying(val bookId: String? = null, val isPlaying: Boolean = false)

    /** Which book is playing. Only changes when that changes, not every half second. */
    val nowPlaying: StateFlow<NowPlaying> = controller.state
        .map { NowPlaying(it.bookId, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlaying())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notes for the screen to show, for example "Book removed". */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Marks a book finished or not finished from the shelf's long-press menu. */
    fun setFinished(bookId: String, finished: Boolean) {
        viewModelScope.launch { repository.setFinished(bookId, finished) }
    }

    /** Starts a finished (or any) book again from the beginning. */
    fun restart(bookId: String) {
        viewModelScope.launch { repository.restart(bookId) }
    }

    /** Changes the title and author of a book. */
    fun editDetails(book: AudioBookEntity, title: String, author: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch {
            repository.updateDetails(book.id, cleanTitle, author.trim(), book.narrator)
        }
    }

    /**
     * Removes a book from the app. The audio files on the phone are not touched.
     *
     * If the book is loaded in the player, the player is closed first. The cover picture is
     * deleted, and the read permission Android kept for the folder or files is given back,
     * unless another book in the app uses the same folder or file.
     */
    fun deleteBook(book: AudioBookEntity) {
        viewModelScope.launch {
            if (controller.state.value.bookId == book.id) controller.close()

            val files = repository.getFiles(book.id)
            val others = repository.getAllBooks().filter { it.id != book.id }

            repository.deleteBook(book.id)
            importer.deleteCover(book.id)

            val stillUsedAddresses = HashSet<String>()
            for (other in others) {
                other.sourceUri?.let { stillUsedAddresses.add(it) }
                repository.getFiles(other.id).forEach { stillUsedAddresses.add(it.uri) }
            }
            val source = book.sourceUri?.takeIf { it !in stillUsedAddresses }
            val fileUris = if (book.sourceUri != null) {
                emptyList()
            } else {
                files.map { it.uri }.filter { it !in stillUsedAddresses }
            }
            importer.releasePermissions(source, fileUris)

            _messages.tryEmit("\"${book.title}\" removed. Your audio files were not deleted.")
        }
    }
}
