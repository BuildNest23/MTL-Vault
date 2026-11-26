package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shelves",
    indices = [Index(value = ["sortOrder"])]
)
data class ShelfEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "novel_shelf_cross_ref",
    primaryKeys = ["novelId", "shelfId"],
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novelId"]),
        Index(value = ["shelfId"])
    ]
)
data class NovelShelfCrossRef(
    val novelId: String,
    val shelfId: String,
    val addedAt: Long = System.currentTimeMillis()
)
