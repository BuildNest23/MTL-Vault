package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmark the user saved while listening to an [AudioBookEntity].
 *
 * Bookmarks are the main way to mark places in a book that was kept as one single chapter.
 * A bookmark can later be turned into a chapter if the user wants.
 *
 * The spot is stored as "which file, and how far into that file", the same way the resume
 * position is stored, so it still points to the right place if chapters are edited.
 * [bookPositionMs] is the same spot measured from the very start of the whole book. It is
 * saved so bookmarks can be sorted and shown as a time without extra work.
 */
@Entity(
    tableName = "audio_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = AudioBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["bookId", "bookPositionMs"])
    ]
)
data class AudioBookmarkEntity(
    @PrimaryKey
    val id: String,
    val bookId: String,

    /** Index of the file in the book (same as AudioFileEntity.orderIndex). */
    val fileIndex: Int,

    /** Milliseconds into that file. */
    val positionMs: Long,

    /** Milliseconds from the start of the whole book. */
    val bookPositionMs: Long,

    /** Optional note written by the user. Empty if none. */
    val note: String = "",

    val createdAt: Long = System.currentTimeMillis()
)
