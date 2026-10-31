package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tts.edge.EdgeProgress
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** "820 KB" / "6.2 MB" */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L * 1024L) {
        return String.format(Locale.US, "%d KB", (bytes / 1024L).coerceAtLeast(if (bytes > 0L) 1L else 0L))
    }
    return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/**
 * Small ring that shows how much of the chapter's Edge audio is on disk, with the chapter's disk
 * size next to it. A light arc = downloaded, a solid arc + dot = how far you have listened.
 * Dimmed when the whole chapter is downloaded, red when the download stalled.
 */
@Composable
fun EdgeDownloadRing(
    progress: EdgeProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = progress.total
    val complete = total > 0 && progress.done >= total
    val downloadedTarget = if (total > 0) progress.done.toFloat() / total else 0f
    val playedTarget = if (total > 0) progress.currentIndex.toFloat() / total else 0f
    val downloaded by animateFloatAsState(targetValue = downloadedTarget, label = "edgeDownloaded")
    val played by animateFloatAsState(targetValue = playedTarget, label = "edgePlayed")

    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val baseColor = if (progress.stalled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val strength = if (complete) 0.55f else 1f

    val label = when {
        progress.stalled -> "Stalled"
        progress.waitingForWifi -> "Wi-Fi only"
        complete -> "✓ " + formatBytes(progress.bytes)
        else -> formatBytes(progress.bytes)
    }
    val labelColor = when {
        progress.stalled -> MaterialTheme.colorScheme.error
        progress.waitingForWifi -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = "Edge audio for this chapter: ${progress.done} of $total sentences downloaded, $label"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .semantics { contentDescription = description }
            .testTag("tts_edge_ring"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val stroke = 3.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            // empty track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            // downloaded (light)
            drawArc(
                color = baseColor.copy(alpha = 0.35f * strength),
                startAngle = -90f,
                sweepAngle = 360f * downloaded,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // listened (solid)
            drawArc(
                color = baseColor.copy(alpha = strength),
                startAngle = -90f,
                sweepAngle = 360f * played,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // dot on the sentence being spoken
            val radius = (size.width - stroke) / 2f
            val angle = Math.toRadians((-90.0 + 360.0 * played))
            drawCircle(
                color = baseColor.copy(alpha = strength),
                radius = stroke * 0.8f,
                center = Offset(
                    size.width / 2f + radius * cos(angle).toFloat(),
                    size.height / 2f + radius * sin(angle).toFloat()
                )
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Opens when the ring is tapped: chapter size, Wi-Fi-only switch and the clear buttons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeDownloadSheet(
    progress: EdgeProgress,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onClearChapter: () -> Unit,
    onClearCache: () -> Unit,
    loadCacheSize: suspend () -> Long,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        // after a clear the files are deleted a moment later, so wait before measuring again
        if (reload > 0) delay(700)
        cacheBytes = try {
            loadCacheSize()
        } catch (e: Exception) {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Edge voice downloads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "This chapter: ${progress.done} of ${progress.total} sentences downloaded · " +
                    formatBytes(progress.bytes),
                style = MaterialTheme.typography.bodyMedium
            )
            if (progress.stalled) {
                Text(
                    text = "Downloading stalled (no connection?). It tries again at the next sentence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (progress.waitingForWifi) {
                Text(
                    text = "Not on Wi-Fi, so only the next few sentences are downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download ahead on Wi-Fi only",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Off Wi-Fi, only the next few sentences are fetched, which saves mobile data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = onWifiOnlyChange,
                    modifier = Modifier.testTag("tts_edge_wifi_switch")
                )
            }

            OutlinedButton(
                onClick = {
                    onClearChapter()
                    reload++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear this chapter")
            }

            val allText = cacheBytes?.let { formatBytes(it) } ?: "…"
            Text(
                text = "All Edge audio on this phone: $allText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    onClearCache()
                    reload++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear all Edge audio")
            }
        }
    }
}
