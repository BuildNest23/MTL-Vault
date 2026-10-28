package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One chapter of an [AudioBookEntity].
 *
 * Every book is stored as an ordered list of chapters, whatever the audio files look like:
 *  - Many files, one per chapter: one chapter per file, startMs = 0, endMs = file length.
 *  - One file with chapter marks inside: one chapter per mark, all pointing at the same file.
 *  - One file, kept whole (the default): a single chapter that covers the entire file.
 *  - One file split by time: "Part 1", "Part 2", ... each a slice of the same file.
 *
 * Because the player, chapter list, skip buttons and "end of chapter" sleep timer all work
 * from this list, they behave the same in every case.
 *
 * Times are in milliseconds from the start of the chapter's own file ([fileId]), not from
 * the start of the book.
 */
@Entity(
    tableName = "audio_chapters",
    foreignKeys = [
        ForeignKey(
            entity = AudioBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AudioFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["fileId"]),
        Index(value = ["bookId", "orderIndex"])
    ]
)
data class AudioChapterEntity(
    @PrimaryKey
    val id: String,
    val bookId: String,

    /** The file this chapter lives in. */
    val fileId: String,

    /** Position of this chapter in the whole book, starting at 0. */
    val orderIndex: Int,

    val title: String,

    /** Where the chapter starts inside its file, in milliseconds. */
    val startMs: Long = 0L,

    /** Where the chapter ends inside its file, in milliseconds. */
    val endMs: Long = 0L,

    /** True if the user added or renamed this chapter by hand, so re-scanning must keep it. */
    val userEdited: Boolean = false
) {
    /** Length of the chapter in milliseconds. Never negative. */
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}
