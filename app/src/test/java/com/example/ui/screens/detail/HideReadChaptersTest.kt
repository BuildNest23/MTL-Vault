package com.example.ui.screens.detail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import com.example.data.database.AppDatabase
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ReaderSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.preferences.dataStore
import com.example.data.repository.NovelRepository
import com.example.importexport.BackupRestoreManager
import com.example.importexport.NovelWithChaptersBackup
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HideReadChaptersTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: NovelRepository
    private lateinit var settingsDataStore: SettingsDataStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
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
    fun testHideReadChaptersSettingPersistence() = runBlocking {
        // Default is false
        assertFalse(settingsDataStore.hideReadChaptersFlow.first())

        // Update to true
        settingsDataStore.updateHideReadChapters(true)
        assertTrue(settingsDataStore.hideReadChaptersFlow.first())

        // Update back to false
        settingsDataStore.updateHideReadChapters(false)
        assertFalse(settingsDataStore.hideReadChaptersFlow.first())
    }

    @Test
    fun testSelectAllOnlySelectsVisibleChaptersWhenHidden() = runBlocking {
        val novel = NovelEntity(id = "novel_hide_test", title = "Hide Test Novel")
        db.novelDao().insertNovel(novel)

        val ch1 = ChapterEntity(id = "c1", novelId = "novel_hide_test", orderIndex = 1, title = "Ch 1", content = "Content 1", read = true)
        val ch2 = ChapterEntity(id = "c2", novelId = "novel_hide_test", orderIndex = 2, title = "Ch 2", content = "Content 2", read = false)
        val ch3 = ChapterEntity(id = "c3", novelId = "novel_hide_test", orderIndex = 3, title = "Ch 3", content = "Content 3", read = true)
        db.chapterDao().insertChapters(listOf(ch1, ch2, ch3))

        // Set hideReadChapters = true
        settingsDataStore.updateHideReadChapters(true)

        val viewModel = NovelDetailViewModel("novel_hide_test", repository, settingsDataStore)
        val job1 = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.chapters.collect {} }
        val job2 = launch(kotlinx.coroutines.Dispatchers.Unconfined) { viewModel.hideReadChapters.collect {} }

        // Wait for chapters and hideReadChapters to load
        var attempts = 0
        while (viewModel.chapters.value.size < 3 && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }
        assertEquals(3, viewModel.chapters.value.size)
        assertTrue(viewModel.hideReadChapters.value)

        // Calling selectAllChapters() without args filters out read chapters when hidden
        viewModel.selectAllChapters()
        assertEquals(setOf("c2"), viewModel.selectedChapterIds.value)

        // Calling selectAllChapters with explicit visible IDs
        viewModel.selectAllChapters(listOf("c2"))
        assertEquals(setOf("c2"), viewModel.selectedChapterIds.value)

        // Toggle hideReadChapters to false
        viewModel.toggleHideReadChapters()
        attempts = 0
        while (viewModel.hideReadChapters.value && attempts < 50) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
            attempts++
        }
        assertFalse(viewModel.hideReadChapters.value)

        // Now selectAllChapters should select all 3
        viewModel.selectAllChapters()
        assertEquals(setOf("c1", "c2", "c3"), viewModel.selectedChapterIds.value)

        job1.cancel()
        job2.cancel()
    }

    @Test
    fun testBackupRestoreToleratesLegacyOpenAtFirstUnread() {
        // Generating backup must not write openAtFirstUnread
        val novel = NovelEntity(id = "n_b", title = "Backup Novel")
        val json = BackupRestoreManager.generateBackupJson(
            novelsWithChapters = listOf(NovelWithChaptersBackup(novel, emptyList())),
            settings = ReaderSettings(theme = "night")
        )
        assertFalse(json.contains("openAtFirstUnread"))

        // Parsing old backup with openAtFirstUnread present must succeed
        val legacyJson = """
            {
              "data": {
                "novels": [],
                "settings": {
                  "theme": "sepia",
                  "fontSize": 22,
                  "openAtFirstUnread": true
                }
              }
            }
        """.trimIndent()

        val parsed = BackupRestoreManager.parseBackupJson(legacyJson)
        assertEquals("sepia", parsed.settings?.theme)
        assertEquals(22, parsed.settings?.fontSize)
    }
}
