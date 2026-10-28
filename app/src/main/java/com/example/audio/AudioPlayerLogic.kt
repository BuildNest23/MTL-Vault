package com.example.audio

import com.example.data.database.entities.AudioChapterEntity
import com.example.data.database.entities.AudioFileEntity

/*
 * The rules behind the audiobook player, kept apart from Android so each one can be tested
 * by itself: smart rewind, the sleep timer, listening-time tracking and chapter navigation.
 * The player (AudioPlayerController) only feeds these classes and acts on their answers.
 */

// =================================================================== Smart rewind

/**
 * When you come back to a book after a break, it helps to hear a little of what you already
 * heard. The longer the break, the bigger the step back, up to 30 seconds.
 */
object SmartRewind {
    const val MAX_REWIND_MS = 30_000L

    fun rewindMs(pausedForMs: Long): Long = when {
        pausedForMs < 5_000L -> 0L
        pausedForMs < 60_000L -> 3_000L
        pausedForMs < 10 * 60_000L -> 8_000L
        pausedForMs < 60 * 60_000L -> 15_000L
        pausedForMs < 24 * 60 * 60_000L -> 25_000L
        else -> MAX_REWIND_MS
    }
}

// ================================================================== Sleep timer

enum class SleepKind { OFF, TIMED, END_OF_CHAPTER }

/**
 * @param remainingMs time left for a timed sleep timer. Not used for the other kinds.
 */
data class SleepState(
    val kind: SleepKind = SleepKind.OFF,
    val remainingMs: Long = 0L
)

/**
 * The sleep timer. It counts down only while the book is playing, and the volume fades out
 * over the last 30 seconds before it stops the book.
 *
 *  - Timed: stops after a set number of minutes of listening.
 *  - End of chapter: stops when the current chapter ends.
 */
class SleepTimerLogic {

    var state: SleepState = SleepState()
        private set

    val isActive: Boolean get() = state.kind != SleepKind.OFF

    fun startTimed(minutes: Int) {
        val clamped = minutes.coerceIn(1, 600)
        state = SleepState(SleepKind.TIMED, clamped * 60_000L)
    }

    fun startEndOfChapter() {
        state = SleepState(SleepKind.END_OF_CHAPTER)
    }

    fun cancel() {
        state = SleepState()
    }

    /**
     * Adds time. A timed timer gets longer; an end-of-chapter timer becomes a timed one.
     * Does nothing, and returns false, when no timer is running.
     */
    fun extend(minutes: Int): Boolean {
        val addMs = minutes.coerceIn(1, 600) * 60_000L
        return when (state.kind) {
            SleepKind.TIMED -> {
                state = state.copy(remainingMs = state.remainingMs + addMs)
                true
            }
            SleepKind.END_OF_CHAPTER -> {
                state = SleepState(SleepKind.TIMED, addMs)
                true
            }
            SleepKind.OFF -> false
        }
    }

    /**
     * Call regularly while playing with the real time that passed. Returns true once, when a
     * timed timer runs out. The timer then switches itself off.
     */
    fun tick(deltaMs: Long): Boolean {
        if (state.kind != SleepKind.TIMED || deltaMs <= 0L) return false
        val left = state.remainingMs - deltaMs
        return if (left <= 0L) {
            state = SleepState()
            true
        } else {
            state = state.copy(remainingMs = left)
            false
        }
    }

    /** Call when a chapter finishes playing. Returns true if the timer was set to end of chapter. */
    fun chapterEnded(): Boolean {
        if (state.kind != SleepKind.END_OF_CHAPTER) return false
        state = SleepState()
        return true
    }

    /**
     * Volume from 0.0 to 1.0. It is 1.0 until the last 30 seconds, then falls to 0.0.
     * @param chapterRemainingMs time left in the current chapter, used for end of chapter
     */
    fun volumeFactor(chapterRemainingMs: Long): Float {
        val remaining = when (state.kind) {
            SleepKind.TIMED -> state.remainingMs
            SleepKind.END_OF_CHAPTER -> chapterRemainingMs
            SleepKind.OFF -> return 1f
        }
        if (remaining >= FADE_MS) return 1f
        return (remaining.coerceAtLeast(0L).toFloat() / FADE_MS.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        const val FADE_MS = 30_000L
    }
}

// ============================================================ Listening-time tracking

/**
 * Adds up how long the user really listened, for the Stats tab. Pauses never count, and
 * neither does time while the phone was asleep: a gap between two ticks longer than
 * [MAX_GAP_MS] is ignored.
 *
 * Two totals are kept: real time spent, and how much of the book that covered (real time
 * multiplied by playback speed).
 */
class ListeningTracker {

    private var lastTickMs = -1L
    private var listenedMs = 0L
    private var bookMs = 0L

    /** Call about once a second while playing. [nowMs] must come from a steady clock. */
    fun tick(nowMs: Long, speed: Float) {
        if (lastTickMs >= 0L) {
            val delta = (nowMs - lastTickMs).coerceIn(0L, MAX_GAP_MS)
            listenedMs += delta
            bookMs += (delta * speed).toLong()
        }
        lastTickMs = nowMs
    }

