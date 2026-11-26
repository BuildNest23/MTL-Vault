package com.example.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioStatsCalculator
import com.example.audio.AudioStatsSummary
import com.example.data.model.NovelSummary
import com.example.data.repository.AudioRepository
import com.example.data.repository.NovelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StatsEvent {
    data class ShowToast(val message: String) : StatsEvent()
}

class StatsViewModel(
    private val repository: NovelRepository,
    private val audioRepository: AudioRepository
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _events = MutableSharedFlow<StatsEvent>()
    val events: SharedFlow<StatsEvent> = _events.asSharedFlow()

    private val refreshTrigger = MutableStateFlow(0L)

    /** The current day number. Checked every minute and also on manual refresh. */
    private val minuteTick = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    private val today = combine(minuteTick, refreshTrigger) { _, _ ->
        AudioRepository.dayEpochFor(System.currentTimeMillis())
    }.distinctUntilChanged()

    /** Everything shown in the Audiobooks part of the Stats tab (strictly scoped to live audiobooks). */
    val audio: StateFlow<AudioStatsSummary> = combine(
        audioRepository.observeListening(),
        audioRepository.observeBooks(),
        today,
        refreshTrigger
    ) { rows, books, todayEpoch, _ ->
        AudioStatsCalculator.compute(rows, books, todayEpoch)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioStatsSummary())

    /**
     * Prunes any stale stat records belonging to removed novels or audiobooks and forces
     * a fresh recalculation of all stats shown on the Reading Digest tab.
     */
    fun refreshStats() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val (removedChapters, removedAudioRows) = withContext(Dispatchers.IO) {
                    val chaptersPruned = repository.pruneOrphanedStats()
                    val audioPruned = audioRepository.pruneOrphanedListeningStats()
                    chaptersPruned to audioPruned
                }
                refreshTrigger.value = System.currentTimeMillis()
                val totalRemoved = removedChapters + removedAudioRows
                val message = if (totalRemoved > 0) {
                    "Stats refreshed — removed $totalRemoved outdated record${if (totalRemoved == 1) "" else "s"}."
                } else {
                    "Stats refreshed and up to date."
                }
                _events.emit(StatsEvent.ShowToast(message))
            } catch (e: Exception) {
                _events.emit(StatsEvent.ShowToast("Couldn't refresh stats: ${e.localizedMessage}"))
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
