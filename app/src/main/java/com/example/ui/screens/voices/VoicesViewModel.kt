package com.example.ui.screens.voices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.NovelRepository
import com.example.tts.TtsManager
import com.example.tts.VoiceCast
import com.example.tts.VoiceCastStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One character found in the novel's speaker tags, with the voice chosen for it. */
data class VoiceRow(
    val key: String,
    val name: String,
    /** Voice name chosen for this character; blank = same as the narrator. */
    val voice: String,
    /** Tagged lines this character has in the novel. */
    val lines: Int = 0,
    /** "m", "f" or "" (not marked). */
    val gender: String = ""
)

data class VoicesUiState(
    val loading: Boolean = true,
    val novelTitle: String = "",
    /** Voice for narration; blank = the normal voice from Settings. */
    val narratorVoice: String = "",
    val defaultMale: String = "",
    val defaultFemale: String = "",
    val rows: List<VoiceRow> = emptyList(),
    val voices: List<TtsManager.PhoneVoiceInfo> = emptyList()
)

class VoicesViewModel(
    private val novelId: String,
    private val repository: NovelRepository,
    private val store: VoiceCastStore,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _state = MutableStateFlow(VoicesUiState())
    val state: StateFlow<VoicesUiState> = _state.asStateFlow()

    private var cast = VoiceCast()

    init {
        refresh()
    }

    /** Looks through the novel again for @Name tags and reloads the saved choices. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val title = repository.getNovelByIdSync(novelId)?.title ?: ""
            val (found, counts) = repository.findSpeakers(novelId)
            cast = store.load(novelId)

            // Characters found in the chapters first, then any that have a saved voice but are no longer found
            val names = LinkedHashMap<String, String>()
            names.putAll(found)
            cast.voices.forEach { (key, voice) ->
                if (voice.isNotBlank() && key !in names) names[key] = cast.names[key] ?: key
            }
            val rows = names.map { (key, name) ->
                VoiceRow(key, name, cast.voices[key].orEmpty(), counts[key] ?: 0, cast.genders[key].orEmpty())
            }.sortedWith(compareByDescending<VoiceRow> { it.lines }.thenBy { it.name.lowercase() })
            _state.value = VoicesUiState(
                loading = false,
                novelTitle = title,
                narratorVoice = cast.narrator,
                defaultMale = cast.defaultMale,
                defaultFemale = cast.defaultFemale,
                rows = rows,
                voices = ttsManager.getPhoneVoices()
            )

            // The speech engine may still be starting; try again a few times for its voice list
            var tries = 0
            while (_state.value.voices.isEmpty() && tries < 6) {
                delay(700)
                _state.value = _state.value.copy(voices = ttsManager.getPhoneVoices())
                tries++
            }
        }
    }

    fun setNarratorVoice(voiceName: String) {
        cast = cast.copy(narrator = voiceName)
        save()
        _state.value = _state.value.copy(narratorVoice = voiceName)
        preview(voiceName)
    }

    fun setDefaultVoice(male: Boolean, voiceName: String) {
        cast = if (male) cast.copy(defaultMale = voiceName) else cast.copy(defaultFemale = voiceName)
        save()
        _state.value = if (male) _state.value.copy(defaultMale = voiceName) else _state.value.copy(defaultFemale = voiceName)
        preview(voiceName.ifBlank { cast.narrator })
    }

    /** Marks a character male or female ("" clears it). Tapping the same mark again clears it. */
    fun setGender(key: String, gender: String) {
        val current = cast.genders[key].orEmpty()
        val next = if (current == gender) "" else gender
        val genders = LinkedHashMap(cast.genders)
        if (next.isBlank()) genders.remove(key) else genders[key] = next
        val names = LinkedHashMap(cast.names)
        _state.value.rows.forEach { names[it.key] = it.name }
        cast = cast.copy(genders = genders, names = names)
        save()
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.key == key) it.copy(gender = next) else it }
        )
        if (_state.value.rows.firstOrNull { it.key == key }?.voice.isNullOrBlank()) preview(cast.voiceFor(key))
    }

    fun setCharacterVoice(key: String, voiceName: String) {
        val voices = LinkedHashMap(cast.voices)
        if (voiceName.isBlank()) voices.remove(key) else voices[key] = voiceName
        val names = LinkedHashMap(cast.names)
        _state.value.rows.forEach { names[it.key] = it.name }
        cast = cast.copy(voices = voices, names = names)
        save()
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.key == key) it.copy(voice = voiceName) else it }
        )
        preview(cast.voiceFor(key))
    }

    /** Speaks a short sample with the given voice (blank = the narrator / normal voice). */
    fun preview(voiceName: String) {
        ttsManager.previewPhoneVoice("Hello, this is how I sound when I speak.", voiceName)
    }

    private fun save() {
        store.save(novelId, cast)
        ttsManager.reloadCast(novelId)
    }
}
