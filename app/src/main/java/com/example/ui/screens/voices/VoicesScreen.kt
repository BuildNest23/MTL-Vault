package com.example.ui.screens.voices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.TtsManager
import com.example.ui.components.MtlTopBar

@Composable
fun VoicesScreen(
    viewModel: VoicesViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MtlTopBar(
                title = "Character voices",
                subtitle = state.novelTitle.ifBlank { "Novel" },
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("voices_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Look for characters again")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A line that starts with @Name: is spoken by that character, for example @Robin: \"Hello.\" " +
                        "Mark each name Male or Female and set the two default voices, or pick a voice for a name yourself. " +
                        "Characters with neither use the narrator's voice. " +
                        "These voices apply to the phone voice (for example ToBe SAID), not to Edge voices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                VoiceCard(
                    title = "Narrator",
                    subtitle = "Everything without a tag",
                    voice = state.narratorVoice,
                    blankLabel = "Normal voice (Settings)",
                    voices = state.voices,
                    onPick = { viewModel.setNarratorVoice(it) },
                    tag = "voices_narrator"
                )
            }

            item {
                VoiceCard(
                    title = "Default male voice",
                    subtitle = "For characters marked Male that have no voice of their own",
                    voice = state.defaultMale,
                    blankLabel = "Same as narrator",
                    voices = state.voices,
                    onPick = { viewModel.setDefaultVoice(true, it) },
                    tag = "voices_default_male"
                )
            }

            item {
                VoiceCard(
                    title = "Default female voice",
                    subtitle = "For characters marked Female that have no voice of their own",
                    voice = state.defaultFemale,
                    blankLabel = "Same as narrator",
                    voices = state.voices,
                    onPick = { viewModel.setDefaultVoice(false, it) },
                    tag = "voices_default_female"
                )
            }

            if (state.loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.rows.isEmpty()) {
                item {
                    Text(
                        text = "No characters found yet. Add speaker tags like @Robin: to your chapters, " +
                            "then tap the refresh button.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = "Characters (${state.rows.size}), most lines first",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.rows, key = { it.key }) { row ->
                    VoiceCard(
                        title = "@" + row.name,
                        subtitle = if (row.lines == 1) "1 line" else "${row.lines} lines",
                        voice = row.voice,
                        blankLabel = when (row.gender) {
                            "m" -> "Default male voice"
                            "f" -> "Default female voice"
                            else -> "Same as narrator"
                        },
                        voices = state.voices,
                        onPick = { viewModel.setCharacterVoice(row.key, it) },
                        tag = "voices_row_" + row.key,
                        gender = row.gender,
                        onGender = { viewModel.setGender(row.key, it) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceCard(
    title: String,
    subtitle: String?,
    voice: String,
    blankLabel: String,
    voices: List<TtsManager.PhoneVoiceInfo>,
    onPick: (String) -> Unit,
    tag: String,
    gender: String? = null,
    onGender: (String) -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = if (voice.isBlank()) blankLabel else (voices.find { it.name == voice }?.let { shortLabel(it) } ?: voice)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (gender != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == "m",
                        onClick = { onGender("m") },
                        label = { Text("Male") },
                        modifier = Modifier.testTag("$tag-male")
                    )
                    FilterChip(
                        selected = gender == "f",
                        onClick = { onGender("f") },
                        label = { Text("Female") },
                        modifier = Modifier.testTag("$tag-female")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth().testTag(tag)
                ) {
                    Text("Voice: $label", maxLines = 1)
                }
                if (menuOpen) {
                    VoicePickerDialog(
                        blankLabel = blankLabel,
                        voices = voices,
                        onPick = {
                            onPick(it)
                            menuOpen = false
                        },
                        onDismiss = { menuOpen = false }
                    )
                }
            }
        }
    }
}

/** "Travis - English  (English (United States))" -> "Travis". Other languages keep their full label. */
private fun shortLabel(v: TtsManager.PhoneVoiceInfo): String {
    return if (v.label.contains("(English")) v.label.substringBefore("  (").removeSuffix(" - English") else v.label
}

/** A scrollable, searchable list of voices (a dropdown is too long for dozens of voices). */
@Composable
private fun VoicePickerDialog(
    blankLabel: String,
    voices: List<TtsManager.PhoneVoiceInfo>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, voices) {
        if (query.isBlank()) voices else voices.filter { shortLabel(it).contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a voice") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    item {
                        Text(
                            text = blankLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 12.dp)
                        )
                    }
                    if (voices.isEmpty()) {
                        item {
                            Text(
                                text = "Voices are still loading. Close this and try again.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(shown, key = { it.name }) { v ->
                        Text(
                            text = shortLabel(v),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(v.name) }.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
