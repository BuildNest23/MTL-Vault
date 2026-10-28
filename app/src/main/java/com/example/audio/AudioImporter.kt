package com.example.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.database.entities.AudioBookEntity
import com.example.data.database.entities.AudioChapterEntity
import com.example.data.database.entities.AudioFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * What the import screen shows before anything is saved: the book's details and the files
 * found, in the order they will play. The screen lets the user edit the title or reorder
 * the files by changing this object with copy().
 *
 * @param sourceTreeUri the folder the book came from, or null if single files were picked
 * @param cueText text of a matching .cue sheet, only set when the book is one file
 * @param coverUri a cover picture found next to the audio, or null
 * @param embeddedChapterCount for a one-file book, how many chapters were found inside it
 *        (or in its .cue sheet). 0 means none were found.
 * @param skippedNames files that were left out because the player can't play them
 */
data class ImportDraft(
    val title: String,
    val author: String = "",
    val narrator: String = "",
    val files: List<DraftFile>,
    val sourceTreeUri: String? = null,
    val cueText: String? = null,
    val coverUri: String? = null,
    val embeddedChapterCount: Int = 0,
    val skippedNames: List<String> = emptyList(),
    val truncated: Boolean = false
) {
    val totalDurationMs: Long get() = files.sumOf { it.durationMs }
}

/** A finished book, ready to be saved with AudioRepository.addBook(book, files, chapters). */
data class ImportedBook(
    val book: AudioBookEntity,
    val files: List<AudioFileEntity>,
    val chapters: List<AudioChapterEntity>
)

/**
 * Turns a picked folder or a set of picked files into a book in two steps, so the user can
 * look at the result before it is saved:
 *
 *  1. [scanFolder] / [scanFiles] find the audio, read lengths and tags, and guess the title.
 *     This gives an [ImportDraft] for the import screen.
 *  2. [build] turns the (possibly edited) draft into database rows, working out the chapters.
 *
 * Chapters follow the options given to [build]. By default each file is kept whole: a single
 * file becomes one chapter, and a book of many files gets one chapter per file. Chapter marks
 * stored inside a file are used when present unless turned off. Splitting by time applies
 * only to a book that is a single file.
 *
 * The audio is never copied. The importer keeps Android's read permission for the picked
 * folder or files so the book can still be played after the app restarts.
 *
 * All functions run on a background thread and are safe to call from a coroutine.
 */
