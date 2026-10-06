package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun testEmptyText() {
        val sentences = SentenceSplitter.splitIntoSentences("")
        assertTrue("Empty text should return empty list", sentences.isEmpty())

        val whitespaceOnly = SentenceSplitter.splitIntoSentences("   \n\t  ")
        assertTrue("Whitespace-only text should return empty list", whitespaceOnly.isEmpty())
    }

    @Test
    fun testAbbreviations() {
        val text = "Dr. Watson met Mr. Holmes at 221B Baker St. They solved the case."
        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals("Should split into exactly 2 sentences", 2, sentences.size)
        assertEquals("Dr. Watson met Mr. Holmes at 221B Baker St.", sentences[0].text)
        assertEquals("They solved the case.", sentences[1].text)
        assertEquals(0, sentences[0].start)
        assertEquals(43, sentences[0].end)
        assertEquals(text.substring(sentences[0].start, sentences[0].end), sentences[0].text)
        assertEquals(text.substring(sentences[1].start, sentences[1].end), sentences[1].text)
    }

    @Test
    fun testEllipsis() {
        val text = "Wait for it... It worked! Now hold on… We are ready."
        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals(4, sentences.size)
        assertEquals("Wait for it...", sentences[0].text)
        assertEquals("It worked!", sentences[1].text)
        assertEquals("Now hold on…", sentences[2].text)
        assertEquals("We are ready.", sentences[3].text)
        // Verify offsets map exactly to original text
        for (s in sentences) {
            assertEquals(text.substring(s.start, s.end), s.text)
        }
    }

    @Test
    fun testQuotes() {
        val text = "\"Stop!\" he shouted. \"Never!\" She whispered, \"Good luck.\""
        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals(3, sentences.size)
        assertEquals("\"Stop!\" he shouted.", sentences[0].text)
        assertEquals("\"Never!\"", sentences[1].text)
        assertEquals("She whispered, \"Good luck.\"", sentences[2].text)
        for (s in sentences) {
            assertEquals(text.substring(s.start, s.end), s.text)
        }
    }

    @Test
    fun testPatternA14AndDecimals() {
        val text = "The unit A-14 costs $3.14 per batch. It is ready for deployment."
        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals(2, sentences.size)
        assertEquals("The unit A-14 costs $3.14 per batch.", sentences[0].text)
        assertEquals("It is ready for deployment.", sentences[1].text)
        for (s in sentences) {
            assertEquals(text.substring(s.start, s.end), s.text)
        }
    }

    @Test
    fun testVeryLongSentence() {
        val longContent = "A".repeat(4000)
        val sentences = SentenceSplitter.splitIntoSentences(longContent)
        assertEquals(1, sentences.size)
        assertEquals(0, sentences[0].start)
        assertEquals(4000, sentences[0].end)
        assertEquals(longContent, sentences[0].text)
    }

    @Test
    fun testStartFromMiddleOffsets() {
        val text = "First sentence. Second sentence. Third sentence."
        val sentences = SentenceSplitter.splitIntoSentences(text)
        assertEquals(3, sentences.size)

        // Offset inside sentence 1
        val s1 = SentenceSplitter.findSentence(sentences, 5)
        assertNotNull(s1)
        assertEquals("First sentence.", s1?.text)

        // Offset inside sentence 2
        val s2 = SentenceSplitter.findSentence(sentences, 20)
        assertNotNull(s2)
        assertEquals("Second sentence.", s2?.text)

        // Offset inside sentence 3
        val s3 = SentenceSplitter.findSentence(sentences, 40)
        assertNotNull(s3)
        assertEquals("Third sentence.", s3?.text)

        // Offset in whitespace between sentence 1 and 2 (e.g. index 15)
        val between = SentenceSplitter.findSentence(sentences, 15)
        assertNotNull(between)
        assertTrue(between == sentences[0] || between == sentences[1])

        // Negative offset -> returns first sentence
        val beforeStart = SentenceSplitter.findSentence(sentences, -5)
        assertEquals(sentences[0], beforeStart)

        // Offset past end -> returns last sentence
        val afterEnd = SentenceSplitter.findSentence(sentences, 500)
        assertEquals(sentences[2], afterEnd)
    }
}
