package com.example.audio

/**
 * One chapter mark found inside an MP4 audio file (.m4b, .m4a, .mp4, .aac in an MP4 wrapper).
 * [startMs] is where the chapter starts in the file. The end is not stored: a chapter ends
 * where the next one starts, and the last one ends at the end of the file.
 */
data class Mp4Chapter(
    val startMs: Long,
    val title: String
)

/**
 * A file that can be read at any position. The parser needs this because the chapter list
 * sits in a small index block that can be anywhere in a file that may be several hundred
 * megabytes. Reading only that block keeps import fast and light on memory.
 *
 * The importer wraps an Android file descriptor in this interface. Tests can use
 * [ByteArraySource].
 */
interface RandomAccessSource {
    /** Total size in bytes. */
    val length: Long

    /**
     * Reads up to [count] bytes starting at [position] into [buffer] at [offset].
     * Returns how many bytes were read, or -1 at the end of the file.
     */
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
}

class ByteArraySource(private val data: ByteArray) : RandomAccessSource {
    override val length: Long get() = data.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position < 0L || position >= data.size) return -1
        val n = minOf(count.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }
}

/**
 * Reads chapter marks from MP4-family audio files.
 *
 * Audiobooks in .m4b files store chapters in one of two ways, and this class understands both:
 *
 *  1. "chpl" box (Nero style). A plain list of start times and titles inside moov/udta.
 *     Written by ffmpeg, mp4chaps, many audiobook tools.
 *  2. QuickTime chapter track. A hidden text track that the audio track points to with a
 *     "chap" reference. Written by iTunes, Apple Books and many converters. Each text
 *     sample is a chapter title and the track's timing gives the start time.
 *
 * If a file has both, the chpl list is used because it is simpler and always complete.
 * If a file has neither, or is not a readable MP4, the result is an empty list and the
 * caller falls back to other ways of making chapters. This class never throws.
 */
object Mp4ChapterParser {

    private const val MAX_MOOV_BYTES = 64L * 1024 * 1024
    private const val MAX_CHAPTERS = 10_000
    private const val MAX_SAMPLE_BYTES = 4096

    fun parse(source: RandomAccessSource): List<Mp4Chapter> {
        return try {
            val moov = readMoov(source) ?: return emptyList()
            val top = children(moov, 0, moov.size)

            val chpl = parseChpl(moov, top)
            if (chpl.isNotEmpty()) return finish(chpl)

            finish(parseChapterTrack(source, moov, top))
        } catch (e: Exception) {
            emptyList()
        } catch (e: OutOfMemoryError) {
            emptyList()
        }
    }

    /** Convenience for tests and small files already in memory. */
    fun parse(data: ByteArray): List<Mp4Chapter> = parse(ByteArraySource(data))

    // ---------------------------------------------------------------- Boxes

    /** A box inside a byte buffer: its type and where its content starts and ends. */
    private class Box(val type: String, val dataStart: Int, val end: Int)

