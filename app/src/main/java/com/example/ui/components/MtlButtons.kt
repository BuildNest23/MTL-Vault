package com.example.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visual weight of an action button. Use ONE Primary per screen section. */
enum class MtlButtonStyle { Primary, Tonal, Subtle }

/**
 * Shared action button so every screen uses the same height, corners, icon size
 * and colours. Pass layout/testTag via [modifier] (e.g. Modifier.weight(1f).testTag("x")).
 */
@Composable
fun MtlActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MtlButtonStyle = MtlButtonStyle.Tonal,
    enabled: Boolean = true,
    large: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (style) {
        MtlButtonStyle.Primary -> scheme.primary to scheme.onPrimary
        MtlButtonStyle.Tonal -> scheme.primaryContainer to scheme.onPrimaryContainer
        MtlButtonStyle.Subtle -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    val buttonHeight = when {
        large -> 52.dp
        style == MtlButtonStyle.Subtle -> 40.dp
        else -> 46.dp
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(buttonHeight),
        shape = RoundedCornerShape(12.dp),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = scheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 22.dp else 17.dp))
        Spacer(modifier = Modifier.width(if (large) 8.dp else 5.dp))
        Text(
            text = text,
            fontSize = if (large) 15.sp else 13.sp,
            fontWeight = if (style == MtlButtonStyle.Subtle) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
