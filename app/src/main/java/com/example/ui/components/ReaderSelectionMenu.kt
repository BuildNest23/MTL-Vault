package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Replaces the system selection menu in the reader with our own: Copy, Replace (add a text
 * replacement rule), Define (dictionary lookup) and More (translate / other apps).
 *
 * Compose only hands a toolbar a "copy" callback, so to find out WHICH text is selected we call
 * that callback and read the text back from the clipboard.
 */
class ReaderTextToolbar : TextToolbar {

    class MenuData(val rect: Rect, val onCopy: (() -> Unit)?)

    var menu by mutableStateOf<MenuData?>(null)
        private set

    override val status: TextToolbarStatus
        get() = if (menu != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menu = MenuData(rect, onCopyRequested)
    }

    override fun hide() {
        menu = null
    }
}

@Composable
fun rememberReaderTextToolbar(): ReaderTextToolbar = remember { ReaderTextToolbar() }

/** Draws the floating menu above (or below) the selected text. Place it once inside the reader. */
@Composable
fun ReaderSelectionPopup(
    toolbar: ReaderTextToolbar,
    onReplace: (selectedText: String) -> Unit
) {
    val data = toolbar.menu ?: return
    val context = LocalContext.current

    val positionProvider = remember(data.rect) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (data.rect.center.x - popupContentSize.width / 2f).toInt()
                    .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                val above = data.rect.top.toInt() - popupContentSize.height - 16
                // If there is no room above, go below (leaving space for the selection handles)
                val y = if (above > 96) above else data.rect.bottom.toInt() + 96
                return IntOffset(x, y.coerceAtLeast(0))
            }
        }
    }

    // Copies the selection (which also ends the selection) and returns its text.
    fun grabSelection(): String {
        val copy = data.onCopy ?: return ""
        copy()
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return cm?.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(modifier = Modifier.padding(horizontal = 2.dp)) {
                MenuItem("Copy") {
                    data.onCopy?.invoke()
                    toolbar.hide()
                }
                MenuItem("Replace") {
                    val text = grabSelection()
                    toolbar.hide()
                    if (text.isNotBlank()) onReplace(text)
                }
                MenuItem("Define") {
                    val text = grabSelection()
                    toolbar.hide()
                    if (text.isNotBlank()) defineWord(context, text)
                }
                MenuItem("More") {
                    val text = grabSelection()
                    toolbar.hide()
                    if (text.isNotBlank()) openWithOtherApps(context, text)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, fontSize = 14.sp)
    }
}

/** Opens a web dictionary search for the selected word or phrase. */
private fun defineWord(context: Context, text: String) {
    val query = Uri.encode("define " + text.take(100))
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}

/** Lets the person send the text to Translate, an AI assistant, a dictionary app, etc. */
private fun openWithOtherApps(context: Context, text: String) {
    val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    // PROCESS_TEXT apps (Translate, assistants, dictionaries) first; plain share as an alternative.
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val intent = Intent.createChooser(process, "Open with").apply {
        putExtra(Intent.EXTRA_ALTERNATE_INTENTS, arrayOf(send))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available", Toast.LENGTH_SHORT).show()
    }
}

/** Small dialog to turn selected text into a replacement rule without leaving the reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleFromSelectionDialog(
    initialFind: String,
    onDismiss: () -> Unit,
    onSave: (find: String, replace: String, caseSensitive: Boolean, partialMatch: Boolean) -> Unit
) {
    var find by remember { mutableStateOf(initialFind.replace('\n', ' ').trim()) }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add replacement rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = find,
                    onValueChange = { find = it },
                    label = { Text("Original") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Replace with") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = caseSensitive,
                        onClick = { caseSensitive = !caseSensitive },
                        label = { Text("[cs] Case", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = partial,
                        onClick = { partial = !partial },
                        label = { Text("[nww] Inside", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(find, replacement, caseSensitive, partial) },
                enabled = find.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
