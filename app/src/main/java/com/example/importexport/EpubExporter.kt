package com.example.importexport

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubExporter {

    fun exportToEpub(
        novel: NovelEntity,
        chapters: List<ChapterEntity>,
        outputStream: OutputStream
    ) {
        val sortedChapters = chapters.sortedBy { it.orderIndex }
        val bookUuid = "urn:uuid:" + UUID.randomUUID().toString()

        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val modifiedDate = utcFormat.format(Date())

        // Detect overall novel language and direction
        val sampleText = buildString {
            append(novel.title).append(" ")
            append(novel.description).append(" ")
            sortedChapters.take(3).forEach {
                append(it.title).append(" ")
                append(it.content.take(300)).append(" ")
            }
        }
        val isOverallRtl = isRtlText(sampleText)
        val novelLanguage = detectLanguage(sampleText)

        ZipOutputStream(outputStream).use { zip ->
            // 1. mimetype (first entry, STORED uncompressed, exactly 20 bytes, no extra fields)
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimetypeBytes)
                setCrc(crc.value)
                extra = ByteArray(0)
            }
            zip.putNextEntry(mimetypeEntry)
            zip.write(mimetypeBytes)
            zip.closeEntry()

            // 2. META-INF/container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent()
            zip.write(containerXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 3. OEBPS/stylesheet.css
            zip.putNextEntry(ZipEntry("OEBPS/stylesheet.css"))
            val cssContent = """
@charset "utf-8";
body {
  margin: 5%;
  padding: 0;
  font-family: serif;
  line-height: 1.6;
}
h1 {
  text-align: center;
  margin-top: 1.2em;
  margin-bottom: 1.5em;
  font-size: 1.5em;
  font-weight: bold;
}
p {
  margin: 0 0 1em 0;
  text-indent: 1.5em;
  text-align: justify;
}
p[dir="rtl"], h1[dir="rtl"] {
  direction: rtl;
  text-align: right;
  text-indent: 1.5em;
  font-family: "Noto Nastaliq Urdu", "Amiri", "Scheherazade", serif;
}
p[dir="ltr"], h1[dir="ltr"] {
  direction: ltr;
  text-align: left;
}
nav#toc ol {
  list-style-type: decimal;
  padding-left: 2em;
}
nav#toc li {
  margin: 0.5em 0;
}
nav#toc a {
  text-decoration: none;
}
""".trimIndent()
            zip.write(cssContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 4. Chapter XHTML files
            val manifestItems = StringBuilder()
            val spineItemrefs = StringBuilder()
            val navPoints = StringBuilder()
            val navListItems = StringBuilder()

            sortedChapters.forEachIndexed { index, chapter ->
                val chId = "ch_${index + 1}"
                val chFile = "$chId.xhtml"
                manifestItems.append("    <item id=\"$chId\" href=\"$chFile\" media-type=\"application/xhtml+xml\"/>\n")
                spineItemrefs.append("    <itemref idref=\"$chId\"/>\n")

                val escapedTitle = escapeXml(chapter.title)
                navPoints.append("""
    <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
      <navLabel><text>$escapedTitle</text></navLabel>
      <content src="$chFile"/>
    </navPoint>
""".trimIndent()).append("\n")

                val chRtlAttr = if (isRtlText(chapter.title)) " dir=\"rtl\"" else ""
                navListItems.append("      <li><a href=\"$chFile\"$chRtlAttr>$escapedTitle</a></li>\n")

                // Write chapter XHTML
                zip.putNextEntry(ZipEntry("OEBPS/$chFile"))
                val xhtmlContent = buildChapterXhtml(chapter, novelLanguage)
                zip.write(xhtmlContent.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // 5. OEBPS/nav.xhtml (EPUB 3 Navigation Document required by @Voice)
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            val navXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$novelLanguage" lang="$novelLanguage"${if (isOverallRtl) " dir=\"rtl\"" else ""}>
  <head>
    <meta charset="utf-8"/>
    <title>${escapeXml(novel.title)} - Table of Contents</title>
    <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
  </head>
  <body>
    <nav epub:type="toc" id="toc">
      <h1>Table of Contents</h1>
      <ol>
$navListItems      </ol>
    </nav>
  </body>
</html>""".trimIndent()
            zip.write(navXhtml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 6. OEBPS/toc.ncx (EPUB 2 Navigation Control Document)
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val ncxXml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$bookUuid"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle><text>${escapeXml(novel.title)}</text></docTitle>
  <docAuthor><text>${escapeXml(novel.author.ifBlank { "Unknown" })}</text></docAuthor>
  <navMap>
$navPoints  </navMap>
</ncx>""".trimIndent()
            zip.write(ncxXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 7. OEBPS/content.opf (EPUB 3 / EPUB 2 Dual Compatibility)
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val author = escapeXml(novel.author.ifBlank { "Unknown" })
            val description = escapeXml(novel.description)
            val title = escapeXml(novel.title)

            val opfXml = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="pub-id" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">$bookUuid</dc:identifier>
    <dc:title>$title</dc:title>
    <dc:language>$novelLanguage</dc:language>
    <dc:creator id="creator">$author</dc:creator>
    <dc:description>$description</dc:description>
    <meta property="dcterms:modified">$modifiedDate</meta>
  </metadata>
  <manifest>
    <item id="style" href="stylesheet.css" media-type="text/css"/>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
$manifestItems  </manifest>
  <spine toc="ncx">
$spineItemrefs  </spine>
</package>""".trimIndent()
            zip.write(opfXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.finish()
        }
    }

    private fun buildChapterXhtml(chapter: ChapterEntity, defaultLanguage: String): String {
        val chapterRtl = isRtlText(chapter.title + " " + chapter.content.take(500))
        val chapterLang = if (chapterRtl) detectLanguage(chapter.title + " " + chapter.content.take(500)) else defaultLanguage

        val paragraphs = chapter.content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { para ->
                val pRtl = isRtlText(para)
                val dirAttr = if (pRtl) " dir=\"rtl\"" else if (chapterRtl) " dir=\"ltr\"" else ""
                "<p$dirAttr>${escapeXml(para)}</p>"
            }

        val h1DirAttr = if (isRtlText(chapter.title)) " dir=\"rtl\"" else ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$chapterLang" lang="$chapterLang"${if (chapterRtl) " dir=\"rtl\"" else ""}>
  <head>
    <meta charset="utf-8"/>
    <title>${escapeXml(chapter.title)}</title>
    <link rel="stylesheet" type="text/css" href="stylesheet.css"/>
  </head>
  <body>
    <h1$h1DirAttr>${escapeXml(chapter.title)}</h1>
$paragraphs
  </body>
</html>""".trimIndent()
    }

    fun isRtlText(text: String): Boolean {
        var rtlCount = 0
        var ltrCount = 0
        for (ch in text) {
            val code = ch.code
            if ((code in 0x0590..0x08FF) || (code in 0xFB1D..0xFEFC)) {
                rtlCount++
            } else if ((code in 0x0041..0x005A) || (code in 0x0061..0x007A) || (code in 0x00C0..0x024F)) {
                ltrCount++
            }
        }
        return rtlCount > ltrCount && rtlCount > 0
    }

    fun detectLanguage(text: String): String {
        var arabicCount = 0
        var urduSpecificCount = 0
        var latinCount = 0

        for (ch in text) {
            val code = ch.code
            if (code in listOf(0x067E, 0x0686, 0x0698, 0x06AF, 0x0679, 0x0688, 0x0691, 0x06BA, 0x06BE, 0x06C1, 0x06D2)) {
                urduSpecificCount++
            } else if (code in 0x0600..0x06FF || code in 0x0750..0x077F) {
                arabicCount++
            } else if ((code in 0x0041..0x005A) || (code in 0x0061..0x007A)) {
                latinCount++
            }
        }

        return when {
            urduSpecificCount > 0 -> "ur"
            arabicCount > latinCount -> "ar"
            else -> "en"
        }
    }

    fun sanitizeXmlText(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            // Keep valid surrogate pairs (emoji, rare CJK); drop only lone surrogates
            if (Character.isHighSurrogate(ch) && i + 1 < text.length && Character.isLowSurrogate(text[i + 1])) {
                sb.append(ch).append(text[i + 1])
                i += 2
                continue
            }
            val code = ch.code
            // Valid XML 1.0 characters
            if (code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) ||
                (code in 0xE000..0xFFFD)
            ) {
                sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    fun escapeXml(text: String): String {
        val sanitized = sanitizeXmlText(text)
        return buildString(sanitized.length) {
            for (ch in sanitized) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
