package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entities.NovelEntity
import com.example.data.model.NovelSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

    @Query("""
        SELECT
            n.id AS id,
            n.title AS title,
            n.author AS author,
            n.translator AS translator,
            n.description AS description,
            n.coverUri AS coverUri,
            n.lastChapterId AS lastChapterId,
            n.replacements AS replacements,
            n.replacementsEnabled AS replacementsEnabled,
            n.createdAt AS createdAt,
            n.updatedAt AS updatedAt,
            COUNT(c.id) AS totalChapters,
            COALESCE(SUM(CASE WHEN c.read = 1 THEN 1 ELSE 0 END), 0) AS readChapters,
            COALESCE(SUM(c.wordCount), 0) AS totalWords,
            MAX(lc.title) AS lastChapterTitle
        FROM novels n
        LEFT JOIN chapters c ON c.novelId = n.id
        LEFT JOIN chapters lc ON lc.id = n.lastChapterId
        GROUP BY n.id
        ORDER BY n.updatedAt DESC
    """)
    fun getNovelSummaries(): Flow<List<NovelSummary>>

    @Query("SELECT * FROM novels ORDER BY updatedAt DESC")
    fun getAllNovels(): Flow<List<NovelEntity>>

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    fun getNovelById(id: String): Flow<NovelEntity?>

    @Query("SELECT * FROM novels WHERE id = :id LIMIT 1")
    suspend fun getNovelByIdSync(id: String): NovelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovel(novel: NovelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNovels(novels: List<NovelEntity>)

    @Update
    suspend fun updateNovel(novel: NovelEntity)

    @Delete
    suspend fun deleteNovel(novel: NovelEntity)

    @Query("DELETE FROM novels WHERE id = :id")
    suspend fun deleteNovelById(id: String)

    @Query("DELETE FROM novels")
    suspend fun deleteAllNovels()

    @Query("UPDATE novels SET lastChapterId = :chapterId, updatedAt = :updatedAt WHERE id = :novelId")
    suspend fun updateLastChapter(novelId: String, chapterId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE novels SET replacements = :rules, replacementsEnabled = :enabled, updatedAt = :updatedAt WHERE id = :novelId")
    suspend fun updateReplacements(novelId: String, rules: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    /** Updates online metadata without touching updatedAt, so "Last read" ordering is unchanged. */
    @Query("UPDATE novels SET description = :description, coverUri = :coverUri, author = :author WHERE id = :novelId")
    suspend fun updateMetadata(novelId: String, description: String, coverUri: String?, author: String)

    @Query("SELECT COUNT(*) FROM novels")
    fun getNovelCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCountSync(): Int
}
