package com.example.ui.screens.audiobooks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioFileReader
import com.example.audio.AudioPlayerController
import com.example.audio.AudioPlayerSettings
import com.example.audio.AudioPlayerUiState
import com.example.audio.ChapterNavigator
import com.example.audio.ChapterSpan
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioBookmarkEntity
import com.example.data.repository.AudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The link between the full player screen and the audiobook player.
 *
 * It opens the book when the screen appears, supplies the chapter and bookmark lists, and
 * passes the buttons the user taps on to [AudioPlayerController]. The screen never talks to
 * the player or the database directly.
 *
 * @param autoPlay start playing as soon as the book is open. True when the user tapped a book
 *        on the shelf, false when they opened the player from the mini-player.
 */
class AudioPlayerViewModel(
    private val bookId: String,
    autoPlay: Boolean,
    private val repository: AudioRepository,
    private val controller: AudioPlayerController,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext

    // ------------------------------------------------------------------ What the screen shows

    /** The player's state, but only while it is this screen's book. Until then it is an empty state. */
    val player: StateFlow<AudioPlayerUiState> = controller.state
        .map { if (it.bookId == bookId) it else AudioPlayerUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioPlayerUiState())

    val settings: StateFlow<AudioPlayerSettings> = controller.settings

    val book: StateFlow<AudioBookEntity?> = repository.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<ChapterSpan>> = combine(
        repository.observeChapters(bookId),
        repository.observeFiles(bookId)
    ) { chapterRows, fileRows -> ChapterNavigator.spansOf(chapterRows, fileRows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<AudioBookmarkEntity>> = repository.observeBookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Short notes for the screen to show, for example "Chapter added". */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { controller.open(bookId, play = autoPlay) }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    // ------------------------------------------------------------------ Playback

    fun playPause() = controller.toggle()
    fun skipBack() = controller.skipBack()
    fun skipForward() = controller.skipForward()
    fun nextChapter() = controller.nextChapter()
    fun previousChapter() = controller.previousChapter()
    fun playChapter(index: Int) = controller.playChapter(index)
    fun seekInChapter(elapsedMs: Long) = controller.seekInChapter(elapsedMs)
    fun setSpeed(speed: Float) = controller.setSpeed(speed)

    // ------------------------------------------------------------------ Sleep timer

    fun startSleepTimer(minutes: Int) = controller.startSleepTimer(minutes)
    fun startSleepAtChapterEnd() = controller.startSleepAtChapterEnd()
    fun cancelSleepTimer() = controller.cancelSleepTimer()

    // ------------------------------------------------------------------ Bookmarks

    fun addBookmarkHere(note: String) {
        controller.addBookmarkHere(note)
        say("Bookmark saved")
    }

    fun openBookmark(bookmark: AudioBookmarkEntity) =
        controller.seekToFilePosition(bookmark.fileIndex, bookmark.positionMs)

    fun editBookmarkNote(bookmarkId: String, note: String) {
        viewModelScope.launch { repository.updateBookmarkNote(bookmarkId, note) }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch { repository.deleteBookmark(bookmarkId) }
    }

    fun bookmarkToChapter(bookmarkId: String) {
        viewModelScope.launch {
            val title = "Chapter ${chapters.value.size + 1}"
            val made = repository.bookmarkToChapter(bookmarkId, title)
            say(if (made) "Chapter added" else "Too close to the edge of another chapter")
        }
    }

    // ------------------------------------------------------------------ Chapters

    fun addChapterHere(title: String) {
        viewModelScope.launch {
            val made = controller.addChapterHere(title)
            say(if (made) "Chapter added" else "Too close to the edge of another chapter")
        }
    }

    fun renameChapter(chapterId: String, title: String) {
        viewModelScope.launch { repository.renameChapter(chapterId, title) }
    }

    fun removeChapter(chapterId: String) {
        viewModelScope.launch {
            val removed = repository.removeChapter(book.value?.id ?: bookId, chapterId)
            if (!removed) say("This chapter can't be removed")
        }
    }

    // ------------------------------------------------------------------ The book itself

    fun markFinished(finished: Boolean) {
        viewModelScope.launch {
            repository.setFinished(bookId, finished)
            say(if (finished) "Marked as finished" else "Marked as not finished")
        }
    }

    fun restartBook() {
        viewModelScope.launch {
            repository.restart(bookId)
            controller.seekToFilePosition(0, 0L)
            say("Back at the start")
        }
    }

    /**
     * Points the file that could not be played at a new location the user picked (Re-link).
     * Android's read permission for the new file is kept so it still works after a restart.
     */
    fun relinkMissingFile(newUri: Uri) {
        viewModelScope.launch {
            val fileIndex = player.value.fileIndex
            val file = repository.getFiles(bookId).firstOrNull { it.orderIndex == fileIndex } ?: return@launch
            val size = withContext(Dispatchers.IO) {
                try {
                    appContext.contentResolver.takePersistableUriPermission(
                        newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Some providers don't offer lasting access. The file works until the next restart.
                }
                AudioFileReader(appContext).queryInfo(newUri).sizeBytes
            }
            repository.relinkFile(file.id, newUri.toString(), size)
            controller.reload()
            say("File re-linked")
        }
    }

    // ------------------------------------------------------------------ Settings

    fun updateSettings(newSettings: AudioPlayerSettings) = controller.updateSettings(newSettings)
}
