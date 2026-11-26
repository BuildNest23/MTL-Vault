package com.example.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerTagsTest {

    @Test
    fun plainParagraphIsNarration() {
        val p = SpeakerTags.parse("The wind was cold.")!!
        assertEquals("The wind was cold.", p.text)
        assertNull(p.speaker)
    }

    @Test
    fun tagIsRemovedAndSpeakerKept() {
        val p = SpeakerTags.parse("@Zeus: \"Hello there.\"")!!
        assertEquals("\"Hello there.\"", p.text)
        assertEquals("zeus", p.speaker)
        assertEquals("Zeus", p.speakerDisplay)
    }

    @Test
    fun multiWordNameAndThoughtAreHandled() {
        val p = SpeakerTags.parse("@Lin  Feng (thought): This is bad.")!!
        assertEquals("This is bad.", p.text)
        assertEquals("lin feng", p.speaker)
    }

    @Test
    fun narratorTagMeansNarration() {
        val p = SpeakerTags.parse("@Narrator: Night fell.")!!
        assertEquals("Night fell.", p.text)
        assertNull(p.speaker)
    }

    @Test
    fun tagWithNoTextIsDropped() {
        assertNull(SpeakerTags.parse("@Zeus:"))
    }

    @Test
    fun emailAddressIsNotATag() {
        val p = SpeakerTags.parse("@home is where the heart is")!!
        assertEquals("@home is where the heart is", p.text)
        assertNull(p.speaker)
    }

    @Test
    fun paragraphsKeepSpeakersAligned() {
        val text = "Night fell.\n\n@Zeus: \"Run.\"\n\n@Mia: \"No.\"\n\n@Zeus:\n\nHe left."
        val list = TtsManager.parseParagraphsWithSpeakers(text)
        assertEquals(listOf("Night fell.", "\"Run.\"", "\"No.\"", "He left."), list.map { it.text })
        assertEquals(listOf(null, "zeus", "mia", null), list.map { it.speaker })
        assertEquals(list.map { it.text }, TtsManager.parseParagraphs(text))
    }

    @Test
    fun taggedLinesWithoutBlankLinesAreSeparateParagraphs() {
        val text = "@Zeus: \"Run.\"\n@Mia: \"No.\"\nHe left."
        val list = TtsManager.parseParagraphsWithSpeakers(text)
        assertEquals(listOf("\"Run.\"", "\"No.\"", "He left."), list.map { it.text })
        assertEquals(listOf("zeus", "mia", null), list.map { it.speaker })
    }

    @Test
    fun stripRemovesTagsOnly() {
        assertEquals("\"Run.\"\nPlain line", SpeakerTags.strip("@Zeus: \"Run.\"\nPlain line"))
    }

    @Test
    fun findNamesCollectsOnce() {
        val names = LinkedHashMap<String, String>()
        SpeakerTags.findNames("@Zeus: a\n\n@zeus: b\n\n@Mia (thought): c\n\n@Narrator: d", names)
        assertEquals(listOf("zeus", "mia"), names.keys.toList())
        assertEquals("Zeus", names["zeus"])
    }
}
