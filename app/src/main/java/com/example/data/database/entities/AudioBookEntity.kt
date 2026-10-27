package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One audiobook on the shelf.
 *
 * A book is made of one or more audio files ([AudioFileEntity]) and an ordered list of
 * chapters ([AudioChapterEntity]). The audio itself is never copied: files stay where they
 * are on the phone and only a read permission (a content URI) is remembered.
 *
 * Resume position is stored as "which file, and how far into that file":
 *  - [lastFileIndex]   position of the file in the book (0-based, same as AudioFileEntity.orderIndex)
 *  - [lastPositionMs]  milliseconds into that file
 * [progressMs] is the same spot expressed as milliseconds from the very start of the whole
 * book. It is saved alongside so the shelf can draw progress bars without extra queries.
 */
@Entity(
    tableName = "audio_books",
    indices = [
        Index(value = ["lastPlayedAt"]),
        Index(value = ["addedAt"])
    ]
)
data class AudioBookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String = "",
    val narrator: String = "",

    /** Cover image: a content:// or file:// URI string, or null for a generated cover. */
    val coverUri: String? = null,

    /** Folder (document tree) URI the book was imported from, if any. Used to re-link missing files. */
    val sourceUri: String? = null,

    /** Total length of all files added together, in milliseconds. */
    val totalDurationMs: Long = 0L,

    // ----- Resume position -----
    val lastFileIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val progressMs: Long = 0L,

    /** Playback speed remembered for this book (1.0 = normal). */
    val playbackSpeed: Float = 1.0f,

    val finished: Boolean = false,
    val finishedAt: Long? = null,

    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