    /** Call when playback pauses or stops, so the pause is not counted. */
    fun pause() {
        lastTickMs = -1L
    }

    fun hasPending(minimumMs: Long = FLUSH_EVERY_MS): Boolean = listenedMs >= minimumMs

    /** Returns the time collected since the last call as (listened, book) and starts again from zero. */
    fun take(): Pair<Long, Long> {
        val result = listenedMs to bookMs
        listenedMs = 0L
        bookMs = 0L
        return result
    }

    companion object {
        const val MAX_GAP_MS = 5_000L

        /** How much time to collect before it is worth writing to the database. */
        const val FLUSH_EVERY_MS = 15_000L
    }
}

// =============================================================== Chapter navigation

/** A spot in the book: which file, and how far into it. */
data class PlayTarget(val fileIndex: Int, val positionMs: Long)

/**
 * A chapter reduced to what the player needs: where it sits. [index] is its position in the
 * book's chapter list.
 */
data class ChapterSpan(
    val index: Int,
    val chapterId: String,
    val title: String,
    val fileIndex: Int,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

object ChapterNavigator {

    /** If you press "previous" this far into a chapter or less, it goes to the chapter before. */
    const val RESTART_THRESHOLD_MS = 3_000L

    /** Builds the chapter spans, in book order, from the database rows. */
    fun spansOf(chapters: List<AudioChapterEntity>, files: List<AudioFileEntity>): List<ChapterSpan> {
        val fileIndexById = files.associate { it.id to it.orderIndex }
        return chapters
            .sortedBy { it.orderIndex }
            .mapIndexedNotNull { index, chapter ->
                val fileIndex = fileIndexById[chapter.fileId] ?: return@mapIndexedNotNull null
                ChapterSpan(index, chapter.id, chapter.title, fileIndex, chapter.startMs, chapter.endMs)
            }
    }

    /** Which chapter a spot belongs to, or -1 if there are no chapters. */
    fun indexAt(spans: List<ChapterSpan>, fileIndex: Int, positionMs: Long): Int {
        val exact = spans.indexOfFirst {
            it.fileIndex == fileIndex && positionMs >= it.startMs && positionMs < it.endMs
        }
        if (exact >= 0) return exact
        // Past the end of the last chapter in the file, or a chapter whose length is not known yet.
        val inFile = spans.indexOfLast { it.fileIndex == fileIndex && it.startMs <= positionMs }
        if (inFile >= 0) return inFile
        return if (spans.isEmpty()) -1 else 0
    }

    /** The start of the chapter after [currentIndex], or null if it is the last. */
    fun next(spans: List<ChapterSpan>, currentIndex: Int): PlayTarget? =
        spans.getOrNull(currentIndex + 1)?.let { PlayTarget(it.fileIndex, it.startMs) }

    /**
     * What "previous" does. Late in a chapter it goes back to the start of that chapter, and
     * early in a chapter (within 3 seconds) it goes to the chapter before. On the first chapter
     * it always goes back to the start.
     */
    fun previous(spans: List<ChapterSpan>, currentIndex: Int, fileIndex: Int, positionMs: Long): PlayTarget? {
        val current = spans.getOrNull(currentIndex) ?: return null
        val intoChapter = if (fileIndex == current.fileIndex) positionMs - current.startMs else Long.MAX_VALUE
        if (intoChapter > RESTART_THRESHOLD_MS) return PlayTarget(current.fileIndex, current.startMs)
        val before = spans.getOrNull(currentIndex - 1) ?: return PlayTarget(current.fileIndex, current.startMs)
        return PlayTarget(before.fileIndex, before.startMs)
    }

    /** How far into the chapter the position is. Never negative. */
    fun elapsedInChapter(span: ChapterSpan, fileIndex: Int, positionMs: Long): Long =
        if (fileIndex == span.fileIndex) (positionMs - span.startMs).coerceAtLeast(0L) else 0L

    /** Time left in the chapter. Never negative. */
    fun remainingInChapter(span: ChapterSpan, fileIndex: Int, positionMs: Long): Long =
        (span.durationMs - elapsedInChapter(span, fileIndex, positionMs)).coerceAtLeast(0L)

    /**
     * Moves by [deltaMs] (negative to go back), crossing from one file into the next when a
     * book is made of several files. Stops at the very start or end of the book. A file whose
     * length is not known yet is not crossed.
     *
     * @param durationsMs length of every file in book order
     */
    fun moveBy(durationsMs: List<Long>, fileIndex: Int, positionMs: Long, deltaMs: Long): PlayTarget {
        if (durationsMs.isEmpty()) return PlayTarget(0, (positionMs + deltaMs).coerceAtLeast(0L))
        var index = fileIndex.coerceIn(0, durationsMs.lastIndex)
        var position = positionMs + deltaMs

        while (position < 0L && index > 0) {
            index--
            position += durationsMs[index]
        }
        while (index < durationsMs.lastIndex && durationsMs[index] > 0L && position >= durationsMs[index]) {
            position -= durationsMs[index]
            index++
        }

        position = position.coerceAtLeast(0L)
        val length = durationsMs[index]
        if (length > 0L) position = position.coerceAtMost(length)
        return PlayTarget(index, position)
    }
}
