package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tts.TtsPlaybackInfo
import com.example.tts.TtsState

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
    modifier: Modifier = Modifier
) {
    var expandedSettings by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("tts_player_bar"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Main Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev Chapter
                IconButton(
                    onClick = onPrevChapter,
                    modifier = Modifier.testTag("tts_prev_chapter")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous Chapter"
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

                // Stop
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("tts_stop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop"
                    )
                }

                // Next Chapter
                IconButton(
                    onClick = onNextChapter,
                    modifier = Modifier.testTag("tts_next_chapter")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
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

            // Status chip (e.g. "Paragraph 4 / 22" or status)
            if (playbackInfo.state == TtsState.PLAYING || playbackInfo.state == TtsState.PAUSED) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (playbackInfo.totalChunks > 0) {
                            "Reading part ${playbackInfo.currentChunkIndex + 1} of ${playbackInfo.totalChunks}"
                        } else {
                            "Speaking..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
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
                }
            }
        }
    }
}
