package com.example.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    private fun buildEpub(chapterHtml: String): File {
        val file = File.createTempFile("test_", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>"""
            )
            add(
                "OEBPS/content.opf",
                """<package><metadata><dc:title>Book</dc:title></metadata>
                <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/></spine></package>"""
            )
            add("OEBPS/c1.xhtml", chapterHtml)
        }
        return file
    }

    @Test
    fun title_is_not_duplicated_in_chapter_content() {
        val html = """<?xml version="1.0" encoding="UTF-8"?>
<html><head><title>Chapter 2448</title></head>
<body><h1>Chapter 2448</h1>
<p>“Oh…” Robin opened his mouth slightly.</p>
<p>Second paragraph.</p></body></html>"""
        val file = buildEpub(html)
        try {
            val chapter = EpubParser.parseEpubFile(file).chapters.single()
            assertEquals("Chapter 2448", chapter.title)
            assertFalse(chapter.content.contains("Chapter 2448"))
            assertEquals(
                "“Oh…” Robin opened his mouth slightly.\n\nSecond paragraph.",
                chapter.content
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun repeated_title_paragraph_is_removed() {
        val html = """<html><head><title>Chapter 7</title></head>
<body><h2>Chapter 7</h2><p>Chapter 7</p><p>Body text.</p></body></html>"""
        val file = buildEpub(html)
        try {
            val chapter = EpubParser.parseEpubFile(file).chapters.single()
            assertEquals("Body text.", chapter.content)
        } finally {
            file.delete()
        }
    }
}
