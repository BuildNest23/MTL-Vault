package com.example.data.model

import com.example.data.database.entities.NovelEntity

data class NovelSummary(
    val id: String,
    val title: String,
    val author: String,
    val translator: String,
    val description: String,
    val coverUri: String?,
    val lastChapterId: String?,
    val replacements: String,
    val replacementsEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val totalChapters: Int = 0,
    val readChapters: Int = 0,
    val totalWords: Long = 0L,
    val lastChapterTitle: String? = null
) {
    val progressPercent: Int
        get() = if (totalChapters > 0) (readChapters * 100) / totalChapters else 0

    val unreadChapters: Int
        get() = (totalChapters - readChapters).coerceAtLeast(0)

    val hasRules: Boolean
        get() = replacements.isNotBlank()
}

data class ChapterSummary(
    val id: String,
    val novelId: String,
    val orderIndex: Int,
    val title: String,
    val wordCount: Int,
    val scrollPosition: Int,
    val read: Boolean,
    val readAt: Long?,
    val createdAt: Long,
    val readingParagraphIndex: Int = 0
)
