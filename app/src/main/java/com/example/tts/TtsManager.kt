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
import android.util.Log
import com.example.MtlVaultApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "TtsManager"

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    private val _playbackInfo = MutableStateFlow(TtsPlaybackInfo())
    val playbackInfo: StateFlow<TtsPlaybackInfo> = _playbackInfo.asStateFlow()

    private var chunks: List<String> = emptyList()
    @Volatile
    private var currentChunkIndex = 0
    private var currentNovelId: String? = null
    private var currentChapterId: String? = null
    private var currentChapterTitle: String = ""

    @Volatile
    private var isSpeakingActive = false

    private var speechRate = 1.0f
    private var speechPitch = 1.0f
    private var selectedVoiceName: String? = null

    var onChapterCompleteCallback: (() -> Unit)? = null
    var onNextChapterRequested: (() -> Unit)? = null
    var onPrevChapterRequested: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
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
        initEngine()
    }

    private fun initEngine(onReady: (() -> Unit)? = null) {
        if (onReady != null) {
            pendingInitCallbacks.add(onReady)
        }
        if (isInitializing) return

        isInitializing = true
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "Old TTS shutdown warning: ${e.localizedMessage}")
        }
        tts = null
        isInitialized = false

        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            isInitializing = false
            Log.e(tag, "Failed to create TextToSpeech instance", e)
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "Failed to initialize TTS engine: ${e.localizedMessage}"
            )
        }
    }

    override fun onInit(status: Int) {
        isInitializing = false
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            val engine = tts
            if (engine != null) {
                engine.language = Locale.getDefault()
                engine.setSpeechRate(speechRate)
                engine.setPitch(speechPitch)

                if (selectedVoiceName != null) {
                    engine.voices?.find { it.name == selectedVoiceName }?.let {
                        engine.voice = it
                    }
                }

                setupProgressListener(engine)
            }

            if (_playbackInfo.value.state == TtsState.INITIALIZING || _playbackInfo.value.state == TtsState.ERROR) {
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = TtsState.IDLE,
                    errorMessage = null
                )
            }

            // Execute pending callbacks
            val callbacks = ArrayList(pendingInitCallbacks)
            pendingInitCallbacks.clear()
            for (cb in callbacks) {
                try {
                    cb.invoke()
                } catch (e: Exception) {
                    Log.e(tag, "Error in pending init callback", e)
                }
            }
        } else {
            isInitialized = false
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "TTS initialization failed (code $status). Make sure Google Speech Services is enabled."
            )
        }
    }

    private fun setupProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val index = extractChunkIndex(utteranceId) ?: currentChunkIndex
                currentChunkIndex = index
                mainScope.launch {
                    _playbackInfo.value = _playbackInfo.value.copy(
                        state = TtsState.PLAYING,
                        currentChunkIndex = index
                    )
                }
            }

            override fun onDone(utteranceId: String?) {
                val finishedIndex = extractChunkIndex(utteranceId) ?: currentChunkIndex
                if (!isSpeakingActive) return

                mainScope.launch {
                    val nextIndex = finishedIndex + 1
                    if (nextIndex < chunks.size) {
                        currentChunkIndex = nextIndex
                        _playbackInfo.value = _playbackInfo.value.copy(
                            currentChunkIndex = nextIndex
                        )
                        speakCurrentChunk()
                    } else {
                        // Current chapter finished naturally!
                        currentChunkIndex = chunks.size
                        _playbackInfo.value = _playbackInfo.value.copy(
                            currentChunkIndex = chunks.size
                        )
                        // Trigger next chapter transition without tearing down service
                        if (onChapterCompleteCallback != null) {
                            onChapterCompleteCallback?.invoke()
                        } else {
                            skipToNextChapter()
                        }
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleUtteranceError("Speech error on $utteranceId")
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
                handleUtteranceError(msg)
            }
        })
    }

    private fun extractChunkIndex(utteranceId: String?): Int? {
        if (utteranceId == null) return null
        return utteranceId.substringAfterLast("_").toIntOrNull()
    }

    private fun handleUtteranceError(message: String) {
        Log.w(tag, "Utterance error: $message")
        if (!isSpeakingActive) return

        mainScope.launch {
            // Attempt to advance past problematic chunk
            val nextIndex = currentChunkIndex + 1
            if (nextIndex < chunks.size) {
                currentChunkIndex = nextIndex
                speakCurrentChunk()
            } else {
                if (onChapterCompleteCallback != null) {
                    onChapterCompleteCallback?.invoke()
                } else {
                    _playbackInfo.value = _playbackInfo.value.copy(
                        state = TtsState.IDLE,
                        errorMessage = message
                    )
                }
            }
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

        ensureReady {
            if (!requestAudioFocus()) {
                _playbackInfo.value = TtsPlaybackInfo(
                    state = TtsState.ERROR,
                    errorMessage = "Could not gain audio focus."
                )
                return@ensureReady
            }

            isSpeakingActive = true
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.PLAYING,
                novelId = novelId,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                totalChunks = chunks.size,
                currentChunkIndex = currentChunkIndex
            )

            TtsPlaybackService.start(context)
            speakCurrentChunk()
        }
    }

    private fun ensureReady(onReady: () -> Unit) {
        if (isInitialized && tts != null) {
            onReady()
        } else {
            initEngine(onReady)
        }
    }

    private fun speakCurrentChunk() {
        if (!isSpeakingActive || chunks.isEmpty()) return

        val index = currentChunkIndex
        if (index < 0 || index >= chunks.size) {
            // Reached the end
            if (onChapterCompleteCallback != null) {
                onChapterCompleteCallback?.invoke()
            }
            return
        }

        val chunk = chunks[index]
        if (chunk.isBlank()) {
            currentChunkIndex = index + 1
            speakCurrentChunk()
            return
        }

        val engine = tts
        if (engine == null || !isInitialized) {
            initEngine {
                speakCurrentChunk()
            }
            return
        }

        val utteranceId = "ch_${currentChapterId}_$index"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(tag, "engine.speak returned $result on chunk $index. Reinitializing...")
            initEngine {
                val retryEngine = tts
                retryEngine?.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }
    }

    fun pause() {
        isSpeakingActive = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error during tts.stop on pause: ${e.localizedMessage}")
        }
        _playbackInfo.value = _playbackInfo.value.copy(state = TtsState.PAUSED)
    }

    fun resume() {
        if (chunks.isEmpty()) return

        ensureReady {
            if (requestAudioFocus()) {
                isSpeakingActive = true
                _playbackInfo.value = _playbackInfo.value.copy(state = TtsState.PLAYING)
                TtsPlaybackService.start(context)
                speakCurrentChunk()
            }
        }
    }

    fun stop(triggerServiceStop: Boolean = true) {
        isSpeakingActive = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error during tts.stop: ${e.localizedMessage}")
        }
        abandonAudioFocus()
        _playbackInfo.value = _playbackInfo.value.copy(
            state = TtsState.IDLE,
            currentChunkIndex = 0
        )
        if (triggerServiceStop) {
            TtsPlaybackService.stop(context)
        }
    }

    fun seekToChunk(index: Int) {
        if (chunks.isEmpty()) return
        val target = index.coerceIn(0, chunks.size - 1)
        currentChunkIndex = target
        _playbackInfo.value = _playbackInfo.value.copy(
            currentChunkIndex = target
        )
        if (isSpeakingActive || _playbackInfo.value.state == TtsState.PLAYING) {
            ensureReady {
                speakCurrentChunk()
            }
        }
    }

    fun previousChunk() {
        if (currentChunkIndex > 0) {
            seekToChunk(currentChunkIndex - 1)
        } else {
            skipToPreviousChapter()
        }
    }

    fun nextChunk() {
        if (currentChunkIndex < chunks.size - 1) {
            seekToChunk(currentChunkIndex + 1)
        } else {
            skipToNextChapter()
        }
    }

    fun skipToNextChapter() {
        if (onNextChapterRequested != null) {
            onNextChapterRequested?.invoke()
        } else {
            loadAndPlayAdjacentChapter(forward = true)
        }
    }

    fun skipToPreviousChapter() {
        if (onPrevChapterRequested != null) {
            onPrevChapterRequested?.invoke()
        } else {
            loadAndPlayAdjacentChapter(forward = false)
        }
    }

    private fun loadAndPlayAdjacentChapter(forward: Boolean) {
        val novelId = currentNovelId ?: return
        val chapterId = currentChapterId ?: return
        val app = context.applicationContext as? MtlVaultApplication ?: return

        mainScope.launch {
            val repository = app.novelRepository
            val currentChapter = repository.getChapterByIdSync(chapterId) ?: return@launch
            val targetChapter = if (forward) {
                repository.getNextChapter(novelId, currentChapter.orderIndex)
            } else {
                repository.getPrevChapter(novelId, currentChapter.orderIndex)
            }

            if (targetChapter != null) {
                val rendered = withContext(Dispatchers.Default) {
                    repository.getRenderedChapterContent(novelId, targetChapter)
                }
                playChapter(
                    novelId = novelId,
                    chapterId = targetChapter.id,
                    chapterTitle = targetChapter.title,
                    content = rendered,
                    startFromChunkIndex = 0
                )
            } else {
                stop(triggerServiceStop = true)
            }
        }
    }

    fun isPlaying(): Boolean {
        return _playbackInfo.value.state == TtsState.PLAYING
    }

    fun isPlayingChapter(novelId: String, chapterId: String): Boolean {
        return (_playbackInfo.value.state == TtsState.PLAYING || _playbackInfo.value.state == TtsState.PAUSED) &&
                currentNovelId == novelId &&
                currentChapterId == chapterId
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
        selectedVoiceName = voiceName
        val engine = tts ?: return
        val target = engine.voices?.find { it.name == voiceName }
        if (target != null) {
            engine.voice = target
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return tts?.voices?.toList() ?: emptyList()
    }

    private fun chunkText(text: String): List<String> {
        val rawParagraphs = text.split(Regex("(\r?\n){2,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        val maxLen = 800 // comfortable length per speech chunk

        for (para in rawParagraphs) {
            if (para.length <= maxLen) {
                result.add(para)
            } else {
                // Split large paragraph into sentences or sub-chunks
                val sentences = para.split(Regex("(?<=[.!?؛۔])\\s+"))
                var currentSubChunk = StringBuilder()
                for (sentence in sentences) {
                    if (currentSubChunk.length + sentence.length > maxLen && currentSubChunk.isNotEmpty()) {
                        result.add(currentSubChunk.toString().trim())
                        currentSubChunk = StringBuilder()
                    }
                    if (currentSubChunk.isNotEmpty()) currentSubChunk.append(" ")
                    currentSubChunk.append(sentence)
                }
                if (currentSubChunk.isNotEmpty()) {
                    result.add(currentSubChunk.toString().trim())
                }
            }
        }
        return result
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
        stop(triggerServiceStop = true)
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "TTS shutdown: ${e.localizedMessage}")
        }
        tts = null
        isInitialized = false
    }
}
