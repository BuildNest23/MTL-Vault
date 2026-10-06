package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.preferences.SettingsDataStore
import com.example.replacement.ReplacementEngine
import com.example.ui.screens.detail.DetailEvent
import com.example.ui.screens.detail.NovelDetailViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteChaptersTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: NovelRepository
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settingsDataStore = SettingsDataStore(context)
        repository = NovelRepository(
            novelDao = db.novelDao(),
            chapterDao = db.chapterDao(),
            settingsDataStore = settingsDataStore,
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testDeleteChaptersChunksAndUpdatesLastChapterId() = runBlocking {
        val novel = NovelEntity(
            id = "novel_del_1",
            title = "Test Novel",
            lastChapterId = "chap_505"
        )
        db.novelDao().insertNovel(novel)

        // Insert 520 chapters to thoroughly verify chunking of 500
        val chapters = (1..520).map { i ->
            ChapterEntity(
                id = "chap_$i",
                novelId = "novel_del_1",
                orderIndex = i,
                title = "Chapter $i",
                content = "Content for chapter $i"
            )
        }
        db.chapterDao().insertChapters(chapters)

        assertEquals(520, db.chapterDao().getChaptersForNovelSync("novel_del_1").size)

        // Cache rendered content in ReplacementEngine to verify invalidation
        ReplacementEngine.cacheRenderedContent("chap_505", 1, 1, "Cached text")
        assertEquals("Cached text", ReplacementEngine.getCachedRenderedContent("chap_505", 1, 1))

        // Delete all 520 chapters
        val allIds = chapters.map { it.id }
        repository.deleteChapters("novel_del_1", allIds)

        // Verify all chapters deleted in database
        val remaining = db.chapterDao().getChaptersForNovelSync("novel_del_1")
        assertTrue("All chapters should be deleted", remaining.isEmpty())

        // Verify novel.lastChapterId is set to null
        val updatedNovel = db.novelDao().getNovelByIdSync("novel_del_1")
        assertNull("novel.lastChapterId should be null", updatedNovel?.lastChapterId)

        // Verify ReplacementEngine cache was invalidated
        assertNull(ReplacementEngine.getCachedRenderedContent("chap_505", 1, 1))
    }

    @Test
    fun testDeleteChaptersDoesNotResetLastChapterIdIfNotDeleted() = runBlocking {
        val novel = NovelEntity(
            id = "novel_del_2",
            title = "Test Novel 2",
            lastChapterId = "chap_preserved"
        )
        db.novelDao().insertNovel(novel)

        val ch1 = ChapterEntity(id = "chap_to_del", novelId = "novel_del_2", orderIndex = 1, title = "Ch 1", content = "C1")
        val ch2 = ChapterEntity(id = "chap_preserved", novelId = "novel_del_2", orderIndex = 2, title = "Ch 2", content = "C2")
        db.chapterDao().insertChapters(listOf(ch1, ch2))

        repository.deleteChapters("novel_del_2", listOf("chap_to_del"))

        val updatedNovel = db.novelDao().getNovelByIdSync("novel_del_2")
        assertEquals("chap_preserved", updatedNovel?.lastChapterId)
        val remaining = db.chapterDao().getChaptersForNovelSync("novel_del_2")
        assertEquals(1, remaining.size)
        assertEquals("chap_preserved", remaining[0].id)
    }

    @Test
    fun testViewModelDeleteSelectedChapters() = runBlocking {
        val novel = NovelEntity(id = "novel_vm", title = "VM Novel")
        db.novelDao().insertNovel(novel)

        val ch1 = ChapterEntity(id = "c1", novelId = "novel_vm", orderIndex = 1, title = "Ch 1", content = "C1")
        val ch2 = ChapterEntity(id = "c2", novelId = "novel_vm", orderIndex = 2, title = "Ch 2", content = "C2")
        db.chapterDao().insertChapters(listOf(ch1, ch2))

        val viewModel = NovelDetailViewModel("novel_vm", repository, settingsDataStore)

        // Enter selection mode and select chapters
        viewModel.enterSelectionMode("c1")
        viewModel.toggleChapterSelection("c2")
        assertTrue(viewModel.isSelectionMode.value)
        assertEquals(setOf("c1", "c2"), viewModel.selectedChapterIds.value)

        var emittedToast: String? = null
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            viewModel.events.collect { event ->
                if (event is DetailEvent.ShowToast) {
                    emittedToast = event.message
                }
            }
        }

        viewModel.deleteSelectedChapters()
        var attempts = 0
        while (viewModel.isSelectionMode.value && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        // Verify selection mode exited and selection cleared
        assertFalse(viewModel.isSelectionMode.value)
        assertTrue(viewModel.selectedChapterIds.value.isEmpty())
        assertEquals("Deleted 2 chapters", emittedToast)

        val remaining = db.chapterDao().getChaptersForNovelSync("novel_vm")
        assertTrue(remaining.isEmpty())

        job.cancel()
    }

    @Test
    fun testDeleteSingleChapterInvalidatesCache() = runBlocking {
        val novel = NovelEntity(id = "n_single", title = "Single Test")
        db.novelDao().insertNovel(novel)

        val ch = ChapterEntity(id = "ch_single_del", novelId = "n_single", orderIndex = 1, title = "Ch 1", content = "Content")
        db.chapterDao().insertChapter(ch)

        ReplacementEngine.cacheRenderedContent("ch_single_del", 1, 1, "Cached Content")
        assertEquals("Cached Content", ReplacementEngine.getCachedRenderedContent("ch_single_del", 1, 1))

        repository.deleteChapter("n_single", "ch_single_del")

        assertNull("Cache should be invalidated after deleteChapter", ReplacementEngine.getCachedRenderedContent("ch_single_del", 1, 1))
        assertNull(db.chapterDao().getChapterByIdSync("ch_single_del"))
    }

    @Test
    fun testGetRenderedChaptersLargeChunking() = runBlocking {
        val novel = NovelEntity(id = "n_large", title = "Large Novel")
        db.novelDao().insertNovel(novel)

        // Create 1200 chapters (exceeding SQLite 999 parameter limit)
        val chapters = (1..1200).map { i ->
            ChapterEntity(
                id = "c_$i",
                novelId = "n_large",
                orderIndex = i,
                title = "Chapter $i",
                content = "Body $i"
            )
        }
        db.chapterDao().insertChapters(chapters)

        val requestedIds = (1..1200).shuffled().map { "c_$it" }
        val rendered = repository.getRenderedChapters("n_large", requestedIds)

        assertEquals(1200, rendered.size)
        // Verify results remain ordered by orderIndex
        for (i in 0 until 1200) {
            assertEquals(i + 1, rendered[i].orderIndex)
        }
    }

    @Test
    fun testGetNovelSummariesGroupedQuery() = runBlocking {
        val novel = NovelEntity(id = "n_grouped", title = "Grouped Novel", lastChapterId = "c_grp_2")
        db.novelDao().insertNovel(novel)

        val ch1 = ChapterEntity(id = "c_grp_1", novelId = "n_grouped", orderIndex = 1, title = "Chapter 1", content = "Body 1", wordCount = 100, read = true)
        val ch2 = ChapterEntity(id = "c_grp_2", novelId = "n_grouped", orderIndex = 2, title = "Chapter 2", content = "Body 2", wordCount = 200, read = false)
        db.chapterDao().insertChapters(listOf(ch1, ch2))

        val summaries = db.novelDao().getNovelSummaries()
        val firstEmission = summaries.first()
        val summary = firstEmission.find { it.id == "n_grouped" }

        assertNotNull(summary)
        assertEquals(2, summary?.totalChapters)
        assertEquals(1, summary?.readChapters)
        assertEquals(300L, summary?.totalWords)
        assertEquals("Chapter 2", summary?.lastChapterTitle)
    }
}
