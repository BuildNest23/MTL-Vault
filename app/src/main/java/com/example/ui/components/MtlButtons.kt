package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visual weight of an action button. Use ONE Primary per screen section. */
enum class MtlButtonStyle { Primary, Tonal, Secondary, Subtle }

/**
 * Shared editorial action button with generous 48dp minimum touch targets on primary actions,
 * crisp typography, and warm hairline border on subtle actions.
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
        MtlButtonStyle.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        MtlButtonStyle.Subtle -> scheme.surface to scheme.onSurface
    }
    val minHeight = when {
        large -> 52.dp
        style == MtlButtonStyle.Subtle -> 44.dp
        else -> 48.dp
    }
    val border = if (style == MtlButtonStyle.Subtle) {
        BorderStroke(1.dp, scheme.outlineVariant)
    } else null

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(14.dp),
        border = border,
        elevation = null,
        contentPadding = PaddingValues(horizontal = if (large) 16.dp else 10.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = scheme.onSurface.copy(alpha = 0.08f),
            disabledContentColor = scheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(if (large) 20.dp else 17.dp))
        Spacer(modifier = Modifier.width(if (large) 8.dp else 6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (large) 15.sp else 13.sp,
                fontWeight = if (style == MtlButtonStyle.Subtle) FontWeight.Medium else FontWeight.SemiBold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
