package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How long the user listened to one audiobook on one day. This feeds the audio summary in
 * the Stats tab (today / this week / total, streak, time per book, average speed, 7-day chart).
 *
 * There is one row per book per day. The player adds to the same row while you listen
 * instead of writing a new row every few seconds.
 *
 * Two lengths are kept because speed changes the answer:
 *  - [listenedMs] is real time spent listening. Pauses do not count.
 *  - [bookMs] is how much of the book that covered. At 2x speed, 30 minutes of listening
 *    covers 60 minutes of the book.
 * Average speed is simply bookMs / listenedMs.
 *
 * This table has no link to the audiobooks table on purpose. If the user removes a book from
 * the shelf, the time they already spent listening should still count in their stats, so
 * the book's title is saved here as [bookTitle].
 */
@Entity(
    tableName = "audio_listening",
    indices = [
        Index(value = ["dayEpoch"]),
        Index(value = ["bookId"])
    ]
)
data class AudioListeningEntity(
    /** "<bookId>|<dayEpoch>", so each book gets exactly one row per day. */
    @PrimaryKey
    val id: String,

    val bookId: String,

    /** Title of the book when the row was last updated. Kept for stats after the book is removed. */
    val bookTitle: String,

    /** The day as a count of days since 1970-01-01 in the phone's local time (LocalDate.toEpochDay). */
    val dayEpoch: Long,

    /** Real time listened that day, in milliseconds. */
    val listenedMs: Long = 0L,

    /** Amount of book covered that day, in milliseconds (listened time multiplied by speed). */
    val bookMs: Long = 0L,

    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun makeId(bookId: String, dayEpoch: Long): String = "$bookId|$dayEpoch"
    }
}
