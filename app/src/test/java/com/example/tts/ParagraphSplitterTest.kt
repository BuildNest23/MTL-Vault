package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphSplitterTest {

    @Test
    fun testAbbreviationsDoNotSplit() {
        val text = "Dr. Watson and Mr. Holmes met at St. Jude. They discussed the case vs. Moriarty."
        val sentences = ParagraphSplitter.splitIntoSentences(text)
        assertEquals(2, sentences.size)
        assertEquals("Dr. Watson and Mr. Holmes met at St. Jude.", sentences[0])
        assertEquals("They discussed the case vs. Moriarty.", sentences[1])
    }

    @Test
    fun testEllipsisSplitting() {
        val text = "Wait... What is that? I cannot believe it… It is impossible."
        val sentences = ParagraphSplitter.splitIntoSentences(text)
        assertEquals(4, sentences.size)
        assertEquals("Wait...", sentences[0])
        assertEquals("What is that?", sentences[1])
        assertEquals("I cannot believe it…", sentences[2])
        assertEquals("It is impossible.", sentences[3])
    }

    @Test
    fun testClosingQuotesStayWithTerminator() {
        val text = "\"Is that true?\" asked Lin Ming. \"Yes!\" replied senior. \"I understand.\""
        val sentences = ParagraphSplitter.splitIntoSentences(text)
        assertEquals(3, sentences.size)
        assertEquals("\"Is that true?\" asked Lin Ming.", sentences[0])
        assertEquals("\"Yes!\" replied senior.", sentences[1])
        assertEquals("\"I understand.\"", sentences[2])
    }

    @Test
    fun testA14AndDecimalsDoNotSplit() {
        val text = "The model A-14 was deployed at coordinates 12.5 and 45.8. The test succeeded."
        val sentences = ParagraphSplitter.splitIntoSentences(text)
        assertEquals(2, sentences.size)
        assertEquals("The model A-14 was deployed at coordinates 12.5 and 45.8.", sentences[0])
        assertEquals("The test succeeded.", sentences[1])
    }

    @Test
    fun testVeryLongSingleSentenceRemainsWholeUnlessHardCap() {
        // Single sentence of 600 characters without period
        val longSentence = "The ancient divine dragon flew across the stormy violet clouds of the heavenly realm while roaring with supreme thunderous resonance and unleashing primordial lightning upon the demonic beasts gathered beneath the desolate sacred mountain valley ".repeat(3).trim() + "."
        assertTrue("Sentence should exceed 500 chars", longSentence.length > 500)
        assertTrue("Sentence should be within 3500 hard cap", longSentence.length < 3500)

        // With Short limit (250 chars), a single sentence cannot be divided at sentence boundary, so it stays whole
        val chunks = ParagraphSplitter.splitSingleParagraph(longSentence, maxChars = 250)
        assertEquals("Single sentence longer than limit should remain whole if under hard cap", 1, chunks.size)
        assertEquals(longSentence, chunks[0])
    }

    @Test
    fun testEmptyText() {
        assertTrue(ParagraphSplitter.parseParagraphChunks("").isEmpty())
        assertTrue(ParagraphSplitter.parseParagraphChunks("   \n\n   ").isEmpty())
        assertTrue(ParagraphSplitter.splitSingleParagraph("", 250).isEmpty())
        assertTrue(ParagraphSplitter.splitIntoSentences("").isEmpty())
    }

    @Test
    fun testOffModeDoesNotSplitNormalParagraphs() {
        val paragraph = "First sentence here. Second sentence follows. Third sentence closes the thought."
        val chunks = ParagraphSplitter.splitSingleParagraph(paragraph, maxChars = Int.MAX_VALUE)
        assertEquals("Off mode (maxChars = Int.MAX_VALUE) should not split normal paragraphs", 1, chunks.size)
        assertEquals(paragraph, chunks[0])
    }

    @Test
    fun testHardCapSplitsEvenWhenOff() {
        // Create an unbroken text of 5000 characters
        val sentence = "This is a very long sentence that keeps going across the boundary of normal speech. "
        val hugeText = sentence.repeat(60) // ~5000 chars
        assertTrue(hugeText.length > 4000)

        // Even with maxChars = Int.MAX_VALUE (Off mode), HARD_CAP (3500) must be enforced
        val chunks = ParagraphSplitter.splitSingleParagraph(hugeText, maxChars = Int.MAX_VALUE)
        assertTrue("Should be split into multiple chunks due to hard cap", chunks.size > 1)
        for (chunk in chunks) {
            assertTrue(
                "No chunk may exceed 3500 characters (was ${chunk.length})",
                chunk.length <= ParagraphSplitter.HARD_CAP
            )
        }
    }

    @Test
    fun testGreedySentenceGroupingAndChunkFlags() {
        val p1 = "Sentence one is brief. Sentence two is also relatively short. Sentence three provides additional context to the narrative. Sentence four concludes."
        val chunks = ParagraphSplitter.parseParagraphChunks(p1, maxChars = 80)
        assertTrue("Should split into multiple chunks", chunks.size >= 2)

        // First chunk must be marked as first chunk of paragraph
        assertTrue(chunks.first().isFirstChunkOfParagraph)
        assertFalse(chunks.first().isLastChunkOfParagraph)

        // Last chunk must be marked as last chunk of paragraph
        assertFalse(chunks.last().isFirstChunkOfParagraph)
        assertTrue(chunks.last().isLastChunkOfParagraph)

        // All chunks in p1 have originalParagraphIndex = 0
        for (chunk in chunks) {
            assertEquals(0, chunk.originalParagraphIndex)
        }
    }
}
