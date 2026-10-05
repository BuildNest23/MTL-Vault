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
import com.example.logging.AppLogger
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
        fun parseParagraphs(content: String, maxChars: Int = Int.MAX_VALUE): List<String> {
            return ParagraphSplitter.parseParagraphChunks(content, maxChars).map { it.text }
        }

        fun parseParagraphChunks(content: String, maxChars: Int = Int.MAX_VALUE): List<ParagraphChunk> {
            return ParagraphSplitter.parseParagraphChunks(content, maxChars)
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
    @Volatile
    private var lastAdvancedChunk = -1
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
        AppLogger.i("TtsManager", "audioFocusChangeListener: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Focus was interrupted by a phone call, WhatsApp voice message, video, or another app.
                // Stop speech immediately and abandon focus.
                // Never auto-resume; wait until the user explicitly presses Play.
                pause()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Strictly do NOT auto-resume playback when another app (like WhatsApp) finishes.
                // Playback must only resume when the user explicitly presses Play or double-taps a paragraph.
            }
        }
    }

    init {
        val initialInfo = TtsPlaybackInfo(state = TtsState.INITIALIZING)
        _playbackInfo.value = initialInfo
        AppLogger.i("TtsManager", "init: set state to INITIALIZING")
        initEngine()
    }

    private fun initEngine(onReady: (() -> Unit)? = null) {
        if (onReady != null) {
            pendingInitCallbacks.add(onReady)
        }
        AppLogger.i("TtsManager", "initEngine called: isInitializing=$isInitializing")
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
            AppLogger.e("TtsManager", "Failed to create TextToSpeech instance", e)
            val errInfo = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "Failed to initialize TTS engine: ${e.localizedMessage}"
            )
            _playbackInfo.value = errInfo
            AppLogger.i("TtsManager", "Playback state: ERROR")
        }
    }

    override fun onInit(status: Int) {
        isInitializing = false
        val defaultEng = tts?.defaultEngine ?: "unknown"
        AppLogger.i("TtsManager", "onInit received: status=$status, defaultEngine=$defaultEng")
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
                AppLogger.i("TtsManager", "setLanguage($defaultLocale) result=$langResult")
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val fallbackResult = engine.setLanguage(Locale.US)
                    AppLogger.i("TtsManager", "setLanguage(Locale.US) fallback result=$fallbackResult")
                }

                engine.setSpeechRate(speechRate)
                engine.setPitch(speechPitch)

                if (selectedVoiceName != null) {
                    engine.voices?.find { it.name == selectedVoiceName }?.let {
                        engine.voice = it
                        AppLogger.i("TtsManager", "Restored voice: ${it.name}")
                    }
                }

                setupProgressListener(engine)
            }

            if (_playbackInfo.value.state == TtsState.INITIALIZING || _playbackInfo.value.state == TtsState.ERROR) {
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = TtsState.IDLE,
                    errorMessage = null
                )
                AppLogger.i("TtsManager", "Playback state changed: IDLE")
            }

            // Execute pending callbacks
            val callbacks = ArrayList(pendingInitCallbacks)
            pendingInitCallbacks.clear()
            for (cb in callbacks) {
                try {
                    cb.invoke()
                } catch (e: Exception) {
                    AppLogger.e("TtsManager", "Error in pending init callback", e)
                }
            }
        } else {
            isInitialized = false
            _playbackInfo.value = TtsPlaybackInfo(
                state = TtsState.ERROR,
                errorMessage = "TTS initialization failed (code $status)."
            )
            AppLogger.e("TtsManager", "Playback state: ERROR (init code $status)")
        }
    }

    private fun setupProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                AppLogger.d("TtsManager", "onStart: utteranceId=$utteranceId")
                // Only update active chunk index & UI highlight when speech of the paragraph actually starts!
                // Never update highlight on silence intervals.
                if (utteranceId != null && utteranceId.startsWith("ch_")) {
                    val index = extractChunkIndex(utteranceId) ?: currentChunkIndex
                    currentChunkIndex = index
                    mainScope.launch {
                        _playbackInfo.value = _playbackInfo.value.copy(
                            state = TtsState.PLAYING,
                            currentChunkIndex = index,
                            errorMessage = null
                        )
                        AppLogger.d("TtsManager", "Playback state: PLAYING, chunk=$index")
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                AppLogger.d("TtsManager", "onDone: utteranceId=$utteranceId")
                if (!isSpeakingActive) return

                if (utteranceId != null && utteranceId.startsWith("ch_")) {
                    val finishedIndex = extractChunkIndex(utteranceId) ?: currentChunkIndex
                    val silenceId = "silence_${currentChapterId}_$finishedIndex"
                    var silenceQueued = false
                    try {
                        // Queue 500ms silence after speech utterance using QUEUE_ADD.
                        // Because QUEUE_ADD is used, the device hardware drains and finishes playing
                        // every last word of the paragraph, then plays the silence before onDone(silenceId) fires.
                        val res = engine.playSilentUtterance(500L, TextToSpeech.QUEUE_ADD, silenceId)
                        silenceQueued = (res == TextToSpeech.SUCCESS)
                    } catch (e: Exception) {
                        Log.w(tag, "playSilentUtterance failed: ${e.localizedMessage}")
                    }

                    if (!silenceQueued) {
                        // Fallback for TTS engines that do not support playSilentUtterance:
                        // Delay 800ms to allow hardware AudioTrack buffer to finish playing the last words
                        mainScope.launch {
                            delay(800)
                            if (isSpeakingActive && currentChunkIndex == finishedIndex) {
                                advanceToNextChunk(finishedIndex)
                            }
                        }
                    } else {
                        // Watchdog timer (2500ms) in case the TTS engine drops the silence onDone callback
                        mainScope.launch {
                            delay(2500)
                            if (isSpeakingActive && currentChunkIndex == finishedIndex && lastAdvancedChunk < finishedIndex) {
                                advanceToNextChunk(finishedIndex)
                            }
                        }
                    }
                } else if (utteranceId != null && utteranceId.startsWith("silence_")) {
                    val finishedIndex = extractChunkIndex(utteranceId) ?: currentChunkIndex
                    advanceToNextChunk(finishedIndex)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                AppLogger.e("TtsManager", "onError deprecated: utteranceId=$utteranceId")
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
                AppLogger.e("TtsManager", "onError: utteranceId=$utteranceId, code=$errorCode, msg=$msg")
                handleUtteranceError(msg)
            }
        })
    }

    private fun advanceToNextChunk(finishedIndex: Int) {
        if (!isSpeakingActive) return
        if (finishedIndex <= lastAdvancedChunk && lastAdvancedChunk != -1) return
        lastAdvancedChunk = finishedIndex

        mainScope.launch {
            val nextIndex = finishedIndex + 1
            if (nextIndex < chunks.size) {
                currentChunkIndex = nextIndex
                speakCurrentChunk()
            } else {
                // Current chapter finished naturally!
                currentChunkIndex = chunks.size
                _playbackInfo.value = _playbackInfo.value.copy(
                    currentChunkIndex = chunks.size
                )
                AppLogger.i("TtsManager", "Chapter complete: novelId=$currentNovelId, chapterId=$currentChapterId, totalChunks=${chunks.size}")
                if (onChapterCompleteCallback != null) {
                    onChapterCompleteCallback?.invoke()
                } else {
                    skipToNextChapter()
                }
            }
        }
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

    @Volatile
    private var currentMaxChars: Int = Int.MAX_VALUE

    fun playChapter(
        novelId: String,
        chapterId: String,
        chapterTitle: String,
        content: String,
        startFromChunkIndex: Int = 0,
        maxChars: Int = Int.MAX_VALUE,
        onChapterComplete: (() -> Unit)? = null
    ) {
        currentNovelId = novelId
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentMaxChars = maxChars
        onChapterCompleteCallback = onChapterComplete

        // Strict 1-to-1 match with UI paragraphs
        chunks = parseParagraphs(content, maxChars)
        currentChunkIndex = startFromChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        lastAdvancedChunk = currentChunkIndex - 1

        val longestChunk = chunks.maxOfOrNull { it.length } ?: 0
        AppLogger.i("TtsManager", "playChapter: novelId=$novelId, chapterId=$chapterId, title=$chapterTitle, chunks=${chunks.size}, maxChunkLen=$longestChunk, startIndex=$startFromChunkIndex, maxChars=$maxChars")

        if (chunks.isEmpty()) {
            AppLogger.w("TtsManager", "playChapter: chunks empty for chapterId=$chapterId")
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
                AppLogger.e("TtsManager", "playChapter: Audio focus request denied")
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
            AppLogger.i("TtsManager", "Playback state: PLAYING, chunk=$currentChunkIndex/${chunks.size}")

            TtsPlaybackService.start(context)
            speakCurrentChunk()
        }
    }

    private fun configureLanguageForText(sample: String) {
        val engine = tts ?: return
        val detected = detectLocaleForText(sample)
        var result = engine.setLanguage(detected)
        AppLogger.i("TtsManager", "configureLanguageForText: detected=$detected, setLanguage result=$result")
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.US)
            AppLogger.i("TtsManager", "Fallback setLanguage(Locale.US) result=$result")
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val sysRes = engine.setLanguage(Locale.getDefault())
                AppLogger.i("TtsManager", "Fallback setLanguage(getDefault) result=$sysRes")
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
            AppLogger.w("TtsManager", "speakCurrentChunk: engine not ready, calling initEngine")
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
        AppLogger.d("TtsManager", "speak: utteranceId=$utteranceId, chunkIndex=$index, len=${chunk.length}, result=$result")

        if (result != TextToSpeech.SUCCESS) {
            AppLogger.w("TtsManager", "engine.speak returned $result on chunk $index. Recovering engine...")
            initEngine {
                val retryEngine = tts
                val retryResult = retryEngine?.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                AppLogger.i("TtsManager", "retry speak result=$retryResult")
            }
        }
    }

    fun pause() {
        AppLogger.i("TtsManager", "pause() called")
        isSpeakingActive = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            AppLogger.w("TtsManager", "Error during tts.stop on pause: ${e.localizedMessage}")
        }
        abandonAudioFocus()
        _playbackInfo.value = _playbackInfo.value.copy(state = TtsState.PAUSED)
        AppLogger.i("TtsManager", "Playback state: PAUSED")
    }

    fun resume() {
        AppLogger.i("TtsManager", "resume() called")
        if (chunks.isEmpty()) return

        ensureReady {
            if (requestAudioFocus()) {
                isSpeakingActive = true
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = TtsState.PLAYING,
                    errorMessage = null
                )
                AppLogger.i("TtsManager", "Playback state: PLAYING, chunk=$currentChunkIndex")
                TtsPlaybackService.start(context)
                speakCurrentChunk()
            } else {
                AppLogger.w("TtsManager", "resume() failed: audio focus denied")
            }
        }
    }

    fun stop(triggerServiceStop: Boolean = true) {
        AppLogger.i("TtsManager", "stop() called (triggerServiceStop=$triggerServiceStop)")
        isSpeakingActive = false
        lastAdvancedChunk = -1
        try {
            tts?.stop()
        } catch (e: Exception) {
            AppLogger.w("TtsManager", "Error during tts.stop: ${e.localizedMessage}")
        }
        abandonAudioFocus()
        _playbackInfo.value = _playbackInfo.value.copy(
            state = TtsState.IDLE,
            currentChunkIndex = 0,
            errorMessage = null
        )
        AppLogger.i("TtsManager", "Playback state: IDLE")
        if (triggerServiceStop) {
            TtsPlaybackService.stop(context)
        }
    }

    fun seekToChunk(index: Int) {
        if (chunks.isEmpty()) return
        val target = index.coerceIn(0, chunks.size - 1)
        AppLogger.i("TtsManager", "seekToChunk: target=$target")
        lastAdvancedChunk = target - 1
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
                    startFromChunkIndex = 0,
                    maxChars = currentMaxChars
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
        if (s.isEmpty()) return ""

        // 1. Safety check that input isn't absurdly long (truncate per chunk if > 4000 chars)
        if (s.length > 4000) {
            s = s.take(4000)
        }

        // 2. Strip residual HTML/XML tags
        s = s.replace(Regex("<[^>]+>"), " ")

        // 3. Unescape entities
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

        // 4. Remove unprintable control characters and zero-width spaces
        s = s.filter { ch ->
            val c = ch.code
            c == 0xA || c == 0xD || c == 0x9 ||
                    (c in 0x20..0xD7FF) ||
                    (c in 0xE000..0xFFFD)
        }

        // 5. Remove runs of repeated punctuation (e.g. ".....", "!!!!!", "????", ",,,,")
        s = s.replace(Regex("\\.{2,}"), ".")
        s = s.replace(Regex("!{2,}"), "!")
        s = s.replace(Regex("\\?{2,}"), "?")
        s = s.replace(Regex(",{2,}"), ",")

        // 6. Remove runs of decorative symbols that make TTS engines fail with ERROR_SYNTHESIS
        s = s.replace(Regex("[-*=_~]{2,}"), " ")

        // 7. Common abbreviations so TTS doesn't stumble
        s = replaceCommonAbbreviations(s)

        // 8. Roman numerals in chapter/volume titles or parentheses (e.g. "Chapter IV" -> "Chapter 4")
        s = replaceRomanNumerals(s)

        return s.trim()
    }

    private fun replaceCommonAbbreviations(text: String): String {
        var s = text
        s = s.replace(Regex("""\bMr\."""), "Mister")
        s = s.replace(Regex("""\bMrs\."""), "Missus")
        s = s.replace(Regex("""\bMs\."""), "Miss")
        s = s.replace(Regex("""\bDr\."""), "Doctor")
        s = s.replace(Regex("""\bProf\."""), "Professor")
        s = s.replace(Regex("""\bSt\."""), "Saint")
        s = s.replace(Regex("""\bvs\.""", RegexOption.IGNORE_CASE), "versus")
        s = s.replace(Regex("""\betc\.""", RegexOption.IGNORE_CASE), "etcetera")
        s = s.replace(Regex("""\be\.g\.""", RegexOption.IGNORE_CASE), "for example")
        s = s.replace(Regex("""\bi\.e\.""", RegexOption.IGNORE_CASE), "that is")
        s = s.replace(Regex("""\bCh\.(\s*\d+)""", RegexOption.IGNORE_CASE), "Chapter $1")
        s = s.replace(Regex("""\bVol\.(\s*\d+)""", RegexOption.IGNORE_CASE), "Volume $1")
        s = s.replace(Regex("""\bNo\.(\s*\d+)""", RegexOption.IGNORE_CASE), "Number $1")
        return s
    }

    private fun replaceRomanNumerals(text: String): String {
        val headingRegex = Regex("""(?i)\b(Chapter|Volume|Vol|Book|Part|Act|Scene|Section|Episode)\s+([IVXLCDM]+)\b""")
        var s = headingRegex.replace(text) { match ->
            val prefix = match.groupValues[1]
            val roman = match.groupValues[2]
            val dec = romanToDecimal(roman)
            if (dec != null) "$prefix $dec" else match.value
        }

        val parenRegex = Regex("""\(([IVXLCDM]+)\)""")
        s = parenRegex.replace(s) { match ->
            val roman = match.groupValues[1]
            val dec = romanToDecimal(roman)
            if (dec != null) "($dec)" else match.value
        }
        return s
    }

    private fun romanToDecimal(roman: String): Int? {
        val upper = roman.uppercase()
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        var prev = 0
        for (i in upper.length - 1 downTo 0) {
            val v = values[upper[i]] ?: return null
            if (v < prev) {
                total -= v
            } else {
                total += v
                prev = v
            }
        }
        return if (total in 1..4999) total else null
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        AppLogger.i("TtsManager", "requestAudioFocus result: granted=$granted")
        return granted
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        AppLogger.i("TtsManager", "abandonAudioFocus called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun shutdown() {
        AppLogger.i("TtsManager", "shutdown called")
        stop(triggerServiceStop = true)
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            AppLogger.w("TtsManager", "TTS shutdown warning: ${e.localizedMessage}")
        }
        tts = null
        isInitialized = false
    }
}
