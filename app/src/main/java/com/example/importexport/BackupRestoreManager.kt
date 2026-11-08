package com.example.importexport

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ReaderSettings
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupData(
    val novels: List<NovelWithChaptersBackup>,
    val settings: ReaderSettings?
)

data class NovelWithChaptersBackup(
    val novel: NovelEntity,
    val chapters: List<ChapterEntity>
)

object BackupRestoreManager {

    fun generateBackupJson(
        novelsWithChapters: List<NovelWithChaptersBackup>,
        settings: ReaderSettings
    ): String {
        val root = JSONObject()
        root.put("app", "MTLVault")
        root.put("version", 1)
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val dataObj = JSONObject()

        // Novels
        val novelsArray = JSONArray()
        for (item in novelsWithChapters) {
            val novelObj = JSONObject()
            novelObj.put("id", item.novel.id)
            novelObj.put("title", item.novel.title)
            novelObj.put("author", item.novel.author)
            novelObj.put("translator", item.novel.translator)
            novelObj.put("description", item.novel.description)
            novelObj.put("cover", item.novel.coverUri ?: "")
            novelObj.put("lastChapterId", item.novel.lastChapterId ?: "")
            novelObj.put("replacements", item.novel.replacements)
            novelObj.put("replacementsEnabled", item.novel.replacementsEnabled)
            novelObj.put("createdAt", item.novel.createdAt)
            novelObj.put("updatedAt", item.novel.updatedAt)

            val chaptersArray = JSONArray()
            val progressObj = JSONObject()
            for (ch in item.chapters) {
                val chObj = JSONObject()
                chObj.put("id", ch.id)
                chObj.put("title", ch.title)
                chObj.put("content", ch.content)
                chObj.put("wordCount", ch.wordCount)
                chObj.put("read", ch.read)
                chObj.put("readAt", ch.readAt ?: JSONObject.NULL)
                chObj.put("scrollPosition", ch.scrollPosition)
                chObj.put("readingParagraphIndex", ch.readingParagraphIndex)
                chObj.put("createdAt", ch.createdAt)
                chObj.put("updatedAt", ch.updatedAt)
                chaptersArray.put(chObj)

                if (ch.scrollPosition > 0) {
                    progressObj.put(ch.id, ch.scrollPosition)
                }
            }
            novelObj.put("chapters", chaptersArray)
            novelObj.put("progress", progressObj)
            novelsArray.put(novelObj)
        }
        dataObj.put("novels", novelsArray)

        // Settings
        val settingsObj = JSONObject()
        settingsObj.put("theme", settings.theme)
        settingsObj.put("fontSize", settings.fontSize)
        settingsObj.put("lineHeight", settings.lineHeight.toDouble())
        settingsObj.put("fontFamily", settings.fontFamily)
        settingsObj.put("maxWidth", settings.maxWidth)
        settingsObj.put("ttsRate", settings.ttsRate.toDouble())
        settingsObj.put("ttsPitch", settings.ttsPitch.toDouble())
        settingsObj.put("ttsVoice", settings.ttsVoice)
        settingsObj.put("continueReading", settings.continueReading)
        settingsObj.put("replacementsEnabled", settings.replacementsEnabled)
        settingsObj.put("splitLongParagraphs", settings.splitLongParagraphs)
        dataObj.put("settings", settingsObj)

        root.put("data", dataObj)
        return root.toString(2)
    }