class AudioImporter(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val reader = AudioFileReader(appContext)
    private val scanner = AudioFolderScanner(appContext)

    // ====================================================== Step 1: look at the files

    /** Looks through a folder (including subfolders) and prepares a draft. */
    suspend fun scanFolder(treeUri: Uri): ImportDraft = withContext(Dispatchers.IO) {
        keepPermission(treeUri)
        val scan = scanner.scan(treeUri)

        val read = scan.audio.map { entry ->
            readDraft(entry.uri, entry.relativePath, entry.name, entry.mimeType, entry.sizeBytes)
        }
        val files = read.map { it.first }
        val firstTags = read.firstOrNull()?.second

        val cueText = pickCueText(scan.audio, scan.cues)
        draftFrom(
            files = files,
            firstTags = firstTags,
            folderName = scan.folderName,
            sourceTreeUri = treeUri.toString(),
            cueText = cueText,
            coverUri = scan.bestCover()?.uri?.toString(),
            skippedNames = scan.unsupported.map { it.name },
            truncated = scan.truncated
        )
    }

    /** Prepares a draft from files picked one by one. A picked .cue sheet is used if there is exactly one audio file. */
    suspend fun scanFiles(uris: List<Uri>): ImportDraft = withContext(Dispatchers.IO) {
        val audio = ArrayList<Pair<DraftFile, AudioTags>>()
        val cueUris = ArrayList<Uri>()
        val skipped = ArrayList<String>()

        for (uri in uris) {
            val info = reader.queryInfo(uri)
            when (AudioImportRules.classify(info.name, info.mimeType)) {
                FileKind.AUDIO -> {
                    keepPermission(uri)
                    audio.add(readDraft(uri, info.name, info.name, info.mimeType, info.sizeBytes))
                }
                FileKind.CUE -> cueUris.add(uri)
                FileKind.UNSUPPORTED_AUDIO -> skipped.add(info.name)
                else -> Unit
            }
        }

        val ordered = audio.sortedWith { a, b -> AudioImportRules.naturalCompare(a.first.path, b.first.path) }
        val cueText = if (ordered.size == 1 && cueUris.size == 1) reader.readText(cueUris[0]) else null

        draftFrom(
            files = ordered.map { it.first },
            firstTags = ordered.firstOrNull()?.second,
            folderName = null,
            sourceTreeUri = null,
            cueText = cueText,
            coverUri = null,
            skippedNames = skipped,
            truncated = false
        )
    }

    private fun readDraft(
        uri: Uri,
        path: String,
        name: String,
        mimeType: String,
        sizeBytes: Long
    ): Pair<DraftFile, AudioTags> {
        val tags = reader.readTags(uri)
        val draft = DraftFile(
            uri = uri.toString(),
            path = path,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = tags.durationMs,
            tagTitle = tags.title.ifBlank { null }
        )
        return draft to tags
    }

    private fun draftFrom(
        files: List<DraftFile>,
        firstTags: AudioTags?,
        folderName: String?,
        sourceTreeUri: String?,
        cueText: String?,
        coverUri: String?,
        skippedNames: List<String>,
        truncated: Boolean
    ): ImportDraft {
        val title = firstTags?.album?.takeIf { it.isNotBlank() }
            ?: AudioImportRules.guessBookTitle(folderName, files.map { it.name })

        var embeddedCount = 0
        if (files.size == 1) {
            val only = files[0]
            val source = reader.openSource(Uri.parse(only.uri))
            try {
                if (source != null) embeddedCount = ChapterExtractor.readEmbedded(source, only.durationMs).size
            } finally {
                source?.close()
            }
            if (embeddedCount < 2 && !cueText.isNullOrBlank()) {
                embeddedCount = ChapterExtractor.parseCue(cueText, only.durationMs).size
            }
            if (embeddedCount < 2) embeddedCount = 0
        }

        val author = if (firstTags != null) firstTags.albumArtist.ifBlank { firstTags.artist } else ""

        return ImportDraft(
            title = title,
            author = author,
            narrator = firstTags?.composer.orEmpty(),
            files = files,
            sourceTreeUri = sourceTreeUri,
            cueText = if (files.size == 1) cueText else null,
            coverUri = coverUri,
            embeddedChapterCount = embeddedCount,
            skippedNames = skippedNames,
            truncated = truncated
        )
    }

    /** The .cue sheet that belongs to a one-file book: same name as the audio, or the only one in the folder. */
    private fun pickCueText(audio: List<ScanEntry>, cues: List<ScanEntry>): String? {
        if (audio.size != 1 || cues.isEmpty()) return null
        val base = audio[0].relativePath.substringBeforeLast('.')
        val match = cues.firstOrNull { it.relativePath.substringBeforeLast('.').equals(base, ignoreCase = true) }
            ?: cues.singleOrNull()
            ?: return null
        return reader.readText(match.uri)
    }

    // ===================================================== Step 2: build the book

    /**
     * Builds the book, its files and its chapters from a draft.
     *
     * Chapters:
     *  - One file: embedded marks or a .cue sheet if found, else split by time if the options
     *    ask for it, else a single chapter named after the book.
     *  - Many files: one chapter per file, except a file that has its own chapter marks,
     *    which gets those chapters instead.
     */
    suspend fun build(draft: ImportDraft, options: ChapterOptions = ChapterOptions()): ImportedBook =
        withContext(Dispatchers.IO) {
            require(draft.files.isNotEmpty()) { "Nothing to import" }

            val bookId = UUID.randomUUID().toString()
            val files = draft.files.mapIndexed { index, f ->
                AudioFileEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    orderIndex = index,
                    uri = f.uri,
                    displayName = f.name,
                    durationMs = f.durationMs,
                    mimeType = f.mimeType,
                    sizeBytes = f.sizeBytes
                )
            }

            val rawPerFile: List<List<RawChapter>> = if (draft.files.size == 1) {
                val f = draft.files[0]
                val source = reader.openSource(Uri.parse(f.uri))
                try {
                    listOf(ChapterExtractor.chaptersForFile(source, draft.cueText, f.durationMs, draft.title, options))
                } finally {
                    source?.close()
                }
            } else {
                val titles = AudioImportRules.chooseChapterTitles(
                    draft.files.map { it.name },
                    draft.files.map { it.tagTitle }
                )
                draft.files.mapIndexed { index, f ->
                    var embedded: List<RawChapter> = emptyList()
                    if (options.useEmbeddedChapters) {
                        val source = reader.openSource(Uri.parse(f.uri))
                        try {
                            if (source != null) embedded = ChapterExtractor.readEmbedded(source, f.durationMs)
                        } finally {
                            source?.close()
                        }
                    }
                    if (embedded.size >= 2) embedded else ChapterExtractor.wholeFile(titles[index], f.durationMs)
                }
            }

            val chapters = ArrayList<AudioChapterEntity>()
            for ((fileIndex, rawList) in rawPerFile.withIndex()) {
                for (raw in rawList) {
                    chapters.add(
                        AudioChapterEntity(
                            id = UUID.randomUUID().toString(),
                            bookId = bookId,
                            fileId = files[fileIndex].id,
                            orderIndex = chapters.size,
                            title = raw.title,
                            startMs = raw.startMs,
                            endMs = raw.endMs
                        )
                    )
                }
            }

            val book = AudioBookEntity(
                id = bookId,
                title = draft.title.trim().ifBlank { "Audiobook" },
                author = draft.author.trim(),
                narrator = draft.narrator.trim(),
                coverUri = makeCover(draft, bookId),
                sourceUri = draft.sourceTreeUri,
                totalDurationMs = files.sumOf { it.durationMs }
            )
            ImportedBook(book, files, chapters)
        }

    /** Saves the cover: a picture found next to the audio first, otherwise one stored inside the first few files. */
    private fun makeCover(draft: ImportDraft, bookId: String): String? {
        draft.coverUri?.let { address ->
            reader.readImageBytes(Uri.parse(address))?.let { bytes ->
                reader.saveCover(bytes, bookId)?.let { return it }
            }
        }
        for (f in draft.files.take(3)) {
            reader.readEmbeddedCover(Uri.parse(f.uri))?.let { bytes ->
                reader.saveCover(bytes, bookId)?.let { return it }
            }
        }
        return null
    }

    // ============================================================= Permissions

    /**
     * Keeps Android's read permission for a picked folder or file after the app restarts.
     * Android allows only a limited number of these, so they are released again when an
     * import is cancelled or a book is removed.
     */
    private fun keepPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some providers don't offer persistent access. The book still imports, but
            // may need to be re-linked after a restart.
        }
    }

    /** Releases the saved permission for a folder, or for each file when no folder was used. */
    fun releasePermissions(sourceTreeUri: String?, fileUris: List<String>) {
        val targets = if (sourceTreeUri != null) listOf(sourceTreeUri) else fileUris
        for (address in targets) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(address), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Nothing was saved for this address, so there is nothing to release.
            }
        }
    }

    /** Cancels an import that was scanned but not saved. */
    fun discard(draft: ImportDraft) = releasePermissions(draft.sourceTreeUri, draft.files.map { it.uri })

    /** Deletes the cover saved for a book. Call this when a book is removed. */
    fun deleteCover(bookId: String) = reader.deleteCover(bookId)
}
