package com.example.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tts.edge.EdgeVoices
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.MtlTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val logSize by viewModel.logSizeText.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearLogDialog by remember { mutableStateOf(false) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(context, uri)
        }
    }

    val saveLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.saveLogToUri(context, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshLogSize(context)
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MtlTopBar(
                title = "Settings",
                subtitle = "APPEARANCE, SPEECH & BACKUP"
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SettingsSectionOverline("APPEARANCE & READING")
                }

                // App Colors Card (Material You)
                item {
                    val dynamicSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Material You colors",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (dynamicSupported) {
                                        "Match the palette to your wallpaper instead of Warm Editorial Jade."
                                    } else {
                                        "Requires Android 12 or newer."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = dynamicColor && dynamicSupported,
                                onCheckedChange = { viewModel.updateDynamicColor(it) },
                                enabled = dynamicSupported,
                                modifier = Modifier.testTag("settings_dynamic_color_switch")
                            )
                        }
                    }
                }

                // Reading Defaults Card
                item {
                    SettingsCard {
                        Text(
                            text = "Reading Defaults",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Text("Paper Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val themes = listOf("light" to "Linen", "sepia" to "Sepia", "dark" to "Study", "night" to "Night")
                            themes.forEachIndexed { index, (key, label) ->
                                SegmentedButton(
                                    selected = settings.theme == key,
                                    onClick = { viewModel.updateTheme(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size)
                                ) {
                                    Text(label, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Default Font Size", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${settings.fontSize} sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.fontSize.toFloat(),
                            onValueChange = { viewModel.updateFontSize(it.toInt()) },
                            valueRange = 12f..32f,
                            steps = 9
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Default Typeface", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        val fonts = listOf("serif" to "Lora Serif", "sans" to "Jakarta Sans", "system" to "System")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            fonts.forEachIndexed { index, (key, label) ->
                                val selected = if (key == "serif") {
                                    settings.fontFamily == "serif" || (settings.fontFamily != "sans" && settings.fontFamily != "system")
                                } else {
                                    settings.fontFamily == key
                                }
                                SegmentedButton(
                                    selected = selected,
                                    onClick = { viewModel.updateFontFamily(key) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = fonts.size)
                                ) {
                                    Text(label, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Text Replacements Global Toggle
                item {
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Text replacements",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Apply per-novel find & replace cleanup rules non-destructively when reading or listening.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.replacementsEnabled,
                                onCheckedChange = { viewModel.updateReplacementsEnabled(it) },
                                modifier = Modifier.testTag("settings_global_replacements_switch")
                            )
                        }
                    }
                }

                item {
                    SettingsSectionOverline("VOICE & NARRATION")
                }

                // Text-to-Speech Defaults Card
                item {
                    SettingsCard {
                        Text(
                            text = "Narration Engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Edge neural voices",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Natural neural Microsoft voices over internet. Falls back to your phone voice offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.ttsEngine == "edge",
                                onCheckedChange = { viewModel.updateUseEdgeVoices(it) },
                                modifier = Modifier.testTag("settings_edge_voices_switch")
                            )
                        }

                        if (settings.ttsEngine == "edge") {
                            Spacer(modifier = Modifier.height(10.dp))
                            var voiceMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { voiceMenuOpen = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 46.dp)
                                ) {
                                    Text("Voice: " + EdgeVoices.labelFor(settings.edgeVoice))
                                }
                                DropdownMenu(
                                    expanded = voiceMenuOpen,
                                    onDismissRequest = { voiceMenuOpen = false }
                                ) {
                                    EdgeVoices.all.forEach { voice ->
                                        DropdownMenuItem(
                                            text = { Text(voice.label) },
                                            onClick = {
                                                viewModel.updateEdgeVoice(voice.shortName)
                                                voiceMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Offline phone voice engine",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Used when Edge voices are off or offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        var engines by remember { mutableStateOf(viewModel.phoneEngines()) }
                        var engineMenuOpen by remember { mutableStateOf(false) }
                        val engineLabel = if (settings.phoneEngine.isBlank()) {
                            "System default"
                        } else {
                            engines.find { it.packageName == settings.phoneEngine }?.label ?: settings.phoneEngine
                        }
                        Box {
                            OutlinedButton(
                                onClick = {
                                    engines = viewModel.phoneEngines()
                                    engineMenuOpen = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("settings_phone_engine_button")
                            ) {
                                Text("Engine: $engineLabel")
                            }
                            DropdownMenu(
                                expanded = engineMenuOpen,
                                onDismissRequest = { engineMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("System default") },
                                    onClick = {
                                        viewModel.updatePhoneEngine("")
                                        engineMenuOpen = false
                                    }
                                )
                                engines.forEach { engine ->
                                    DropdownMenuItem(
                                        text = { Text(engine.label) },
                                        onClick = {
                                            viewModel.updatePhoneEngine(engine.packageName)
                                            engineMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        var phoneVoices by remember { mutableStateOf(viewModel.phoneVoices()) }
                        var phoneVoiceMenuOpen by remember { mutableStateOf(false) }
                        val phoneVoiceLabel = settings.ttsVoice.ifBlank { "Engine default" }
                        Box {
                            OutlinedButton(
                                onClick = {
                                    phoneVoices = viewModel.phoneVoices()
                                    phoneVoiceMenuOpen = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("settings_phone_voice_button")
                            ) {
                                Text("Voice: $phoneVoiceLabel")
                            }
                            DropdownMenu(
                                expanded = phoneVoiceMenuOpen,
                                onDismissRequest = { phoneVoiceMenuOpen = false }
                            ) {
                                if (phoneVoices.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No voices yet. Wait a moment and try again.") },
                                        onClick = { phoneVoiceMenuOpen = false }
                                    )
                                }
                                phoneVoices.forEach { voice ->
                                    DropdownMenuItem(
                                        text = { Text(voice.label) },
                                        onClick = {
                                            viewModel.updateTtsVoice(voice.name)
                                            phoneVoiceMenuOpen = false
                                            viewModel.previewPhoneVoice()
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.previewPhoneVoice() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("settings_phone_voice_sample")
                        ) {
                            Text("Play voice sample")
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Rate", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format("%.1fx", settings.ttsRate),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.ttsRate,
                            onValueChange = { viewModel.updateTtsRate(it) },
                            valueRange = 0.5f..2.5f,
                            steps = 7
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Pitch", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format("%.1fx", settings.ttsPitch),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.ttsPitch,
                            onValueChange = { viewModel.updateTtsPitch(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Auto-advance chapters",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Automatically continue reading into the next chapter when one finishes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.continueReading,
                                onCheckedChange = { viewModel.updateContinueReading(it) },
                                modifier = Modifier.testTag("settings_continue_reading_switch")
                            )
                        }
                    }
                }

                item {
                    SettingsSectionOverline("LIBRARY BACKUP")
                }

                // Backup & Restore Card
                item {
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Export your entire library, chapters, reading progress, and replacement rules to a portable JSON file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.exportBackup(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_export_backup_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Export JSON")
                            }

                            OutlinedButton(
                                onClick = { importBackupLauncher.launch("application/json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_import_backup_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Import JSON")
                            }
                        }
                    }
                }

                item {
                    SettingsSectionOverline("DIAGNOSTICS & RESET")
                }

                // Diagnostics Card
                item {
                    SettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Captures technical events, TTS status, and error traces without recording your novel text. Current size: $logSize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Detailed logging",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Record debug details in addition to info, warnings, and errors",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.detailedLogging,
                                onCheckedChange = { viewModel.updateDetailedLogging(it) },
                                modifier = Modifier.testTag("settings_detailed_logging_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.shareLog(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                                    .testTag("settings_share_log_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Share Log", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    saveLogLauncher.launch("mtl_vault_log_$dateStr.txt")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 46.dp)
                                    .testTag("settings_save_log_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save File", maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showClearLogDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("settings_clear_log_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear Log", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Danger Zone Card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Permanently erase all novels, chapters, covers, voice casts, progress, and replacement rules stored on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { showResetDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_reset_all_button")
                            ) {
                                Text("Reset All Data")
                            }
                        }
                    }
                }

                // Editorial Colophon Footer
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val appVersion = remember {
                            try {
                                context.packageManager
                                    .getPackageInfo(context.packageName, 0)
                                    .versionName ?: ""
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        Text(
                            text = "MTL Vault • v$appVersion",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Local • Offline • Private",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Erase Everything?",
            message = "This will permanently erase all novels, chapters, and data from your device. This cannot be undone.",
            confirmText = "Erase All",
            isDestructive = true,
            onConfirm = {
                showResetDialog = false
                viewModel.resetAllData(context)
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showClearLogDialog) {
        ConfirmDialog(
            title = "Clear Logs?",
            message = "Are you sure you want to clear all current and archived diagnostic log entries?",
            confirmText = "Clear",
            isDestructive = true,
            onConfirm = {
                showClearLogDialog = false
                viewModel.clearLogs(context)
            },
            onDismiss = { showClearLogDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionOverline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 2.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}
