package com.example.data.model

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.database.entities.NovelShelfCrossRef
import com.example.data.database.entities.ShelfEntity
import com.example.importexport.BackupRestoreManager
import com.example.importexport.NovelWithChaptersBackup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovelSearchAndShelvesTest {

    @Test
    fun toLikePattern_escapesWildcardsAndBackslash() {
        assertEquals("%100\\%\\_safe\\\\path%", NovelSearchEngine.toLikePattern("100%_safe\\path"))
    }

    @Test
    fun searchChapterText_alignsParagraphIndicesAndMatchBounds() {
        val content = """
            The lantern flickered along the quiet stone alley.

            Lin Chen paused beside the Azure Pavilion gate, tightening his cloak against the autumn rain.

            Inside the Azure Pavilion, an elder awaited with two porcelain teacups.
        """.trimIndent()

        val hits = NovelSearchEngine.searchChapterText(
            novelId = "n1",
            novelTitle = "Chronicles of Mist",
            chapterId = "c1",
            chapterTitle = "Chapter 1: Rain at Dusk",
            chapterOrder = 0,
            renderedContent = content,
            query = "azure pavilion"
        )

        assertEquals(2, hits.size)
        assertEquals(1, hits[0].paragraphIndex)
        assertEquals(2, hits[1].paragraphIndex)

        for (hit in hits) {
            val matched = hit.snippet.substring(hit.matchStart, hit.matchEnd)
            assertEquals("Azure Pavilion", matched)
        }
    }

    @Test
    fun searchChapterText_fallsBackToChapterTitleWhenBodyHasNoMatch() {
        val hits = NovelSearchEngine.searchChapterText(
            novelId = "n1",
            novelTitle = "Chronicles of Mist",
            chapterId = "c4",
            chapterTitle = "Chapter 4: The Jade Sword",
            chapterOrder = 3,
            renderedContent = "Nothing stirred in the courtyard.",
            query = "jade sword"
        )

        assertEquals(1, hits.size)
        assertEquals(0, hits[0].paragraphIndex)
        assertEquals("Jade Sword", hits[0].snippet.substring(hits[0].matchStart, hits[0].matchEnd))
    }

    @Test
    fun backupJson_roundTripsShelvesAndFiltersOrphanMemberships() {
        val novel = NovelEntity(id = "n1", title = "Dao of Ink")
        val chapter = ChapterEntity(id = "c1", novelId = "n1", orderIndex = 0, title = "Ch 1", content = "Hello world")
        val shelves = listOf(
            ShelfEntity(id = "s1", name = "Xianxia", sortOrder = 0, createdAt = 100L),
            ShelfEntity(id = "s2", name = "Favorites", sortOrder = 1, createdAt = 200L)
        )
        val memberships = listOf(
            NovelShelfCrossRef(novelId = "n1", shelfId = "s1", addedAt = 300L),
            NovelShelfCrossRef(novelId = "missing_novel", shelfId = "s2", addedAt = 400L)
        )

        val json = BackupRestoreManager.generateBackupJson(
            novelsWithChapters = listOf(NovelWithChaptersBackup(novel, listOf(chapter))),
            settings = ReaderSettings(),
            shelves = shelves,
            shelfMemberships = memberships
        )

        val parsed = BackupRestoreManager.parseBackupJson(json)
        assertEquals(2, parsed.shelves.size)
        assertEquals("Xianxia", parsed.shelves[0].name)
        assertEquals("Favorites", parsed.shelves[1].name)
        assertEquals(1, parsed.shelfMemberships.size)
        assertEquals("n1", parsed.shelfMemberships[0].novelId)
        assertEquals("s1", parsed.shelfMemberships[0].shelfId)
    }

    @Test
    fun backupStream_roundTripsShelvesAndMemberships() = runBlocking {
        val novel = NovelEntity(id = "n1", title = "Dao of Ink")
        val chapter = ChapterEntity(id = "c1", novelId = "n1", orderIndex = 0, title = "Ch 1", content = "Hello world")
        val shelves = listOf(ShelfEntity(id = "s1", name = "Completed", sortOrder = 0, createdAt = 500L))
        val memberships = listOf(NovelShelfCrossRef(novelId = "n1", shelfId = "s1", addedAt = 600L))

        val out = ByteArrayOutputStream()
        BackupRestoreManager.writeBackupStream(
            out = out,
            novels = listOf(novel),
            settings = ReaderSettings(),
            loadChapters = { listOf(chapter) },
            readCover = { null },
            shelves = shelves,
            shelfMemberships = memberships
        )

        val restoredNovels = mutableListOf<NovelWithChaptersBackup>()
        var restoredShelves: List<ShelfEntity> = emptyList()
        var restoredMemberships: List<NovelShelfCrossRef> = emptyList()

        BackupRestoreManager.readBackupStream(
            input = ByteArrayInputStream(out.toByteArray()),
            saveCover = { _, _ -> null },
            onShelves = { s, m ->
                restoredShelves = s
                restoredMemberships = m
            }
        ) { item ->
            restoredNovels.add(item)
        }

        assertEquals(1, restoredNovels.size)
        assertEquals(1, restoredShelves.size)
        assertEquals("Completed", restoredShelves[0].name)
        assertEquals(1, restoredMemberships.size)
        assertTrue(restoredMemberships[0].novelId == "n1" && restoredMemberships[0].shelfId == "s1")
    }
}
