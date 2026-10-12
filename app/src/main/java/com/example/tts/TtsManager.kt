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
import kotlinx.coroutines.flow.first
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
            if (content.isBlank()) return emptyList()
            return content.split(Regex("(\r?\n){2,}"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        fun parseParagraphChunks(content: String, maxChars: Int = Int.MAX_VALUE): List<ParagraphChunk> {
            return ParagraphSplitter.parseParagraphChunks(content, maxChars)
        }

        /** Sentences shorter than this are merged with the following sentence into one utterance. */
        const val MIN_SENTENCE_CHARS = 25
        const val MAX_UTTERANCE_CHARS = 3500

        /**
         * Builds one utterance per sentence so the reader can highlight the spoken sentence from
         * onStart, without relying on onRangeStart (which many TTS engines never send).
         * Very short sentences are merged with the next one; extremely long sentences are split.
         */
        internal fun buildSubUtterances(
            paragraphText: String,
            sentences: List<SentenceSpan>,
            startCharOffset: Int
        ): List<SubUtterance> {
            if (sentences.isEmpty()) {
                val remainingText = if (startCharOffset in paragraphText.indices) paragraphText.substring(startCharOffset) else paragraphText
                return if (remainingText.length <= MAX_UTTERANCE_CHARS) {
                    listOf(SubUtterance(remainingText, startCharOffset))
                } else {
                    remainingText.chunked(MAX_UTTERANCE_CHARS).mapIndexed { i, t ->
                        SubUtterance(t, startCharOffset + i * MAX_UTTERANCE_CHARS)
                    }
                }
            }

            val eligible = sentences.filter { it.end > startCharOffset }
            if (eligible.isEmpty()) {
                val lastChar = paragraphText.takeLast(1)
                val base = (paragraphText.length - 1).coerceAtLeast(0)
                return listOf(SubUtterance(if (lastChar.isNotBlank()) lastChar else ".", base))
            }

            val result = mutableListOf<SubUtterance>()
            var i = 0
            while (i < eligible.size) {
                val spanStart = eligible[i].start
                var spanEnd = eligible[i].end
                var j = i
                // Merge short sentences into the next one (keeps the safety limit)
                while (spanEnd - spanStart < MIN_SENTENCE_CHARS &&
                    j + 1 < eligible.size &&
                    eligible[j + 1].end - spanStart <= MAX_UTTERANCE_CHARS
                ) {
                    j++
                    spanEnd = eligible[j].end
                }

                if (spanEnd - spanStart > MAX_UTTERANCE_CHARS) {
                    // A single extremely long sentence: split it, highlighting each part
                    val longText = paragraphText.substring(spanStart, spanEnd)
                    var offset = spanStart
                    for (part in longText.chunked(MAX_UTTERANCE_CHARS)) {
                        result.add(SubUtterance(part, offset))
                        offset += part.length
                    }
                } else {
                    result.add(SubUtterance(paragraphText.substring(spanStart, spanEnd), spanStart, spanEnd))
                }
                i = j + 1
            }
            return result
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

    private val _highlightState = MutableStateFlow(TtsHighlightState())
    val highlightState: StateFlow<TtsHighlightState> = _highlightState.asStateFlow()

    private var chunks: List<String> = emptyList()
    private var chapterSentences: List<List<SentenceSpan>> = emptyList()

    data class SubUtterance(
        val text: String,
        val baseCharOffset: Int,
        val endCharOffset: Int = baseCharOffset + text.length
    )

    @Volatile
    private var currentSubUtterances: List<SubUtterance> = emptyList()
    @Volatile
    private var currentSubUtteranceIndex = 0

    // Where the current paragraph is being spoken from, so resume() continues from the
    // sentence that was playing instead of restarting the paragraph.
    @Volatile
    private var resumeChunkIndex = -1
    @Volatile
    private var resumeCharOffset = 0

    @Volatile
    private var currentChunkIndex = 0

    /** Id of the sentence utterance we most recently asked the engine to speak. Callbacks for any
     *  other id (e.g. a flushed/superseded utterance) are stale and must be ignored. */
    @Volatile private var activeUtteranceId: String? = null
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Focus was interrupted by a phone call, WhatsApp voice message, video, or another app.
                // Stop speech immediately and abandon focus.
                // Never auto-resume; wait until the user explicitly presses Play.
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification sounds or navigation cues: ignore CAN_DUCK so reading is not paused
                AppLogger.d("TtsManager", "audioFocusChangeListener: ignoring AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
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

            if (_playbackInfo.value.state == TtsState.INITIALIZING) {
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = TtsState.IDLE,
                    errorMessage = null
                )
                AppLogger.i("TtsManager", "Playback state changed: IDLE")
            } else if (_playbackInfo.value.state == TtsState.ERROR) {
                _playbackInfo.value = _playbackInfo.value.copy(
                    state = if (isSpeakingActive) TtsState.PLAYING else TtsState.PAUSED,
                    errorMessage = null
                )
                AppLogger.i("TtsManager", "Playback state recovered from ERROR")
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

    data class ParsedUtterance(
        val chunkIndex: Int,
        val subIndex: Int,
        val baseOffset: Int
    )

    private fun parseUtteranceId(utteranceId: String?): ParsedUtterance? {
        if (utteranceId == null || !utteranceId.startsWith("ch_")) return null
        return try {
            val parts = utteranceId.split("_")
            val subIdxPos = parts.indexOf("sub")
            if (subIdxPos >= 3) {
                val chunkIndex = parts[subIdxPos - 1].toInt()
                val subIndex = parts[subIdxPos + 1].toInt()
                val basePos = parts.indexOf("base")
                val baseOffset = if (basePos >= 0 && basePos + 1 < parts.size) parts[basePos + 1].toInt() else 0
                ParsedUtterance(chunkIndex, subIndex, baseOffset)
            } else {
                val chunkIndex = parts.last().toInt()
                ParsedUtterance(chunkIndex, 0, 0)
            }
        } catch (e: Exception) {
            extractChunkIndex(utteranceId)?.let { ParsedUtterance(it, 0, 0) }
        }
    }

    private fun setupProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                AppLogger.d("TtsManager", "onStart: utteranceId=$utteranceId")
                if (utteranceId != null && utteranceId.startsWith("ch_")) {
                    val parsed = parseUtteranceId(utteranceId)
                    val index = parsed?.chunkIndex ?: (extractChunkIndex(utteranceId) ?: currentChunkIndex)
                    currentChunkIndex = index
                    mainScope.launch {
                        _playbackInfo.value = _playbackInfo.value.copy(
                            state = TtsState.PLAYING,
                            currentChunkIndex = index,
                            errorMessage = null
                        )
                        AppLogger.d("TtsManager", "Playback state: PLAYING, chunk=$index")
                    }

                    // Highlight the sentence (sub-utterance) that just started speaking.
                    // This does not depend on onRangeStart, which many engines never send.
                    if (parsed != null && isSpeakingActive) {
                        val sub = currentSubUtterances.getOrNull(parsed.subIndex)
                        if (sub != null && sub.baseCharOffset == parsed.baseOffset) {
                            resumeChunkIndex = index
                            resumeCharOffset = sub.baseCharOffset
                            _highlightState.value = TtsHighlightState(
                                paragraphIndex = index,
                                sentenceStart = sub.baseCharOffset,
                                sentenceEnd = sub.endCharOffset,
                                isSentenceLevel = true
                            )
                            if (AppLogger.isDetailedLogging) {
                                AppLogger.d("TtsManager", "Highlight sentence: chunk=$index span=${sub.baseCharOffset}-${sub.endCharOffset}")
                            }
                        }
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != null && utteranceId.startsWith("ch_")) {
                    val parsed = parseUtteranceId(utteranceId) ?: return
                    handleRangeStart(parsed.chunkIndex, parsed.baseOffset, start, end)
                }
            }

            override fun onDone(utteranceId: String?) {
                AppLogger.d("TtsManager", "onDone: utteranceId=$utteranceId")
                if (!isSpeakingActive) return

                val parsed = parseUtteranceId(utteranceId)
                // A flushed (superseded) utterance also reports onDone. Without this check the stale
                // callback advanced the NEW sentence list, so double-tapping an earlier sentence
                // jumped to the next one.
                if (parsed != null && utteranceId != activeUtteranceId) {
                    AppLogger.d("TtsManager", "onDone ignored (stale): $utteranceId, active=$activeUtteranceId")
                    return
                }
                if (utteranceId != null && utteranceId.startsWith("silence_") &&
                    extractChunkIndex(utteranceId) != currentChunkIndex) {
                    return
                }
                if (parsed != null) {
                    val finishedChunk = parsed.chunkIndex
                    val finishedSub = parsed.subIndex

                    if (finishedSub + 1 < currentSubUtterances.size) {
                        currentSubUtteranceIndex = finishedSub + 1
                        mainScope.launch {
                            speakCurrentSubUtterance(finishedChunk)
                        }
                        return
                    }

                    val silenceId = "silence_${currentChapterId}_$finishedChunk"
                    var silenceQueued = false
                    try {
                        val res = engine.playSilentUtterance(500L, TextToSpeech.QUEUE_ADD, silenceId)
                        silenceQueued = (res == TextToSpeech.SUCCESS)
                    } catch (e: Exception) {
                        Log.w(tag, "playSilentUtterance failed: ${e.localizedMessage}")
                    }

                    if (!silenceQueued) {
                        mainScope.launch {
                            delay(800)
                            if (isSpeakingActive && currentChunkIndex == finishedChunk) {
                                advanceToNextChunk(finishedChunk)
                            }
                        }
                    } else {
                        mainScope.launch {
                            delay(2500)
                            if (isSpeakingActive && currentChunkIndex == finishedChunk && lastAdvancedChunk < finishedChunk) {
                                advanceToNextChunk(finishedChunk)
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
                handleUtteranceError("Synthesis error on $utteranceId", TextToSpeech.ERROR)
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
                handleUtteranceError(msg, errorCode)
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
                prepareAndSpeakChunk(nextIndex, 0)
            } else {
                // Current chapter finished naturally! Do not set IDLE here so service is not prematurely torn down.
                currentChunkIndex = chunks.size
                _playbackInfo.value = _playbackInfo.value.copy(
                    currentChunkIndex = chunks.size,
                    errorMessage = null
                )
                _highlightState.value = TtsHighlightState()
                AppLogger.i("TtsManager", "Chapter complete: novelId=$currentNovelId, chapterId=$currentChapterId, totalChunks=${chunks.size}")
                // Mark as read here, independent of the Reader screen/ViewModel, so it also works when the
                // screen is off or the reader was closed and TTS keeps going in the background.
                markChapterReadInBackground(currentChapterId)
                if (onChapterCompleteCallback != null) {
                    onChapterCompleteCallback?.invoke()
                } else {
                    continueInBackground()
                }
            }
        }
    }

    /**
     * Used when no screen is listening for "chapter finished" (reader closed, TTS running in the
     * background). Honours the user's "continue reading" setting like the reader screen does.
     */
    private fun continueInBackground() {
        val app = context.applicationContext as? MtlVaultApplication
        mainScope.launch {
            val continueReading = try {
                app?.settingsDataStore?.settingsFlow?.first()?.continueReading ?: true
            } catch (e: Exception) {
                true
            }
            if (continueReading) skipToNextChapter() else stop()
        }
    }

    private fun extractChunkIndex(utteranceId: String?): Int? {
        if (utteranceId == null) return null
        return utteranceId.substringAfterLast("_").toIntOrNull()
    }

    private fun handleUtteranceError(message: String, errorCode: Int = TextToSpeech.ERROR) {
        Log.w(tag, "Utterance error encountered: $message (code $errorCode) on chunk $currentChunkIndex")
        // If playback is already paused or not active, the error is an expected result of stopping/interruption.
        // Never change state to IDLE or ERROR.
        if (!isSpeakingActive || _playbackInfo.value.state == TtsState.PAUSED) return

        // Interruption-caused errors (audio output revoked, phone call, etc.) must transition to PAUSED only.
        if (errorCode == TextToSpeech.ERROR_OUTPUT || errorCode == TextToSpeech.ERROR_SERVICE) {
            AppLogger.i("TtsManager", "Utterance error is an audio interruption ($message). Pausing safely.")
            pause()
            return
        }

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
                prepareAndSpeakChunk(nextIndex, 0)
            } else {
                // At chapter end with error, pause safely without setting IDLE or ERROR
                pause()
            }
        }
    }

    @Volatile
    private var currentMaxChars: Int = Int.MAX_VALUE

    fun prepareChapter(
        novelId: String,
        chapterId: String,
        chapterTitle: String,
        content: String,
        maxChars: Int = Int.MAX_VALUE,
        onChapterComplete: (() -> Unit)? = null
    ) {
        currentNovelId = novelId
        currentChapterId = chapterId
        currentChapterTitle = chapterTitle
        currentMaxChars = maxChars
        onChapterCompleteCallback = onChapterComplete
        resumeChunkIndex = -1
        resumeCharOffset = 0
        chunks = parseParagraphs(content, maxChars)
        chapterSentences = chunks.map { SentenceSplitter.splitIntoSentences(it) }
        _playbackInfo.value = _playbackInfo.value.copy(
            novelId = novelId,
            chapterId = chapterId,
            chapterTitle = chapterTitle,
            totalChunks = chunks.size
        )
    }

    fun playChapter(
        novelId: String,
        chapterId: String,
        chapterTitle: String,
        content: String,
        startFromChunkIndex: Int = 0,
        maxChars: Int = Int.MAX_VALUE,
        onChapterComplete: (() -> Unit)? = null
    ) {
        prepareChapter(novelId, chapterId, chapterTitle, content, maxChars, onChapterComplete)
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

    private fun handleRangeStart(chunkIndex: Int, baseOffset: Int, start: Int, end: Int) {
        // Optional refinement only (engines that send word ranges). Never highlights a whole paragraph.
        if (!isSpeakingActive) return
        if (chunkIndex < 0 || chunkIndex >= chunks.size) return
        val paragraphText = chunks[chunkIndex]
        val sentences = chapterSentences.getOrNull(chunkIndex) ?: SentenceSplitter.splitIntoSentences(paragraphText)
        val absoluteOffset = (baseOffset + start).coerceIn(0, paragraphText.length)
        val sentence = SentenceSplitter.findSentence(sentences, absoluteOffset) ?: return

        val current = _highlightState.value
        if (current.paragraphIndex == chunkIndex &&
            current.sentenceStart == sentence.start &&
            current.sentenceEnd == sentence.end &&
            current.isSentenceLevel
        ) {
            return // Update ONLY when the sentence changes, not on every word!
        }

        _highlightState.value = TtsHighlightState(
            paragraphIndex = chunkIndex,
            sentenceStart = sentence.start,
            sentenceEnd = sentence.end,
            isSentenceLevel = true
        )
    }

    private fun prepareAndSpeakChunk(index: Int, startCharOffset: Int = 0) {
        if (!isSpeakingActive || chunks.isEmpty()) return
        if (index < 0 || index >= chunks.size) {
            onChapterCompleteCallback?.invoke()
            return
        }

        val paragraphText = chunks[index]
        val sentences = chapterSentences.getOrNull(index) ?: SentenceSplitter.splitIntoSentences(paragraphText)
        resumeChunkIndex = index
        resumeCharOffset = startCharOffset
        currentSubUtterances = buildSubUtterances(paragraphText, sentences, startCharOffset)
        currentSubUtteranceIndex = 0

        speakCurrentSubUtterance(index)
    }

    private fun speakCurrentSubUtterance(chunkIndex: Int) {
        if (!isSpeakingActive || currentSubUtterances.isEmpty()) return
        val sub = currentSubUtterances.getOrNull(currentSubUtteranceIndex) ?: return

        val engine = tts
        if (engine == null || !isInitialized) {
            AppLogger.w("TtsManager", "speakCurrentSubUtterance: engine not ready, calling initEngine")
            initEngine { speakCurrentSubUtterance(chunkIndex) }
            return
        }

        val utteranceId = "ch_${currentChapterId}_${chunkIndex}_sub_${currentSubUtteranceIndex}_base_${sub.baseCharOffset}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val chunkText = if (sub.text.any { it.isLetterOrDigit() }) sub.text else "."
        activeUtteranceId = utteranceId
        val result = engine.speak(chunkText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        AppLogger.d("TtsManager", "speak: utteranceId=$utteranceId, chunkIndex=$chunkIndex, sub=$currentSubUtteranceIndex, len=${chunkText.length}, result=$result")

        if (result != TextToSpeech.SUCCESS) {
            AppLogger.w("TtsManager", "engine.speak returned $result on chunk $chunkIndex sub $currentSubUtteranceIndex. Recovering engine...")
            if (!isSpeakingActive || _playbackInfo.value.state == TtsState.PAUSED) {
                return
            }
            initEngine {
                if (isSpeakingActive && _playbackInfo.value.state == TtsState.PLAYING) {
                    val retryEngine = tts
                    val retryResult = retryEngine?.speak(chunkText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    AppLogger.i("TtsManager", "retry speak result=$retryResult")
                }
            }
        }
    }

    private fun speakCurrentChunk() {
        prepareAndSpeakChunk(currentChunkIndex, 0)
    }

    fun playFromSentence(paraIndex: Int, startCharOffset: Int) {
        if (chunks.isEmpty() || paraIndex !in chunks.indices) return
        val paragraphText = chunks[paraIndex]
        val sentences = chapterSentences.getOrNull(paraIndex) ?: SentenceSplitter.splitIntoSentences(paragraphText)
        val targetSentence = SentenceSplitter.findSentence(sentences, startCharOffset)
        val effectiveStartOffset = targetSentence?.start ?: startCharOffset

        AppLogger.i("TtsManager", "start-from-sentence: para=$paraIndex, charOffset=$effectiveStartOffset, sentence='${targetSentence?.text?.take(30)}...'")

        currentChunkIndex = paraIndex
        lastAdvancedChunk = paraIndex - 1
        _playbackInfo.value = _playbackInfo.value.copy(
            state = TtsState.PLAYING,
            currentChunkIndex = paraIndex,
            errorMessage = null
        )

        if (targetSentence != null) {
            _highlightState.value = TtsHighlightState(
                paragraphIndex = paraIndex,
                sentenceStart = targetSentence.start,
                sentenceEnd = targetSentence.end,
                isSentenceLevel = true
            )
        }

        ensureReady {
            if (requestAudioFocus()) {
                isSpeakingActive = true
                TtsPlaybackService.start(context)
                prepareAndSpeakChunk(paraIndex, effectiveStartOffset)
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
                val resumeOffset = if (resumeChunkIndex == currentChunkIndex) resumeCharOffset else 0
                prepareAndSpeakChunk(currentChunkIndex, resumeOffset)
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
        _highlightState.value = TtsHighlightState()
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

    private fun markChapterReadInBackground(chapterId: String?) {
        val id = chapterId ?: return
        val app = context.applicationContext as? MtlVaultApplication ?: return
        mainScope.launch {
            try {
                app.novelRepository.setChapterRead(id, true)
                AppLogger.i("TtsManager", "Marked chapter as read: $id")
            } catch (e: Exception) {
                AppLogger.w("TtsManager", "Failed to mark chapter read: ${e.message}")
            }
        }
    }

    fun skipToNextChapter() {
        // Skipping forward (notification/headset "next") counts as finishing the chapter,
        // same as the on-screen Next button does.
        markChapterReadInBackground(currentChapterId)
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

    fun releaseEngineIfIdle() {
        if (_playbackInfo.value.state == TtsState.IDLE) {
            AppLogger.i("TtsManager", "releaseEngineIfIdle: releasing TTS engine while idle")
            try {
                tts?.shutdown()
            } catch (e: Exception) {
                AppLogger.w("TtsManager", "TTS shutdown warning: ${e.localizedMessage}")
            }
            tts = null
            isInitialized = false
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
