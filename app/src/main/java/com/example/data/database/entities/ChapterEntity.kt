package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novelId"]),
        Index(value = ["novelId", "orderIndex"])
    ]
)
data class ChapterEntity(
    @PrimaryKey
    val id: String,
    val novelId: String,
    val orderIndex: Int,
    val title: String,
    val content: String,
    val wordCount: Int = 0,
    val scrollPosition: Int = 0,
    val readingParagraphIndex: Int = 0,
    val read: Boolean = false,
    val readAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
