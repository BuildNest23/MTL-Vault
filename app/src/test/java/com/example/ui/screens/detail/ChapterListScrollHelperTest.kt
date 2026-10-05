package com.example.ui.screens.detail

import com.example.data.model.ChapterSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterListScrollHelperTest {

    private fun createChapter(id: String, orderIndex: Int, read: Boolean): ChapterSummary {
        return ChapterSummary(
            id = id,
            novelId = "novel_1",
            orderIndex = orderIndex,
            title = "Chapter $orderIndex",
            wordCount = 1500,
            scrollPosition = 0,
            read = read,
            readAt = if (read) System.currentTimeMillis() else null,
            createdAt = System.currentTimeMillis(),
            readingParagraphIndex = 0
        )
    }

    @Test
    fun testNoChaptersReturnsNull() {
        val chapters = emptyList<ChapterSummary>()
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = 2, enabled = true)
        assertNull("Empty chapter list should return null (do nothing)", target)
    }

    @Test
    fun testAllUnreadReturnsZero() {
        // Chapters: [unread, unread, unread]
        val chapters = listOf(
            createChapter("c0", 0, read = false),
            createChapter("c1", 1, read = false),
            createChapter("c2", 2, read = false)
        )
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = 2, enabled = true)
        assertEquals("First unread is chapter 0, so list should stay at the top (index 0)", 0, target)
    }

    @Test
    fun testMiddleUnreadReturnsHeaderPlusFirstUnread() {
        // Chapters: [read, read, unread, unread]
        val chapters = listOf(
            createChapter("c0", 0, read = true),
            createChapter("c1", 1, read = true),
            createChapter("c2", 2, read = false),
            createChapter("c3", 3, read = false)
        )
        val headerCount = 2
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = headerCount, enabled = true)
        // First unread is index 2, target should be 2 + 2 = 4
        assertEquals(headerCount + 2, target)
    }

    @Test
    fun testAllReadReturnsHeaderPlusLastChapter() {
        // Chapters: [read, read, read, read, read] (size = 5)
        val chapters = listOf(
            createChapter("c0", 0, read = true),
            createChapter("c1", 1, read = true),
            createChapter("c2", 2, read = true),
            createChapter("c3", 3, read = true),
            createChapter("c4", 4, read = true)
        )
        val headerCount = 2
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = headerCount, enabled = true)
        // All read: target positions last chapter (index 4) at top -> 2 + 4 = 6
        assertEquals(headerCount + (chapters.size - 1), target)
    }

    @Test
    fun testSkippedUnreadChapterEarlierInList() {
        // Chapters: [read, unread, read, unread]
        // E.g. user jumped ahead and read chapter 2, but skipped chapter 1
        val chapters = listOf(
            createChapter("c0", 0, read = true),
            createChapter("c1", 1, read = false), // First unread in displayed order
            createChapter("c2", 2, read = true),
            createChapter("c3", 3, read = false)
        )
        val headerCount = 2
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = headerCount, enabled = true)
        // First unread is index 1 -> target is 2 + 1 = 3
        assertEquals(headerCount + 1, target)
    }

    @Test
    fun testDisabledSettingReturnsZero() {
        // Setting OFF: list starts at top
        val chapters = listOf(
            createChapter("c0", 0, read = true),
            createChapter("c1", 1, read = true),
            createChapter("c2", 2, read = false)
        )
        val target = ChapterListScrollHelper.calculateTargetScrollIndex(chapters, headerItemCount = 2, enabled = false)
        assertEquals("Disabled setting should start list at the top (index 0)", 0, target)
    }
}
