package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.database.entities.NovelShelfCrossRef
import com.example.data.database.entities.ShelfEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {

    @Query("SELECT * FROM shelves ORDER BY sortOrder ASC, createdAt ASC")
    fun observeShelves(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllShelvesSync(): List<ShelfEntity>

    @Query("SELECT * FROM novel_shelf_cross_ref")
    fun observeAllMemberships(): Flow<List<NovelShelfCrossRef>>

    @Query("SELECT * FROM novel_shelf_cross_ref")
    suspend fun getAllMembershipsSync(): List<NovelShelfCrossRef>

    @Query("SELECT shelfId FROM novel_shelf_cross_ref WHERE novelId = :novelId")
    fun observeShelfIdsForNovel(novelId: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM novel_shelf_cross_ref WHERE novelId = :novelId AND shelfId = :shelfId")
    suspend fun membershipCount(novelId: String, shelfId: String): Int

    @Query("SELECT MAX(sortOrder) FROM shelves")
    suspend fun getMaxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShelf(shelf: ShelfEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelves(shelves: List<ShelfEntity>)

    @Query("UPDATE shelves SET name = :name WHERE id = :shelfId")
    suspend fun renameShelf(shelfId: String, name: String)

    @Query("DELETE FROM shelves WHERE id = :shelfId")
    suspend fun deleteShelf(shelfId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addNovelToShelf(ref: NovelShelfCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberships(refs: List<NovelShelfCrossRef>)

    @Query("DELETE FROM novel_shelf_cross_ref WHERE novelId = :novelId AND shelfId = :shelfId")
    suspend fun removeNovelFromShelf(novelId: String, shelfId: String)

    @Query("DELETE FROM novel_shelf_cross_ref")
    suspend fun deleteAllMemberships()

    @Query("DELETE FROM shelves")
    suspend fun deleteAllShelves()
}
