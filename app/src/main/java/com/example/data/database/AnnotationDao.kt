package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entities.NovelAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM novel_annotations WHERE novelId = :novelId ORDER BY createdAt DESC")
    fun observeForNovel(novelId: String): Flow<List<NovelAnnotationEntity>>

    @Query(
        "SELECT * FROM novel_annotations WHERE chapterId = :chapterId " +
            "ORDER BY paragraphIndex ASC, startOffset ASC, createdAt ASC"
    )
    fun observeForChapter(chapterId: String): Flow<List<NovelAnnotationEntity>>

    @Query("SELECT * FROM novel_annotations WHERE novelId = :novelId ORDER BY createdAt DESC")
    suspend fun getForNovelSync(novelId: String): List<NovelAnnotationEntity>

    @Query("SELECT COUNT(*) FROM novel_annotations WHERE novelId = :novelId")
    fun observeCountForNovel(novelId: String): Flow<Int>

    @Query(
        "SELECT * FROM novel_annotations WHERE chapterId = :chapterId " +
            "AND paragraphIndex = :paragraphIndex AND type = :type LIMIT 1"
    )
    suspend fun findAtParagraph(
        chapterId: String,
        paragraphIndex: Int,
        type: String = NovelAnnotationEntity.TYPE_BOOKMARK
    ): NovelAnnotationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: NovelAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(annotations: List<NovelAnnotationEntity>)

    @Query("DELETE FROM novel_annotations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM novel_annotations")
    suspend fun deleteAll()
}
