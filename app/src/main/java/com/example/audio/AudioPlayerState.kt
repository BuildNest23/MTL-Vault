package com.example.audio

/** What the player is doing right now. */
enum class PlaybackStatus {
    /** No book is loaded. */
    IDLE,

    /** A book is being opened. */
    LOADING,

    PLAYING,

    /** A book is loaded and stopped, for example after pressing pause. */
    PAUSED,

    /** The book reached its end. */
    ENDED,

    /** The book can't be played, for example because a file is missing. See [AudioPlayerUiState.error]. */
    ERROR
}

/**
 * Everything the audiobook screens need to draw the player: the mini-player, the full player
 * and the sheets. The player (AudioPlayerController) publishes a new copy whenever something
 * changes, and the screens only read it.
 *
 * Positions come in two kinds. [positionMs] is inside the current file, which is what the
 * player engine uses. [bookPositionMs] is measured from the start of the whole book, which is
 * what a progress bar for the book shows. The chapter fields describe the chapter the
 * position is in, and the seek bar on the player screen follows the chapter.
 */
data class AudioPlayerUiState(
    val bookId: String? = null,
    val title: String = "",
    val author: String = "",
    val coverUri: String? = null,

    val status: PlaybackStatus = PlaybackStatus.IDLE,

    val fileIndex: Int = 0,
    val positionMs: Long = 0L,
    val bookPositionMs: Long = 0L,
    val bookDurationMs: Long = 0L,

    val chapterIndex: Int = -1,
    val chapterCount: Int = 0,
    val chapterTitle: String = "",
    val chapterElapsedMs: Long = 0L,
    val chapterDurationMs: Long = 0L,

    val speed: Float = 1.0f,
    val sleep: SleepState = SleepState(),

    /** Name of a file that could not be opened, so the screen can offer Re-link. */
    val missingFileName: String? = null,
    val error: String? = null
) {
    val hasBook: Boolean get() = bookId != null
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING

    val hasPreviousChapter: Boolean get() = chapterIndex > 0
    val hasNextChapter: Boolean get() = chapterIndex >= 0 && chapterIndex < chapterCount - 1

    /** Progress through the whole book, from 0.0 to 1.0. */
    val bookProgress: Float
        get() = if (bookDurationMs > 0L) (bookPositionMs.toFloat() / bookDurationMs).coerceIn(0f, 1f) else 0f

    /** Progress through the current chapter, from 0.0 to 1.0. */
    val chapterProgress: Float
        get() = if (chapterDurationMs > 0L) (chapterElapsedMs.toFloat() / chapterDurationMs).coerceIn(0f, 1f) else 0f

    val chapterRemainingMs: Long get() = (chapterDurationMs - chapterElapsedMs).coerceAtLeast(0L)
    val bookRemainingMs: Long get() = (bookDurationMs - bookPositionMs).coerceAtLeast(0L)

    /** How long the rest of the book takes at the current speed. */
    val bookRemainingAtSpeedMs: Long
        get() = if (speed > 0f) (bookRemainingMs / speed).toLong() else bookRemainingMs
}

/**
 * The player options the user can change in Settings.
 *
 * @param skipBackSeconds length of the "back" button
 * @param skipForwardSeconds length of the "forward" button
 * @param rewindOnResume step back a little when playing again after a pause (see SmartRewind)
 * @param skipSilence cut out silent gaps while playing
 * @param volumeBoost make quiet recordings louder
 * @param shakeToExtendSleep shake the phone to add time while the sleep timer is running
 * @param sleepExtendMinutes minutes added by a shake
 */
data class AudioPlayerSettings(
    val skipBackSeconds: Int = 15,
    val skipForwardSeconds: Int = 30,
    val rewindOnResume: Boolean = true,
    val skipSilence: Boolean = false,
    val volumeBoost: Boolean = false,
    val shakeToExtendSleep: Boolean = true,
    val sleepExtendMinutes: Int = 10
) {
    companion object {
        /** Choices offered for the skip buttons. */
        val SKIP_OPTIONS = listOf(5, 10, 15, 30, 45, 60, 90)

        /** Choices offered for the sleep timer, in minutes. */
        val SLEEP_OPTIONS = listOf(15, 30, 45, 60)

        /** Speeds from 0.5x to 3.0x in steps of 0.05x. */
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
        const val SPEED_STEP = 0.05f
    }
}

/**
 * Turns times and speeds into the short text the player screens show. Written by hand
 * instead of with String.format so the result never depends on the phone's language
 * (no decimal commas, no non-Latin digits).
 */
object AudioFormat {

    private fun two(n: Long): String = if (n < 10) "0$n" else "$n"

    /** "4:52" or, from one hour up, "1:02:03". */
    fun clock(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
    }

    /** "-12:40": time left, with a minus sign. */
    fun clockRemaining(ms: Long): String = "-" + clock(ms)

    /** "8h 12m", "12m" or "45s". Rounds down to the minute once past an hour. */
    fun duration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }

    /** "1x", "1.25x", "1.5x", "0.75x". */
    fun speed(speed: Float): String {
        val hundredths = Math.round(speed * 100f)
        val whole = hundredths / 100
        val fraction = hundredths % 100
        return when {
            fraction == 0 -> "${whole}x"
            fraction % 10 == 0 -> "$whole.${fraction / 10}x"
            else -> "$whole.${if (fraction < 10) "0$fraction" else "$fraction"}x"
        }
    }

    /** "Off", "22 min", "45 s" or "End of chapter". */
    fun sleep(state: SleepState): String = when (state.kind) {
        SleepKind.OFF -> "Off"
        SleepKind.END_OF_CHAPTER -> "End of chapter"
        SleepKind.TIMED -> {
            val seconds = (state.remainingMs.coerceAtLeast(0L) + 999L) / 1000L
            if (seconds >= 60L) "${(seconds + 59L) / 60L} min" else "$seconds s"
        }
    }

    /** Whole percent from 0 to 100. */
    fun percent(fraction: Float): Int = Math.round(fraction.coerceIn(0f, 1f) * 100f)

    /** Rounds a requested speed to the nearest step and keeps it in range. */
    fun clampSpeed(speed: Float): Float {
        val stepped = Math.round(speed / AudioPlayerSettings.SPEED_STEP) * AudioPlayerSettings.SPEED_STEP
        return stepped.coerceIn(AudioPlayerSettings.MIN_SPEED, AudioPlayerSettings.MAX_SPEED)
    }
}
