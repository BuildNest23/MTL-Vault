package com.example.ui.screens.audiobooks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioFormat
import com.example.audio.AudioPlayerSettings
import com.example.audio.SleepKind
import com.example.audio.SleepState

// ============================================================================ Speed

/**
 * Bottom sheet for the playback speed, from 0.5x to 3.0x in steps of 0.05x.
 *
 * The slider is only applied when the finger is lifted, so dragging does not write to the
 * database dozens of times. The plus and minus buttons and the preset chips apply at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSpeedSheet(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderValue by remember(speed) { mutableFloatStateOf(speed) }

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
                .navigationBarsPadding()
                .testTag("audio_speed_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Playback speed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Current speed with minus and plus buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = { onSpeedChange(AudioFormat.clampSpeed(sliderValue - AudioPlayerSettings.SPEED_STEP)) },
                    modifier = Modifier.testTag("audio_speed_minus")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Slower")
                }
                Text(
                    text = AudioFormat.speed(sliderValue),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                FilledTonalIconButton(
                    onClick = { onSpeedChange(AudioFormat.clampSpeed(sliderValue + AudioPlayerSettings.SPEED_STEP)) },
                    modifier = Modifier.testTag("audio_speed_plus")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Faster")
                }
            }

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = AudioFormat.clampSpeed(it) },
                onValueChangeFinished = { onSpeedChange(sliderValue) },
                valueRange = AudioPlayerSettings.MIN_SPEED..AudioPlayerSettings.MAX_SPEED,
                steps = 49,
                modifier = Modifier.testTag("audio_speed_slider")
            )

            // Quick choices, four to a row
            for (rowOfPresets in SPEED_PRESETS.chunked(4)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (preset in rowOfPresets) {
                        FilterChip(
                            selected = kotlin.math.abs(sliderValue - preset) < 0.001f,
                            onClick = { onSpeedChange(preset) },
                            label = { Text(AudioFormat.speed(preset)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = "Each book remembers its own speed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private val SPEED_PRESETS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

// ======================================================================= Sleep timer

/**
 * Bottom sheet for the sleep timer: 15, 30, 45 or 60 minutes, the end of the current
 * chapter, or a custom number of minutes. While a timer is running it shows what is left
 * and a button to turn it off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSleepSheet(
    sleep: SleepState,
    onStartTimed: (Int) -> Unit,
    onEndOfChapter: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customMinutes by remember { mutableIntStateOf(20) }

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
                .navigationBarsPadding()
                .testTag("audio_sleep_sheet"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // What is running now
            if (sleep.kind != SleepKind.OFF) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (sleep.kind == SleepKind.END_OF_CHAPTER) {
                            "Stops at the end of this chapter"
                        } else {
                            "Stops in ${AudioFormat.sleep(sleep)}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("audio_sleep_off")
                    ) {
                        Text("Turn off")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            for (minutes in AudioPlayerSettings.SLEEP_OPTIONS) {
                SleepOptionRow(label = "$minutes minutes") {
                    onStartTimed(minutes)
                    onDismiss()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            SleepOptionRow(label = "End of this chapter") {
                onEndOfChapter()
                onDismiss()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Custom number of minutes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = { customMinutes = (customMinutes - 5).coerceAtLeast(5) },
                    modifier = Modifier.testTag("audio_sleep_custom_minus")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer minutes")
                }
                Text(
                    text = "$customMinutes min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(
                    onClick = { customMinutes = (customMinutes + 5).coerceAtMost(600) },
                    modifier = Modifier.testTag("audio_sleep_custom_plus")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "More minutes")
                }
                Button(
                    onClick = {
                        onStartTimed(customMinutes)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("audio_sleep_custom_start")
                ) {
                    Text("Start")
                }
            }

            Text(
                text = "The volume fades out over the last 30 seconds. The timer counts down only while the book is playing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SleepOptionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
