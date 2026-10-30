package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Name, type and size of a file, as reported by the file picker. */
data class FileInfo(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)

/** What is stored inside an audio file's tags. Any field can be empty. */
data class AudioTags(
    val durationMs: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val albumArtist: String = "",
    val album: String = "",
        val composer: String = ""
)

/**
 * An open audio file that can be read at any position. This is what the chapter readers use.
 * Always close it when done (use `use { }`).
 */
class FileSource(private val descriptor: ParcelFileDescriptor) : RandomAccessSource, Closeable {

    private val stream = FileInputStream(descriptor.fileDescriptor)
    private val channel: FileChannel = stream.channel

    override val length: Long = try {
        channel.size()
    } catch (e: Exception) {
        descriptor.statSize
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        return try {
            channel.read(ByteBuffer.wrap(buffer, offset, count), position)
        } catch (e: Exception) {
            -1
        }
    }

    override fun close() {
        try {
            stream.close()
        } catch (e: Exception) {
            // nothing to do
        }
        try {
            descriptor.close()
        } catch (e: Exception) {
            // nothing to do
        }
    }
}

/**
 * Reads information from audio files on the phone: their name and size, their tags and
 * length, their embedded cover picture, and the raw bytes for chapter reading.
 *
 * Files are opened through the content:// addresses the user picked, so no storage
 * permission is needed. Nothing here changes or copies the audio files.
 *
 * Every function here can block while the file is read, so call them from a background
 * thread (Dispatchers.IO). None of them throws: a file that cannot be read gives an empty
 * or null result.
 */
class AudioFileReader(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** File name, type and size from the file picker. Falls back to the address if no name is given. */
    fun queryInfo(uri: Uri): FileInfo {
        var name = ""
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex).orEmpty()
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (e: Exception) {
            // fall through to the defaults below
        }
        if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "audio" }
        val mime = try {
            resolver.getType(uri).orEmpty()
        } catch (e: Exception) {
            ""
        }
        return FileInfo(name = name, mimeType = mime, sizeBytes = size)
    }

    /** Opens a file for reading at any position, or returns null if it cannot be opened. */
    fun openSource(uri: Uri): FileSource? {
        return try {
            val descriptor = resolver.openFileDescriptor(uri, "r") ?: return null
            FileSource(descriptor)
        } catch (e: Exception) {
            null
        }
    }

    /** Length and tags of an audio file. Missing information is left empty. */
    fun readTags(uri: Uri): AudioTags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            fun field(key: Int): String = retriever.extractMetadata(key).orEmpty().trim()
            AudioTags(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                title = field(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = field(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                albumArtist = field(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = field(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                composer = field(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            )
        } catch (e: Exception) {
            AudioTags()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    /** The cover picture stored inside the audio file, or null if there is none. */
    fun readEmbeddedCover(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }

    /** Text of a small file such as a .cue sheet, or null if it cannot be read. */
    fun readText(uri: Uri, maxBytes: Int = 512 * 1024): String? {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val n = input.read(buffer, total, maxBytes - total)
                    if (n <= 0) break
                    total += n
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Raw bytes of a picture file, or null if it cannot be read or is too large. */
    fun readImageBytes(uri: Uri, maxBytes: Int = 15 * 1024 * 1024): ByteArray? {
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) null else bytes
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Saves a cover picture inside the app, shrunk to at most 800 pixels on its long side so
     * the shelf stays light on memory. Returns the saved file's address, or null if the
     * bytes are not a picture. The saved file is named after the book, so saving again
     * replaces the old cover.
     */
    fun saveCover(bytes: ByteArray, bookId: String): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_COVER_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

            val folder = File(appContext.filesDir, COVER_FOLDER).apply { mkdirs() }
            val file = File(folder, "$bookId.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            bitmap.recycle()
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Deletes the saved cover of a book, if there is one. Used when a book is removed. */
    fun deleteCover(bookId: String) {
        try {
            File(File(appContext.filesDir, COVER_FOLDER), "$bookId.jpg").delete()
        } catch (e: Exception) {
            // nothing to do
        }
    }

    private companion object {
        const val COVER_FOLDER = "audio_covers"
        const val MAX_COVER_SIDE = 800
    }
}
