package com.example.data.repository

import com.example.data.database.ChapterDao
import com.example.data.database.NovelDao
import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ChapterSummary
import com.example.data.model.NovelSummary
import com.example.data.preferences.SettingsDataStore
import com.example.importexport.BackupRestoreManager
import com.example.importexport.ChapterSplitter
import com.example.importexport.SplitChapterResult
import com.example.replacement.ReplacementEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import kotlinx.coroutines.withContext
import java.util.UUID

class NovelRepository(
    private val novelDao: NovelDao,
    private val chapterDao: ChapterDao,
    private val settingsDataStore: SettingsDataStore,
    private val database: AppDatabase? = null
) {

    /** Runs [block] in one database transaction when a database is available. */
    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        if (database != null) database.withTransaction { block() } else block()

    fun getNovelSummaries(): Flow<List<NovelSummary>> {
        return novelDao.getNovelSummaries()
    }

    fun getNovelById(novelId: String): Flow<NovelEntity?> = novelDao.getNovelById(novelId)

    suspend fun getNovelByIdSync(novelId: String): NovelEntity? = novelDao.getNovelByIdSync(novelId)

    fun getChapterSummaries(novelId: String): Flow<List<ChapterSummary>> = chapterDao.getChapterSummaries(novelId)

    suspend fun getChaptersForNovelSync(novelId: String): List<ChapterEntity> = chapterDao.getChaptersForNovelSync(novelId)

    fun getChapterById(chapterId: String): Flow<ChapterEntity?> = chapterDao.getChapterById(chapterId)

    suspend fun getChapterByIdSync(chapterId: String): ChapterEntity? = chapterDao.getChapterByIdSync(chapterId)

    suspend fun getNextChapter(novelId: String, currentOrder: Int): ChapterEntity? =
        chapterDao.getNextChapter(novelId, currentOrder)

    suspend fun getPrevChapter(novelId: String, currentOrder: Int): ChapterEntity? =
        chapterDao.getPrevChapter(novelId, currentOrder)

    suspend fun createNovel(
        title: String,
        author: String = "",
        translator: String = "",
        description: String = ""
    ): String = withContext(Dispatchers.IO) {
        val id = "n_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(4)
        val novel = NovelEntity(
            id = id,
            title = title.trim(),
            author = author.trim(),
            translator = translator.trim(),
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        novelDao.insertNovel(novel)
        id
    }

    suspend fun createSampleNovel(): String = withContext(Dispatchers.IO) {
        val novelId = createNovel(
            title = com.example.data.sample.SampleDataGenerator.getSampleNovelTitle(),
            author = com.example.data.sample.SampleDataGenerator.getSampleNovelAuthor(),
            description = com.example.data.sample.SampleDataGenerator.getSampleNovelDescription()
        )
        val chapters = com.example.data.sample.SampleDataGenerator.generateSampleChapters()
        importChapters(novelId, chapters)
        novelId
    }

    suspend fun ensureSampleDataIfEmpty() = withContext(Dispatchers.IO) {
        if (novelDao.getNovelCountSync() == 0) {
            createSampleNovel()
        }
    }

    suspend fun updateNovel(novel: NovelEntity) = withContext(Dispatchers.IO) {
        novelDao.updateNovel(novel.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNovelMetadata(novelId: String, description: String, coverUri: String?, author: String) =
        withContext(Dispatchers.IO) {
            novelDao.updateMetadata(novelId, description, coverUri, author)
        }

    suspend fun deleteNovel(novelId: String) = withContext(Dispatchers.IO) {
        val runBlock: suspend () -> Unit = {
            chapterDao.deleteChaptersForNovel(novelId)
            novelDao.deleteNovelById(novelId)
            // Forget the cached text without loading every chapter of the novel into memory first
            ReplacementEngine.invalidateCache()
        }
        if (database != null) {
            database.withTransaction { runBlock() }
        } else {
            runBlock()
        }
    }

    suspend fun addChapter(novelId: String, title: String, content: String): String = withContext(Dispatchers.IO) { inTransaction {
        val maxOrder = chapterDao.getMaxOrderIndex(novelId) ?: -1
        val newOrder = maxOrder + 1
        val id = "c_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(8)
        val wc = ChapterSplitter.countWords(content)
        val chapter = ChapterEntity(
            id = id,
            novelId = novelId,
            orderIndex = newOrder,
            title = title.ifBlank { "Chapter ${newOrder + 1}" },
            content = content,
            wordCount = wc,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        chapterDao.insertChapter(chapter)
        touchNovelKeepingProgress(novelId, id)
        id
    } }

    /** Saves an edited title and text without touching read state or reading position. */
    suspend fun updateChapterText(chapter: ChapterEntity, title: String, content: String) = withContext(Dispatchers.IO) {
        ReplacementEngine.invalidateChapter(chapter.id)
        chapterDao.updateChapterText(
            chapterId = chapter.id,
            title = title,
            content = content,
            wordCount = ChapterSplitter.countWords(content),
            updatedAt = System.currentTimeMillis()
        )
        touchNovelKeepingProgress(chapter.novelId, chapter.id)
    }

    /**
     * Bumps the novel's updatedAt without moving the "continue reading" pointer.
     * Only sets lastChapterId when the novel has none yet.
     */
    private suspend fun touchNovelKeepingProgress(novelId: String, fallbackChapterId: String?) {
        val existing = novelDao.getNovelByIdSync(novelId)?.lastChapterId
        novelDao.updateLastChapter(novelId, existing ?: fallbackChapterId, System.currentTimeMillis())
    }

    suspend fun deleteChapter(novelId: String, chapterId: String) = withContext(Dispatchers.IO) {
        val runBlock: suspend () -> Unit = {
            val deletedOrder = chapterDao.getChapterByIdSync(chapterId)?.orderIndex
            chapterDao.deleteChapterById(chapterId)
            ReplacementEngine.invalidateChapter(chapterId)
            val novel = novelDao.getNovelByIdSync(novelId)
            if (novel?.lastChapterId == chapterId) {
                // Nearest following chapter, else the last remaining one (no chapter text is loaded)
                val fallbackChapterId =
                    (if (deletedOrder != null) chapterDao.getFirstChapterIdFrom(novelId, deletedOrder) else null)
                        ?: chapterDao.getLastChapterId(novelId)
                novelDao.updateLastChapter(novelId, fallbackChapterId, System.currentTimeMillis())
            }
        }
        if (database != null) {
            database.withTransaction { runBlock() }
        } else {
            runBlock()
        }
    }

    suspend fun deleteChapters(novelId: String, ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        val runBlock: suspend () -> Unit = {
            // Remember where the deleted block started, before the rows are gone
            val firstDeletedOrder = ids.chunked(500)
                .mapNotNull { chunk -> chapterDao.getMinOrderIndexForIds(chunk) }
                .minOrNull()
            // Delete in chunks of 500 ids to avoid SQLite variable limit
            ids.chunked(500).forEach { chunk ->
                chapterDao.deleteChaptersByIds(chunk)
            }

            // Set novel.lastChapterId to the nearest following chapter (else the last one) if it was deleted
            val novel = novelDao.getNovelByIdSync(novelId)
            val idSet = ids.toSet()
            if (novel != null && novel.lastChapterId != null && idSet.contains(novel.lastChapterId)) {
                val fallbackChapterId =
                    (if (firstDeletedOrder != null) chapterDao.getFirstChapterIdFrom(novelId, firstDeletedOrder) else null)
                        ?: chapterDao.getLastChapterId(novelId)
                novelDao.updateLastChapter(novelId, fallbackChapterId)
            }

            // Invalidate ReplacementEngine cache for each deleted chapter
            ids.forEach { id ->
                ReplacementEngine.invalidateChapter(id)
            }
        }

        if (database != null) {
            database.withTransaction {
                runBlock()
            }
        } else {
            runBlock()
        }
    }

    suspend fun toggleChapterRead(chapterId: String) = withContext(Dispatchers.IO) {
        val chapter = chapterDao.getChapterByIdSync(chapterId) ?: return@withContext
        val newRead = !chapter.read
        chapterDao.updateReadStatus(chapterId, newRead, if (newRead) System.currentTimeMillis() else null)
    }

    suspend fun setChapterRead(chapterId: String, read: Boolean) = withContext(Dispatchers.IO) {
        chapterDao.updateReadStatus(chapterId, read, if (read) System.currentTimeMillis() else null)
    }

    /** Marks every chapter of a novel as read (true) or unread (false). */
    suspend fun setAllChaptersRead(novelId: String, read: Boolean) = withContext(Dispatchers.IO) {
        chapterDao.updateReadStatusForNovel(novelId, read, if (read) System.currentTimeMillis() else null)
    }

    suspend fun updateScrollPosition(chapterId: String, position: Int) = withContext(Dispatchers.IO) {
        chapterDao.updateScrollPosition(chapterId, position)
    }

    suspend fun updateReadingPosition(chapterId: String, paragraphIndex: Int, scrollY: Int = 0) = withContext(Dispatchers.IO) {
        chapterDao.updateReadingPosition(chapterId, paragraphIndex, scrollY)
    }

    suspend fun updateLastChapter(novelId: String, chapterId: String) = withContext(Dispatchers.IO) {
        novelDao.updateLastChapter(novelId, chapterId, System.currentTimeMillis())
    }

    suspend fun updateReplacements(novelId: String, rules: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        com.example.replacement.ReplacementEngine.invalidateCache()
        novelDao.updateReplacements(novelId, rules, enabled, System.currentTimeMillis())
    }

    suspend fun importChapters(novelId: String, chapters: List<SplitChapterResult>): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        com.example.logging.AppLogger.i("Import", "importChapters started: novelId=$novelId, count=${chapters.size}")
        try {
            val entities = inTransaction {
            val currentMaxOrder = chapterDao.getMaxOrderIndex(novelId) ?: -1
            var nextOrder = currentMaxOrder + 1
            val built = chapters.map { ch ->
                ChapterEntity(
                    id = "c_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(12),
                    novelId = novelId,
                    orderIndex = nextOrder++,
                    title = ch.title,
                    content = ch.content,
                    wordCount = ch.wordCount,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
            built.chunked(500).forEach { chapterDao.insertChapters(it) }
            touchNovelKeepingProgress(novelId, built.firstOrNull()?.id)
            built
            }
            val duration = System.currentTimeMillis() - startTime
            com.example.logging.AppLogger.i("Import", "importChapters finished: inserted=${entities.size} in ${duration}ms")
            entities.size
        } catch (e: Exception) {
            com.example.logging.AppLogger.e("Import", "importChapters failed for novelId=$novelId", e)
            throw e
        }
    }

    suspend fun getRenderedChapterContent(novelId: String, chapter: ChapterEntity): String = withContext(Dispatchers.Default) {
        val novel = novelDao.getNovelByIdSync(novelId)
        val settings = settingsDataStore.settingsFlow.first()
        val rulesActive = novel != null && novel.replacementsEnabled && settings.replacementsEnabled && novel.replacements.isNotBlank()
        val rulesHash = if (rulesActive) novel!!.replacements.hashCode() else 0
        val contentHash = chapter.content.hashCode()

        val cached = com.example.replacement.ReplacementEngine.getCachedRenderedContent(chapter.id, rulesHash, contentHash)
        if (cached != null) {
            return@withContext cached
        }

        val normalized = com.example.replacement.ReplacementEngine.normalizeText(chapter.content)
        val result = if (rulesActive) {
            val rules = com.example.replacement.ReplacementEngine.parseRules(novel!!.replacements)
            com.example.replacement.ReplacementEngine.applyRules(normalized, rules)
        } else {
            normalized
        }

        com.example.replacement.ReplacementEngine.cacheRenderedContent(chapter.id, rulesHash, contentHash, result)
        result
    }

    /** Same normalisation the reader uses, then the replacement rules (if any). */
    private fun renderForExport(raw: String, rules: List<com.example.replacement.ReplacementRule>): String {
        val normalized = ReplacementEngine.normalizeText(raw)
        return if (rules.isNotEmpty()) ReplacementEngine.applyRules(normalized, rules) else normalized
    }

    suspend fun getRenderedChapters(novelId: String, chapterIds: List<String>? = null): List<ChapterEntity> = withContext(Dispatchers.Default) {
        val novel = novelDao.getNovelByIdSync(novelId) ?: return@withContext emptyList()
        val rawChapters = if (chapterIds == null) {
            chapterDao.getChaptersForNovelSync(novelId)
        } else {
            chapterIds.chunked(900).flatMap { chunk ->
                chapterDao.getChaptersByIdsSync(chunk)
            }.sortedBy { it.orderIndex }
        }
        val settings = settingsDataStore.settingsFlow.first()
        val rules = if (novel.replacementsEnabled && settings.replacementsEnabled && novel.replacements.isNotBlank()) {
            ReplacementEngine.parseRules(novel.replacements)
        } else {
            emptyList()
        }
        rawChapters.map { ch -> ch.copy(content = renderForExport(ch.content, rules)) }
    }

    suspend fun getNextDefaultChapterTitle(novelId: String): String = withContext(Dispatchers.IO) {
        // Titles only: avoids loading the full text of every chapter just to pick a number
        val titles = chapterDao.getChapterTitles(novelId)
        if (titles.isEmpty()) {
            return@withContext "Chapter 1"
        }

        var highestNumber: Int? = null
        val chapterPattern = Regex("""(?i)\b(?:chapter|ch|chapitre|capitulo|capítulo|ch\.)\s*(\d+)\b""")
        val fallbackNumberPattern = Regex("""\b(\d+)\b""")

        for (title in titles) {
            val chapterMatch = chapterPattern.find(title)
            val num = if (chapterMatch != null) {
                chapterMatch.groupValues[1].toIntOrNull()
            } else {
                fallbackNumberPattern.findAll(title)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .maxOrNull()
            }

            if (num != null) {
                if (highestNumber == null || num > highestNumber) {
                    highestNumber = num
                }
            }
        }

        val nextNum = if (highestNumber != null) {
            highestNumber + 1
        } else {
            titles.size + 1
        }

        "Chapter $nextNum"
    }

    /**
     * Writes the whole library to [out] without ever holding more than one novel in memory.
     * [readCover] gives the bytes of a saved cover picture so it is included in the backup.
     */
    suspend fun writeBackup(out: java.io.OutputStream, readCover: (String) -> ByteArray?) = withContext(Dispatchers.IO) {
        val novels = novelDao.getAllNovels().first()
        val settings = settingsDataStore.settingsFlow.first()
        BackupRestoreManager.writeBackupStream(
            out = out,
            novels = novels,
            settings = settings,
            loadChapters = { id -> chapterDao.getChaptersForNovelSync(id) },
            readCover = readCover
        )
    }

    /**
     * Restores a backup from [input], one novel at a time. Everything happens in one database
     * transaction, so a damaged file leaves the current library untouched.
     * [saveCover] stores an embedded cover picture and returns its file address.
     */
    suspend fun restoreFromBackup(
        input: java.io.InputStream,
        saveCover: (novelId: String, bytes: ByteArray) -> String?
    ) = withContext(Dispatchers.IO) {
        var restoredSettings: com.example.data.model.ReaderSettings? = null
        val performDbRestore: suspend () -> Unit = {
            chapterDao.deleteAllChapters()
            novelDao.deleteAllNovels()
            restoredSettings = BackupRestoreManager.readBackupStream(input, saveCover) { item ->
                novelDao.insertNovel(item.novel)
                // Insert in slices so a huge novel never builds one enormous SQL batch
                item.chapters.chunked(200).forEach { chapterDao.insertChapters(it) }
            }
        }
        if (database != null) {
            database.withTransaction { performDbRestore() }
        } else {
            performDbRestore()
        }
        ReplacementEngine.invalidateCache()
        restoredSettings?.let { restored ->
            // Backups don't store these two, so keep the device's current values
            val current = settingsDataStore.settingsFlow.first()
            settingsDataStore.saveAllSettings(
                restored.copy(
                    ttsEngine = if (restored.ttsEngine.isBlank()) current.ttsEngine else restored.ttsEngine,
                    edgeVoice = if (restored.edgeVoice.isBlank()) current.edgeVoice else restored.edgeVoice,
                    detailedLogging = current.detailedLogging
                )
            )
        }
    }

    suspend fun resetAllData() = withContext(Dispatchers.IO) {
        val runBlock: suspend () -> Unit = {
            chapterDao.deleteAllChapters()
            novelDao.deleteAllNovels()
        }
        if (database != null) {
            database.withTransaction { runBlock() }
        } else {
            runBlock()
        }
        ReplacementEngine.invalidateCache()
    }

    // Stats
    val totalNovelsCount: Flow<Int> = novelDao.getNovelCount()
    val totalChaptersCount: Flow<Int> = chapterDao.getTotalChapterCount()
    val totalReadChaptersCount: Flow<Int> = chapterDao.getTotalReadChapterCount()
    val totalWordCount: Flow<Long> = chapterDao.getTotalWordCount()
}
