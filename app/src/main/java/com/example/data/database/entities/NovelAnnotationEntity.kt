package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "novel_annotations",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["id"],
            childColumns = ["novelId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["novelId"]),
        Index(value = ["chapterId"]),
        Index(value = ["novelId", "chapterId", "paragraphIndex"])
    ]
)
data class NovelAnnotationEntity(
    @PrimaryKey
    val id: String,
    val novelId: String,
    val chapterId: String,
    val paragraphIndex: Int,
    val type: String,
    val quoteText: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val colorKey: String = "amber",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_BOOKMARK = "bookmark"
        const val TYPE_HIGHLIGHT = "highlight"
    }
}
