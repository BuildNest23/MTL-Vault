package com.example.importexport

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class EpubBook(
    val title: String,
    val author: String,
    val description: String,
    val chapters: List<SplitChapterResult>
)

object EpubParser {

    fun isEpubFile(fileNameOrPath: String?): Boolean {
        return fileNameOrPath?.lowercase()?.endsWith(".epub") == true
    }

    fun parseEpub(context: Context, uri: Uri): EpubBook {
        val tempFile = File.createTempFile("import_", ".epub", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

            return parseEpubFile(tempFile)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun parseEpubFile(file: File): EpubBook {
        val zipFile = ZipFile(file)
        try {
            // 1. Locate OPF file path from META-INF/container.xml
            val containerEntry = findZipEntry(zipFile, "META-INF/container.xml")
                ?: throw IllegalArgumentException("Invalid EPUB: META-INF/container.xml not found")

            val containerXml = zipFile.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val rawOpfPath = extractOpfPath(containerXml)
                ?: throw IllegalArgumentException("Invalid EPUB: could not find rootfile in container.xml")
            val opfPath = normalizePath(rawOpfPath)

            val opfEntry = findZipEntry(zipFile, opfPath)
                ?: throw IllegalArgumentException("Invalid EPUB: OPF file not found at $opfPath")

            val opfXml = zipFile.getInputStream(opfEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

            // 2. Parse metadata
            val title = extractTagContent(opfXml, "dc:title").ifBlank { file.nameWithoutExtension }
            val author = extractTagContent(opfXml, "dc:creator")
            val description = extractTagContent(opfXml, "dc:description")

            // 3. Parse manifest (id -> href)
            val manifestMap = mutableMapOf<String, String>()
            val manifestOrder = mutableListOf<String>()
            val itemMatcher = Pattern.compile("<item\\s+[^>]*?id=[\"']([^\"']+)[\"'][^>]*?href=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE).matcher(opfXml)
            while (itemMatcher.find()) {
                val id = itemMatcher.group(1)
                val href = itemMatcher.group(2)
                if (id != null && href != null) {
                    manifestMap[id] = href
                    if (!manifestOrder.contains(href)) manifestOrder.add(href)
                }
            }
            // Also check reversed attribute order href then id
            val itemMatcherRev = Pattern.compile("<item\\s+[^>]*?href=[\"']([^\"']+)[\"'][^>]*?id=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE).matcher(opfXml)
            while (itemMatcherRev.find()) {
                val href = itemMatcherRev.group(1)
                val id = itemMatcherRev.group(2)
                if (id != null && href != null && !manifestMap.containsKey(id)) {
                    manifestMap[id] = href
                    if (!manifestOrder.contains(href)) manifestOrder.add(href)
                }
            }

            // 4. Parse spine order
            val spineIds = mutableListOf<String>()
            val spineMatcher = Pattern.compile("<itemref\\s+[^>]*?idref=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE).matcher(opfXml)
            while (spineMatcher.find()) {
                val idref = spineMatcher.group(1)
                if (idref != null) {
                    spineIds.add(idref)
                }
            }

            // 5. Read chapters in spine order (or fallback to manifest HTML items if spine is empty/missing)
            val targetHrefs = if (spineIds.isNotEmpty()) {
                spineIds.mapNotNull { manifestMap[it] }
            } else {
                manifestOrder.filter { href ->
                    val lower = href.lowercase()
                    (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                        !lower.contains("toc.") && !lower.contains("nav.")
                }.ifEmpty {
                    manifestOrder.filter { href ->
                        val lower = href.lowercase()
                        lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                    }
                }
            }

            val chapters = mutableListOf<SplitChapterResult>()
            var chapterIndex = 1

            fun processHrefs(hrefs: List<String>) {
                for (rawHref in hrefs) {
                    // Clean anchor tags if href contains '#'
                    val cleanHref = if (rawHref.contains('#')) rawHref.substringBefore('#') else rawHref
                    // Escape "+" first: URLDecoder would otherwise turn it into a space (file names are not form data)
                    val decodedHref = try {
                        java.net.URLDecoder.decode(cleanHref.replace("+", "%2B"), "UTF-8")
                    } catch (e: IllegalArgumentException) {
                        cleanHref
                    }
                    val fullPath = if (decodedHref.startsWith("/")) {
                        normalizePath(decodedHref.removePrefix("/"))
                    } else {
                        normalizePath(opfDir + decodedHref)
                    }

                    val entry = findZipEntry(zipFile, fullPath) ?: continue

                    val html = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val (chapterTitle, chapterText) = parseHtmlChapter(html, "Chapter $chapterIndex")

                    if (chapterText.isNotBlank()) {
                        val finalTitle = if (chapterTitle.isNotBlank()) chapterTitle else "Chapter $chapterIndex"
                        chapters.add(
                            SplitChapterResult(
                                title = finalTitle,
                                content = chapterText,
                                wordCount = ChapterSplitter.countWords(chapterText)
                            )
                        )
                        chapterIndex++
                    }
                }
            }

            processHrefs(targetHrefs)

            // If spine was non-empty but failed to find chapters, fallback to manifest items
            if (chapters.isEmpty() && spineIds.isNotEmpty()) {
                val fallbackHrefs = manifestOrder.filter { href ->
                    val lower = href.lowercase()
                    (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                        !lower.contains("toc.") && !lower.contains("nav.")
                }.ifEmpty {
                    manifestOrder.filter { href ->
                        val lower = href.lowercase()
                        lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
                    }
                }
                processHrefs(fallbackHrefs)
            }

            return EpubBook(
                title = cleanHtmlEntities(title),
                author = cleanHtmlEntities(author),
                description = cleanHtmlEntities(description),
                chapters = chapters
            )
        } finally {
            try {
                zipFile.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun extractOpfPath(containerXml: String): String? {
        val matcher = Pattern.compile("<rootfile\\s+[^>]*?full-path=[\"']([^\"']+)[\"'][^>]*?>", Pattern.CASE_INSENSITIVE).matcher(containerXml)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractTagContent(xml: String, tagName: String): String {
        val pattern = Pattern.compile("<$tagName\\b[^>]*>(.*?)</$tagName>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) {
            matcher.group(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
        } else {
            ""
        }
    }

    private fun parseHtmlChapter(html: String, defaultTitle: String): Pair<String, String> {
        // Extract title from <title>, <h1>, <h2> or <h3>
        var title = ""
        val h1Matcher = Pattern.compile("<h[1-2]\\b[^>]*>(.*?)</h[1-2]>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
        if (h1Matcher.find()) {
            title = h1Matcher.group(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
        }
        if (title.isBlank()) {
            val titleMatcher = Pattern.compile("<title\\b[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
            if (titleMatcher.find()) {
                title = titleMatcher.group(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""
            }
        }

        // Clean text formatting
        var text = html
        // Remove <script> and <style>
        text = text.replace(Regex("(?is)<script.*?</script>"), "")
        text = text.replace(Regex("(?is)<style.*?</style>"), "")
        // Remove <head> (its <title> text would otherwise leak into the body) and comments
        text = text.replace(Regex("(?is)<head\\b[^>]*>.*?</head>"), "")
        text = text.replace(Regex("(?is)<title\\b[^>]*>.*?</title>"), "")
        text = text.replace(Regex("(?s)<!--.*?-->"), "")
        // Remove the first heading: it is already used as the chapter title and the
        // reader renders the title separately, so keeping it would duplicate it.
        text = text.replaceFirst(Regex("(?is)<h[1-2]\\b[^>]*>.*?</h[1-2]>"), "")

        // Replace line break tags
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote)>"), "\n\n")

        // Strip remaining HTML tags
        text = text.replace(Regex("<[^>]+>"), "")

        // Unescape entities
        text = cleanHtmlEntities(text)

        // Normalize multiple spaces/newlines
        val paragraphs = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        val finalTitle = cleanHtmlEntities(title).ifBlank { defaultTitle }

        // Drop leading lines that merely repeat the chapter title (e.g. "<p>Chapter 2448</p>"
        // or a heading that was not h1/h2), so the title is only shown once by the reader.
        val normTitle = normalizeForCompare(finalTitle)
        while (paragraphs.isNotEmpty() && normalizeForCompare(paragraphs.first()) == normTitle) {
            paragraphs.removeAt(0)
        }

        val cleanContent = paragraphs.joinToString("\n\n")

        return Pair(finalTitle, cleanContent)
    }

    private fun normalizeForCompare(s: String): String =
        s.trim().trimStart('#').trim().replace(Regex("\\s+"), " ").lowercase()

    fun cleanHtmlEntities(input: String): String {
        if (!input.contains('&') && !input.contains('\u00A0')) return input
        // "&amp;" is decoded LAST so "&amp;lt;" correctly becomes "&lt;" and not "<".
        var s = input
            .replace("\u00A0", " ")
            .replace("&nbsp;", " ")
            .replace("&ensp;", " ")
            .replace("&emsp;", " ")
            .replace("&thinsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")

        // Numeric entities &#123; (code points, so emoji above U+FFFF work)
        s = s.replace(Regex("&#(\\d+);")) { match ->
            codePointToString(match.groupValues[1].toIntOrNull(), match.value)
        }
        // Hex entities &#x1F;
        s = s.replace(Regex("&#[xX]([0-9a-fA-F]+);")) { match ->
            codePointToString(match.groupValues[1].toIntOrNull(16), match.value)
        }
        return s.replace("&amp;", "&").replace("\u00A0", " ")
    }

    private fun codePointToString(cp: Int?, original: String): String {
        if (cp == null || cp == 0 || !Character.isValidCodePoint(cp) || cp in 0xD800..0xDFFF) return original
        return String(Character.toChars(cp))
    }

    private fun findZipEntry(zipFile: ZipFile, targetPath: String): ZipEntry? {
        val normTarget = normalizePath(targetPath).removePrefix("/")
        zipFile.getEntry(normTarget)?.let { return it }
        zipFile.getEntry(normTarget.replace('/', '\\'))?.let { return it }
        return zipFile.entries().asSequence().find { entry ->
            val normName = normalizePath(entry.name).removePrefix("/")
            normName.equals(normTarget, ignoreCase = true)
        }
    }

    private fun normalizePath(path: String): String {
        val normalized = path.replace('\\', '/')
        val parts = normalized.split('/')
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
