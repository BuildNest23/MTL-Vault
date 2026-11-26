package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NovelSummary
import com.example.ui.theme.EditorialBookplatePalettes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ChapterNumberRegex = Regex("""(?i)chapter\s*(\d+)""")

/** In-memory decoded bitmap cache for novel covers so scrolling stays silky-smooth. */
private object NovelCoverCache : LruCache<String, ImageBitmap>(10 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

fun evictNovelCoverCache(coverUri: String?) {
    if (!coverUri.isNullOrBlank()) {
        NovelCoverCache.remove(coverUri)
    }
}

fun bookplateColorsFor(title: String): Pair<Color, Color> {
    val index = Math.floorMod(title.hashCode(), EditorialBookplatePalettes.size)
    return EditorialBookplatePalettes[index]
}

private fun bookMonogram(title: String): String {
    val parts = title.trim()
        .split(Regex("""[\s\-_:]+"""))
        .filter { word -> word.any { it.isLetterOrDigit() } }
    return when {
        parts.isEmpty() -> "N"
        parts.size == 1 -> parts[0].filter { it.isLetterOrDigit() }.take(2).uppercase().ifBlank { "N" }
        else -> parts.take(2).mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "N" }
    }
}

/**
 * Reusable editorial book-jacket cover with subtle left-edge spine crease, warm ivory fallback
 * bookplate motif (when no cover image exists), and cached async bitmap decoding.
 */
@Composable
fun EditorialBookCover(
    title: String,
    author: String = "",
    coverUri: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    compactBookplate: Boolean = false,
    miniThumbnail: Boolean = false
) {
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(
        initialValue = coverUri?.let { NovelCoverCache.get(it) },
        coverUri
    ) {
        val uriString = coverUri?.takeIf { it.isNotBlank() }
        value = if (uriString == null) {
            null
        } else {
            NovelCoverCache.get(uriString) ?: withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }?.asImageBitmap()
                    if (bmp != null) NovelCoverCache.put(uriString, bmp)
                    bmp
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
        }
    }

    val (topCloth, bottomCloth) = remember(title) { bookplateColorsFor(title) }
    val monogram = remember(title) { bookMonogram(title) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.verticalGradient(listOf(topCloth, bottomCloth)))
    ) {
        val bitmap = cover
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Tactile cloth-bound bookplate with gold-ivory hairline inner frame and serif monogram
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (miniThumbnail) 3.dp else if (compactBookplate) 6.dp else 9.dp)
                    .border(
                        width = if (miniThumbnail) 0.75.dp else 1.dp,
                        color = Color(0xFFEBDDC4).copy(alpha = 0.35f),
                        shape = RoundedCornerShape((cornerRadius - 3.dp).coerceAtLeast(4.dp))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = if (miniThumbnail) 2.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = monogram,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (miniThumbnail) 13.sp else if (compactBookplate) 22.sp else 30.sp,
                            letterSpacing = if (miniThumbnail) 0.5.sp else 1.sp
                        ),
                        color = Color(0xFFF6ECDA).copy(alpha = 0.92f)
                    )
                    if (!compactBookplate && !miniThumbnail && author.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(1.dp)
                                .background(Color(0xFFEBDDC4).copy(alpha = 0.45f))
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = author,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp),
                            color = Color(0xFFF6ECDA).copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Subtle left-side book spine groove for depth
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(10.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Editorial Library Card: crisp 2:3 book jacket with top unread/finished pill, subtle bottom
 * progress bar, and clean bookshop caption below (spacious 2-column mode) or overlaid serif
 * scrim (compact 3-column mode).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelCoverCard(
    novel: NovelSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val unread = novel.unreadChapters
    val finished = novel.totalChapters > 0 && unread == 0
    val progress = if (novel.totalChapters > 0) {
        (novel.readChapters.toFloat() / novel.totalChapters.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val resumeChapter = remember(novel.lastChapterTitle) {
        novel.lastChapterTitle
            ?.let { ChapterNumberRegex.find(it)?.groupValues?.getOrNull(1) }
    }

    if (compact) {
        // Compact 3-column shelf tile
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .alpha(if (finished) 0.72f else 1f)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .testTag("novel_card_${novel.id}")
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            EditorialBookCover(
                title = novel.title,
                author = novel.author,
                coverUri = novel.coverUri,
                cornerRadius = 10.dp,
                compactBookplate = true,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient scrim + serif title
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                        )
                    )
                    .padding(start = 7.dp, end = 7.dp, top = 26.dp, bottom = 7.dp)
            ) {
                Text(
                    text = novel.title.ifBlank { "Untitled" },
                    color = Color(0xFFFAF6EE),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (resumeChapter != null) {
                    Text(
                        text = "Ch. $resumeChapter",
                        color = Color(0xFFFAF6EE).copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1
                    )
                }
            }

            BookCoverBadges(
                unread = unread,
                total = novel.totalChapters,
                finished = finished,
                hasRules = novel.hasRules,
                compact = true,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle progress bar along the bottom edge
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
            }
        }
    } else {
        // Spacious 2-column Warm Editorial Book Card
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("novel_card_${novel.id}")
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (finished) 0.78f else 1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .padding(8.dp)
                ) {
                    EditorialBookCover(
                        title = novel.title,
                        author = novel.author,
                        coverUri = novel.coverUri,
                        cornerRadius = 11.dp,
                        compactBookplate = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .shadow(elevation = 3.dp, shape = RoundedCornerShape(11.dp), clip = false)
                    )

                    BookCoverBadges(
                        unread = unread,
                        total = novel.totalChapters,
                        finished = finished,
                        hasRules = novel.hasRules,
                        compact = false,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Thin editorial progress bar nested inside cover bottom
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(bottomStart = 11.dp, bottomEnd = 11.dp))
                                .height(4.dp)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 11.dp)
                ) {
                    Text(
                        text = novel.title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    val metaLine = when {
                        resumeChapter != null -> "Ch. $resumeChapter • ${novel.readChapters}/${novel.totalChapters}"
                        novel.author.isNotBlank() -> novel.author
                        novel.totalChapters > 0 -> "${novel.totalChapters} chapters"
                        else -> "Empty volume"
                    }
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCoverBadges(
    unread: Int,
    total: Int,
    finished: Boolean,
    hasRules: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(if (compact) 5.dp else 7.dp)) {
        // Top-left status pill: unread count or Finished checkmark
        if (total > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(8.dp),
                color = if (finished) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                } else {
                    Color(0xFF1A1714).copy(alpha = 0.78f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 5.dp else 7.dp,
                        vertical = if (compact) 2.dp else 3.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (finished) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Finished",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        if (!compact) {
                            Text(
                                text = "Read",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                            )
                        }
                    } else {
                        Text(
                            text = if (compact) "$unread" else "$unread left",
                            color = Color(0xFFFAF6EE),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Top-right subtle replacement rules indicator
        if (hasRules) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(if (compact) 20.dp else 22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1714).copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Replacement rules active",
                    tint = Color(0xFFE0A25E),
                    modifier = Modifier.size(if (compact) 12.dp else 13.dp)
                )
            }
        }
    }
}
