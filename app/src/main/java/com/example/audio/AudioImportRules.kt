package com.example.audio

/** What a file found during import turns out to be. */
enum class FileKind {
    /** An audio file the player can play. */
    AUDIO,

    /** Looks like audio but the Android player can't play it (for example .wma or protected Audible files). */
    UNSUPPORTED_AUDIO,

    /** A .cue sheet that may describe chapters. */
    CUE,

    /** A picture that may be the book cover. */
    IMAGE,

    /** Anything else. Ignored. */
    OTHER
}

/**
 * One audio file found by the importer, before the book is saved.
 *
 * @param uri content:// address of the file
 * @param path name of the file, with its folders if it came from a folder import
 *        (for example "CD 1/01 Intro.mp3"). Used for sorting.
 * @param name the file name alone
 * @param tagTitle title stored inside the file's tags, if any
 */
data class DraftFile(
    val uri: String,
    val path: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val tagTitle: String? = null
)

/**
 * The plain rules the importer follows: which files count as audio, how to put them in
 * natural order, and how to turn file and folder names into readable titles.
 *
 * No Android classes are used here, so every rule can be tested on its own.
 */
object AudioImportRules {

    /** Formats the player can play. */
    private val audioExtensions = setOf(
        "mp3", "m4b", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "mp4"
    )

    /** Audio formats Android can't play, so the importer can say why a file was skipped. */
    private val unsupportedExtensions = setOf("wma", "aax", "aa", "ape", "wv", "ra", "rm")

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    /** Cover file names in order of preference. */
    private val coverNames = listOf("cover", "folder", "front", "album", "art", "albumart")

    // ============================================================ File kinds

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /**
     * Decides what a file is from its name and, when the name is not clear, its MIME type.
     * File names from the picker are not always trustworthy, so both are used.
     */
    fun classify(name: String, mimeType: String?): FileKind {
        val ext = extensionOf(name)
        val mime = mimeType.orEmpty().lowercase()

        if (ext == "cue") return FileKind.CUE
        if (ext in unsupportedExtensions) return FileKind.UNSUPPORTED_AUDIO
        if (mime.contains("wma") || mime == "audio/x-ms-wma") return FileKind.UNSUPPORTED_AUDIO

        if (ext == "mp4" && mime.startsWith("video/")) return FileKind.OTHER
        if (ext in audioExtensions) return FileKind.AUDIO
        if (ext in imageExtensions || mime.startsWith("image/")) return FileKind.IMAGE
        if (mime.startsWith("audio/")) return FileKind.AUDIO
        return FileKind.OTHER
    }

    /**
     * Picks the best cover from a list of image file names: a file called cover, folder,
     * front or album first, otherwise the first image in name order. Returns its index,
     * or null if the list is empty.
     */
    fun pickCover(imageNames: List<String>): Int? {
        if (imageNames.isEmpty()) return null
        for (wanted in coverNames) {
            val index = imageNames.indexOfFirst { name ->
                name.substringBeforeLast('.').trim().lowercase() == wanted
            }
            if (index >= 0) return index
        }
        return imageNames.indices.minWithOrNull { a, b -> naturalCompare(imageNames[a], imageNames[b]) }
    }

    // ============================================================ Ordering

