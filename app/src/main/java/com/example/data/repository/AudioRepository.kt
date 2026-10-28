package com.example.data.repository

import com.example.data.database.AudioDao
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioBookmarkEntity
import com.example.data.database.entities.AudioChapterEntity
import com.example.data.database.entities.AudioFileEntity
import com.example.data.database.entities.AudioListeningEntity
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone
import java.util.UUID

/**
 * The one place the audiobook screens, the player and the importer go through to reach the
 * database. It adds the small rules that sit on top of plain reads and writes, such as
 * "adding a chapter splits the chapter you are in" and "progress is measured from the start
 * of the whole book".
 *
 * It does not touch Android classes, so it can be unit tested. Anything that needs a
 * Context (reading files, releasing file permissions) lives in the importer and the player.
 *
 * The audio files themselves are never deleted or modified by this class. Removing a book
 * only removes its entries from the app.
 */
class AudioRepository(
    private val audioDao: AudioDao,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    // ============================================================ Reading

    fun observeBooks(): Flow<List<AudioBookEntity>> = audioDao.observeBooks()

    fun observeBook(bookId: String): Flow<AudioBookEntity?> = audioDao.observeBook(bookId)

    /** The most recently played unfinished book, for the "Continue listening" card. */
    fun observeContinueBook(): Flow<AudioBookEntity?> = audioDao.observeContinueBook()

    suspend fun getBook(bookId: String): AudioBookEntity? = audioDao.getBook(bookId)

    suspend fun getAllBooks(): List<AudioBookEntity> = audioDao.getAllBooks()

    suspend fun getFiles(bookId: String): List<AudioFileEntity> = audioDao.getFiles(bookId)

    fun observeFiles(bookId: String): Flow<List<AudioFileEntity>> = audioDao.observeFiles(bookId)

    suspend fun getChapters(bookId: String): List<AudioChapterEntity> = audioDao.getChapters(bookId)

    fun observeChapters(bookId: String): Flow<List<AudioChapterEntity>> = audioDao.observeChapters(bookId)

    fun observeBookmarks(bookId: String): Flow<List<AudioBookmarkEntity>> = audioDao.observeBookmarks(bookId)

    suspend fun getBookmarks(bookId: String): List<AudioBookmarkEntity> = audioDao.getBookmarks(bookId)

    fun observeListening(): Flow<List<AudioListeningEntity>> = audioDao.observeListening()

    // ============================================================ Books

    /**
     * Saves a new book with its files and chapters in one step.
     *
     * If no chapters were given, one chapter per file is created so the player always has
     * something to show. The total length is worked out from the files.
     */
    suspend fun addBook(
        book: AudioBookEntity,
        files: List<AudioFileEntity>,
        chapters: List<AudioChapterEntity>
    ) {
        require(files.isNotEmpty()) { "An audiobook needs at least one audio file" }

        val orderedFiles = files
            .sortedBy { it.orderIndex }
            .mapIndexed { index, file -> if (file.orderIndex == index) file else file.copy(orderIndex = index) }

        val finalChapters = if (chapters.isEmpty()) {
            defaultChaptersForFiles(book.id, orderedFiles)
        } else {
            renumber(chapters.sortedBy { it.orderIndex })
        }

        val total = orderedFiles.sumOf { it.durationMs }
        val now = timeProvider()
        audioDao.insertBookWithContents(
            book = book.copy(totalDurationMs = total, addedAt = now, updatedAt = now),
            files = orderedFiles,
            chapters = finalChapters
        )
    }

    /** Removes the book from the app. The audio files on the phone are left alone. */
    suspend fun deleteBook(bookId: String) = audioDao.deleteBook(bookId)

    suspend fun updateDetails(bookId: String, title: String, author: String, narrator: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        audioDao.updateDetails(bookId, cleanTitle, author.trim(), narrator.trim(), timeProvider())
    }

    suspend fun updateCover(bookId: String, coverUri: String?) =
        audioDao.updateCover(bookId, coverUri, timeProvider())

    suspend fun setSpeed(bookId: String, speed: Float) {
        audioDao.updateSpeed(bookId, speed.coerceIn(MIN_SPEED, MAX_SPEED), timeProvider())
    }

    /**
     * Saves where the user stopped. Called every few seconds while playing and on pause, so
     * it does one small write and nothing else.
     *
     * @param progressMs the same spot measured from the start of the whole book
     *                   (see [progressMsFor]).
     */
    suspend fun saveProgress(bookId: String, fileIndex: Int, positionMs: Long, progressMs: Long) {
        audioDao.updateProgress(
            bookId = bookId,
            fileIndex = fileIndex.coerceAtLeast(0),
            positionMs = positionMs.coerceAtLeast(0L),
            progressMs = progressMs.coerceAtLeast(0L),
            now = timeProvider()
        )
    }

    suspend fun setFinished(bookId: String, finished: Boolean) {
        val now = timeProvider()
        audioDao.setFinished(bookId, finished, if (finished) now else null, now)
    }

    /** Back to the very beginning and not finished. Listening history is kept. */
    suspend fun restart(bookId: String) = audioDao.resetProgress(bookId, timeProvider())

    // ============================================================ Files

    /** Points a file at its new location after the user moved it (Re-link). */
    suspend fun relinkFile(fileId: String, newUri: String, sizeBytes: Long) =
        audioDao.updateFileUri(fileId, newUri, sizeBytes)

    /**
     * Applies a corrected length for a file, for example when the player learns the real
     * duration. Updates the book total and keeps chapters inside the file's length.
     */
    suspend fun correctFileDuration(bookId: String, fileId: String, durationMs: Long) {
        if (durationMs <= 0L) return

        audioDao.updateFileDuration(fileId, durationMs)
        val files = audioDao.getFiles(bookId)
        audioDao.updateTotalDuration(bookId, files.sumOf { it.durationMs }, timeProvider())

        val chapters = audioDao.getChapters(bookId)
        val lastInFile = chapters.lastOrNull { it.fileId == fileId }
        var changed = false
        val fixed = chapters.map { chapter ->
            if (chapter.fileId != fileId) return@map chapter
            var end = chapter.endMs
            if (end > durationMs) end = durationMs
            if (chapter.id == lastInFile?.id && end < durationMs) end = durationMs
            end = end.coerceAtLeast(chapter.startMs)
            if (end != chapter.endMs) {
                changed = true
                chapter.copy(endMs = end)
            } else {
                chapter
            }
        }
        if (changed) audioDao.replaceChapters(bookId, fixed)
    }

    // ============================================================ Chapters

    /** Replaces the whole chapter list, for example after choosing a different way to split a book. */
    suspend fun replaceChapters(bookId: String, chapters: List<AudioChapterEntity>) {
        if (chapters.isEmpty()) return
        audioDao.replaceChapters(bookId, renumber(chapters.sortedBy { it.orderIndex }))
    }

    suspend fun renameChapter(chapterId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        audioDao.renameChapter(chapterId, clean)
    }

    /**
     * Starts a new chapter at [positionMs] inside the file [fileId]. The chapter the position
     * is in is cut in two. Returns false if the spot is too close to an existing chapter edge.
     */
    suspend fun addChapterAt(bookId: String, fileId: String, positionMs: Long, title: String): Boolean {
        val chapters = audioDao.getChapters(bookId)
        val updated = insertChapterAt(
            chapters = chapters,
            fileId = fileId,
            positionMs = positionMs,
            title = title.trim().ifEmpty { "Chapter ${chapters.size + 1}" },
            newId = UUID.randomUUID().toString()
        ) ?: return false
        audioDao.replaceChapters(bookId, updated)
        return true
    }

    /**
     * Removes a chapter by merging its time into the chapter next to it, so no audio is left
     * without a chapter. Returns false if it is the only chapter of its file.
     */
    suspend fun removeChapter(bookId: String, chapterId: String): Boolean {
        val chapters = audioDao.getChapters(bookId)
        val updated = removeChapterMerging(chapters, chapterId) ?: return false
        audioDao.replaceChapters(bookId, updated)
        return true
    }

    // ============================================================ Bookmarks

    suspend fun addBookmark(
        bookId: String,
        fileIndex: Int,
        positionMs: Long,
        bookPositionMs: Long,
        note: String = ""
    ): AudioBookmarkEntity {
        val bookmark = AudioBookmarkEntity(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            fileIndex = fileIndex.coerceAtLeast(0),
            positionMs = positionMs.coerceAtLeast(0L),
            bookPositionMs = bookPositionMs.coerceAtLeast(0L),
            note = note.trim(),
            createdAt = timeProvider()
        )
        audioDao.insertBookmark(bookmark)
        return bookmark
    }

    suspend fun updateBookmarkNote(bookmarkId: String, note: String) =
        audioDao.updateBookmarkNote(bookmarkId, note.trim())

    suspend fun deleteBookmark(bookmarkId: String) = audioDao.deleteBookmark(bookmarkId)

    /** Turns a bookmark into a chapter start. Returns false if a chapter can't start there. */
    suspend fun bookmarkToChapter(bookmarkId: String, title: String): Boolean {
        val bookmark = audioDao.getBookmark(bookmarkId) ?: return false
        val file = audioDao.getFiles(bookmark.bookId).firstOrNull { it.orderIndex == bookmark.fileIndex }
            ?: return false
        return addChapterAt(bookmark.bookId, file.id, bookmark.positionMs, title)
    }

    // ============================================================ Listening stats

    /**
     * Adds listening time to today's row for a book.
     *
     * @param listenedMs real time spent listening (pauses do not count)
     * @param bookMs how much of the book that covered (listened time multiplied by speed)
     */
    suspend fun addListening(bookId: String, bookTitle: String, listenedMs: Long, bookMs: Long) {
        if (listenedMs <= 0L && bookMs <= 0L) return
        val now = timeProvider()
        audioDao.addListening(
            bookId = bookId,
            bookTitle = bookTitle,
            dayEpoch = dayEpochFor(now),
            listenedMs = listenedMs,
            bookMs = bookMs,
            now = now
        )
    }

    // ============================================================ Pure helpers

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f

        /** A new chapter must leave at least this much audio on both sides of it. */
        const val MIN_CHAPTER_MS = 1_000L

        private const val DAY_MS = 86_400_000L

        /**
         * The day as a count of days since 1970-01-01 in the phone's local time.
         * Same value as LocalDate.toEpochDay, worked out by hand because the app supports
         * Android 7, where java.time is not available.
         */
        fun dayEpochFor(timeMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
            val local = timeMs + timeZone.getOffset(timeMs)
            return Math.floorDiv(local, DAY_MS)
        }

        /** Position measured from the start of the whole book. */
        fun progressMsFor(files: List<AudioFileEntity>, fileIndex: Int, positionMs: Long): Long {
            val before = files
                .filter { it.orderIndex < fileIndex }
                .sumOf { it.durationMs }
            val file = files.firstOrNull { it.orderIndex == fileIndex }
            val within = if (file != null && file.durationMs > 0L) {
                positionMs.coerceIn(0L, file.durationMs)
            } else {
                positionMs.coerceAtLeast(0L)
            }
            return before + within
        }

        /**
         * The reverse of [progressMsFor]: which file and how far into it a book-wide position is.
         * Positions past the end land at the end of the last file.
         */
        fun locate(files: List<AudioFileEntity>, bookPositionMs: Long): Pair<Int, Long> {
            if (files.isEmpty()) return 0 to 0L
            var remaining = bookPositionMs.coerceAtLeast(0L)
            val ordered = files.sortedBy { it.orderIndex }
            for (file in ordered) {
                if (remaining < file.durationMs || file === ordered.last()) {
                    val position = if (file.durationMs > 0L) remaining.coerceAtMost(file.durationMs) else remaining
                    return file.orderIndex to position
                }
                remaining -= file.durationMs
            }
            val last = ordered.last()
            return last.orderIndex to last.durationMs
        }

        /**
         * Which chapter a position inside a file belongs to. Returns the index in [chapters],
         * or -1 if nothing fits (for example an empty list).
         */
        fun findChapterIndex(chapters: List<AudioChapterEntity>, fileId: String, positionMs: Long): Int {
            val exact = chapters.indexOfFirst {
                it.fileId == fileId && positionMs >= it.startMs && positionMs < it.endMs
            }
            if (exact >= 0) return exact
            // Past the end of the last chapter of the file, or a chapter with unknown length.
            return chapters.indexOfLast { it.fileId == fileId && it.startMs <= positionMs }
        }

        /** One chapter per file, named after the file. Used when a book arrives with no chapters. */
        fun defaultChaptersForFiles(bookId: String, files: List<AudioFileEntity>): List<AudioChapterEntity> =
            files.sortedBy { it.orderIndex }.mapIndexed { index, file ->
                AudioChapterEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    fileId = file.id,
                    orderIndex = index,
                    title = file.displayName.substringBeforeLast('.').ifBlank { "Chapter ${index + 1}" },
                    startMs = 0L,
                    endMs = file.durationMs
                )
            }

        /** Makes orderIndex run 0, 1, 2, ... in list order. */
        fun renumber(chapters: List<AudioChapterEntity>): List<AudioChapterEntity> =
            chapters.mapIndexed { index, chapter ->
                if (chapter.orderIndex == index) chapter else chapter.copy(orderIndex = index)
            }

        /**
         * Cuts the chapter that contains [positionMs] in two and returns the new list, or null
         * if no chapter contains the position or it is within [MIN_CHAPTER_MS] of an edge.
         */
        fun insertChapterAt(
            chapters: List<AudioChapterEntity>,
            fileId: String,
            positionMs: Long,
            title: String,
            newId: String
        ): List<AudioChapterEntity>? {
            val index = chapters.indexOfFirst {
                it.fileId == fileId && positionMs > it.startMs && positionMs < it.endMs
            }
            if (index < 0) return null
            val chapter = chapters[index]
            if (positionMs - chapter.startMs < MIN_CHAPTER_MS) return null
            if (chapter.endMs - positionMs < MIN_CHAPTER_MS) return null

            val firstHalf = chapter.copy(endMs = positionMs)
            val secondHalf = AudioChapterEntity(
                id = newId,
                bookId = chapter.bookId,
                fileId = fileId,
                orderIndex = 0,
                title = title,
                startMs = positionMs,
                endMs = chapter.endMs,
                userEdited = true
            )
            val result = chapters.toMutableList()
            result[index] = firstHalf
            result.add(index + 1, secondHalf)
            return renumber(result)
        }

        /**
         * Removes a chapter and gives its time to the previous chapter in the same file, or
         * to the next one if it is the first in its file. Returns null if the chapter does
         * not exist or is the only chapter of its file.
         */
        fun removeChapterMerging(
            chapters: List<AudioChapterEntity>,
            chapterId: String
        ): List<AudioChapterEntity>? {
            val index = chapters.indexOfFirst { it.id == chapterId }
            if (index < 0) return null
            val chapter = chapters[index]

            val previous = chapters.getOrNull(index - 1)?.takeIf { it.fileId == chapter.fileId }
            val next = chapters.getOrNull(index + 1)?.takeIf { it.fileId == chapter.fileId }

            val result = chapters.toMutableList()
            when {
                previous != null -> result[index - 1] = previous.copy(endMs = chapter.endMs)
                next != null -> result[index + 1] = next.copy(startMs = chapter.startMs)
                else -> return null
            }
            result.removeAt(index)
            return renumber(result)
        }
    }
}
