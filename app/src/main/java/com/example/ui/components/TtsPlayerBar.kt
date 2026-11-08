package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.tts.SleepTimerState
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState
import com.example.tts.edge.EdgeProgress
import com.example.tts.edge.EdgeVoices

@Composable
fun TtsPlayerBar(
    playbackInfo: TtsPlaybackInfo,
    speechRate: Float,
    speechPitch: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onPrevParagraph: () -> Unit = {},
    onNextParagraph: () -> Unit = {},
    onSeek: (Int) -> Unit = {},
    remainingSeconds: Int = 0,
    sleepTimer: SleepTimerState = SleepTimerState(),
    onSetSleepMinutes: (Int) -> Unit = {},
    onSetSleepAtChapterEnd: () -> Unit = {},
    onCancelSleep: () -> Unit = {},
    useEdge: Boolean = false,
    edgeVoice: String = "",
    onUseEdgeChange: (Boolean) -> Unit = {},
    onEdgeVoiceChange: (String) -> Unit = {},
    edgeProgress: EdgeProgress = EdgeProgress(),
    edgeWifiOnly: Boolean = false,
    onEdgeWifiOnlyChange: (Boolean) -> Unit = {},
    onClearEdgeChapter: () -> Unit = {},
    onClearEdgeCache: () -> Unit = {},
    loadEdgeCacheSize: suspend () -> Long = { 0L }
) {
    var expandedSettings by remember { mutableStateOf(false) }
    var showEdgeSheet by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tts_player_bar"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp)
        ) {
            // Seek bar above the buttons: "8 / 41", a draggable bar, and time left.
            val showStatus = playbackInfo.state == TtsState.PLAYING || playbackInfo.state == TtsState.PAUSED
            val showRing = useEdge && edgeProgress.total > 0
            if (showStatus || showRing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showStatus) {
                        val total = playbackInfo.totalChunks
                        val current = playbackInfo.currentChunkIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
                        var dragValue by remember { mutableStateOf<Float?>(null) }

                        Text(
                            text = if (total > 0) "${(dragValue?.toInt() ?: current) + 1} / $total" else "Speaking...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        if (total > 1) {
                            Slider(
                                value = dragValue ?: current.toFloat(),
                                onValueChange = { dragValue = it },
                                onValueChangeFinished = {
                                    dragValue?.let { onSeek(it.toInt().coerceIn(0, total - 1)) }
                                    dragValue = null
                                },
                                valueRange = 0f..(total - 1).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .testTag("tts_seek")
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (remainingSeconds > 0) {
                            Text(
                                text = if (remainingSeconds < 60) "<1 min" else "~${(remainingSeconds + 59) / 60} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    if (showRing) {
                        EdgeDownloadRing(
                            progress = edgeProgress,
                            onClick = { showEdgeSheet = true }
                        )
                    }
                }
            }

            // Main Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Previous chapter
                IconButton(
                    onClick = onPrevChapter,
                    modifier = Modifier.testTag("tts_prev_chapter")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter"
                    )
                }

                // Previous paragraph
                IconButton(
                    onClick = onPrevParagraph,
                    modifier = Modifier.testTag("tts_prev_paragraph")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous Paragraph"
                    )
                }

                // Play / Pause Primary Action
                val isPlaying = playbackInfo.state == TtsState.PLAYING
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("tts_play_pause"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Next paragraph
                IconButton(
                    onClick = onNextParagraph,
                    modifier = Modifier.testTag("tts_next_paragraph")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Next Paragraph"
                    )
                }

                // Next chapter
                IconButton(
                    onClick = onNextChapter,
                    modifier = Modifier.testTag("tts_next_chapter")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Chapter"
                    )
                }

                // Expand / Collapse Speed/Pitch Drawer
                IconButton(
                    onClick = { expandedSettings = !expandedSettings },
                    modifier = Modifier.testTag("tts_toggle_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "TTS Voice Settings",
                        tint = if (expandedSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error display if any
            if (!playbackInfo.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playbackInfo.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Expandable settings drawer: Speed & Pitch
            AnimatedVisibility(
                visible = expandedSettings,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 8.dp, end = 8.dp)
                ) {
                    // Stop reading (moved here from the main row so it can't be hit by accident)
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("tts_stop")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop reading")
                    }

                    // Voice engine: phone voice or Edge neural voices
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Edge neural voice",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Switch(
                            checked = useEdge,
                            onCheckedChange = onUseEdgeChange,
                            modifier = Modifier.testTag("tts_edge_switch")
                        )
                    }
                    if (useEdge) {
                        var voiceMenuOpen by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { voiceMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Voice: " + EdgeVoices.labelFor(edgeVoice))
                            }
                            DropdownMenu(
                                expanded = voiceMenuOpen,
                                onDismissRequest = { voiceMenuOpen = false }
                            ) {
                                EdgeVoices.all.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.label) },
                                        onClick = {
                                            onEdgeVoiceChange(voice.shortName)
                                            voiceMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Speed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Speed: ${String.format("%.1fx", speechRate)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(100.dp)
                        )
                        Slider(
                            value = speechRate,
                            onValueChange = onRateChange,
                            valueRange = 0.5f..2.5f,
                            steps = 7,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Pitch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pitch: ${String.format("%.1fx", speechPitch)}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(100.dp)
                        )
                        Slider(
                            value = speechPitch,
                            onValueChange = onPitchChange,
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Sleep timer: pause reading after a set time, or when this chapter ends
                    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(sleepTimer.endAtMs) {
                        while (sleepTimer.endAtMs > 0L) {
                            nowMs = System.currentTimeMillis()
                            delay(15_000L)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sleep timer",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = when {
                                sleepTimer.endAtMs > 0L -> {
                                    val mins = ((sleepTimer.endAtMs - nowMs + 59_999L) / 60_000L).coerceAtLeast(1L)
                                    "Pauses in $mins min"
                                }
                                sleepTimer.atChapterEnd -> "Stops after this chapter"
                                else -> "Off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sleepTimer.active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !sleepTimer.active,
                            onClick = onCancelSleep,
                            label = { Text("Off") },
                            modifier = Modifier.testTag("sleep_off")
                        )
                        listOf(15, 30, 45, 60).forEach { m ->
                            FilterChip(
                                selected = sleepTimer.endAtMs > 0L && sleepTimer.minutes == m,
                                onClick = { onSetSleepMinutes(m) },
                                label = { Text("$m min") },
                                modifier = Modifier.testTag("sleep_$m")
                            )
                        }
                        FilterChip(
                            selected = sleepTimer.atChapterEnd,
                            onClick = onSetSleepAtChapterEnd,
                            label = { Text("End of chapter") },
                            modifier = Modifier.testTag("sleep_chapter_end")
                        )
                    }
                }
            }
        }
    }
    if (showEdgeSheet) {
        EdgeDownloadSheet(
            progress = edgeProgress,
            wifiOnly = edgeWifiOnly,
            onWifiOnlyChange = onEdgeWifiOnlyChange,
            onClearChapter = onClearEdgeChapter,
            onClearCache = onClearEdgeCache,
            loadCacheSize = loadEdgeCacheSize,
            onDismiss = { showEdgeSheet = false }
        )
    }
}
