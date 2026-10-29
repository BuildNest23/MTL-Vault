package com.example.audio

import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioListeningEntity

/** Time listened to one book, over all days. */
data class BookListening(
    val bookId: String,
    val title: String,
    val listenedMs: Long
)

/** Time listened on one day. [dayEpoch] counts days since 1970-01-01 in the phone's local time. */
data class DayListening(
    val dayEpoch: Long,
    val listenedMs: Long
) {
    /** Day of the week, 0 = Monday ... 6 = Sunday. 1 January 1970 was a Thursday. */
    val dayOfWeek: Int get() = Math.floorMod(dayEpoch + 3L, 7L).toInt()
}

/**
 * The numbers on the audiobook part of the Stats tab.
 *
 * @param last7Days exactly seven entries, oldest first, ending today. Days without listening are zero.
 * @param averageSpeed how fast the user listens on average. 1.0 when nothing was listened to yet.
 * @param streakDays days in a row with listening. Today may still be empty, and the streak
 *        counts from yesterday then, so it does not drop to zero before the day is over.
 */
data class AudioStatsSummary(
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val totalMs: Long = 0L,
    val booksTotal: Int = 0,
    val booksFinished: Int = 0,
    val booksInProgress: Int = 0,
    val streakDays: Int = 0,
    val averageSpeed: Float = 1.0f,
    val perBook: List<BookListening> = emptyList(),
    val last7Days: List<DayListening> = emptyList()
) {
    val hasListening: Boolean get() = totalMs > 0L
    val hasBooks: Boolean get() = booksTotal > 0
}

/** Works out [AudioStatsSummary] from the saved listening rows and the books. No Android code. */
object AudioStatsCalculator {

    /** A day only counts for the streak with at least this much listening, so a stray tap does not. */
    const val MIN_STREAK_DAY_MS = 60_000L

    fun compute(
        rows: List<AudioListeningEntity>,
        books: List<AudioBookEntity>,
        todayEpoch: Long
    ): AudioStatsSummary {
        val perDay = HashMap<Long, Long>()
        for (row in rows) {
            perDay[row.dayEpoch] = (perDay[row.dayEpoch] ?: 0L) + row.listenedMs.coerceAtLeast(0L)
        }

        val last7 = (6 downTo 0).map { back ->
            val day = todayEpoch - back
            DayListening(day, perDay[day] ?: 0L)
        }

        val totalListened = rows.sumOf { it.listenedMs.coerceAtLeast(0L) }
        val totalBookMs = rows.sumOf { it.bookMs.coerceAtLeast(0L) }
        val averageSpeed = if (totalListened > 0L && totalBookMs > 0L) {
            (totalBookMs.toDouble() / totalListened.toDouble()).toFloat().coerceIn(0.5f, 3.0f)
        } else {
            1.0f
        }

        // One entry per book. The title of the newest row wins, so a renamed book shows its new name.
        val perBook = rows
            .groupBy { it.bookId }
            .map { (bookId, bookRows) ->
                BookListening(
                    bookId = bookId,
                    title = bookRows.maxByOrNull { it.dayEpoch }?.bookTitle.orEmpty(),
                    listenedMs = bookRows.sumOf { it.listenedMs.coerceAtLeast(0L) }
                )
            }
            .filter { it.listenedMs > 0L }
            .sortedByDescending { it.listenedMs }

        return AudioStatsSummary(
            todayMs = perDay[todayEpoch] ?: 0L,
            weekMs = last7.sumOf { it.listenedMs },
            totalMs = totalListened,
            booksTotal = books.size,
            booksFinished = books.count { it.finished },
            booksInProgress = books.count { !it.finished && it.progressMs > 0L },
            streakDays = streak(perDay, todayEpoch),
            averageSpeed = averageSpeed,
            perBook = perBook,
            last7Days = last7
        )
    }

    private fun streak(perDay: Map<Long, Long>, todayEpoch: Long): Int {
        fun counts(day: Long) = (perDay[day] ?: 0L) >= MIN_STREAK_DAY_MS
        var day = if (counts(todayEpoch)) todayEpoch else todayEpoch - 1
        var days = 0
        while (counts(day)) {
            days++
            day--
        }
        return days
    }
}
