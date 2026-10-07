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

    @Test
    fun test_windows_path_separators() {
        val file = File.createTempFile("test_win_", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF\\container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS\content.opf"/></rootfiles></container>"""
            )
            add(
                "OEBPS\\content.opf",
                """<package><metadata><dc:title>Windows Book</dc:title></metadata>
                <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="c1"/></spine></package>"""
            )
            add("OEBPS\\c1.xhtml", "<html><body><p>Windows path chapter content.</p></body></html>")
        }
        try {
            val book = EpubParser.parseEpubFile(file)
            assertEquals("Windows Book", book.title)
            assertEquals(1, book.chapters.size)
            assertEquals("Windows path chapter content.", book.chapters[0].content)
        } finally {
            file.delete()
        }
    }

    @Test
    fun test_empty_spine_manifest_fallback() {
        val file = File.createTempFile("test_fallback_", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="content.opf"/></rootfiles></container>"""
            )
            add(
                "content.opf",
                """<package><metadata><dc:title>Spine-less Book</dc:title></metadata>
                <manifest>
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine></spine></package>"""
            )
            add("chapter1.xhtml", "<html><body><h1>First</h1><p>First text.</p></body></html>")
            add("chapter2.xhtml", "<html><body><h1>Second</h1><p>Second text.</p></body></html>")
        }
        try {
            val book = EpubParser.parseEpubFile(file)
            assertEquals("Spine-less Book", book.title)
            assertEquals(2, book.chapters.size)
            assertEquals("First text.", book.chapters[0].content)
            assertEquals("Second text.", book.chapters[1].content)
        } finally {
            file.delete()
        }
    }
}
