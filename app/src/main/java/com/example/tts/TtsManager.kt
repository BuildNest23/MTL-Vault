package com.example.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _playbackInfo = MutableStateFlow(TtsPlaybackInfo())
    val playbackInfo: StateFlow<TtsPlaybackInfo> = _playbackInfo.asStateFlow()

    private var chunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    private var currentNovelId: String? = null
    private var currentChapterId: String? = null
    private var currentChapterTitle: String = ""

    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var onChapterCompleteCallback: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                stop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (_playbackInfo.value.state == TtsState.PAUSED) {
                    resume()
                }
            }
        }
    }

    init {
        _playbackInfo.value = TtsPlaybackInfo(state = TtsState.INITIALIZING)
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "Failed to initialize TTS engine: ${e.localizedMessage}"
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(speechPitch)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    val index = utteranceId?.substringAfter("chunk_")?.toIntOrNull() ?: 0
                    currentChunkIndex = index
                    _playbackInfo.value = _playbackInfo.value.copy(
                        state = TtsState.PLAYING,
                        currentChunkIndex = index
                    )
                }

                override fun onDone(utteranceId: String?) {
                    val index = utteranceId?.substringAfter("chunk_")?.toIntOrNull() ?: 0
                    if (index >= chunks.size - 1) {
                        mainScope.launch {
                            _playbackInfo.value = _playbackInfo.value.copy(
                                state = TtsState.IDLE,
                                currentChunkIndex = chunks.size
                            )
                            abandonAudioFocus()
                            TtsPlaybackService.stop(context)
                            onChapterCompleteCallback?.invoke()
                        }
                    } else {
                        currentChunkIndex = index + 1
                        _playbackInfo.value = _playbackInfo.value.copy(
                            currentChunkIndex = currentChunkIndex
                        )
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainScope.launch {
                        _playbackInfo.value = _playbackInfo.value.copy(
                            state = TtsState.ERROR,
                            errorMessage = "Error during speech synthesis."
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    val msg = when (errorCode) {
                        TextToSpeech.ERROR_SYNTHESIS -> "Synthesis error"
                        TextToSpeech.ERROR_SERVICE -> "TTS service error"
                        TextToSpeech.ERROR_OUTPUT -> "Audio output error"
                        TextToSpeech.ERROR_NETWORK -> "Network TTS error"
                        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice data downloading"
                        else -> "TTS Error code: $errorCode"
                    }
                    mainScope.launch {
                        _playbackInfo.value = _playbackInfo.value.copy(
                            state = TtsState.ERROR,
                            errorMessage = msg
                        )
                    }
                }
            })

            _playbackInfo.value = TtsPlaybackInfo(state = TtsState.IDLE)
        } else {
            isInitialized = false
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "TTS engine initialization failed (code $status). Make sure Google Speech Services is installed."
            )
        }
    }

    fun playChapter(
        novelId: String,
        chapterId: String,
        chapterTitle: String,
        content: String,
        startFromChunkIndex: Int = 0,
        onChapterComplete: (() -> Unit)? = null
    ) {
        if (!isInitialized || tts == null) {
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "Text-to-Speech is not ready yet."
            )
            return
        }

        stop(triggerServiceStop = false)

        currentNovelId = novelId
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        onChapterCompleteCallback = onChapterComplete

        chunks = chunkText(content)
        currentChunkIndex = startFromChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))

        if (chunks.isEmpty()) {
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.IDLE,
                novelId = novelId,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                errorMessage = "Chapter content is empty."
            )
            return
        }

        if (!requestAudioFocus()) {
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "Could not gain audio focus."
            )
            return
        }

        _playbackInfo.value = TtsPlaybackInfo(
            state = TtsState.PLAYING,
            novelId = novelId,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            totalChunks = chunks.size,
            currentChunkIndex = currentChunkIndex
        )

        TtsPlaybackService.start(context)
        speakFromCurrentChunk()
    }

    fun seekToChunk(index: Int) {
        if (chunks.isEmpty()) return
        val target = index.coerceIn(0, chunks.size - 1)
        currentChunkIndex = target
        _playbackInfo.value = _playbackInfo.value.copy(
            state = TtsState.PLAYING,
            currentChunkIndex = target
        )
        if (requestAudioFocus()) {
            speakFromCurrentChunk()
        }
    }

    fun previousChunk() {
        if (currentChunkIndex > 0) {
            seekToChunk(currentChunkIndex - 1)
        }
    }

    fun nextChunk() {
        if (currentChunkIndex < chunks.size - 1) {
            seekToChunk(currentChunkIndex + 1)
        }
    }

    private fun speakFromCurrentChunk() {
        val engine = tts ?: return
        engine.stop()

        for (i in currentChunkIndex until chunks.size) {
            val chunk = chunks[i]
            val utteranceId = "chunk_$i"
            val queueMode = if (i == currentChunkIndex) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            engine.speak(chunk, queueMode, params, utteranceId)
        }
    }

    fun pause() {
        if (_playbackInfo.value.state == TtsState.PLAYING) {
            tts?.stop()
            _playbackInfo.value = _playbackInfo.value.copy(state = TtsState.PAUSED)
        }
    }

    fun resume() {
        if (_playbackInfo.value.state == TtsState.PAUSED && chunks.isNotEmpty()) {
            if (requestAudioFocus()) {
                _playbackInfo.value = _playbackInfo.value.copy(state = TtsState.PLAYING)
                speakFromCurrentChunk()
            }
        }
    }

    fun stop(triggerServiceStop: Boolean = true) {
        tts?.stop()
        abandonAudioFocus()
        _playbackInfo.value = _playbackInfo.value.copy(
            state = TtsState.IDLE,
            currentChunkIndex = 0
        )
        if (triggerServiceStop) {
            TtsPlaybackService.stop(context)
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        speechPitch = pitch
        tts?.setPitch(pitch)
    }

    fun setVoice(voiceName: String) {
        val engine = tts ?: return
        val availableVoices = engine.voices ?: return
        val target = availableVoices.find { it.name == voiceName }
        if (target != null) {
            engine.voice = target
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.toList() ?: emptyList()
    }

    fun isPlayingChapter(novelId: String, chapterId: String): Boolean {
        return (_playbackInfo.value.state == TtsState.PLAYING || _playbackInfo.value.state == TtsState.PAUSED) &&
                currentNovelId == novelId &&
                currentChapterId == chapterId
    }

    private fun chunkText(text: String): List<String> {
        return text.split(Regex("(\r?\n){2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
