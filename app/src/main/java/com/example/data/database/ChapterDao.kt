package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entities.ChapterEntity
import com.example.data.model.ChapterSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("""
        SELECT id, novelId, orderIndex, title, wordCount, scrollPosition, read, readAt, createdAt, readingParagraphIndex 
        FROM chapters 
        WHERE novelId = :novelId 
        ORDER BY orderIndex ASC
    """)
    fun getChapterSummaries(novelId: String): Flow<List<ChapterSummary>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    fun getChaptersForNovel(novelId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId ORDER BY orderIndex ASC")
    suspend fun getChaptersForNovelSync(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    fun getChapterById(id: String): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterByIdSync(id: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE id IN (:ids) ORDER BY orderIndex ASC")
    suspend fun getChaptersByIdsSync(ids: List<String>): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND orderIndex > :currentOrder ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getNextChapter(novelId: String, currentOrder: Int): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE novelId = :novelId AND orderIndex < :currentOrder ORDER BY orderIndex DESC LIMIT 1")
    suspend fun getPrevChapter(novelId: String, currentOrder: Int): ChapterEntity?

    @Query("SELECT title FROM chapters WHERE novelId = :novelId")
    suspend fun getChapterTitles(novelId: String): List<String>

    @Query("SELECT MAX(orderIndex) FROM chapters WHERE novelId = :novelId")
    suspend fun getMaxOrderIndex(novelId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Query("UPDATE chapters SET read = :read, readAt = :readAt WHERE id = :chapterId")
    suspend fun updateReadStatus(chapterId: String, read: Boolean, readAt: Long?)

    @Query("UPDATE chapters SET scrollPosition = :position WHERE id = :chapterId")
    suspend fun updateScrollPosition(chapterId: String, position: Int)

    @Query("UPDATE chapters SET readingParagraphIndex = :paragraphIndex, scrollPosition = :scrollY WHERE id = :chapterId")
    suspend fun updateReadingPosition(chapterId: String, paragraphIndex: Int, scrollY: Int)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterById(id: String)

    @Query("DELETE FROM chapters WHERE id IN (:ids)")
    suspend fun deleteChaptersByIds(ids: List<String>)

    @Query("SELECT id FROM chapters WHERE novelId = :novelId AND orderIndex >= :orderIndex ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getFirstChapterIdFrom(novelId: String, orderIndex: Int): String?

    @Query("SELECT id FROM chapters WHERE novelId = :novelId ORDER BY orderIndex DESC LIMIT 1")
    suspend fun getLastChapterId(novelId: String): String?

    @Query("DELETE FROM chapters WHERE novelId = :novelId")
    suspend fun deleteChaptersForNovel(novelId: String)

    @Query("DELETE FROM chapters")
    suspend fun deleteAllChapters()

    @Query("SELECT COUNT(*) FROM chapters")
    fun getTotalChapterCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM chapters WHERE read = 1")
    fun getTotalReadChapterCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM chapters")
    fun getTotalWordCount(): Flow<Long>

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId")
    suspend fun getChapterCountForNovel(novelId: String): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE novelId = :novelId AND read = 1")
    suspend fun getReadChapterCountForNovel(novelId: String): Int

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM chapters WHERE novelId = :novelId")
    suspend fun getWordCountForNovel(novelId: String): Long
}
