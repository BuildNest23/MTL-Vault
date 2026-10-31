package com.example.tts.edge

import android.content.Context
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.media.MediaPlayer
import android.os.PowerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Speaks one sentence at a time with Edge neural voices, a bit like Android's TextToSpeech.speak():
 * calling [speak] cancels whatever was playing, and the callback reports start / done / error.
 *
 * Audio is downloaded as MP3, cached on disk (so re-reading a sentence is instant and works
 * offline) and played with MediaPlayer.
 *
 * [syncQueue] keeps a background queue that downloads the whole rest of the chapter in reading
 * order, starting with the sentence being spoken, so playback almost never waits for the network.
 * When the chapter (or the voice / speed / pitch) changes, audio of the previous chapter is deleted.
 */
private const val QUEUE_PARALLEL = 3
private const val QUEUE_MAX_FAILURES = 3

/** How many sentences are downloaded ahead when the full-chapter download is switched off. */
private const val RESTRICTED_LOOKAHEAD = 3

/** What the download ring in the player bar shows about the current chapter. */
data class EdgeProgress(
    /** Total sentences the voice will speak in this chapter (0 = nothing to show). */
    val total: Int = 0,
    /** Sentences whose audio is on disk. */
    val done: Int = 0,
    /** Disk space used by this chapter's audio. */
    val bytes: Long = 0L,
    /** Position of the sentence being spoken, 0..total. */
    val currentIndex: Int = 0,
    /** The download gave up (probably offline). It tries again at the next sentence. */
    val stalled: Boolean = false,
    /** Only a few sentences ahead are downloaded because "Wi-Fi only" is on and Wi-Fi is not available. */
    val waitingForWifi: Boolean = false
)

