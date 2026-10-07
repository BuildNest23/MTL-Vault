package com.example.importexport

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ReaderSettings
import org.json.JSONArray
import org.json.JSONObject
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
}
