package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NovelSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TotalBadgeBg = Color(0xFFAAB8F5)
private val TotalBadgeFg = Color(0xFF0F1633)
private val UnreadBadgeBg = Color(0xFF79D58C)
private val UnreadBadgeFg = Color(0xFF06260F)

private val ChapterNumberRegex = Regex("""(?i)chapter\s*(\d+)""")

/**
 * Library tile: cover image with two small badge cards at the top-left
 * (blue = total chapters, green = unread chapters, same size), the title at the bottom
 * and a "Ch. N" resume marker showing where you stopped reading.
 * A book with nothing left to read is dimmed.
 */
@Composable
fun NovelCoverCard(
    novel: NovelSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val unread = novel.unreadChapters
    val finished = novel.totalChapters > 0 && unread == 0

    // No image-loading library in this project, so decode a saved cover (content:// or file://) ourselves.
    val cover by produceState<ImageBitmap?>(initialValue = null, novel.coverUri) {
        val uriString = novel.coverUri?.takeIf { it.isNotBlank() }
        value = if (uriString == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val placeholderBrush = remember(novel.id, novel.title) {
        val hue = ((novel.title.hashCode() and 0x7fffffff) % 360).toFloat()
        Brush.linearGradient(
            listOf(
                Color.hsv(hue, 0.50f, 0.32f),
                Color.hsv((hue + 35f) % 360f, 0.45f, 0.58f)
            )
        )
    }

    val resumeChapter = remember(novel.lastChapterTitle) {
        novel.lastChapterTitle
            ?.let { ChapterNumberRegex.find(it)?.groupValues?.getOrNull(1) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .alpha(if (finished) 0.55f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .testTag("novel_card_${novel.id}")
            .clickable(onClick = onClick)
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Bottom scrim + title + resume marker
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
                .padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 6.dp)
        ) {
            Text(
                text = novel.title.ifBlank { "Untitled" },
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (resumeChapter != null) {
                Text(
                    text = "Ch. $resumeChapter",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }

        // Two small cards: total chapters (blue) and unread chapters (green)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
        ) {
            CountBadge(text = novel.totalChapters.toString(), bg = TotalBadgeBg, fg = TotalBadgeFg)
            Box(modifier = Modifier.size(4.dp))
            CountBadge(text = unread.toString(), bg = UnreadBadgeBg, fg = UnreadBadgeFg)
        }

        if (novel.hasRules) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Replacements active",
                    tint = Color(0xFF7DC79A),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun CountBadge(text: String, bg: Color, fg: Color) {
    // Fixed size so the total and unread cards always have the same shape
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = if (text.length > 4) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