    fun parseBackupJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)
        val dataObj = when {
            root.has("data") -> root.getJSONObject("data")
            root.has("novels") -> root
            else -> throw IllegalArgumentException("This file is not a valid MTL Vault backup (no \"novels\" list found).")
        }
        if (!dataObj.has("novels")) {
            throw IllegalArgumentException("This file is not a valid MTL Vault backup (no \"novels\" list found).")
        }

        val novelsList = mutableListOf<NovelWithChaptersBackup>()
        if (dataObj.has("novels")) {
            val novelsArray = dataObj.getJSONArray("novels")
            for (i in 0 until novelsArray.length()) {
                val novelObj = novelsArray.getJSONObject(i)
                val novelId = novelObj.optString("id", "n_${System.currentTimeMillis()}_$i")
                val novel = NovelEntity(
                    id = novelId,
                    title = novelObj.optString("title", "Untitled Novel"),
                    author = novelObj.optString("author", ""),
                    translator = novelObj.optString("translator", ""),
                    description = novelObj.optString("description", ""),
                    coverUri = novelObj.optString("cover").takeIf { it.isNotBlank() },
                    lastChapterId = novelObj.optString("lastChapterId").takeIf { it.isNotBlank() },
                    replacements = novelObj.optString("replacements", ""),
                    replacementsEnabled = novelObj.optBoolean("replacementsEnabled", true),
                    createdAt = novelObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = novelObj.optLong("updatedAt", System.currentTimeMillis())
                )

                val progressObj = novelObj.optJSONObject("progress")

                val chaptersList = mutableListOf<ChapterEntity>()
                if (novelObj.has("chapters")) {
                    val chaptersArray = novelObj.getJSONArray("chapters")
                    for (j in 0 until chaptersArray.length()) {
                        val chObj = chaptersArray.getJSONObject(j)
                        val chId = chObj.optString("id", "c_${System.currentTimeMillis()}_${j}")
                        val savedProgress = progressObj?.optInt(chId, 0) ?: chObj.optInt("scrollPosition", 0)
                        val readingParagraphIndex = chObj.optInt("readingParagraphIndex", 0)
                        val content = chObj.optString("content", "")
                        val wc = if (chObj.has("wordCount")) chObj.getInt("wordCount") else ChapterSplitter.countWords(content)

                        val chapter = ChapterEntity(
                            id = chId,
                            novelId = novelId,
                            orderIndex = j,
                            title = chObj.optString("title", "Chapter ${j + 1}"),
                            content = content,
                            wordCount = wc,
                            scrollPosition = savedProgress,
                            readingParagraphIndex = readingParagraphIndex,
                            read = chObj.optBoolean("read", false),
                            readAt = if (chObj.isNull("readAt")) null else chObj.optLong("readAt"),
                            createdAt = chObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = chObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        chaptersList.add(chapter)
                    }
                }
                novelsList.add(NovelWithChaptersBackup(novel, chaptersList))
            }
        }

        var parsedSettings: ReaderSettings? = null
        if (dataObj.has("settings")) {
            val sObj = dataObj.getJSONObject("settings")
            parsedSettings = ReaderSettings(
                theme = sObj.optString("theme", "light"),
                fontSize = sObj.optInt("fontSize", 18),
                lineHeight = sObj.optDouble("lineHeight", 1.75).toFloat(),
                fontFamily = sObj.optString("fontFamily", "serif"),
                maxWidth = sObj.optInt("maxWidth", 720),
                ttsRate = sObj.optDouble("ttsRate", 1.0).toFloat(),
                ttsPitch = sObj.optDouble("ttsPitch", 1.0).toFloat(),
                ttsVoice = sObj.optString("ttsVoice", ""),
                continueReading = sObj.optBoolean("continueReading", true),
                replacementsEnabled = sObj.optBoolean("replacementsEnabled", true),
                splitLongParagraphs = sObj.optString("splitLongParagraphs", "medium")
            )
        }

        return BackupData(novelsList, parsedSettings)
    }

    // ====================================================================================
    // Streaming backup / restore. Only one novel is held in memory at a time, so large
    // libraries don't run out of memory. The file format is the same as generateBackupJson,
    // with two optional extra novel fields: "coverData" (base64) and "coverExt".
    // ====================================================================================

    private const val MAX_COVER_BYTES = 6 * 1024 * 1024

    /**
     * Writes a backup straight to [out]. [loadChapters] is asked for one novel's chapters at a
     * time, and [readCover] returns the bytes of a novel's saved cover (or null) so the picture
     * travels with the backup.
     */
    suspend fun writeBackupStream(
        out: OutputStream,
        novels: List<NovelEntity>,
        settings: ReaderSettings,
        loadChapters: suspend (String) -> List<ChapterEntity>,
        readCover: (String) -> ByteArray?
    ) {
        val writer = JsonWriter(OutputStreamWriter(out, Charsets.UTF_8).buffered())
        writer.beginObject()
        writer.name("app").value("MTLVault")
        writer.name("version").value(1)
        writer.name("exportedAt").value(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        writer.name("data").beginObject()
        writer.name("novels").beginArray()
        for (novel in novels) {
            val chapters = loadChapters(novel.id)
            writer.beginObject()
            writer.name("id").value(novel.id)
            writer.name("title").value(novel.title)
            writer.name("author").value(novel.author)
            writer.name("translator").value(novel.translator)
            writer.name("description").value(novel.description)
            writer.name("cover").value(novel.coverUri ?: "")
            novel.coverUri?.let { uri ->
                readCover(uri)?.takeIf { it.size <= MAX_COVER_BYTES }?.let { bytes ->
                    writer.name("coverData").value(Base64.encodeToString(bytes, Base64.NO_WRAP))
                    writer.name("coverExt").value("jpg")
                }
            }
            writer.name("lastChapterId").value(novel.lastChapterId ?: "")
            writer.name("replacements").value(novel.replacements)
            writer.name("replacementsEnabled").value(novel.replacementsEnabled)
            writer.name("createdAt").value(novel.createdAt)
            writer.name("updatedAt").value(novel.updatedAt)
            writer.name("chapters").beginArray()
            for (ch in chapters) {
                writer.beginObject()
                writer.name("id").value(ch.id)
                writer.name("title").value(ch.title)
                writer.name("content").value(ch.content)
                writer.name("wordCount").value(ch.wordCount)
                writer.name("read").value(ch.read)
                writer.name("readAt")
                if (ch.readAt != null) writer.value(ch.readAt) else writer.nullValue()
                writer.name("scrollPosition").value(ch.scrollPosition)
                writer.name("readingParagraphIndex").value(ch.readingParagraphIndex)
                writer.name("createdAt").value(ch.createdAt)
                writer.name("updatedAt").value(ch.updatedAt)
                writer.endObject()
            }
            writer.endArray()
            writer.name("progress").beginObject()
            for (ch in chapters) {
                if (ch.scrollPosition > 0) writer.name(ch.id).value(ch.scrollPosition)
            }
            writer.endObject()
            writer.endObject()
        }
        writer.endArray()
        writer.name("settings").beginObject()
        writer.name("theme").value(settings.theme)
        writer.name("fontSize").value(settings.fontSize)
        writer.name("lineHeight").value(settings.lineHeight.toDouble())
        writer.name("fontFamily").value(settings.fontFamily)
        writer.name("maxWidth").value(settings.maxWidth)
        writer.name("ttsRate").value(settings.ttsRate.toDouble())
        writer.name("ttsPitch").value(settings.ttsPitch.toDouble())
        writer.name("ttsVoice").value(settings.ttsVoice)
        writer.name("continueReading").value(settings.continueReading)
        writer.name("replacementsEnabled").value(settings.replacementsEnabled)
        writer.name("splitLongParagraphs").value(settings.splitLongParagraphs)
        writer.endObject()
        writer.endObject()
        writer.endObject()
        writer.flush()
        writer.close()
    }

    /**
     * Reads a backup from [input] one novel at a time, calling [onNovel] for each. [saveCover]
     * turns embedded cover bytes into a saved file address. Returns the backup's settings, if any.
     * Throws IllegalArgumentException when the file is not a backup.
     */
    suspend fun readBackupStream(
        input: InputStream,
        saveCover: (novelId: String, bytes: ByteArray) -> String?,
        onNovel: suspend (NovelWithChaptersBackup) -> Unit
    ): ReaderSettings? {
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8).buffered())
        var settings: ReaderSettings? = null
        var sawNovels = false
        try {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw notABackup()
            suspend fun readData() {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "novels" -> {
                            sawNovels = true
                            reader.beginArray()
                            var index = 0
                            while (reader.hasNext()) {
                                onNovel(readNovel(reader, index++, saveCover))
                            }
                            reader.endArray()
                        }
                        "settings" -> settings = readSettings(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "data" -> {
                        if (reader.peek() != JsonToken.BEGIN_OBJECT) throw notABackup()
                        readData()
                    }
                    "novels" -> {
                        sawNovels = true
                        reader.beginArray()
                        var index = 0
                        while (reader.hasNext()) {
                            onNovel(readNovel(reader, index++, saveCover))
                        }
                        reader.endArray()
                    }
                    "settings" -> settings = readSettings(reader)
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        } catch (e: java.io.EOFException) {
            throw IllegalArgumentException("The backup file is empty or incomplete.")
        } catch (e: android.util.MalformedJsonException) {
            throw IllegalArgumentException("The backup file is damaged or is not an MTL Vault backup.")
        } finally {
            try { reader.close() } catch (_: Exception) {}
        }
        if (!sawNovels) throw notABackup()
        return settings
    }

    private fun notABackup() =
        IllegalArgumentException("This file is not a valid MTL Vault backup (no \"novels\" list found).")

    private fun JsonReader.stringOrNull(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING -> nextString()
        JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    private fun JsonReader.longOr(default: Long): Long = when (peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> try { nextString().toDouble().toLong() } catch (e: NumberFormatException) { default }
        JsonToken.NULL -> { nextNull(); default }
        else -> { skipValue(); default }
    }

    private fun JsonReader.boolOr(default: Boolean): Boolean = when (peek()) {
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.NULL -> { nextNull(); default }
        else -> { skipValue(); default }
    }

    private fun JsonReader.doubleOr(default: Double): Double = when (peek()) {
        JsonToken.NUMBER, JsonToken.STRING -> try { nextString().toDouble() } catch (e: NumberFormatException) { default }
        JsonToken.NULL -> { nextNull(); default }
        else -> { skipValue(); default }
    }

    private fun readNovel(
        reader: JsonReader,
        index: Int,
        saveCover: (String, ByteArray) -> String?
    ): NovelWithChaptersBackup {
        var id: String? = null
        var title = "Untitled Novel"
        var author = ""
        var translator = ""
        var description = ""
        var cover: String? = null
        var coverData: String? = null
        var lastChapterId: String? = null
        var replacements = ""
        var replacementsEnabled = true
        var createdAt = System.currentTimeMillis()
        var updatedAt = System.currentTimeMillis()
        var progress: Map<String, Int>? = null
        val rawChapters = mutableListOf<ChapterEntity>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.stringOrNull()
                "title" -> title = reader.stringOrNull() ?: title
                "author" -> author = reader.stringOrNull() ?: ""
                "translator" -> translator = reader.stringOrNull() ?: ""
                "description" -> description = reader.stringOrNull() ?: ""
                "cover" -> cover = reader.stringOrNull()
                "coverData" -> coverData = reader.stringOrNull()
                "lastChapterId" -> lastChapterId = reader.stringOrNull()
                "replacements" -> replacements = reader.stringOrNull() ?: ""
                "replacementsEnabled" -> replacementsEnabled = reader.boolOr(true)
                "createdAt" -> createdAt = reader.longOr(createdAt)
                "updatedAt" -> updatedAt = reader.longOr(updatedAt)
                "progress" -> {
                    if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                        val map = HashMap<String, Int>()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val key = reader.nextName()
                            map[key] = reader.longOr(0).toInt()
                        }
                        reader.endObject()
                        progress = map
                    } else reader.skipValue()
                }
                "chapters" -> {
                    reader.beginArray()
                    var j = 0
                    while (reader.hasNext()) {
                        rawChapters.add(readChapter(reader, j++))
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val novelId = id ?: "n_${System.currentTimeMillis()}_$index"
        var coverUri = cover?.takeIf { it.isNotBlank() }
        if (!coverData.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(coverData, Base64.DEFAULT)
                saveCover(novelId, bytes)?.let { coverUri = it }
            } catch (e: IllegalArgumentException) {
                // A damaged cover must not stop the restore; keep whatever address was stored.
            }
        }
        val novel = NovelEntity(
            id = novelId,
            title = title,
            author = author,
            translator = translator,
            description = description,
            coverUri = coverUri,
            lastChapterId = lastChapterId?.takeIf { it.isNotBlank() },
            replacements = replacements,
            replacementsEnabled = replacementsEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        val progressMap = progress
        val chapters = rawChapters.map { ch ->
            ch.copy(
                novelId = novelId,
                scrollPosition = if (progressMap != null) (progressMap[ch.id] ?: 0) else ch.scrollPosition
            )
        }
        return NovelWithChaptersBackup(novel, chapters)
    }

    private fun readChapter(reader: JsonReader, j: Int): ChapterEntity {
        var id: String? = null
        var title = "Chapter ${j + 1}"
        var content = ""
        var wordCount: Int? = null
        var read = false
        var readAt: Long? = null
        var scroll = 0
        var para = 0
        var createdAt = System.currentTimeMillis()
        var updatedAt = System.currentTimeMillis()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.stringOrNull()
                "title" -> title = reader.stringOrNull() ?: title
                "content" -> content = reader.stringOrNull() ?: ""
                "wordCount" -> wordCount = reader.longOr(-1).toInt().takeIf { it >= 0 }
                "read" -> read = reader.boolOr(false)
                "readAt" -> readAt = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else reader.longOr(0)
                "scrollPosition" -> scroll = reader.longOr(0).toInt()
                "readingParagraphIndex" -> para = reader.longOr(0).toInt()
                "createdAt" -> createdAt = reader.longOr(createdAt)
                "updatedAt" -> updatedAt = reader.longOr(updatedAt)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ChapterEntity(
            id = id ?: "c_${System.currentTimeMillis()}_$j",
            novelId = "",
            orderIndex = j,
            title = title,
            content = content,
            wordCount = wordCount ?: ChapterSplitter.countWords(content),
            scrollPosition = scroll,
            readingParagraphIndex = para,
            read = read,
            readAt = readAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun readSettings(reader: JsonReader): ReaderSettings? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); return null }
        var theme = "light"; var fontSize = 18; var lineHeight = 1.75; var fontFamily = "serif"
        var maxWidth = 720; var ttsRate = 1.0; var ttsPitch = 1.0; var ttsVoice = ""
        var continueReading = true; var replacementsEnabled = true; var split = "medium"
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "theme" -> theme = reader.stringOrNull() ?: theme
                "fontSize" -> fontSize = reader.longOr(fontSize.toLong()).toInt()
                "lineHeight" -> lineHeight = reader.doubleOr(lineHeight)
                "fontFamily" -> fontFamily = reader.stringOrNull() ?: fontFamily
                "maxWidth" -> maxWidth = reader.longOr(maxWidth.toLong()).toInt()
                "ttsRate" -> ttsRate = reader.doubleOr(ttsRate)
                "ttsPitch" -> ttsPitch = reader.doubleOr(ttsPitch)
                "ttsVoice" -> ttsVoice = reader.stringOrNull() ?: ""
                "continueReading" -> continueReading = reader.boolOr(true)
                "replacementsEnabled" -> replacementsEnabled = reader.boolOr(true)
                "splitLongParagraphs" -> split = reader.stringOrNull() ?: split
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ReaderSettings(
            theme = theme,
            fontSize = fontSize,
            lineHeight = lineHeight.toFloat(),
            fontFamily = fontFamily,
            maxWidth = maxWidth,
            ttsRate = ttsRate.toFloat(),
            ttsPitch = ttsPitch.toFloat(),
            ttsVoice = ttsVoice,
            continueReading = continueReading,
            replacementsEnabled = replacementsEnabled,
            splitLongParagraphs = split
        )
    }
}