class EdgeSpeaker(
    context: Context,
    private val progressSink: MutableStateFlow<EdgeProgress>? = null
) {

    interface Callback {
        fun onStart(id: String)
        fun onDone(id: String)
        fun onError(id: String, message: String)
    }

    private data class Params(val voice: String, val rate: String, val pitch: String)

    private val appContext = context.applicationContext
    private val client = EdgeTtsClient()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val netGate = Semaphore(4)
    private val cacheDir = File(appContext.cacheDir, "edge_tts").apply { mkdirs() }
    private val inFlight = HashMap<String, Deferred<File>>()

    @Volatile
    private var generation = 0
    private var job: Job? = null
    private var player: MediaPlayer? = null

    // "Standby" player: the NEXT sentence's MediaPlayer, already prepared and waiting. Preparing a
    // MediaPlayer takes ~100-300 ms, which was audible as a gap between sentences. All of these
    // are only touched on the main thread.
    private var standby: MediaPlayer? = null
    private var standbyPath: String? = null
    private var standbyReady = false
    private var warmJob: Job? = null

    // Chapter download queue (see syncQueue). queueChapter / queueParams are main-thread only;
    // trackedFiles is guarded by its own lock and holds the cache files that belong to the chapter.
    @Volatile private var queueJob: Job? = null
    private var queueChapter: String? = null
    private var queueParams: Params? = null
    private val trackedFiles = HashSet<String>()
    private var pendingCleanup = false

    /** When true, the full-chapter download only runs on an unmetered network (Wi-Fi). */
    @Volatile
    var wifiOnly = false

    // The chapter the user cleared by hand: only a few sentences ahead are downloaded for it.
    private var suspendedChapter: String? = null

    // Data behind the progress ring.
    @Volatile private var progressFiles: List<File> = emptyList()
    @Volatile private var progressStart = 0
    @Volatile private var progressStalled = false
    @Volatile private var progressWaitingForWifi = false
    private var progressJob: Job? = null

    // Small file that remembers which cached sentences belong to the chapter being read, so the
    // old chapter can still be cleaned up after the app was closed or killed.
    private val manifestFile = File(cacheDir, "chapter.manifest")

    init {
        loadManifest()
        ioScope.launch { pruneCache() }
    }

    /** Speaks [text]. Anything that is currently playing is stopped first. */
    fun speak(id: String, text: String, voice: String, rate: Float, pitch: Float, callback: Callback) {
        stop()
        val gen = generation

        if (text.none { it.isLetterOrDigit() }) {
            // Nothing the voice could say (just punctuation): treat as instantly finished.
            mainScope.launch { if (gen == generation) callback.onDone(id) }
            return
        }

        val params = Params(voice, rateString(rate), pitchString(pitch))
        val extraSpeed = if (rate > 2f) rate / 2f else 1f

        // If this sentence was pre-warmed, take its ready player right now (before anything else
        // can replace it).
        var warm: MediaPlayer? = claimStandby(cacheFile(text, params))

        val launched = mainScope.launch {
            try {
                val file = awaitAudio(text, params)
                if (gen != generation) return@launch
                val handedOver = warm
                warm = null
                play(file, id, gen, extraSpeed, callback, handedOver)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) {
                    callback.onError(id, e.message ?: e.javaClass.simpleName)
                }
            }
        }
        // If the job ended without using the warm player, don't leak it.
        launched.invokeOnCompletion {
            warm?.let { unused -> releasePlayer(unused) }
            warm = null
        }
        job = launched
    }

    /**
     * Keeps the chapter downloading in the background, in reading order.
     * [texts] is the WHOLE chapter in reading order and [startIndex] is the sentence being spoken
     * now. Everything from there to the end is downloaded. Call it for every sentence: it restarts
     * the queue from the current position (so jumps, voice / speed changes and new chapters are
     * handled automatically), and sentences that are already on disk are skipped in a blink.
     *
     * When [chapterKey] or the voice / rate / pitch changes, audio of the previous chapter that
     * is not needed any more is deleted. The same call keeps the progress ring up to date.
     */
    fun syncQueue(
        chapterKey: String, texts: List<String>, startIndex: Int,
        voice: String, rate: Float, pitch: Float
    ) {
        val params = Params(voice, rateString(rate), pitchString(pitch))
        if (chapterKey != queueChapter) suspendedChapter = null
        if (chapterKey != queueChapter || params != queueParams) {
            synchronized(trackedFiles) { pendingCleanup = true }
        }
        queueChapter = chapterKey
        queueParams = params
        val clearedByUser = chapterKey == suspendedChapter
        queueJob?.cancel()
        queueJob = ioScope.launch {
            val speakable = ArrayList<String>(texts.size)
            val files = ArrayList<File>(texts.size)
            var startSpeakable = 0
            for ((i, t) in texts.withIndex()) {
                if (t.none { it.isLetterOrDigit() }) continue
                if (i < startIndex) startSpeakable++
                speakable.add(t)
                files.add(cacheFile(t, params))
            }
            val names = HashSet<String>(files.size * 2)
            for (f in files) names.add(f.name)
            synchronized(trackedFiles) {
                var cleaned = false
                if (pendingCleanup) {
                    pendingCleanup = false
                    cleaned = true
                    for (name in trackedFiles) {
                        if (name !in names) File(cacheDir, name).delete()
                    }
                    trackedFiles.clear()
                }
                val grew = trackedFiles.addAll(names)
                if (cleaned || grew) saveManifest(chapterKey, params)
            }

            val waitingForWifi = wifiOnly && !isUnmetered()
            progressFiles = files
            progressStart = startSpeakable
            progressStalled = false
            progressWaitingForWifi = waitingForWifi

            val end = if (waitingForWifi || clearedByUser) {
                minOf(files.size, startSpeakable + RESTRICTED_LOOKAHEAD)
            } else {
                files.size
            }
            runQueue(speakable, files, params, startSpeakable, end)
        }
        startProgressLoop()
    }

    /** Stops the background chapter download (the files already downloaded are kept). */
    fun stopQueue() {
        queueJob?.cancel()
        queueJob = null
    }

    private suspend fun runQueue(texts: List<String>, files: List<File>, params: Params, from: Int, to: Int) {
        val gate = Semaphore(QUEUE_PARALLEL)
        val failures = AtomicInteger(0)
        coroutineScope {
            for (i in from until to) {
                val file = files[i]
                if (file.exists() && file.length() > 0L) continue
                gate.acquire()
                if (failures.get() >= QUEUE_MAX_FAILURES) {
                    // Probably offline. The queue is restarted at the next sentence.
                    gate.release()
                    progressStalled = true
                    break
                }
                launch {
                    try {
                        startSynthesis(texts[i], params).await()
                        failures.set(0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                    } finally {
                        gate.release()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ progress ring

    /** Publishes progress twice a second while the queue is working, then once more when it ends. */
    private fun startProgressLoop() {
        if (progressSink == null) return
        progressJob?.cancel()
        progressJob = ioScope.launch {
            while (true) {
                val running = queueJob?.isActive == true
                publishProgress()
                if (!running) break
                delay(500)
            }
        }
    }

    private fun publishProgress() {
        val sink = progressSink ?: return
        val files = progressFiles
        var done = 0
        var bytes = 0L
        val seen = HashSet<String>()
        for (f in files) {
            val len = f.length()
            if (len > 0L) {
                done++
                if (seen.add(f.name)) bytes += len
            }
        }
        sink.value = EdgeProgress(
            total = files.size,
            done = done,
            bytes = bytes,
            currentIndex = progressStart.coerceIn(0, files.size),
            stalled = progressStalled && done < files.size,
            waitingForWifi = progressWaitingForWifi && done < files.size
        )
    }

    private fun isUnmetered(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return true // offline: the normal failure handling deals with it
            val caps = cm.getNetworkCapabilities(net) ?: return true
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Exception) {
            true
        }
    }

    /** Total size of all downloaded Edge audio. Reads the disk, so call it off the main thread. */
    fun cacheSizeBytes(): Long {
        return try {
            (cacheDir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".mp3") }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /** Deletes the audio of the chapter being read. Only a few sentences ahead are fetched for it afterwards. */
    fun clearChapter() {
        val key = queueChapter
        val params = queueParams
        suspendedChapter = key
        queueJob?.cancel()
        queueJob = null
        ioScope.launch {
            synchronized(trackedFiles) {
                for (name in trackedFiles) File(cacheDir, name).delete()
                trackedFiles.clear()
                if (key != null && params != null) saveManifest(key, params)
            }
            publishProgress()
        }
    }

    /** Reads the saved chapter list. Runs once at start-up (the file is small). */
    private fun loadManifest() {
        try {
            if (!manifestFile.exists()) return
            val lines = manifestFile.readLines()
            if (lines.size < 2) return
            val bits = lines[1].split("|")
            if (bits.size != 3) return
            queueChapter = lines[0]
            queueParams = Params(bits[0], bits[1], bits[2])
            synchronized(trackedFiles) {
                for (i in 2 until lines.size) {
                    if (lines[i].isNotBlank()) trackedFiles.add(lines[i].trim())
                }
            }
        } catch (e: Exception) {
            // A damaged manifest only means old audio is cleaned up by the size limit instead.
        }
    }

    /** Saves which chapter / settings the cached sentences belong to. Call while holding the trackedFiles lock. */
    private fun saveManifest(chapterKey: String, params: Params) {
        try {
            val tmp = File(cacheDir, "chapter.manifest.tmp")
            val sb = StringBuilder()
            sb.append(chapterKey).append('\n')
            sb.append(params.voice).append('|').append(params.rate).append('|').append(params.pitch).append('\n')
            for (name in trackedFiles) sb.append(name).append('\n')
            tmp.writeText(sb.toString())
            if (!tmp.renameTo(manifestFile)) {
                manifestFile.writeText(sb.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // best effort
        }
    }

    /** Stops the background chapter download (the files already downloaded are kept). */
    fun stopQueue() {
        queueJob?.cancel()
        queueJob = null
    }

    private suspend fun runQueue(texts: List<String>, files: List<File>, params: Params) {
        val gate = Semaphore(QUEUE_PARALLEL)
        val failures = AtomicInteger(0)
        coroutineScope {
            for (i in texts.indices) {
                val file = files[i]
                if (file.exists() && file.length() > 0L) continue
                gate.acquire()
                if (failures.get() >= QUEUE_MAX_FAILURES) {
                    // Probably offline. The queue is restarted at the next sentence.
                    gate.release()
                    break
                }
                launch {
                    try {
                        startSynthesis(texts[i], params).await()
                        failures.set(0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                    } finally {
                        gate.release()
                    }
                }
            }
        }
    }

    /** Prepares a player for the very next sentence so it can start with no delay. */
    fun warmNext(text: String, voice: String, rate: Float, pitch: Float) {
        if (text.none { it.isLetterOrDigit() }) return
        warmUp(text, Params(voice, rateString(rate), pitchString(pitch)))
    }

    /** Waits for [text]'s audio, then prepares a MediaPlayer for it and keeps it on standby. */
    private fun warmUp(text: String, params: Params) {
        val target = cacheFile(text, params).absolutePath
        if (standbyPath == target) return
        warmJob?.cancel()
        warmJob = mainScope.launch {
            try {
                val file = awaitAudio(text, params)
                if (standbyPath == file.absolutePath) return@launch
                discardStandby()
                val mp = MediaPlayer()
                standby = mp
                standbyPath = file.absolutePath
                standbyReady = false
                try {
                    configurePlayer(mp)
                    mp.setDataSource(file.absolutePath)
                    mp.setOnPreparedListener { p -> if (standby === p) standbyReady = true }
                    mp.setOnErrorListener { p, _, _ ->
                        if (standby === p) discardStandby() else releasePlayer(p)
                        true
                    }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    discardStandby()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Warm-up is an optimisation only; speak() still works without it.
            }
        }
    }

    private fun claimStandby(file: File): MediaPlayer? {
        val mp = standby ?: return null
        if (!standbyReady || standbyPath != file.absolutePath) return null
        standby = null
        standbyPath = null
        standbyReady = false
        mp.setOnPreparedListener(null)
        mp.setOnErrorListener(null)
        return mp
    }

    private fun discardStandby() {
        val mp = standby
        standby = null
        standbyPath = null
        standbyReady = false
        if (mp != null) releasePlayer(mp)
    }

    private fun configurePlayer(mp: MediaPlayer) {
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setWakeMode(appContext, PowerManager.PARTIAL_WAKE_LOCK)
    }

    /** Stops playback immediately. Safe to call at any time. */
    fun stop() {
        generation++
        job?.cancel()
        job = null
        val current = player
        if (current != null) releasePlayer(current)
    }

    fun release() {
        stop()
        warmJob?.cancel()
        queueJob?.cancel()
        progressJob?.cancel()
        discardStandby()
        mainScope.cancel()
        ioScope.cancel()
    }

    // ------------------------------------------------------------------ audio files

    private suspend fun awaitAudio(text: String, params: Params): File {
        val file = cacheFile(text, params)
        if (file.exists() && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        return startSynthesis(text, params).await()
    }

    private fun startSynthesis(text: String, params: Params): Deferred<File> {
        val file = cacheFile(text, params)
        val key = file.name
        return synchronized(inFlight) {
            val existing = inFlight[key]
            if (existing != null && existing.isActive) {
                existing
            } else {
                val deferred = ioScope.async {
                    try {
                        netGate.withPermit {
                            if (file.exists() && file.length() > 0L) {
                                file
                            } else {
                                val bytes = client.synthesize(text, params.voice, params.rate, params.pitch)
                                val tmp = File(cacheDir, "$key.tmp")
                                tmp.writeBytes(bytes)
                                if (!tmp.renameTo(file)) {
                                    file.writeBytes(bytes)
                                    tmp.delete()
                                }
                                file
                            }
                        }
                    } finally {
                        synchronized(inFlight) { inFlight.remove(key) }
                    }
                }
                inFlight[key] = deferred
                deferred
            }
        }
    }

    private fun cacheFile(text: String, params: Params): File {
        val raw = params.voice + "|" + params.rate + "|" + params.pitch + "|" + text
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        val hex = StringBuilder(digest.size * 2 + 4)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            hex.append(digits[v shr 4]).append(digits[v and 0x0F])
        }
        return File(cacheDir, hex.toString() + ".mp3")
    }

    private fun pruneCache() {
        try {
            val files = cacheDir.listFiles() ?: return
            for (f in files) {
                if (f.name.endsWith(".tmp")) f.delete()
            }
            val mp3s = files.filter { it.name.endsWith(".mp3") && it.exists() }
            var total = mp3s.sumOf { it.length() }
            val maxBytes = 150L * 1024L * 1024L
            val targetBytes = 100L * 1024L * 1024L
            if (total <= maxBytes) return
            for (f in mp3s.sortedBy { it.lastModified() }) {
                if (total <= targetBytes) break
                val size = f.length()
                if (f.delete()) total -= size
            }
        } catch (e: Exception) {
            // Cache cleanup is best effort only.
        }
    }

    /** Deletes all downloaded sentence audio. */
    fun clearCache() {
        suspendedChapter = queueChapter
        queueJob?.cancel()
        queueJob = null
        ioScope.launch {
            try {
                synchronized(trackedFiles) { trackedFiles.clear() }
                cacheDir.listFiles()?.forEach { it.delete() }
                publishProgress()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // ------------------------------------------------------------------ playback

    private suspend fun play(
        file: File, id: String, gen: Int, speed: Float, callback: Callback, warm: MediaPlayer? = null
    ) {
        suspendCancellableCoroutine<Unit> { cont ->
            val mp = warm ?: MediaPlayer()
            player = mp

            fun finish() {
                releasePlayer(mp)
                if (cont.isActive) cont.resume(Unit)
            }

            fun begin(prepared: MediaPlayer) {
                if (gen != generation) {
                    finish()
                } else {
                    prepared.start()
                    if (speed != 1f) {
                        try {
                            prepared.playbackParams = prepared.playbackParams.setSpeed(speed)
                        } catch (e: Exception) {
                            // Speed above the service maximum is optional; play at normal speed.
                        }
                    }
                    callback.onStart(id)
                }
            }

            try {
                mp.setOnCompletionListener {
                    finish()
                    if (gen == generation) callback.onDone(id)
                }
                mp.setOnErrorListener { _, what, extra ->
                    finish()
                    if (gen == generation) callback.onError(id, "Audio playback error ($what/$extra)")
                    true
                }
                if (warm != null) {
                    // Already prepared while the previous sentence was playing: start instantly.
                    begin(mp)
                } else {
                    configurePlayer(mp)
                    mp.setDataSource(file.absolutePath)
                    mp.setOnPreparedListener { prepared -> begin(prepared) }
                    mp.prepareAsync()
                }
            } catch (e: Exception) {
                finish()
                // A broken cached file would fail forever, so remove it.
                file.delete()
                if (gen == generation) callback.onError(id, e.message ?: "Could not play audio")
            }
        }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try {
            mp.setOnPreparedListener(null)
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.reset()
        } catch (e: Exception) {
            // already released
        }
        try {
            mp.release()
        } catch (e: Exception) {
            // already released
        }
        if (player === mp) player = null
    }

    // ------------------------------------------------------------------ rate and pitch

    /** App rate 0.5x..2.5x -> Edge percent. The service handles up to +100%; the rest is done by the player. */
    private fun rateString(rate: Float): String {
        val percent = ((rate - 1f) * 100f).roundToInt().coerceIn(-50, 100)
        return String.format(Locale.US, "%+d%%", percent)
    }

    /** App pitch 0.5..2.0 (1.0 = normal) -> Edge Hertz offset. */
    private fun pitchString(pitch: Float): String {
        val hertz = ((pitch - 1f) * 50f).roundToInt().coerceIn(-100, 100)
        return String.format(Locale.US, "%+dHz", hertz)
    }
}
