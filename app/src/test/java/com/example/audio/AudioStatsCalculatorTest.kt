package com.example.audio

import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioListeningEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioStatsCalculatorTest {

    @Test
    fun compute_excludesListeningRowsForRemovedAudiobooks() {
        val today = 20_000L
        val keptBook = AudioBookEntity(
            id = "book_kept",
            title = "Renamed Live Audiobook",
            totalDurationMs = 3_600_000L,
            progressMs = 900_000L,
            finished = false
        )
        val rows = listOf(
            AudioListeningEntity(
                id = AudioListeningEntity.makeId("book_kept", today),
                bookId = "book_kept",
                bookTitle = "Old Title",
                dayEpoch = today,
                listenedMs = 600_000L,
                bookMs = 900_000L // 1.5x
            ),
            AudioListeningEntity(
                id = AudioListeningEntity.makeId("book_removed", today),
                bookId = "book_removed",
                bookTitle = "Deleted Audiobook",
                dayEpoch = today,
                listenedMs = 1_800_000L,
                bookMs = 3_600_000L
            ),
            AudioListeningEntity(
                id = AudioListeningEntity.makeId("book_removed", today - 1),
                bookId = "book_removed",
                bookTitle = "Deleted Audiobook",
                dayEpoch = today - 1,
                listenedMs = 1_200_000L,
                bookMs = 2_400_000L
            )
        )

        val summary = AudioStatsCalculator.compute(rows = rows, books = listOf(keptBook), todayEpoch = today)

        assertTrue(summary.hasBooks)
        assertTrue(summary.hasListening)
        assertEquals(600_000L, summary.todayMs)
        assertEquals(600_000L, summary.weekMs)
        assertEquals(600_000L, summary.totalMs)
        assertEquals(1, summary.streakDays)
        assertEquals(1.5f, summary.averageSpeed, 0.01f)
        assertEquals(1, summary.perBook.size)
        assertEquals("book_kept", summary.perBook[0].bookId)
        assertEquals("Renamed Live Audiobook", summary.perBook[0].title)
    }

    @Test
    fun compute_returnsEmptyAudioStatsWhenNoBooksExistEvenWithStaleRows() {
        val today = 20_000L
        val staleRows = listOf(
            AudioListeningEntity(
                id = AudioListeningEntity.makeId("book_removed", today),
                bookId = "book_removed",
                bookTitle = "Old Removed Book",
                dayEpoch = today,
                listenedMs = 1_200_000L,
                bookMs = 1_800_000L
            )
        )

        val summary = AudioStatsCalculator.compute(rows = staleRows, books = emptyList(), todayEpoch = today)

        assertFalse(summary.hasBooks)
        assertFalse(summary.hasListening)
        assertEquals(0L, summary.todayMs)
        assertEquals(0L, summary.weekMs)
        assertEquals(0L, summary.totalMs)
        assertEquals(0, summary.streakDays)
        assertEquals(0, summary.booksTotal)
        assertTrue(summary.perBook.isEmpty())
        assertTrue(summary.last7Days.all { it.listenedMs == 0L })
    }
}
