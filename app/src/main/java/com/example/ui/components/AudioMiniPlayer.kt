package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioFormat
import com.example.audio.AudioPlayerUiState
import com.example.audio.PlaybackStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ====================================================================== Cover picture

/** Colors for books that have no cover picture. The same title always gets the same color. */
private val generatedCoverColors = listOf(
    Color(0xFF2F6F4E), // jade
    Color(0xFFB4791F), // gold
    Color(0xFF3D5A80), // blue
    Color(0xFF8C3B4A), // wine
    Color(0xFF2A7F86), // teal
    Color(0xFF6B4A8F)  // plum
)

/** Keeps recently shown covers in memory so scrolling the shelf does not decode them again. */
private object CoverCache : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
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
 * A book cover. Shows the saved cover picture when there is one, and otherwise a colored
 * square with the title's initials. Give it a size with [modifier]; the picture fills it.
 */
@Composable
fun AudioCover(
    title: String,
    coverUri: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    initialsSize: Int = 16
) {
    val picture by produceState<ImageBitmap?>(initialValue = coverUri?.let { CoverCache.get(it) }, coverUri) {
        value = if (coverUri.isNullOrBlank()) null else withContext(Dispatchers.IO) { decodeCover(coverUri) }
    }
    val color = generatedCoverColors[Math.floorMod(title.hashCode(), generatedCoverColors.size)]

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(color),
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
            Text(
                text = initialsOf(title),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = initialsSize.sp
            )
        }
    }
}

// ====================================================================== Mini-player

/**
 * The small player bar that sits above the tab bar while a book is loaded. It shows the cover,
 * the title, the chapter and speed, a thin line for progress through the whole book, and
 * play/pause. Tapping the bar opens the full player. The cross stops playback and hides the bar.
 *
 * It draws nothing when no book is loaded, so it can always be placed in the layout.
 */
@Composable
fun AudioMiniPlayer(
    state: AudioPlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.hasBook) return

    val hasError = state.status == PlaybackStatus.ERROR
    val subtitle = when {
        hasError -> "Can't play. Tap to fix."
        state.chapterTitle.isNotBlank() -> "${state.chapterTitle}  ·  ${AudioFormat.speed(state.speed)}"
        state.author.isNotBlank() -> state.author
        else -> AudioFormat.speed(state.speed)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("audio_mini_player"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 10.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AudioCover(
                    title = state.title,
                    coverUri = state.coverUri,
                    modifier = Modifier.size(44.dp),
                    initialsSize = 14
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("audio_mini_play_pause"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (state.status == PlaybackStatus.LOADING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("audio_mini_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close player",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { state.bookProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Book progress ${AudioFormat.percent(state.bookProgress)} percent" },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}