    private fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int): Int = (u8(b, i) shl 8) or u8(b, i + 1)
    private fun u32(b: ByteArray, i: Int): Long =
        (u8(b, i).toLong() shl 24) or (u8(b, i + 1).toLong() shl 16) or
            (u8(b, i + 2).toLong() shl 8) or u8(b, i + 3).toLong()

    private fun u64(b: ByteArray, i: Int): Long = (u32(b, i) shl 32) or u32(b, i + 4)

    private fun ascii(b: ByteArray, i: Int): String =
        String(charArrayOf(
            (u8(b, i)).toChar(), (u8(b, i + 1)).toChar(),
            (u8(b, i + 2)).toChar(), (u8(b, i + 3)).toChar()
        ))

    /** Lists the boxes found between [start] and [end] in [buf]. Stops quietly at damaged data. */
    private fun children(buf: ByteArray, start: Int, end: Int): List<Box> {
        val result = ArrayList<Box>()
        var pos = start
        while (pos + 8 <= end) {
            var size = u32(buf, pos)
            val type = ascii(buf, pos + 4)
            var header = 8
            if (size == 1L) {
                if (pos + 16 > end) break
                size = u64(buf, pos + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < header) break
            val boxEnd = minOf(pos.toLong() + size, end.toLong()).toInt()
            result.add(Box(type, pos + header, boxEnd))
            if (boxEnd <= pos) break
            pos = boxEnd
        }
        return result
    }

    private fun find(buf: ByteArray, parent: Box, type: String): Box? =
        children(buf, parent.dataStart, parent.end).firstOrNull { it.type == type }

    private fun find(buf: ByteArray, boxes: List<Box>, type: String): Box? =
        boxes.firstOrNull { it.type == type }

    private fun readFully(source: RandomAccessSource, position: Long, count: Int): ByteArray? {
        if (count < 0 || position < 0L) return null
        val out = ByteArray(count)
        var done = 0
        while (done < count) {
            val n = source.read(position + done, out, done, count - done)
            if (n <= 0) return null
            done += n
        }
        return out
    }

    /**
     * Finds the "moov" box at the top level of the file and returns its content. The index is
     * usually near the start or near the end of the file, so the top-level boxes are walked
     * with small reads that skip over the large audio data block.
     */
    private fun readMoov(source: RandomAccessSource): ByteArray? {
        var pos = 0L
        val total = source.length
        while (pos + 8 <= total) {
            val head = readFully(source, pos, minOf(16L, total - pos).toInt()) ?: return null
            var size = u32(head, 0)
            val type = ascii(head, 4)
            var header = 8L
            if (size == 1L) {
                if (head.size < 16) return null
                size = u64(head, 8)
                header = 16L
            } else if (size == 0L) {
                size = total - pos
            }
            if (size < header) return null

            if (type == "moov") {
                val contentSize = size - header
                if (contentSize <= 0L || contentSize > MAX_MOOV_BYTES) return null
                val available = minOf(contentSize, total - pos - header)
                return readFully(source, pos + header, available.toInt())
            }
            pos += size
        }
        return null
    }

    // ------------------------------------------------------- chpl (Nero style)

    private fun parseChpl(moov: ByteArray, top: List<Box>): List<Mp4Chapter> {
        val udta = find(moov, top, "udta") ?: return emptyList()
        val chpl = find(moov, udta, "chpl") ?: return emptyList()

        var p = chpl.dataStart
        if (p + 5 > chpl.end) return emptyList()
        val version = u8(moov, p)
        p += 4                       // version (1) + flags (3)
        if (version != 0) p += 4     // newer files carry 4 extra reserved bytes
        if (p >= chpl.end) return emptyList()
        val count = u8(moov, p)
        p += 1

        val chapters = ArrayList<Mp4Chapter>()
        for (i in 0 until count) {
            if (p + 9 > chpl.end) break
            val start100ns = u64(moov, p)
            p += 8
            val len = u8(moov, p)
            p += 1
            if (p + len > chpl.end) break
            val title = decodeTitle(moov, p, len)
            p += len
            chapters.add(Mp4Chapter(startMs = start100ns / 10_000L, title = title))
        }
        return chapters
    }

    // ------------------------------------------------ QuickTime chapter track

    private fun parseChapterTrack(source: RandomAccessSource, moov: ByteArray, top: List<Box>): List<Mp4Chapter> {
        val traks = top.filter { it.type == "trak" }
        if (traks.isEmpty()) return emptyList()

        // 1. Which track ids does any track point to as its chapter track?
        val chapterTrackIds = HashSet<Long>()
        for (trak in traks) {
            val tref = find(moov, trak, "tref") ?: continue
            val chap = find(moov, tref, "chap") ?: continue
            var p = chap.dataStart
            while (p + 4 <= chap.end) {
                chapterTrackIds.add(u32(moov, p))
                p += 4
            }
        }
        if (chapterTrackIds.isEmpty()) return emptyList()

        // 2. Find that track and read its sample table.
        for (trak in traks) {
            val tkhd = find(moov, trak, "tkhd") ?: continue
            val idOffset = if (u8(moov, tkhd.dataStart) == 1) 4 + 8 + 8 else 4 + 4 + 4
            if (tkhd.dataStart + idOffset + 4 > tkhd.end) continue
            val trackId = u32(moov, tkhd.dataStart + idOffset)
            if (trackId !in chapterTrackIds) continue

            val chapters = readTextTrack(source, moov, trak)
            if (chapters.isNotEmpty()) return chapters
        }
        return emptyList()
    }

    private fun readTextTrack(source: RandomAccessSource, moov: ByteArray, trak: Box): List<Mp4Chapter> {
        val mdia = find(moov, trak, "mdia") ?: return emptyList()

        val mdhd = find(moov, mdia, "mdhd") ?: return emptyList()
        val timescaleOffset = if (u8(moov, mdhd.dataStart) == 1) 4 + 8 + 8 else 4 + 4 + 4
        if (mdhd.dataStart + timescaleOffset + 4 > mdhd.end) return emptyList()
        val timescale = u32(moov, mdhd.dataStart + timescaleOffset)
        if (timescale <= 0L) return emptyList()

        val minf = find(moov, mdia, "minf") ?: return emptyList()
        val stbl = find(moov, minf, "stbl") ?: return emptyList()
        val stblBoxes = children(moov, stbl.dataStart, stbl.end)

        // ---- stts: how long each sample lasts
        val stts = find(moov, stblBoxes, "stts") ?: return emptyList()
        val sttsCount = u32(moov, stts.dataStart + 4).toInt()
        if (sttsCount < 0 || stts.dataStart + 8 + sttsCount.toLong() * 8 > stts.end) return emptyList()

        // ---- stsz: how big each sample is
        val stsz = find(moov, stblBoxes, "stsz") ?: return emptyList()
        val fixedSize = u32(moov, stsz.dataStart + 4)
        val sampleCount = u32(moov, stsz.dataStart + 8).toInt()
        if (sampleCount <= 0 || sampleCount > MAX_CHAPTERS) return emptyList()
        if (fixedSize == 0L && stsz.dataStart + 12 + sampleCount.toLong() * 4 > stsz.end) return emptyList()

        // ---- stsc: how many samples sit in each chunk
        val stsc = find(moov, stblBoxes, "stsc") ?: return emptyList()
        val stscCount = u32(moov, stsc.dataStart + 4).toInt()
        if (stscCount <= 0 || stsc.dataStart + 8 + stscCount.toLong() * 12 > stsc.end) return emptyList()

        // ---- stco / co64: where each chunk starts in the file
        val stco = find(moov, stblBoxes, "stco")
        val co64 = find(moov, stblBoxes, "co64")
        val chunkBox = stco ?: co64 ?: return emptyList()
        val is64 = stco == null
        val chunkCount = u32(moov, chunkBox.dataStart + 4).toInt()
        val entrySize = if (is64) 8 else 4
        if (chunkCount <= 0 || chunkBox.dataStart + 8 + chunkCount.toLong() * entrySize > chunkBox.end) {
            return emptyList()
        }

        fun chunkOffset(chunkIndex: Int): Long {
            val at = chunkBox.dataStart + 8 + chunkIndex * entrySize
            return if (is64) u64(moov, at) else u32(moov, at)
        }

        fun sampleSize(index: Int): Long =
            if (fixedSize != 0L) fixedSize else u32(moov, stsz.dataStart + 12 + index * 4)

        // Work out the file offset of every sample.
        val sampleOffsets = LongArray(sampleCount)
        var sampleIndex = 0
        for (e in 0 until stscCount) {
            val entryAt = stsc.dataStart + 8 + e * 12
            val firstChunk = u32(moov, entryAt).toInt()
            val perChunk = u32(moov, entryAt + 4).toInt()
            val nextFirst = if (e + 1 < stscCount) u32(moov, entryAt + 12).toInt() else chunkCount + 1
            if (firstChunk < 1 || perChunk <= 0) continue
            for (chunk in firstChunk until minOf(nextFirst, chunkCount + 1)) {
                var offset = chunkOffset(chunk - 1)
                for (s in 0 until perChunk) {
                    if (sampleIndex >= sampleCount) break
                    sampleOffsets[sampleIndex] = offset
                    offset += sampleSize(sampleIndex)
                    sampleIndex++
                }
            }
        }
        if (sampleIndex < sampleCount) return emptyList()

        // Work out the start time of every sample.
        val startsMs = LongArray(sampleCount)
        var timeUnits = 0L
        var filled = 0
        for (e in 0 until sttsCount) {
            val entryAt = stts.dataStart + 8 + e * 8
            val count = u32(moov, entryAt).toInt()
            val delta = u32(moov, entryAt + 4)
            for (n in 0 until count) {
                if (filled >= sampleCount) break
                startsMs[filled] = timeUnits * 1000L / timescale
                timeUnits += delta
                filled++
            }
            if (filled >= sampleCount) break
        }
        if (filled < sampleCount) return emptyList()

        // Read each title. A text sample is a 2-byte length followed by that many text bytes.
        val chapters = ArrayList<Mp4Chapter>(sampleCount)
        for (i in 0 until sampleCount) {
            val size = sampleSize(i)
            var title = ""
            if (size in 2L..MAX_SAMPLE_BYTES.toLong()) {
                val sample = readFully(source, sampleOffsets[i], size.toInt())
                if (sample != null) {
                    val textLen = minOf(u16(sample, 0), sample.size - 2)
                    title = decodeTitle(sample, 2, textLen)
                }
            }
            chapters.add(Mp4Chapter(startMs = startsMs[i], title = title))
        }
        return chapters
    }

    // -------------------------------------------------------------- Helpers

    /** Reads text that is UTF-8, or UTF-16 when it starts with a byte order mark. */
    private fun decodeTitle(buf: ByteArray, start: Int, len: Int): String {
        if (len <= 0) return ""
        val raw = when {
            len >= 2 && u8(buf, start) == 0xFE && u8(buf, start + 1) == 0xFF ->
                String(buf, start + 2, len - 2, Charsets.UTF_16BE)
            len >= 2 && u8(buf, start) == 0xFF && u8(buf, start + 1) == 0xFE ->
                String(buf, start + 2, len - 2, Charsets.UTF_16LE)
            else -> String(buf, start, len, Charsets.UTF_8)
        }
        return raw.filter { !it.isISOControl() }.trim()
    }

    /**
     * Cleans up the list: sorted by time, no duplicate start times, at most [MAX_CHAPTERS],
     * and blank titles replaced by "Chapter N".
     */
    private fun finish(chapters: List<Mp4Chapter>): List<Mp4Chapter> {
        val sorted = chapters
            .filter { it.startMs >= 0L }
            .sortedBy { it.startMs }
            .distinctBy { it.startMs }
            .take(MAX_CHAPTERS)
        return sorted.mapIndexed { index, chapter ->
            if (chapter.title.isBlank()) chapter.copy(title = "Chapter ${index + 1}") else chapter
        }
    }
}
