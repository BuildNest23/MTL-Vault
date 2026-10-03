package com.example.importexport

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import java.io.OutputStream
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

        ZipOutputStream(outputStream).use { zip ->
            // 1. mimetype (first entry, STORED, uncompressed)
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                val crc = CRC32()
                crc.update(mimetypeBytes)
                setCrc(crc.value)
            }
            zip.putNextEntry(mimetypeEntry)
            zip.write(mimetypeBytes)
            zip.closeEntry()

            // 2. META-INF/container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent()
            zip.write(containerXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 3. Chapter XHTML files
            val manifestItems = StringBuilder()
            val spineItemrefs = StringBuilder()
            val navPoints = StringBuilder()

            sortedChapters.forEachIndexed { index, chapter ->
                val chId = "ch_${index + 1}"
                val chFile = "$chId.xhtml"
                manifestItems.append("    <item id=\"$chId\" href=\"$chFile\" media-type=\"application/xhtml+xml\"/>\n")
                spineItemrefs.append("    <itemref idref=\"$chId\"/>\n")

                navPoints.append("""
                    <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
                      <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>
                      <content src="$chFile"/>
                    </navPoint>
                """.trimIndent()).append("\n")

                // Write chapter XHTML
                zip.putNextEntry(ZipEntry("OEBPS/$chFile"))
                val xhtmlContent = buildChapterXhtml(chapter)
                zip.write(xhtmlContent.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // 4. OEBPS/toc.ncx
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val ncxXml = """
                <?xml version="1.0" encoding="UTF-8"?>
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
                </ncx>
            """.trimIndent()
            zip.write(ncxXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 5. OEBPS/content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opfXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookID" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>${escapeXml(novel.title)}</dc:title>
                    <dc:creator opf:role="aut">${escapeXml(novel.author.ifBlank { "Unknown" })}</dc:creator>
                    <dc:description>${escapeXml(novel.description)}</dc:description>
                    <dc:language>en</dc:language>
                    <dc:identifier id="BookID" opf:scheme="UUID">$bookUuid</dc:identifier>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                $manifestItems  </manifest>
                  <spine toc="ncx">
                $spineItemrefs  </spine>
                </package>
            """.trimIndent()
            zip.write(opfXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.finish()
        }
    }

    private fun buildChapterXhtml(chapter: ChapterEntity): String {
        val paragraphs = chapter.content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${escapeXml(it)}</p>" }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
              <head>
                <title>${escapeXml(chapter.title)}</title>
                <style type="text/css">
                  body {
                    font-family: serif;
                    margin: 1.5em;
                    line-height: 1.6;
                  }
                  h1 {
                    text-align: center;
                    margin-top: 1em;
                    margin-bottom: 1.5em;
                    font-size: 1.5em;
                  }
                  p {
                    margin: 0 0 1em 0;
                    text-indent: 1.5em;
                  }
                </style>
              </head>
              <body>
                <h1>${escapeXml(chapter.title)}</h1>
                $paragraphs
              </body>
            </html>
        """.trimIndent()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
