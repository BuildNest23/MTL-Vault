package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStatsCalculator
import com.example.audio.AudioStatsSummary
import com.example.data.model.NovelSummary
import com.example.data.repository.AudioRepository
import com.example.data.repository.NovelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(
    repository: NovelRepository,
    audioRepository: AudioRepository
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

    /** The current day number. Checked every minute so the stats roll over at midnight on their own. */
    private val today = flow {
        while (true) {
            emit(AudioRepository.dayEpochFor(System.currentTimeMillis()))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    /** Everything shown in the Audiobooks part of the Stats tab. */
    val audio: StateFlow<AudioStatsSummary> = combine(
        audioRepository.observeListening(),
        audioRepository.observeBooks(),
        today
    ) { rows, books, todayEpoch ->
        AudioStatsCalculator.compute(rows, books, todayEpoch)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioStatsSummary())
}
