package com.nextgen.caros.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

/** Frosted translucent panel with a soft highlight border – the core surface of the design. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    strong: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val ng = LocalNg.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (ng.isDark) listOf(if (strong) ng.glassStrong else ng.glass, Color(0x0DFFFFFF))
                    else listOf(ng.glassStrong, ng.glass)
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(ng.glassBorder, ng.glassBorder.copy(alpha = ng.glassBorder.alpha * 0.25f))),
                shape,
            )
            .padding(padding),
        content = content,
    )
}

/** Clickable with a subtle spring "press" scale instead of a ripple. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .scale(s)
        .combinedClickable(
            interactionSource = source, indication = null, enabled = enabled,
            onLongClick = onLongClick, onClick = onClick,
        )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = LocalNg.current.textPrimary,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val ng = LocalNg.current
    Box(
        modifier
            .size(size)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (accent) ng.accentBrush else Brush.linearGradient(listOf(ng.glassStrong, ng.glass)))
            .border(1.dp, ng.glassBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (accent) Color.Black else tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ng = LocalNg.current
    val shape = RoundedCornerShape(50)
    val bg: Brush = when {
        danger -> Brush.linearGradient(listOf(ng.danger, ng.danger.copy(alpha = 0.8f)))
        primary -> ng.accentBrush
        else -> Brush.linearGradient(listOf(ng.glassStrong, ng.glass))
    }
    val fg = if (primary && !danger) Color(0xFF020617) else if (danger) Color.White else ng.textPrimary
    Row(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .clip(shape)
            .background(bg, alpha = if (enabled) 1f else 0.4f)
            .border(1.dp, ng.glassBorder, shape)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = NgType.label.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = NgType.caption.copy(color = LocalNg.current.textTertiary), modifier = modifier)
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    color: Color? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val ng = LocalNg.current
    val shape: Shape = RoundedCornerShape(50)
    val c = color ?: ng.accent
    Row(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(shape)
            .background(if (selected) c.copy(alpha = 0.22f) else ng.glass)
            .border(1.dp, if (selected) c.copy(alpha = 0.7f) else ng.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c else ng.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = NgType.label.copy(color = if (selected) ng.textPrimary else ng.textSecondary), maxLines = 1)
    }
}

@Composable
fun EmptyHint(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val ng = LocalNg.current
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = ng.textTertiary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = NgType.headline.copy(color = ng.textPrimary))
        Spacer(Modifier.height(4.dp))
        Text(body, style = NgType.body.copy(color = ng.textSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

@Composable
fun RowScope.Weight(w: Float) = Spacer(Modifier.weight(w))
