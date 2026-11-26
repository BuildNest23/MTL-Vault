package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Shared storage utility for novel cover images saved inside `filesDir/covers/`.
 * Recycles decoded bitmaps promptly and writes unique filenames so Compose image loaders refresh immediately.
 */
object CoverStorage {

    private const val MAX_DIMENSION_PX = 1000
    private const val JPEG_QUALITY = 90
    private const val COVERS_DIR_NAME = "covers"

    /**
     * Copies and downscales a picked cover image into the app's private covers directory.
     * Returns the new `file://` URI string, or null if the source cannot be decoded or written.
     */
    fun copyCoverIntoApp(context: Context, novelId: String, source: Uri): String? {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION_PX) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val longest = maxOf(decoded.width, decoded.height)
            val bitmapToWrite = if (longest > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / longest
                scaled = Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).toInt().coerceAtLeast(1),
                    (decoded.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                scaled
            } else {
                decoded
            }

            val safeId = novelId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val uniqueSuffix = "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            val file = File(context.filesDir, "$COVERS_DIR_NAME/${safeId}_$uniqueSuffix.jpg")
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                bitmapToWrite.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (file.length() > 0L) {
                Uri.fromFile(file).toString()
            } else {
                file.delete()
                null
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            if (scaled != null && scaled !== decoded && !scaled.isRecycled) {
                scaled.recycle()
            }
            if (decoded != null && !decoded.isRecycled) {
                decoded.recycle()
            }
        }
    }

    /** Deletes a cover file saved by the app inside `filesDir/covers/`. External URIs are ignored. */
    fun deleteOwnCover(context: Context, coverUri: String?) {
        if (coverUri.isNullOrBlank()) return
        try {
            val uri = Uri.parse(coverUri)
            if (uri.scheme != "file") return
            val path = uri.path ?: return
            val file = File(path)
            val coversDir = File(context.filesDir, COVERS_DIR_NAME)
            if (file.exists() && file.parentFile?.canonicalPath == coversDir.canonicalPath) {
                file.delete()
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }

    /** Deletes all cover files saved inside `filesDir/covers/`. */
    fun deleteAllCovers(context: Context) {
        try {
            val coversDir = File(context.filesDir, COVERS_DIR_NAME)
            if (coversDir.isDirectory) {
                coversDir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }
}
