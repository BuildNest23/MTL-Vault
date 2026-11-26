package com.example.ui.screens.reader

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelAnnotationEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ReaderSettings
import com.example.importexport.BackupRestoreManager
import com.example.importexport.NovelWithChaptersBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnotationHighlighterTest {

    @Test
    fun locateSelectionPrefersVisibleParagraphOverEarlierDuplicate() {
        val paragraphs = listOf(
            "The jade seal gleamed in the moonlight.",
            "He walked silently through the courtyard.",
            "Once more, the jade seal gleamed in the moonlight."
        )

        val located = locateSelectionInParagraphs(
            paragraphs = paragraphs,
            visibleParagraphIndices = listOf(1, 2),
            selectedText = "jade seal"
        )

        assertNotNull(located)
        assertEquals(2, located!!.paragraphIndex)
        assertEquals(15, located.startOffset)
        assertEquals(24, located.endOffset)
    }

    @Test
    fun resolveHighlightRangeSurvivesShiftedOffsetsAfterReplacementRule() {
        val originalText = "Master Lin stepped into the pavilion."
        val updatedText = "Grandmaster Lin Xian stepped into the pavilion."
        val ann = NovelAnnotationEntity(
            id = "hl_1",
            novelId = "n1",
            chapterId = "c1",
            paragraphIndex = 0,
            type = NovelAnnotationEntity.TYPE_HIGHLIGHT,
            quoteText = "stepped into the pavilion",
            startOffset = originalText.indexOf("stepped"),
            endOffset = originalText.indexOf("stepped") + "stepped into the pavilion".length,
            colorKey = "jade"
        )

        val range = resolveHighlightRange(updatedText, ann)
        assertNotNull(range)
        assertEquals("stepped into the pavilion", updatedText.substring(range!!))
    }

    @Test
    fun resolveHighlightRangeFallsBackToFullParagraphWhenQuoteRewritten() {
        val rewrittenText = "Completely rewritten line."
        val ann = NovelAnnotationEntity(
            id = "hl_2",
            novelId = "n1",
            chapterId = "c1",
            paragraphIndex = 0,
            type = NovelAnnotationEntity.TYPE_HIGHLIGHT,
            quoteText = "old vanished phrase",
            startOffset = 5,
            endOffset = 15,
            colorKey = "rose"
        )

        val range = resolveHighlightRange(rewrittenText, ann)
        assertEquals(0 until rewrittenText.length, range)
    }

    @Test
    fun backupJsonRoundTripsAnnotations() {
        val novel = NovelEntity(id = "n1", title = "Chronicle")
        val chapter = ChapterEntity(id = "c1", novelId = "n1", orderIndex = 0, title = "Ch 1", content = "Hello world.")
        val annotation = NovelAnnotationEntity(
            id = "hl_1",
            novelId = "n1",
            chapterId = "c1",
            paragraphIndex = 0,
            type = NovelAnnotationEntity.TYPE_HIGHLIGHT,
            quoteText = "Hello",
            startOffset = 0,
            endOffset = 5,
            colorKey = "slate",
            note = "Opening word"
        )

        val json = BackupRestoreManager.generateBackupJson(
            listOf(NovelWithChaptersBackup(novel, listOf(chapter), listOf(annotation))),
            ReaderSettings()
        )
        val parsed = BackupRestoreManager.parseBackupJson(json)
        assertEquals(1, parsed.novels.size)
        assertEquals(1, parsed.novels[0].annotations.size)
        val restored = parsed.novels[0].annotations[0]
        assertEquals("hl_1", restored.id)
        assertEquals("slate", restored.colorKey)
        assertEquals("Opening word", restored.note)
    }
}
