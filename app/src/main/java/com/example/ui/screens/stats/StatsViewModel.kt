package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NovelSummary
import com.example.data.repository.NovelRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    repository: NovelRepository
) : ViewModel() {

    val novels: StateFlow<List<NovelSummary>> = repository.getNovelSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalNovels: StateFlow<Int> = repository.totalNovelsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalChapters: StateFlow<Int> = repository.totalChaptersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalReadChapters: StateFlow<Int> = repository.totalReadChaptersCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalWords: StateFlow<Long> = repository.totalWordCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
}
