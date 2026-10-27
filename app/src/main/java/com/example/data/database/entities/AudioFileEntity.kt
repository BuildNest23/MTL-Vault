package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One audio file that belongs to an [AudioBookEntity].
 *
 * A book made of many chapter files has many rows here (ordered by [orderIndex]).
 * A whole-book file (for example one big .m4b) has exactly one row.
 *
 * The file is never copied. [uri] is a content:// URI the user granted access to through
 * Android's file picker, and the app keeps a persistable read permission for it.
 * If the user later moves or deletes the file, the URI stops working and the shelf shows
 * "File missing" with a Re-link option.
 */
@Entity(
    tableName = "audio_files",
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
        Index(value = ["bookId", "orderIndex"])
    ]
)
data class AudioFileEntity(
    @PrimaryKey
    val id: String,
    val bookId: String,

    /** Position of this file in the book, starting at 0. */
    val orderIndex: Int,

    /** content:// URI string of the audio file. */
    val uri: String,

    /** File name shown to the user, for example "Chapter 01.mp3". */
    val displayName: String,

    /** Length of this file in milliseconds. */
    val durationMs: Long = 0L,

    /** File type such as "audio/mpeg" or "audio/mp4". May be empty if unknown. */
    val mimeType: String = "",

    /** Size in bytes, used only to help spot a replaced file when re-linking. 0 if unknown. */
    val sizeBytes: Long = 0L
)
