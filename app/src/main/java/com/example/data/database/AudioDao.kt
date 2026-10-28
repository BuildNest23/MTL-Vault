package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioBookmarkEntity
import com.example.data.database.entities.AudioChapterEntity
import com.example.data.database.entities.AudioFileEntity
import com.example.data.database.entities.AudioListeningEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes everything for audiobooks: books, files, chapters, bookmarks and the
 * listening-time history.
 *
 * This is an abstract class (not an interface) so it can hold a few @Transaction helpers
 * that combine several steps into one all-or-nothing change.
 *
 * Important: books are inserted with IGNORE and changed with @Update, never with REPLACE.
 * Replacing a book row would delete and re-create it, and that would wipe its files,
 * chapters and bookmarks through the cascade rule.
 */
@Dao
abstract class AudioDao {

    // ------------------------------------------------------------------ Books

    /** The shelf: most recently played first, never-played books ordered by when they were added. */
    @Query("SELECT * FROM audio_books ORDER BY COALESCE(lastPlayedAt, addedAt) DESC")
    abstract fun observeBooks(): Flow<List<AudioBookEntity>>

    @Query("SELECT * FROM audio_books WHERE id = :id LIMIT 1")
    abstract fun observeBook(id: String): Flow<AudioBookEntity?>

    @Query("SELECT * FROM audio_books WHERE id = :id LIMIT 1")
    abstract suspend fun getBook(id: String): AudioBookEntity?

    @Query("SELECT * FROM audio_books ORDER BY COALESCE(lastPlayedAt, addedAt) DESC")
    abstract suspend fun getAllBooks(): List<AudioBookEntity>

    /** The book played most recently that is not finished, for the "Continue listening" row. */
    @Query(
        """
        SELECT * FROM audio_books
        WHERE lastPlayedAt IS NOT NULL AND finished = 0
        ORDER BY lastPlayedAt DESC
        LIMIT 1
        """
    )
    abstract fun observeContinueBook(): Flow<AudioBookEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBook(book: AudioBookEntity)

    @Update
    abstract suspend fun updateBook(book: AudioBookEntity)

    @Query("DELETE FROM audio_books WHERE id = :id")
    abstract suspend fun deleteBook(id: String)

    /** Saves where the user stopped. Called every few seconds while playing and on pause. */
    @Query(
        """
        UPDATE audio_books
        SET lastFileIndex = :fileIndex,
            lastPositionMs = :positionMs,
            progressMs = :progressMs,
            lastPlayedAt = :now,
            updatedAt = :now
        WHERE id = :bookId
        """
    )
    abstract suspend fun updateProgress(
        bookId: String,
        fileIndex: Int,
        positionMs: Long,
        progressMs: Long,
        now: Long
    )

    @Query("UPDATE audio_books SET playbackSpeed = :speed, updatedAt = :now WHERE id = :bookId")
    abstract suspend fun updateSpeed(bookId: String, speed: Float, now: Long)

    @Query(
        """
        UPDATE audio_books
        SET finished = :finished,
            finishedAt = :finishedAt,
            updatedAt = :now
        WHERE id = :bookId
        """
    )
    abstract suspend fun setFinished(bookId: String, finished: Boolean, finishedAt: Long?, now: Long)

    /** Back to the very beginning, and not finished any more. Listening history is kept. */
    @Query(
        """
        UPDATE audio_books
        SET lastFileIndex = 0,
            lastPositionMs = 0,
            progressMs = 0,
            finished = 0,
            finishedAt = NULL,
            updatedAt = :now
        WHERE id = :bookId
        """
    )
    abstract suspend fun resetProgress(bookId: String, now: Long)

    @Query("UPDATE audio_books SET totalDurationMs = :totalMs, updatedAt = :now WHERE id = :bookId")
    abstract suspend fun updateTotalDuration(bookId: String, totalMs: Long, now: Long)

    @Query("UPDATE audio_books SET title = :title, author = :author, narrator = :narrator, updatedAt = :now WHERE id = :bookId")
    abstract suspend fun updateDetails(bookId: String, title: String, author: String, narrator: String, now: Long)

    @Query("UPDATE audio_books SET coverUri = :coverUri, updatedAt = :now WHERE id = :bookId")
    abstract suspend fun updateCover(bookId: String, coverUri: String?, now: Long)

    // ------------------------------------------------------------------ Files

    @Query("SELECT * FROM audio_files WHERE bookId = :bookId ORDER BY orderIndex ASC")
    abstract suspend fun getFiles(bookId: String): List<AudioFileEntity>

    @Query("SELECT * FROM audio_files WHERE bookId = :bookId ORDER BY orderIndex ASC")
    abstract fun observeFiles(bookId: String): Flow<List<AudioFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFiles(files: List<AudioFileEntity>)

    @Query("DELETE FROM audio_files WHERE bookId = :bookId")
    abstract suspend fun deleteFilesForBook(bookId: String)

    /** Used by Re-link when a file was moved: point the row at the new location. */
    @Query("UPDATE audio_files SET uri = :uri, sizeBytes = :sizeBytes WHERE id = :fileId")
    abstract suspend fun updateFileUri(fileId: String, uri: String, sizeBytes: Long)

