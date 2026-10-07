package com.example.ui.screens.replacements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.entities.NovelEntity
import com.example.data.repository.NovelRepository
import com.example.replacement.ReplacementEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ReplacementsEvent {
    data class ShowToast(val message: String) : ReplacementsEvent()
    object Saved : ReplacementsEvent()
}

class ReplacementsViewModel(
    val novelId: String,
    private val repository: NovelRepository
) : ViewModel() {

    private val _novel = MutableStateFlow<NovelEntity?>(null)
    val novel: StateFlow<NovelEntity?> = _novel.asStateFlow()

    private val _rulesText = MutableStateFlow("")
    val rulesText: StateFlow<String> = _rulesText.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _sampleText = MutableStateFlow("")
    val sampleText: StateFlow<String> = _sampleText.asStateFlow()

    private val _previewResult = MutableStateFlow("")
    val previewResult: StateFlow<String> = _previewResult.asStateFlow()

    private val _events = MutableSharedFlow<ReplacementsEvent>()
    val events: SharedFlow<ReplacementsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val n = repository.getNovelByIdSync(novelId)
            _novel.value = n
            if (n != null) {
                _rulesText.value = n.replacements
                _enabled.value = n.replacementsEnabled
            }
        }
    }

    fun onRulesTextChange(text: String) {
        _rulesText.value = text
        updatePreview()
    }

    fun onEnabledChange(newEnabled: Boolean) {
        _enabled.value = newEnabled
    }

    fun addRule(find: String, replace: String, caseSensitive: Boolean, partialMatch: Boolean) {
        val trimmedFind = find.trim()
        if (trimmedFind.isEmpty()) return
        val trimmedReplace = replace.trim()

        val flags = buildString {
            if (caseSensitive) append("[cs] ")
            if (partialMatch) append("[nww] ")
        }
        val newRuleLine = "$flags$trimmedFind = $trimmedReplace"

        val updated = if (_rulesText.value.isBlank()) {
            newRuleLine
        } else {
            "${_rulesText.value.trimEnd()}\n$newRuleLine"
        }
        _rulesText.value = updated
        updatePreview()

        viewModelScope.launch {
            repository.updateReplacements(novelId, _rulesText.value, _enabled.value)
            _events.emit(ReplacementsEvent.ShowToast("Added: $trimmedFind ➔ $trimmedReplace"))
        }
    }

    fun onSampleTextChange(sample: String) {
        _sampleText.value = sample
        updatePreview()
    }

    private fun updatePreview() {
        if (_sampleText.value.isBlank()) {
            _previewResult.value = ""
            return
        }
        val rules = ReplacementEngine.parseRules(_rulesText.value)
        _previewResult.value = ReplacementEngine.applyRules(_sampleText.value, rules)
    }

    fun loadSampleFromChapter() {
        viewModelScope.launch {
            val novel = repository.getNovelByIdSync(novelId)
            val chapter = (novel?.lastChapterId?.let { repository.getChapterByIdSync(it) })
                ?: repository.getChaptersForNovelSync(novelId).firstOrNull()

            if (chapter != null && chapter.content.isNotBlank()) {
                _sampleText.value = chapter.content.take(1500)
                updatePreview()
            } else {
                _events.emit(ReplacementsEvent.ShowToast("No chapter content to sample from."))
            }
        }
    }

    fun loadExampleRules() {
        val example = """
            # Example rules — edit or delete freely.
            # Basic (case-insensitive, whole-word):
            Elder = Senior
            Sect Master = Sect Leader

            # Case-sensitive only:
            [cs] Lord = Lord God

            # Match inside words too (substring):
            [nww] mana = Mana

            # Both flags:
            [cs,nww] qi = Qi
        """.trimIndent()
        _rulesText.value = example
        updatePreview()
    }

    fun clearRules() {
        _rulesText.value = ""
        updatePreview()
    }

    fun saveRules() {
        viewModelScope.launch {
            repository.updateReplacements(novelId, _rulesText.value, _enabled.value)
            _events.emit(ReplacementsEvent.ShowToast("Replacements saved."))
            _events.emit(ReplacementsEvent.Saved)
        }
    }
}
