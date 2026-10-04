package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.database.entities.NovelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NovelDao {

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

    @Query("SELECT COUNT(*) FROM novels")
    fun getNovelCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM novels")
    suspend fun getNovelCountSync(): Int
}
