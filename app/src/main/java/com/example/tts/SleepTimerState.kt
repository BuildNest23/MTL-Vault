package com.example.tts

/**
 * Sleep timer for TTS reading.
 *
 * @property endAtMs When a timed sleep ends (epoch ms); 0 when no timed sleep is set.
 * @property minutes The chosen length of a timed sleep, so the matching option can be shown selected.
 * @property atChapterEnd True when reading should stop once the current chapter is finished.
 */
data class SleepTimerState(
    val endAtMs: Long = 0L,
    val minutes: Int = 0,
    val atChapterEnd: Boolean = false
) {
    val active: Boolean get() = endAtMs > 0L || atChapterEnd
}
