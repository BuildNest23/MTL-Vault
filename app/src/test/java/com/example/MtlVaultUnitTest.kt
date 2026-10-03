package com.example

import com.example.data.database.entities.ChapterEntity
import com.example.data.database.entities.NovelEntity
import com.example.data.model.ReaderSettings
import com.example.importexport.BackupRestoreManager
import com.example.importexport.ChapterSplitter
import com.example.importexport.NovelWithChaptersBackup
import com.example.replacement.ReplacementEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MtlVaultUnitTest {

    @Test
    fun testReplacementEngineRulesParsing() {
        val rulesString = """
            # This is a comment
            Elder = Senior
            [cs] Lord = Lord God
            [nww] mana = Mana
            [cs,nww] qi = Qi
        """.trimIndent()

        val rules = ReplacementEngine.parseRules(rulesString)
        assertEquals(4, rules.size)

        // Rule 1: Whole word, case insensitive
        assertEquals("Elder", rules[0].find)
        assertEquals("Senior", rules[0].replace)
        assertFalse(rules[0].caseSensitive)
        assertTrue(rules[0].wholeWord)

        // Rule 2: Case sensitive, whole word
        assertEquals("Lord", rules[1].find)
        assertEquals("Lord God", rules[1].replace)
        assertTrue(rules[1].caseSensitive)
        assertTrue(rules[1].wholeWord)

        // Rule 3: Case insensitive, substring (not whole word)
        assertEquals("mana", rules[2].find)
        assertEquals("Mana", rules[2].replace)
        assertFalse(rules[2].caseSensitive)
        assertFalse(rules[2].wholeWord)

        // Rule 4: Both
        assertEquals("qi", rules[3].find)
        assertEquals("Qi", rules[3].replace)
        assertTrue(rules[3].caseSensitive)
        assertFalse(rules[3].wholeWord)
    }

    @Test
    fun testReplacementEngineApplication() {
        val rulesString = """
            Elder = Senior
            [cs] Lord = Lord God
            [nww] mana = Mana
        """.trimIndent()

        val rules = ReplacementEngine.parseRules(rulesString)
        val input = "The elder greeted the Lord and another lord with his emanated mana."
        val output = ReplacementEngine.applyRules(input, rules)

        // "elder" -> "Senior"
        // "Lord" -> "Lord God"
        // "lord" -> unchanged (case-sensitive)
        // "emanated" -> contains "mana" inside, replaced because of [nww] -> "eManated"
        assertTrue(output.contains("The Senior greeted the Lord God and another lord"))
        assertTrue(output.contains("eManated Mana"))
    }

    @Test
    fun testChapterSplitterAutoRegex() {
        val text = """
            Chapter 1: The Awakening
            Lin walked into the foggy forest.

            Chapter 2: The First Encounter
            A strange beast appeared from the shadows.

            Chapter 3: Returning Home
            The beast was defeated.
        """.trimIndent()

        val chapters = ChapterSplitter.splitText(text, ChapterSplitter.SplitMode.AUTO_CHAPTER)
        assertEquals(3, chapters.size)
        assertEquals("Chapter 1: The Awakening", chapters[0].title)
        assertEquals("Chapter 2: The First Encounter", chapters[1].title)
        assertEquals("Chapter 3: Returning Home", chapters[2].title)
    }

    @Test
    fun testChapterSplitterCustomMarker() {
        val text = """
            Middle Sector 1
            First section content.

            Middle Sector 2
            Second section content.
        """.trimIndent()

        val chapters = ChapterSplitter.splitText(
            text = text,
            mode = ChapterSplitter.SplitMode.CUSTOM_MARKER,
            customMarker = "Middle Sector"
        )
        assertEquals(2, chapters.size)
        assertEquals("Middle Sector 1", chapters[0].title)
        assertEquals("Middle Sector 2", chapters[1].title)
    }

    @Test
    fun testBackupRestoreRoundTrip() {
        val novel = NovelEntity(
            id = "n_1",
            title = "Test Novel",
            author = "Author A",
            replacements = "A = B"
        )
        val chapter = ChapterEntity(
            id = "c_1",
            novelId = "n_1",
            orderIndex = 0,
            title = "Chapter 1",
            content = "Hello world",
            wordCount = 2
        )
        val settings = ReaderSettings(
            theme = "sepia",
            fontSize = 20
        )

        val json = BackupRestoreManager.generateBackupJson(
            listOf(NovelWithChaptersBackup(novel, listOf(chapter))),
            settings
        )

        val restored = BackupRestoreManager.parseBackupJson(json)
        assertEquals(1, restored.novels.size)
        assertEquals("Test Novel", restored.novels[0].novel.title)
        assertEquals(1, restored.novels[0].chapters.size)
        assertEquals("Chapter 1", restored.novels[0].chapters[0].title)
        assertEquals("sepia", restored.settings?.theme)
        assertEquals(20, restored.settings?.fontSize)
    }

    @Test
    fun testEpubParser() {
        val tempZip = java.io.File.createTempFile("test_novel", ".epub")
        try {
            java.util.zip.ZipOutputStream(tempZip.outputStream()).use { zos ->
                // 1. mimetype
                zos.putNextEntry(java.util.zip.ZipEntry("mimetype"))
                zos.write("application/epub+zip".toByteArray())
                zos.closeEntry()

                // 2. container.xml
                zos.putNextEntry(java.util.zip.ZipEntry("META-INF/container.xml"))
                val containerXml = """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                       <rootfiles>
                          <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                       </rootfiles>
                    </container>
                """.trimIndent()
                zos.write(containerXml.toByteArray())
                zos.closeEntry()

                // 3. content.opf
                zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/content.opf"))
                val opfXml = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Cultivation Online</dc:title>
                        <dc:creator>MyNovelWriter</dc:creator>
                        <dc:description>A virtual world meets cultivation.</dc:description>
                      </metadata>
                      <manifest>
                        <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="ch1"/>
                        <itemref idref="ch2"/>
                      </spine>
                    </package>
                """.trimIndent()
                zos.write(opfXml.toByteArray())
                zos.closeEntry()

                // 4. ch1.xhtml
                zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/ch1.xhtml"))
                val ch1Xml = """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter 1: The First Step</title></head>
                      <body>
                        <h1>Chapter 1: The First Step</h1>
                        <p>Lin Tian opened his eyes in the serene courtyard.</p>
                        <p>A soft breeze brushed against the bamboo &amp; pine trees.</p>
                      </body>
                    </html>
                """.trimIndent()
                zos.write(ch1Xml.toByteArray())
                zos.closeEntry()

                // 5. ch2.xhtml
                zos.putNextEntry(java.util.zip.ZipEntry("OEBPS/ch2.xhtml"))
                val ch2Xml = """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter 2: The Breakthrough</title></head>
                      <body>
                        <h1>Chapter 2: The Breakthrough</h1>
                        <p>Spiritual energy gathered around his dantian.</p>
                      </body>
                    </html>
                """.trimIndent()
                zos.write(ch2Xml.toByteArray())
                zos.closeEntry()
            }

            val epub = com.example.importexport.EpubParser.parseEpubFile(tempZip)
            assertEquals("Cultivation Online", epub.title)
            assertEquals("MyNovelWriter", epub.author)
            assertEquals("A virtual world meets cultivation.", epub.description)
            assertEquals(2, epub.chapters.size)
            assertEquals("Chapter 1: The First Step", epub.chapters[0].title)
            assertTrue(epub.chapters[0].content.contains("bamboo & pine trees"))
            assertEquals("Chapter 2: The Breakthrough", epub.chapters[1].title)
            assertTrue(epub.chapters[1].content.contains("dantian"))
        } finally {
            tempZip.delete()
        }
    }
}
