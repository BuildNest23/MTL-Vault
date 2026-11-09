package com.example.ui.screens.voices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
                        "Pick a voice for each name below. Characters without a voice use the narrator's voice. " +
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
                        text = "Characters (${state.rows.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.rows, key = { it.key }) { row ->
                    VoiceCard(
                        title = "@" + row.name,
                        subtitle = null,
                        voice = row.voice,
                        blankLabel = "Same as narrator",
                        voices = state.voices,
                        onPick = { viewModel.setCharacterVoice(row.key, it) },
                        tag = "voices_row_" + row.key
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun VoiceCard(
    title: String,
    subtitle: String?,
    voice: String,
    blankLabel: String,
    voices: List<TtsManager.PhoneVoiceInfo>,
    onPick: (String) -> Unit,
    tag: String
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = if (voice.isBlank()) blankLabel else (voices.find { it.name == voice }?.label ?: voice)

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
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.fillMaxWidth().testTag(tag)
                ) {
                    Text("Voice: $label", maxLines = 1)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(blankLabel) },
                        onClick = {
                            onPick("")
                            menuOpen = false
                        }
                    )
                    if (voices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Voices are still loading. Close this and try again.") },
                            onClick = { menuOpen = false }
                        )
                    }
                    voices.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.label) },
                            onClick = {
                                onPick(v.name)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}
