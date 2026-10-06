package com.example.ui.screens.reader

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: NovelRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var ttsManager: TtsManager

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
        ttsManager = TtsManager(context)
    }

    @After
    fun tearDown() {
        ttsManager.shutdown()
        db.close()
    }

    private fun waitUntil(condition: () -> Boolean) {
        var attempts = 0
        while (!condition() && attempts < 50) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun testScrollPercent85DoesNotMarkRead() = runBlocking {
        val novel = NovelEntity(id = "n_read_test", title = "Novel Read Test")
        db.novelDao().insertNovel(novel)
        val ch1 = ChapterEntity(id = "c1", novelId = "n_read_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = false)
        db.chapterDao().insertChapters(listOf(ch1))

        val viewModel = ReaderViewModel("n_read_test", "c1", repository, settingsDataStore, ttsManager)
        val job = launch(Dispatchers.Unconfined) { viewModel.currentChapter.collect {} }

        waitUntil { viewModel.currentChapter.value?.id == "c1" }
        assertEquals("c1", viewModel.currentChapter.value?.id)
        assertFalse(viewModel.currentChapter.value!!.read)

        // Update scroll position beyond 0.85f
        viewModel.updateReadingPosition(paragraphIndex = 5, scrollY = 5, scrollPercent = 0.95f)
        ShadowLooper.idleMainLooper()
        Thread.sleep(25)
        ShadowLooper.idleMainLooper()

        // Verify chapter remains unread in both ViewModel state and database
        assertFalse(viewModel.currentChapter.value!!.read)
        val dbChapter = db.chapterDao().getChapterByIdSync("c1")
        assertFalse(dbChapter!!.read)

        // Save position now at 1.0f progress
        viewModel.saveCurrentPositionNow()
        ShadowLooper.idleMainLooper()
        Thread.sleep(25)
        ShadowLooper.idleMainLooper()
        assertFalse(viewModel.currentChapter.value!!.read)
        assertFalse(db.chapterDao().getChapterByIdSync("c1")!!.read)

        job.cancel()
    }

    @Test
    fun testPrevChapterDoesNotMarkRead() = runBlocking {
        val novel = NovelEntity(id = "n_prev_test", title = "Prev Test")
        db.novelDao().insertNovel(novel)
        val ch1 = ChapterEntity(id = "c1", novelId = "n_prev_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = false)
        val ch2 = ChapterEntity(id = "c2", novelId = "n_prev_test", orderIndex = 2, title = "Ch 2", content = "Content 2", read = false)
        db.chapterDao().insertChapters(listOf(ch1, ch2))

        val viewModel = ReaderViewModel("n_prev_test", "c2", repository, settingsDataStore, ttsManager)
        val job = launch(Dispatchers.Unconfined) { viewModel.currentChapter.collect {} }

        waitUntil { viewModel.currentChapter.value?.id == "c2" }
        assertEquals("c2", viewModel.currentChapter.value?.id)

        // Press previous chapter
        viewModel.prevChapter()
        waitUntil { viewModel.currentChapter.value?.id == "c1" }

        assertEquals("c1", viewModel.currentChapter.value?.id)
        // Chapter 2 must still be unread
        assertFalse(db.chapterDao().getChapterByIdSync("c2")!!.read)
        assertFalse(db.chapterDao().getChapterByIdSync("c1")!!.read)

        job.cancel()
    }

    @Test
    fun testNextChapterMarksCurrentChapterReadWhenNextExists() = runBlocking {
        val novel = NovelEntity(id = "n_next_test", title = "Next Test")
        db.novelDao().insertNovel(novel)
        val ch1 = ChapterEntity(id = "c1", novelId = "n_next_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = false)
        val ch2 = ChapterEntity(id = "c2", novelId = "n_next_test", orderIndex = 2, title = "Ch 2", content = "Content 2", read = false)
        db.chapterDao().insertChapters(listOf(ch1, ch2))

        val viewModel = ReaderViewModel("n_next_test", "c1", repository, settingsDataStore, ttsManager)
        val job = launch(Dispatchers.Unconfined) { viewModel.currentChapter.collect {} }

        waitUntil { viewModel.currentChapter.value?.id == "c1" }
        assertEquals("c1", viewModel.currentChapter.value?.id)
        assertFalse(viewModel.currentChapter.value!!.read)

        // Move to next chapter
        viewModel.nextChapter()
        waitUntil { viewModel.currentChapter.value?.id == "c2" }

        // Current chapter should now be c2
        assertEquals("c2", viewModel.currentChapter.value?.id)
        // Chapter 1 must be marked read in the database
        assertTrue(db.chapterDao().getChapterByIdSync("c1")!!.read)
        // Chapter 2 remains unread
        assertFalse(db.chapterDao().getChapterByIdSync("c2")!!.read)

        job.cancel()
    }

    @Test
    fun testNextChapterOnLastChapterDoesNotMarkRead() = runBlocking {
        val novel = NovelEntity(id = "n_last_test", title = "Last Test")
        db.novelDao().insertNovel(novel)
        val ch1 = ChapterEntity(id = "c1", novelId = "n_last_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = false)
        db.chapterDao().insertChapters(listOf(ch1))

        val viewModel = ReaderViewModel("n_last_test", "c1", repository, settingsDataStore, ttsManager)
        val job = launch(Dispatchers.Unconfined) { viewModel.currentChapter.collect {} }

        waitUntil { viewModel.currentChapter.value?.id == "c1" }
        assertEquals("c1", viewModel.currentChapter.value?.id)

        // Attempt nextChapter when already on last chapter
        viewModel.nextChapter()
        ShadowLooper.idleMainLooper()
        Thread.sleep(25)
        ShadowLooper.idleMainLooper()

        // Must remain on c1 and must NOT be marked read
        assertEquals("c1", viewModel.currentChapter.value?.id)
        assertFalse(db.chapterDao().getChapterByIdSync("c1")!!.read)
        assertFalse(viewModel.currentChapter.value!!.read)

        job.cancel()
    }

    @Test
    fun testManualToggleReadStatus() = runBlocking {
        val novel = NovelEntity(id = "n_toggle_test", title = "Toggle Test")
        db.novelDao().insertNovel(novel)
        val ch1 = ChapterEntity(id = "c1", novelId = "n_toggle_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = false)
        db.chapterDao().insertChapters(listOf(ch1))

        val viewModel = ReaderViewModel("n_toggle_test", "c1", repository, settingsDataStore, ttsManager)
        val job = launch(Dispatchers.Unconfined) { viewModel.currentChapter.collect {} }

        waitUntil { viewModel.currentChapter.value?.id == "c1" }
        assertFalse(viewModel.currentChapter.value!!.read)

        // Toggle to read
        viewModel.toggleReadStatus()
        waitUntil { viewModel.currentChapter.value?.read == true }
        assertTrue(viewModel.currentChapter.value!!.read)
        assertTrue(db.chapterDao().getChapterByIdSync("c1")!!.read)

        // Toggle to unread
        viewModel.toggleReadStatus()
        waitUntil { viewModel.currentChapter.value?.read == false }
        assertFalse(viewModel.currentChapter.value!!.read)
        assertFalse(db.chapterDao().getChapterByIdSync("c1")!!.read)

        job.cancel()
    }
}
