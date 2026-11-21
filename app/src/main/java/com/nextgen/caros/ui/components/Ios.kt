package com.nextgen.caros.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

/** iOS system colours (dark-mode variants). */
object IosColors {
    val green = Color(0xFF30D158)
    val blue = Color(0xFF0A84FF)
    val red = Color(0xFFFF453A)
    val orange = Color(0xFFFF9F0A)
    val yellow = Color(0xFFFFD60A)
    val teal = Color(0xFF64D2FF)
    val indigo = Color(0xFF5E5CE6)
    val purple = Color(0xFFBF5AF2)
    val pink = Color(0xFFFF375F)
    val gray = Color(0xFF8E8E93)
    val mint = Color(0xFF66D4CF)
}

/**
 * "Liquid Glass" surface: translucent body, bright specular rim on the top edge fading
 * towards the bottom, and a soft inner sheen – readable over maps, artwork and wallpapers.
 */
fun Modifier.liquidGlass(shape: Shape, dark: Boolean, tint: Color = Color.White, strength: Float = 1f): Modifier = this
    .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f))
    .clip(shape)
    .background(
        if (dark) Brush.verticalGradient(listOf(Color(0xCC23262E), Color(0xD9141519)))
        else Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0xD9F2F2F7)))
    )
    .background(
        Brush.verticalGradient(
            0f to tint.copy(alpha = 0.10f * strength), 0.45f to Color.Transparent, 1f to tint.copy(alpha = 0.04f * strength),
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.35f else 0.9f), Color.White.copy(alpha = if (dark) 0.06f else 0.4f))),
        shape,
    )

/** iOS-style switch (51×31pt proportions, green when on). */
@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ng = LocalNg.current
    val track by animateColorAsState(
        if (checked) IosColors.green else if (ng.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA), tween(200), label = "track",
    )
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "thumb")
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(track.copy(alpha = if (enabled) 1f else 0.4f))
            .pointerInput(checked, enabled) { detectTapGestures { if (enabled) onChange(!checked) } },
    ) {
        Box(
            Modifier.offset(x = x, y = 2.dp).size(27.dp)
                .shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White),
        )
    }
}

/** iOS segmented control with a sliding selection "lens". */
@Composable
fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val ng = LocalNg.current
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.height(height).clip(RoundedCornerShape(11.dp))
            .background(if (ng.isDark) Color(0x3D767680) else Color(0x1F767680)),
    ) {
        val w = maxWidth / options.size
        val off by animateDpAsState(w * idx, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "seg")
        Box(
            Modifier.offset(x = off).width(w).fillMaxHeight().padding(2.dp)
                .shadow(2.dp, RoundedCornerShape(9.dp))
                .clip(RoundedCornerShape(9.dp))
                .background(if (ng.isDark) Color(0xFF636366) else Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (v, label) ->
                Box(
                    Modifier.width(w).fillMaxHeight().pointerInput(v) { detectTapGestures { onSelect(v) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = NgType.label.copy(color = ng.textPrimary, textAlign = TextAlign.Center), maxLines = 1)
                }
            }
        }
    }
}

/** Big, finger-friendly capsule slider like iOS Control Centre. */
@Composable
fun CapsuleSlider(value: Float, onChange: (Float) -> Unit, icon: ImageVector, modifier: Modifier = Modifier, label: String? = null) {
    val ng = LocalNg.current
    BoxWithConstraints(
        modifier.height(64.dp).clip(RoundedCornerShape(22.dp))
            .background(if (ng.isDark) Color(0x33FFFFFF) else Color(0x1A000000))
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        Box(Modifier.fillMaxHeight().width(maxWidth * value.coerceIn(0f, 1f)).background(Color.White.copy(alpha = if (ng.isDark) 0.92f else 1f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(26.dp))
            if (label != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                Text(label, style = NgType.label.copy(color = Color(0xFF636366)))
            }
        }
    }
}

/** iOS settings-style coloured icon square. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = NgType.largeTitle.copy(color = LocalNg.current.textPrimary), modifier = modifier)
}

@Suppress("unused")
private fun Modifier.fullWidth() = fillMaxWidth()
