package com.example.audio

/**
 * A chapter inside one audio file, with times measured from the start of that file.
 * The importer turns these into database chapters.
 */
data class RawChapter(
    val startMs: Long,
    val endMs: Long,
    val title: String
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** A chapter start found in a file or cue sheet, before ends and titles are tidied up. */
data class ChapterMark(
    val startMs: Long,
    val title: String
)

/**
 * How the chapters of an imported file should be made.
 *
 * @param useEmbeddedChapters read chapter marks stored inside the file (or a .cue sheet) when
 *        there are any. Turning this off treats the file as one piece even if it has marks.
 * @param splitEveryMinutes when the file has no chapter marks (or marks are switched off),
 *        cut it into parts of this many minutes. 0 keeps the file as one single chapter,
 *        which is the default.
 */
data class ChapterOptions(
    val useEmbeddedChapters: Boolean = true,
    val splitEveryMinutes: Int = 0
)

/**
 * Works out the chapters for one audio file.
 *
 * Order of preference for a file:
 *  1. Chapter marks stored inside the file: MP4 / m4b (both styles), MP3 (ID3 "CHAP" frames),
 *     Ogg / Opus / FLAC ("CHAPTER001=00:00:00.000" tags).
 *  2. A .cue sheet that sits next to the file (the importer reads it and passes the text in).
 *  3. Splitting by time, only if the user asked for it.
 *  4. Otherwise one single chapter that covers the whole file.
 *
 * Everything here is plain Kotlin with no Android classes, so it can be tested on its own.
 * Nothing in this file throws: a file that cannot be read simply has no embedded chapters.
 */
object ChapterExtractor {

    const val MAX_CHAPTERS = 10_000

    /** A file needs at least this many marks before they count as real chapters. */
    private const val MIN_MARKS = 2

    /** If the first mark is later than this, an "Intro" chapter covers the gap before it. */
    private const val INTRO_GAP_MS = 5_000L

    private const val SCAN_BYTES = 2 * 1024 * 1024
    private const val MAX_CHAP_FRAME_BYTES = 64 * 1024

    // =================================================================== Main entry

    /**
     * Chapters for one file, following the order of preference above.
     *
     * @param source the file, or null if it could not be opened
     * @param cueText text of a .cue file next to it, or null
     * @param durationMs length of the file in milliseconds, or 0 if unknown
     * @param fallbackTitle name for the single chapter when the file is kept whole
     */
    fun chaptersForFile(
        source: RandomAccessSource?,
        cueText: String?,
        durationMs: Long,
        fallbackTitle: String,
        options: ChapterOptions = ChapterOptions()
    ): List<RawChapter> {
        if (options.useEmbeddedChapters) {
            if (source != null) {
                val embedded = readEmbedded(source, durationMs)
                if (embedded.size >= MIN_MARKS) return embedded
            }
            if (!cueText.isNullOrBlank()) {
                val fromCue = parseCue(cueText, durationMs)
                if (fromCue.size >= MIN_MARKS) return fromCue
            }
        }
        if (options.splitEveryMinutes > 0 && durationMs > 0L) {
            return splitByMinutes(durationMs, options.splitEveryMinutes)
        }
        return wholeFile(fallbackTitle, durationMs)
    }

    /** One chapter covering the whole file. */
    fun wholeFile(title: String, durationMs: Long): List<RawChapter> =
        listOf(RawChapter(0L, durationMs.coerceAtLeast(0L), title.ifBlank { "Chapter 1" }))

    /**
     * Cuts a file into equal parts called "Part 1", "Part 2", and so on. A very short last
     * part is joined to the one before it.
     */
    fun splitByMinutes(durationMs: Long, minutes: Int): List<RawChapter> {
        val step = minutes.coerceIn(1, 600) * 60_000L
        if (durationMs <= step) return listOf(RawChapter(0L, durationMs.coerceAtLeast(0L), "Part 1"))

        var count = ((durationMs + step - 1) / step).toInt()
        val tail = durationMs - (count - 1) * step
        if (count > 1 && tail < step / 4) count -= 1

        return (0 until count).map { i ->
            val start = i * step
            val end = if (i == count - 1) durationMs else (i + 1) * step
            RawChapter(start, end, "Part ${i + 1}")
        }
    }

    // ============================================================ Embedded chapters

    /**
     * Reads chapter marks stored inside the file. The format is recognised from the first
     * bytes of the file, not from its name, because names from the file picker are not
     * always reliable. Returns an empty list when there are none.
     */
    fun readEmbedded(source: RandomAccessSource, durationMs: Long): List<RawChapter> {
        return try {
            val head = readUpTo(source, 0L, 12)
            if (head.size < 8) return emptyList()

            val marks: List<ChapterMark> = when {
                startsWith(head, "ID3") -> readId3Chapters(source)
                head.size >= 8 && ascii(head, 4, 4) == "ftyp" ->
                    Mp4ChapterParser.parse(source).map { ChapterMark(it.startMs, it.title) }
                startsWith(head, "OggS") || startsWith(head, "fLaC") -> readVorbisChapters(source)
                else -> emptyList()
            }
            fromMarks(marks, durationMs)
        } catch (e: Exception) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ MP3 (ID3v2 CHAP)

    private fun readId3Chapters(source: RandomAccessSource): List<ChapterMark> {
        val head = readUpTo(source, 0L, 10)
        if (head.size < 10 || !startsWith(head, "ID3")) return emptyList()

        val major = u8(head, 3)
        if (major != 3 && major != 4) return emptyList()   // v2.2 has no chapter frames
        val flags = u8(head, 5)
        val tagSize = synchsafe(head, 6)
        val end = minOf(10L + tagSize, source.length)

        var pos = 10L
        if (flags and 0x40 != 0) {                           // extended header: skip it
            val ext = readUpTo(source, pos, 4)
            if (ext.size < 4) return emptyList()
            pos += if (major == 4) synchsafe(ext, 0) else u32(ext, 0) + 4
        }

        val marks = ArrayList<ChapterMark>()
        while (pos + 10 <= end && marks.size < MAX_CHAPTERS) {
            val frameHead = readUpTo(source, pos, 10)
            if (frameHead.size < 10 || u8(frameHead, 0) == 0) break   // padding reached
            val id = ascii(frameHead, 0, 4)
            val size = if (major == 4) synchsafe(frameHead, 4) else u32(frameHead, 4)
            if (size < 0 || pos + 10 + size > end) break

            if (id == "CHAP" && size in 1..MAX_CHAP_FRAME_BYTES) {
                val data = readUpTo(source, pos + 10, size.toInt())
                parseChapFrame(data, major)?.let { marks.add(it) }
            }
            pos += 10 + size
        }
        return marks
    }

    /** A CHAP frame: element id, four 32-bit numbers (start, end, offsets), then sub-frames such as TIT2. */
    private fun parseChapFrame(data: ByteArray, major: Int): ChapterMark? {
        val idEnd = data.indexOf(0.toByte())
        if (idEnd < 0) return null
        var p = idEnd + 1
        if (p + 16 > data.size) return null
        val startMs = u32(data, p)
        p += 16

        var title = ""
        var subtitle = ""
        while (p + 10 <= data.size) {
            val id = ascii(data, p, 4)
            val size = if (major == 4) synchsafe(data, p + 4) else u32(data, p + 4)
            if (size < 0 || p + 10 + size > data.size) break
            if (id == "TIT2" && size >= 1) title = decodeId3Text(data, p + 10, size.toInt())
            if (id == "TIT3" && size >= 1) subtitle = decodeId3Text(data, p + 10, size.toInt())
            p += 10 + size.toInt()
        }
        return ChapterMark(startMs, title.ifBlank { subtitle })
    }

    /** ID3 text: first byte says how the rest is encoded. */
    private fun decodeId3Text(buf: ByteArray, start: Int, len: Int): String {
        if (len <= 1) return ""
        val charset = when (u8(buf, start)) {
            1 -> Charsets.UTF_16        // with byte order mark
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(buf, start + 1, len - 1, charset)
            .substringBefore('\u0000')
            .filter { !it.isISOControl() }
            .trim()
    }

    // ------------------------------------------- Ogg / Opus / FLAC (CHAPTERnnn tags)

    private val vorbisTag = Regex("(?i)CHAPTER(\\d{1,4})(NAME)?=")

    /**
     * Ogg, Opus and FLAC keep tags as length-prefixed "KEY=value" entries near the start of
     * the file. Chapter marks use CHAPTER001=00:00:00.000 for the time and
     * CHAPTER001NAME=Title for the name. Only the first 2 MB is searched.
     */
    private fun readVorbisChapters(source: RandomAccessSource): List<ChapterMark> {
        val data = readUpTo(source, 0L, SCAN_BYTES)
        if (data.isEmpty()) return emptyList()
        val text = String(data, Charsets.ISO_8859_1)

        val times = HashMap<Int, Long>()
        val names = HashMap<Int, String>()

        for (match in vorbisTag.findAll(text)) {
            val number = match.groupValues[1].toIntOrNull() ?: continue
            val isName = match.groupValues[2].isNotEmpty()
            val valueStart = match.range.last + 1
            val entryStart = match.range.first

            // Each entry is preceded by its length as a 32-bit little-endian number.
            var valueEnd = -1
            if (entryStart >= 4) {
                val len = u32le(data, entryStart - 4)
                val candidate = entryStart + len
                if (len >= valueStart - entryStart && len <= 4096 && candidate <= data.size) {
                    valueEnd = candidate.toInt()
                }
            }
            if (valueEnd < 0) {
                valueEnd = valueStart
                while (valueEnd < data.size && valueEnd - valueStart < 512 && u8(data, valueEnd) >= 0x20) valueEnd++
            }
            if (valueEnd <= valueStart) continue

            val value = String(data, valueStart, valueEnd - valueStart, Charsets.UTF_8)
                .filter { !it.isISOControl() }
                .trim()
            if (isName) {
                names[number] = value
            } else {
                parseTimestamp(value)?.let { times[number] = it }
            }
        }

        return times.entries
            .sortedBy { it.key }
            .take(MAX_CHAPTERS)
            .map { ChapterMark(it.value, names[it.key].orEmpty()) }
    }

    /** "01:02:03.500", "02:03.5" or "02:03,5" to milliseconds. Returns null if it is not a time. */
    internal fun parseTimestamp(text: String): Long? {
        val parts = text.trim().split(':')
        if (parts.size !in 2..3) return null
        val seconds = parts.last().replace(',', '.').toDoubleOrNull() ?: return null
        val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) (parts[0].toLongOrNull() ?: return null) else 0L
        if (seconds < 0.0 || minutes < 0L || hours < 0L) return null
        return (hours * 3600L + minutes * 60L) * 1000L + Math.round(seconds * 1000.0)
    }

    // ------------------------------------------------------------------- CUE sheets

    private val cueTitle = Regex("^\\s*TITLE\\s+\"?(.*?)\"?\\s*$", RegexOption.IGNORE_CASE)
    private val cueIndex = Regex("^\\s*INDEX\\s+01\\s+(\\d+):(\\d{1,2}):(\\d{1,2})", RegexOption.IGNORE_CASE)
    private val cueTrack = Regex("^\\s*TRACK\\s+\\d+", RegexOption.IGNORE_CASE)
    private val cueFile = Regex("^\\s*FILE\\s+", RegexOption.IGNORE_CASE)

    /**
     * Reads a .cue sheet that describes one audio file. A sheet that lists several files is
     * ignored because its times would not match this single file.
     */
    fun parseCue(text: String, durationMs: Long): List<RawChapter> {
        return try {
            val lines = text.lines()
            if (lines.count { cueFile.containsMatchIn(it) } > 1) return emptyList()

            val marks = ArrayList<ChapterMark>()
            var title = ""
            var inTrack = false
            for (line in lines) {
                when {
                    cueTrack.containsMatchIn(line) -> {
                        inTrack = true
                        title = ""
                    }
                    inTrack && cueTitle.containsMatchIn(line) ->
                        title = cueTitle.find(line)?.groupValues?.get(1).orEmpty().trim()
                    inTrack && cueIndex.containsMatchIn(line) -> {
                        val m = cueIndex.find(line) ?: continue
                        val minutes = m.groupValues[1].toLong()
                        val seconds = m.groupValues[2].toLong()
                        val frames = m.groupValues[3].toLong()      // 75 frames per second
                        marks.add(ChapterMark((minutes * 60L + seconds) * 1000L + frames * 1000L / 75L, title))
                    }
                }
            }
            fromMarks(marks, durationMs)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ----------------------------------------------------------------- Tidying up

    /**
     * Turns chapter starts into complete chapters: sorted, no duplicate starts, nothing past
     * the end of the file, each chapter ending where the next starts, the last ending at the
     * end of the file, and blank titles replaced by "Chapter N".
     *
     * If the first mark is not at the start, a gap under 5 seconds is absorbed into the first
     * chapter and a longer gap gets its own "Intro" chapter, so no audio is left uncovered.
     */
    fun fromMarks(marks: List<ChapterMark>, durationMs: Long): List<RawChapter> {
        val cleaned = marks
            .filter { it.startMs >= 0L && (durationMs <= 0L || it.startMs < durationMs) }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
            .take(MAX_CHAPTERS)
        if (cleaned.isEmpty()) return emptyList()

        val named = cleaned.mapIndexed { index, mark ->
            if (mark.title.isBlank()) mark.copy(title = "Chapter ${index + 1}") else mark
        }

        val result = ArrayList<RawChapter>(named.size + 1)
        for (i in named.indices) {
            val start = named[i].startMs
            val end = when {
                i + 1 < named.size -> named[i + 1].startMs
                durationMs > 0L -> durationMs
                else -> start
            }
            result.add(RawChapter(start, end, named[i].title))
        }

        val first = result[0]
        if (first.startMs > 0L) {
            if (first.startMs < INTRO_GAP_MS) {
                result[0] = first.copy(startMs = 0L)
            } else {
                result.add(0, RawChapter(0L, first.startMs, "Intro"))
            }
        }
        return result
    }

    // --------------------------------------------------------------------- Helpers

    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF

    private fun u32(b: ByteArray, i: Int): Long =
        (u8(b, i).toLong() shl 24) or (u8(b, i + 1).toLong() shl 16) or
            (u8(b, i + 2).toLong() shl 8) or u8(b, i + 3).toLong()

    private fun u32le(b: ByteArray, i: Int): Long =
        u8(b, i).toLong() or (u8(b, i + 1).toLong() shl 8) or
            (u8(b, i + 2).toLong() shl 16) or (u8(b, i + 3).toLong() shl 24)

    /** ID3 "synchsafe" number: four bytes that use only 7 bits each. */
    private fun synchsafe(b: ByteArray, i: Int): Long =
        ((u8(b, i) and 0x7F).toLong() shl 21) or ((u8(b, i + 1) and 0x7F).toLong() shl 14) or
            ((u8(b, i + 2) and 0x7F).toLong() shl 7) or (u8(b, i + 3) and 0x7F).toLong()

    private fun ascii(b: ByteArray, i: Int, len: Int): String =
        String(b, i, len, Charsets.ISO_8859_1)

    private fun startsWith(b: ByteArray, text: String): Boolean {
        if (b.size < text.length) return false
        for (i in text.indices) if (b[i].toInt() != text[i].code) return false
        return true
    }

    /** Reads as many bytes as are available up to [count], without failing at the end of the file. */
    private fun readUpTo(source: RandomAccessSource, position: Long, count: Int): ByteArray {
        if (position < 0L || position >= source.length || count <= 0) return ByteArray(0)
        val wanted = minOf(count.toLong(), source.length - position).toInt()
        val out = ByteArray(wanted)
        var done = 0
        while (done < wanted) {
            val n = source.read(position + done, out, done, wanted - done)
            if (n <= 0) break
            done += n
        }
        return if (done == wanted) out else out.copyOf(done)
    }
}
