package com.example.audio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** A file found while looking through a folder. */
data class ScanEntry(
    val uri: Uri,
    /** Name with its folders, for example "CD 1/01 Intro.mp3". */
    val relativePath: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: FileKind
)

/** Everything useful found in a picked folder, sorted in natural order. */
data class FolderScan(
    val folderName: String,
    val audio: List<ScanEntry>,
    val cues: List<ScanEntry>,
    val images: List<ScanEntry>,
    /** Audio files the player can't play, such as .wma or protected Audible files. */
    val unsupported: List<ScanEntry>,
    /** True if the folder was so large that the search stopped early. */
    val truncated: Boolean
) {
    /**
     * The best cover picture: one in the top folder if there is any, otherwise one from a
     * subfolder. A file named cover, folder or front is preferred.
     */
    fun bestCover(): ScanEntry? {
        val topLevel = images.filter { !it.relativePath.contains('/') }
        val pool = if (topLevel.isNotEmpty()) topLevel else images
        val index = AudioImportRules.pickCover(pool.map { it.name }) ?: return null
        return pool[index]
    }
}

/**
 * Looks through a folder the user picked and lists its audio files, cue sheets and pictures,
 * including the ones inside subfolders (for example "CD 1" and "CD 2").
 *
 * It uses Android's document tree, so it works with the folder picker's permission and needs
 * no storage permission of its own. Nothing is opened or read here beyond the file list.
 *
 * Hidden files (names starting with a dot, such as macOS "._01.mp3" leftovers) are skipped.
 * Call it from a background thread. It never throws; a folder that cannot be read gives an
 * empty result.
 */
class AudioFolderScanner(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun scan(treeUri: Uri): FolderScan {
        val audio = ArrayList<ScanEntry>()
        val cues = ArrayList<ScanEntry>()
        val images = ArrayList<ScanEntry>()
        val unsupported = ArrayList<ScanEntry>()
        var count = 0
        var truncated = false

        val rootId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return FolderScan("", emptyList(), emptyList(), emptyList(), emptyList(), false)
        }
        val folderName = queryName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId))

        fun walk(parentId: String, parentPath: String, depth: Int) {
            if (truncated) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            val subfolders = ArrayList<Pair<String, String>>()   // document id, name

            try {
                resolver.query(childrenUri, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0).orEmpty()
                        val name = cursor.getString(1).orEmpty()
                        val mime = cursor.getString(2).orEmpty()
                        val size = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                        if (id.isEmpty() || name.isEmpty() || name.startsWith(".")) continue

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            subfolders.add(id to name)
                            continue
                        }

                        val kind = AudioImportRules.classify(name, mime)
                        if (kind == FileKind.OTHER) continue

                        count++
                        if (count > MAX_ENTRIES) {
                            truncated = true
                            break
                        }
                        val entry = ScanEntry(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            relativePath = if (parentPath.isEmpty()) name else "$parentPath/$name",
                            name = name,
                            mimeType = mime,
                            sizeBytes = size,
                            kind = kind
                        )
                        when (kind) {
                            FileKind.AUDIO -> audio.add(entry)
                            FileKind.UNSUPPORTED_AUDIO -> unsupported.add(entry)
                            FileKind.CUE -> cues.add(entry)
                            FileKind.IMAGE -> images.add(entry)
                            FileKind.OTHER -> Unit
                        }
                    }
                }
            } catch (e: Exception) {
                // An unreadable folder is skipped; the rest of the scan carries on.
            }

            if (depth >= MAX_DEPTH) return
            for ((id, name) in subfolders) {
                if (truncated) return
                walk(id, if (parentPath.isEmpty()) name else "$parentPath/$name", depth + 1)
            }
        }

        walk(rootId, "", 0)

        fun sorted(list: List<ScanEntry>) =
            list.sortedWith { a, b -> AudioImportRules.naturalCompare(a.relativePath, b.relativePath) }

        return FolderScan(
            folderName = folderName,
            audio = sorted(audio),
            cues = sorted(cues),
            images = sorted(images),
            unsupported = sorted(unsupported),
            truncated = truncated
        )
    }

    /** Display name of a document, or an empty string if it can't be read. */
    private fun queryName(documentUri: Uri): String {
        return try {
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    private companion object {
        /** Folders nested deeper than this are not searched. */
        const val MAX_DEPTH = 5

        /** Stops the search if a folder holds an unreasonable number of files. */
        const val MAX_ENTRIES = 3000
    }
}