    @Query("UPDATE audio_files SET durationMs = :durationMs WHERE id = :fileId")
    abstract suspend fun updateFileDuration(fileId: String, durationMs: Long)

    // --------------------------------------------------------------- Chapters

    @Query("SELECT * FROM audio_chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    abstract suspend fun getChapters(bookId: String): List<AudioChapterEntity>

    @Query("SELECT * FROM audio_chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    abstract fun observeChapters(bookId: String): Flow<List<AudioChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChapters(chapters: List<AudioChapterEntity>)

    @Update
    abstract suspend fun updateChapter(chapter: AudioChapterEntity)

    @Query("DELETE FROM audio_chapters WHERE id = :chapterId")
    abstract suspend fun deleteChapter(chapterId: String)

    @Query("DELETE FROM audio_chapters WHERE bookId = :bookId")
    abstract suspend fun deleteChaptersForBook(bookId: String)

    @Query("UPDATE audio_chapters SET title = :title, userEdited = 1 WHERE id = :chapterId")
    abstract suspend fun renameChapter(chapterId: String, title: String)

    // -------------------------------------------------------------- Bookmarks

    @Query("SELECT * FROM audio_bookmarks WHERE bookId = :bookId ORDER BY bookPositionMs ASC")
    abstract fun observeBookmarks(bookId: String): Flow<List<AudioBookmarkEntity>>

    @Query("SELECT * FROM audio_bookmarks WHERE bookId = :bookId ORDER BY bookPositionMs ASC")
    abstract suspend fun getBookmarks(bookId: String): List<AudioBookmarkEntity>

    @Query("SELECT * FROM audio_bookmarks WHERE id = :id LIMIT 1")
    abstract suspend fun getBookmark(id: String): AudioBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBookmark(bookmark: AudioBookmarkEntity)

    @Query("UPDATE audio_bookmarks SET note = :note WHERE id = :id")
    abstract suspend fun updateBookmarkNote(id: String, note: String)

    @Query("DELETE FROM audio_bookmarks WHERE id = :id")
    abstract suspend fun deleteBookmark(id: String)

    // -------------------------------------------------------------- Listening

    /** Every day-row, newest first. The table only grows by one row per book per day, so it stays small. */
    @Query("SELECT * FROM audio_listening ORDER BY dayEpoch DESC")
    abstract fun observeListening(): Flow<List<AudioListeningEntity>>

    @Query("SELECT * FROM audio_listening WHERE dayEpoch >= :fromDayEpoch ORDER BY dayEpoch ASC")
    abstract fun observeListeningSince(fromDayEpoch: Long): Flow<List<AudioListeningEntity>>

    @Query(
        """
        INSERT OR IGNORE INTO audio_listening
            (id, bookId, bookTitle, dayEpoch, listenedMs, bookMs, updatedAt)
        VALUES (:id, :bookId, :bookTitle, :dayEpoch, 0, 0, :now)
        """
    )
    protected abstract suspend fun insertListeningRowIfMissing(
        id: String,
        bookId: String,
        bookTitle: String,
        dayEpoch: Long,
        now: Long
    )

    @Query(
        """
        UPDATE audio_listening
        SET listenedMs = listenedMs + :listenedMs,
            bookMs = bookMs + :bookMs,
            bookTitle = :bookTitle,
            updatedAt = :now
        WHERE id = :id
        """
    )
    protected abstract suspend fun addToListeningRow(
        id: String,
        bookTitle: String,
        listenedMs: Long,
        bookMs: Long,
        now: Long
    )

    /**
     * Adds listening time to today's row for a book, creating the row the first time.
     * [listenedMs] is real time spent, [bookMs] is how much of the book that covered.
     */
    @Transaction
    open suspend fun addListening(
        bookId: String,
        bookTitle: String,
        dayEpoch: Long,
        listenedMs: Long,
        bookMs: Long,
        now: Long
    ) {
        if (listenedMs <= 0L && bookMs <= 0L) return
        val id = AudioListeningEntity.makeId(bookId, dayEpoch)
        insertListeningRowIfMissing(id, bookId, bookTitle, dayEpoch, now)
        addToListeningRow(id, bookTitle, listenedMs.coerceAtLeast(0L), bookMs.coerceAtLeast(0L), now)
    }

    // ------------------------------------------------------- Combined changes

    /**
     * Saves a brand-new book together with its files and chapters in one step, so a failure
     * half-way never leaves a book with no chapters.
     */
    @Transaction
    open suspend fun insertBookWithContents(
        book: AudioBookEntity,
        files: List<AudioFileEntity>,
        chapters: List<AudioChapterEntity>
    ) {
        insertBook(book)
        insertFiles(files)
        insertChapters(chapters)
    }

    /**
     * Replaces the chapter list of an existing book in one step. Used when the user changes
     * how a book is split, or after adding, renaming or removing chapters.
     */
    @Transaction
    open suspend fun replaceChapters(bookId: String, chapters: List<AudioChapterEntity>) {
        deleteChaptersForBook(bookId)
        insertChapters(chapters)
    }
}
