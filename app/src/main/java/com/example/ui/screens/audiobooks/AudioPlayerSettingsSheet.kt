package com.example.ui.screens.audiobooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AudioPlayerSettings

/**
 * Bottom sheet with the audiobook player options: the length of the skip buttons and four
 * switches (smart rewind, skip silence, volume boost, shake to extend the sleep timer).
 * Every change is passed up at once and saved by the player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerSettingsSheet(
    settings: AudioPlayerSettings,
    onChange: (AudioPlayerSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .testTag("audio_settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Player settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            SecondsChooser(
                label = "Skip back",
                selected = settings.skipBackSeconds,
                onSelect = { onChange(settings.copy(skipBackSeconds = it)) }
            )
            SecondsChooser(
                label = "Skip forward",
                selected = settings.skipForwardSeconds,
                onSelect = { onChange(settings.copy(skipForwardSeconds = it)) }
            )

            SettingSwitchRow(
                title = "Rewind a little when resuming",
                subtitle = "After a break, playback steps back a few seconds so you hear what you missed. The longer the break, the bigger the step, up to 30 seconds.",
                checked = settings.rewindOnResume,
                onCheckedChange = { onChange(settings.copy(rewindOnResume = it)) }
            )
            SettingSwitchRow(
                title = "Skip silence",
                subtitle = "Cuts out long quiet gaps while playing.",
                checked = settings.skipSilence,
                onCheckedChange = { onChange(settings.copy(skipSilence = it)) }
            )
            SettingSwitchRow(
                title = "Boost quiet recordings",
                subtitle = "Makes the sound louder. Turn it off if it distorts.",
                checked = settings.volumeBoost,
                onCheckedChange = { onChange(settings.copy(volumeBoost = it)) }
            )
            SettingSwitchRow(
                title = "Shake to extend sleep timer",
                subtitle = "While a sleep timer is running, shake the phone to add more time.",
                checked = settings.shakeToExtendSleep,
                onCheckedChange = { onChange(settings.copy(shakeToExtendSleep = it)) }
            )

            if (settings.shakeToExtendSleep) {
                Text(
                    text = "A shake adds",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (minutes in EXTEND_OPTIONS) {
                        FilterChip(
                            selected = settings.sleepExtendMinutes == minutes,
                            onClick = { onChange(settings.copy(sleepExtendMinutes = minutes)) },
                            label = { Text("$minutes min") }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private val EXTEND_OPTIONS = listOf(5, 10, 15, 20)

/** A label with a row of choices in seconds. Seven choices are shown four, then three, to a row. */
@Composable
private fun SecondsChooser(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        for (rowOfChoices in AudioPlayerSettings.SKIP_OPTIONS.chunked(4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (seconds in rowOfChoices) {
                    FilterChip(
                        selected = selected == seconds,
                        onClick = { onSelect(seconds) },
                        label = { Text("$seconds s") }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
