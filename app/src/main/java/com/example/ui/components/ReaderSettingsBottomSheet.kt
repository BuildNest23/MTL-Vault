package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReaderSettings
import com.example.ui.theme.LoraFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.ReaderDarkBg
import com.example.ui.theme.ReaderDarkText
import com.example.ui.theme.ReaderLightBg
import com.example.ui.theme.ReaderLightText
import com.example.ui.theme.ReaderNightBg
import com.example.ui.theme.ReaderNightText
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaText

private data class PaperThemeOption(
    val id: String,
    val label: String,
    val bg: Color,
    val fg: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsBottomSheet(
    settings: ReaderSettings,
    onUpdateSettings: (ReaderSettings) -> Unit,
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
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Reading Room",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Paper tone, typeface, and rhythm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Paper Theme Swatches
            Text(
                text = "PAPER THEME",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            val themes = listOf(
                PaperThemeOption("light", "Linen", ReaderLightBg, ReaderLightText),
                PaperThemeOption("sepia", "Sepia", ReaderSepiaBg, ReaderSepiaText),
                PaperThemeOption("dark", "Study", ReaderDarkBg, ReaderDarkText),
                PaperThemeOption("night", "Night", ReaderNightBg, ReaderNightText)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                themes.forEach { theme ->
                    val isSelected = settings.theme == theme.id
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onUpdateSettings(settings.copy(theme = theme.id)) },
                        shape = RoundedCornerShape(14.dp),
                        color = theme.bg,
                        border = BorderStroke(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Aa",
                                fontFamily = LoraFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = theme.fg
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = theme.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.fg.copy(alpha = if (isSelected) 1f else 0.72f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Typeface Choice
            Text(
                text = "TYPEFACE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val fontOptions = listOf(
                Triple("serif", "Lora Serif", LoraFontFamily),
                Triple("sans", "Jakarta Sans", PlusJakartaSansFontFamily),
                Triple("system", "System", FontFamily.Default)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                fontOptions.forEachIndexed { index, (key, label, family) ->
                    val selected = if (key == "serif") {
                        settings.fontFamily == "serif" || (settings.fontFamily != "sans" && settings.fontFamily != "system")
                    } else {
                        settings.fontFamily == key
                    }
                    SegmentedButton(
                        selected = selected,
                        onClick = { onUpdateSettings(settings.copy(fontFamily = key)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = fontOptions.size)
                    ) {
                        Text(
                            text = label,
                            fontFamily = family,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(14.dp))

            // Font Size Slider
            var fontSizeDraft by remember(settings.fontSize) {
                mutableFloatStateOf(settings.fontSize.toFloat())
            }
            val displayFontSize = kotlin.math.round(fontSizeDraft).toInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text Size",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$displayFontSize sp",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = fontSizeDraft,
                onValueChange = { fontSizeDraft = it },
                onValueChangeFinished = {
                    val committed = kotlin.math.round(fontSizeDraft).toInt()
                    if (committed != settings.fontSize) {
                        onUpdateSettings(settings.copy(fontSize = committed))
                    }
                },
                valueRange = 12f..32f,
                steps = 9
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Line Height Slider
            var lineHeightDraft by remember(settings.lineHeight) {
                mutableFloatStateOf(settings.lineHeight)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Line Spacing",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.2f×", lineHeightDraft),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = lineHeightDraft,
                onValueChange = { lineHeightDraft = it },
                onValueChangeFinished = {
                    if (lineHeightDraft != settings.lineHeight) {
                        onUpdateSettings(settings.copy(lineHeight = lineHeightDraft))
                    }
                },
                valueRange = 1.2f..2.4f,
                steps = 11
            )
        }
    }
}
