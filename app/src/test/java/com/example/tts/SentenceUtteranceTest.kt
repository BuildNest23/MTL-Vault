package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceUtteranceTest {

    private fun build(text: String, start: Int = 0): List<TtsManager.SubUtterance> {
        val sentences = SentenceSplitter.splitIntoSentences(text)
        return TtsManager.buildSubUtterances(text, sentences, start)
    }

    @Test
    fun oneUtterancePerLongSentence() {
        val text = "This is the first sentence of the paragraph. This is the second sentence here. And this is the third one."
        val subs = build(text)
        assertEquals(3, subs.size)
        subs.forEach { sub ->
            assertEquals(sub.text, text.substring(sub.baseCharOffset, sub.endCharOffset))
        }
    }

    @Test
    fun shortSentencesAreMergedWithNext() {
        val text = "Yes. This is a much longer sentence that follows it."
        val subs = build(text)
        assertEquals(1, subs.size)
        assertEquals(0, subs[0].baseCharOffset)
        assertEquals(text.length, subs[0].endCharOffset)
    }

    @Test
    fun startOffsetSkipsEarlierSentences() {
        val text = "This is the first sentence of the paragraph. This is the second sentence here. And this is the third one."
        val secondStart = text.indexOf("This is the second")
        val subs = build(text, secondStart)
        assertEquals(2, subs.size)
        assertEquals(secondStart, subs[0].baseCharOffset)
    }

    @Test
    fun veryLongSentenceIsSplitWithOffsets() {
        val text = "word ".repeat(1500).trim() + "."
        val subs = build(text)
        assertTrue(subs.size >= 2)
        assertEquals(0, subs[0].baseCharOffset)
        assertEquals(subs[0].endCharOffset, subs[1].baseCharOffset)
    }
}
