package com.example.tts

/**
 * State representing the active TTS inline text highlight.
 *
 * @property paragraphIndex The 0-based index of the currently speaking paragraph in the chapter.
 * @property sentenceStart The character start offset of the highlighted sentence within the paragraph.
 * @property sentenceEnd The character end offset of the highlighted sentence within the paragraph.
 * @property isSentenceLevel Always true now: the sentence being spoken is highlighted from each sentence utterance's onStart
 *   (works with engines that never send onRangeStart). Kept for compatibility.
 */
data class TtsHighlightState(
    val paragraphIndex: Int = -1,
    val sentenceStart: Int = 0,
    val sentenceEnd: Int = 0,
    val isSentenceLevel: Boolean = true
)
