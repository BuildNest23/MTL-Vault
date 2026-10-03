package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.importexport.ChapterSplitter

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("dialog_confirm_button"),
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text(cancelText)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun PromptDialog(
    title: String,
    message: String? = null,
    initialValue: String = "",
    placeholder: String = "",
    multiline: Boolean = false,
    confirmText: String = "OK",
    cancelText: String = "Cancel",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column {
                if (!message.isNullOrBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder) },
                    singleLine = !multiline,
                    maxLines = if (multiline) 8 else 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prompt_text_field"),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                modifier = Modifier.testTag("prompt_ok_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("prompt_cancel_button")
            ) {
                Text(cancelText)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun ChapterSplitDialog(
    totalWords: Int,
    onSelectSplitMode: (mode: ChapterSplitter.SplitMode, customMarker: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(ChapterSplitter.SplitMode.AUTO_CHAPTER) }
    var customMarker by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Chapter Splitting Method", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Text contains approx ${totalWords.toString()} words. How should it be split into chapters?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMode == ChapterSplitter.SplitMode.AUTO_CHAPTER,
                        onClick = { selectedMode = ChapterSplitter.SplitMode.AUTO_CHAPTER }
                    )
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text("Auto Regex (Recommended)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Detects 'Chapter 1', 'Chapitre', 'Capítulo', 'باب', etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMode == ChapterSplitter.SplitMode.CUSTOM_MARKER,
                        onClick = { selectedMode = ChapterSplitter.SplitMode.CUSTOM_MARKER }
                    )
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text("Custom Marker", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Splits on your exact keyword or line header",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedMode == ChapterSplitter.SplitMode.CUSTOM_MARKER) {
                    OutlinedTextField(
                        value = customMarker,
                        onValueChange = { customMarker = it },
                        placeholder = { Text("e.g. Middle Sector, Chapter, ===") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedMode == ChapterSplitter.SplitMode.SINGLE_CHAPTER,
                        onClick = { selectedMode = ChapterSplitter.SplitMode.SINGLE_CHAPTER }
                    )
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text("Single Chapter", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Do not split; import entire text as one chapter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectSplitMode(selectedMode, customMarker) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Next")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
