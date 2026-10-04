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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "tts_media_channel"
        const val NOTIFICATION_ID = 1001
        const val CHUNK_UNIT_MS = 10_000L

        /**
         * Single source of truth for splitting text into paragraphs across ReaderScreen and TtsManager.
         * Ensures a strict 1-to-1 match between UI paragraphs and TTS chunks so highlights are never misaligned.
         */
        fun parseParagraphs(content: String): List<String> {
            return content.split(Regex("(\r?\n){2,}"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }

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
                // Apply AudioAttributes for media playback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttributes)
                }

                // Default to US English or default system locale with fallback
                val defaultLocale = Locale.getDefault()
                val langResult = engine.setLanguage(defaultLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }

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
                errorMessage = "TTS initialization failed (code $status)."
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
                        currentChunkIndex = index,
                        errorMessage = null
                    )
                }
            }

            override fun onDone(utteranceId: String?) {
                val finishedIndex = extractChunkIndex(utteranceId) ?: currentChunkIndex
                if (!isSpeakingActive) return

                mainScope.launch {
                    // Small yield to let audio sink settle
                    delay(30)
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
                handleUtteranceError("Synthesis error on $utteranceId")
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
        Log.w(tag, "Utterance error encountered: $message on chunk $currentChunkIndex")
        if (!isSpeakingActive) return

        mainScope.launch {
            // Automatically advance past problematic chunk instead of freezing
            val nextIndex = currentChunkIndex + 1
            if (nextIndex < chunks.size) {
                currentChunkIndex = nextIndex
                _playbackInfo.value = _playbackInfo.value.copy(
                    currentChunkIndex = nextIndex,
                    errorMessage = null
                )
                delay(80)
                speakCurrentChunk()
            } else {
                if (onChapterCompleteCallback != null) {
                    onChapterCompleteCallback?.invoke()
                } else {
                    skipToNextChapter()
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

        // Strict 1-to-1 match with UI paragraphs
        chunks = parseParagraphs(content)
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

            configureLanguageForText(content.take(600))

            isSpeakingActive = true
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.PLAYING,
                novelId = novelId,
                chapterId = chapterId,
                chapterTitle = chapterTitle,
                totalChunks = chunks.size,
                currentChunkIndex = currentChunkIndex,
                errorMessage = null
            )

            TtsPlaybackService.start(context)
            speakCurrentChunk()
        }
    }

    private fun configureLanguageForText(sample: String) {
        val engine = tts ?: return
        val detected = detectLocaleForText(sample)
        var result = engine.setLanguage(detected)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
        }
    }

    private fun detectLocaleForText(sample: String): Locale {
        var urduArabicCount = 0
        var latinCount = 0
        for (ch in sample) {
            val code = ch.code
            if ((code in 0x0600..0x06FF) || (code in 0x0750..0x077F) || (code in 0xFB50..0xFDFF) || (code in 0xFE70..0xFEFF)) {
                urduArabicCount++
            } else if ((code in 0x0041..0x005A) || (code in 0x0061..0x007A)) {
                latinCount++
            }
        }
        return if (urduArabicCount > latinCount && urduArabicCount > 10) {
            Locale("ur")
        } else {
            Locale.US
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
            if (onChapterCompleteCallback != null) {
                onChapterCompleteCallback?.invoke()
            }
            return
        }

        val rawChunk = chunks[index]
        val sanitized = sanitizeSpeechText(rawChunk)
        // If a paragraph contains only symbols (e.g. "***" or "---"), speak a brief silent period
        // so that onStart highlights this exact paragraph, and onDone proceeds to the next without index drift.
        val chunk = if (sanitized.any { it.isLetterOrDigit() }) {
            sanitized
        } else {
            "."
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
            Log.w(tag, "engine.speak returned $result on chunk $index. Recovering engine...")
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
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = TtsState.PLAYING,
                    errorMessage = null
                )
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
            currentChunkIndex = 0,
            errorMessage = null
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

    fun sanitizeSpeechText(raw: String): String {
        var s = raw
        // Strip residual HTML/XML tags
        s = s.replace(Regex("<[^>]+>"), " ")
        // Unescape entities
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", " and ")
            .replace("&lt;", " ")
            .replace("&gt;", " ")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", " — ")
            .replace("&ndash;", " – ")
            .replace("&hellip;", " ... ")
        // Remove unprintable control characters and zero-width spaces
        s = s.filter { ch ->
            val c = ch.code
            c == 0xA || c == 0xD || c == 0x9 ||
                    (c in 0x20..0xD7FF) ||
                    (c in 0xE000..0xFFFD)
        }
        // Remove runs of decorative symbols that make TTS engines fail with ERROR_SYNTHESIS
        s = s.replace(Regex("[-*=_~]{2,}"), " ")
        return s.trim()
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
