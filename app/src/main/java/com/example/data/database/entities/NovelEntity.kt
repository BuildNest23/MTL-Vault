package com.example.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String = "",
    val translator: String = "",
    val description: String = "",
    val coverUri: String? = null,
    val lastChapterId: String? = null,
    val replacements: String = "",
    val replacementsEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