    /**
     * Compares names the way a person would: "Chapter 2" before "Chapter 10", ignoring
     * upper and lower case, and treating "01" and "1" as the same number.
     * Names that are otherwise equal are put in a fixed order so sorting never flips.
     */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca in '0'..'9' && cb in '0'..'9') {
                var ie = i
                while (ie < a.length && a[ie] in '0'..'9') ie++
                var je = j
                while (je < b.length && b[je] in '0'..'9') je++
                val na = a.substring(i, ie).trimStart('0')
                val nb = b.substring(j, je).trimStart('0')
                if (na.length != nb.length) return na.length.compareTo(nb.length)
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ie
                j = je
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i).compareTo(b.length - j)
        if (rest != 0) return rest
        return a.compareTo(b)
    }

    /** Files in natural order of their path, so "CD 1/..." comes before "CD 2/...". */
    fun sortNaturally(files: List<DraftFile>): List<DraftFile> =
        files.sortedWith { x, y -> naturalCompare(x.path, y.path) }

    // ============================================================ Titles

    /** "01 - Intro", "3. Rain", "07)Storm": a number followed by a separator is always a track number. */
    private val leadingTrackNumberTight = Regex("^\\s*\\d{1,4}\\s*[-_.)]+\\s*(?=\\S)")

    /**
     * "01 Intro": a number with only a space after it counts as a track number only when it is
     * zero-padded. That keeps titles like "12 Angry Men" intact.
     */
    private val leadingTrackNumberPadded = Regex("^\\s*0\\d{1,3}\\s+(?=\\S)")

    private val trailingLabel = Regex(
        "[\\s\\-_.,:]*\\b(chapter|part|track|disc|disk|cd|ch|pt)\\.?$",
        RegexOption.IGNORE_CASE
    )
    private val chapterNumberTitle = Regex("^Chapter \\d+$")

    /**
     * Turns a file or folder name into a readable title: removes the extension (files only),
     * swaps underscores for spaces, drops a leading track number such as "01 - ", and calls
     * a name that is only a number "Chapter N".
     */
    fun cleanTitle(raw: String, isFile: Boolean = true): String {
        var text = raw.trim()
        if (isFile) {
            val ext = extensionOf(text)
            if (ext.isNotEmpty() && ext.length <= 5 && ext.all { it.isLetterOrDigit() }) {
                text = text.substring(0, text.length - ext.length - 1)
            }
        }
        text = text.replace('_', ' ').replace(Regex("\\s+"), " ").trim()

        val digitsOnly = text.isNotEmpty() && text.all { it in '0'..'9' }
        if (digitsOnly) {
            val number = text.trimStart('0').ifEmpty { "0" }
            return "Chapter $number"
        }

        for (pattern in listOf(leadingTrackNumberTight, leadingTrackNumberPadded)) {
            val stripped = text.replaceFirst(pattern, "").trim()
            if (stripped != text && stripped.any { it.isLetter() }) {
                text = stripped
                break
            }
        }
        return text
    }

    /**
     * A title for the whole book. A folder name wins; for loose files the part the file
     * names share is used ("Dune 01", "Dune 02" gives "Dune"), and otherwise the first file's name.
     */
    fun guessBookTitle(folderName: String?, fileNames: List<String>): String {
        val folder = folderName?.let { cleanTitle(it, isFile = false) }.orEmpty()
        if (folder.isNotBlank()) return folder
        if (fileNames.isEmpty()) return "Audiobook"
        if (fileNames.size == 1) return singleFileTitle(fileNames[0])

        val stems = fileNames.map { name ->
            cleanTitle(name).replace(chapterNumberTitle, "")
        }
        var prefix = commonPrefix(stems).trimEnd(' ', '-', '_', '.', ',', ':', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        // Drop a trailing label such as "Chapter" or "Part", then any separator left over.
        while (true) {
            val shorter = prefix.replace(trailingLabel, "")
            if (shorter == prefix) break
            prefix = shorter
        }
        prefix = prefix.trimEnd(' ', '-', '_', '.', ',', ':').trim()
        if (prefix.length >= 3) return prefix

        // Several files whose names are only numbers say nothing about the book.
        val first = cleanTitle(fileNames[0])
        return if (chapterNumberTitle.matches(first)) "Audiobook" else first.ifBlank { "Audiobook" }
    }

    /** Book title from one file name. A name that is only a number, like "1984", stays as it is. */
    private fun singleFileTitle(fileName: String): String {
        val cleaned = cleanTitle(fileName)
        if (chapterNumberTitle.matches(cleaned)) {
            val raw = fileName.trim().replace(Regex("\\.[A-Za-z0-9]{1,5}$"), "").trim()
            return raw.ifBlank { "Audiobook" }
        }
        return cleaned.ifBlank { "Audiobook" }
    }

    private fun commonPrefix(items: List<String>): String {
        if (items.isEmpty()) return ""
        var prefix = items[0]
        for (item in items) {
            var n = 0
            while (n < prefix.length && n < item.length && prefix[n].lowercaseChar() == item[n].lowercaseChar()) n++
            prefix = prefix.substring(0, n)
            if (prefix.isEmpty()) break
        }
        return prefix
    }

    /**
     * Chapter titles for a book made of one file per chapter.
     *
     * Titles stored in the file tags are used only when every file has a different one.
     * Many audiobooks put the book title in every file, which would make all chapters look
     * the same, so then the cleaned file names are used instead.
     */
    fun chooseChapterTitles(fileNames: List<String>, tagTitles: List<String?>): List<String> {
        val tags = fileNames.indices.map { tagTitles.getOrNull(it)?.trim().orEmpty() }
        val usable = tags.all { it.isNotEmpty() } &&
            tags.map { it.lowercase() }.toSet().size == tags.size
        return if (usable) tags else fileNames.map { cleanTitle(it) }
    }
}
