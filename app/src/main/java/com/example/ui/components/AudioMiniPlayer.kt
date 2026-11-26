package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlayfairDisplayFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps recently shown covers in memory so scrolling the shelf does not decode them again. */
private object CoverCache : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Evicts a saved audiobook cover from memory when its file is replaced or deleted. */
fun evictAudioCoverCache(address: String?) {
    if (!address.isNullOrBlank()) {
        CoverCache.remove(address)
    }
}

private fun decodeCover(address: String): ImageBitmap? {
    CoverCache.get(address)?.let { return it }
    return try {
        val path = Uri.parse(address).path ?: return null
        // Saved covers are at most 800 px. Half size is plenty for the shelf and the mini-player.
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        val image = bitmap.asImageBitmap()
        CoverCache.put(address, image)
        image
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** One or two capital letters from a title, for the generated cover. */
private fun initialsOf(title: String): String {
    val letters = title.split(' ', '-', '_')
        .filter { word -> word.any { it.isLetterOrDigit() } }
        .take(2)
        .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
    return if (letters.isEmpty()) "?" else letters.joinToString("")
}

/**
 * An audiobook cover. Shows the saved cover picture when there is one, and otherwise a curated
 * editorial cloth bookplate with a hairline frame and serif monogram.
 */
@Composable
fun AudioCover(
    title: String,
    coverUri: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    initialsSize: Int = 16
) {
    val picture by produceState<ImageBitmap?>(initialValue = coverUri?.let { CoverCache.get(it) }, coverUri) {
        value = if (coverUri.isNullOrBlank()) null else withContext(Dispatchers.IO) { decodeCover(coverUri) }
    }
    val (topCloth, bottomCloth) = bookplateColorsFor(title)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.verticalGradient(listOf(topCloth, bottomCloth))),
        contentAlignment = Alignment.Center
    ) {
        val image = picture
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (initialsSize >= 24) 7.dp else 4.dp)
                    .background(Color.Transparent)
                    .clip(RoundedCornerShape((cornerRadius - 3.dp).coerceAtLeast(4.dp))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialsOf(title),
                    color = Color(0xFFF6ECDA).copy(alpha = 0.92f),
                    fontFamily = PlayfairDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = initialsSize.sp
                )
            }
        }
    }
}
