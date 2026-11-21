package com.nextgen.caros.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.ui.theme.NgType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** iOS-style app icon: continuous-corner squircle, top-lit gradient, white glyph. */
@Composable
fun SquircleIcon(
    icon: ImageVector?,
    colors: List<Color>,
    size: Dp,
    modifier: Modifier = Modifier,
    glyphTint: Color = Color.White,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(size * 0.225f)
    Box(
        modifier.size(size).shadow(size * 0.08f, shape).clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(size * 0.01f, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        // soft top sheen
        Box(Modifier.size(size).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent))))
        if (content != null) content() else if (icon != null) Icon(icon, null, tint = glyphTint, modifier = Modifier.size(size * 0.56f))
    }
}

/** Calendar icon that always shows today's weekday and date. */
@Composable
fun CalendarIcon(now: Long, size: Dp, modifier: Modifier = Modifier) {
    SquircleIcon(null, listOf(Color.White, Color(0xFFF2F2F7)), size, modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(now)).uppercase(),
                style = NgType.label.copy(color = Color(0xFFFF3B30), fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.17f).sp))
            Text(SimpleDateFormat("d", Locale.getDefault()).format(Date(now)),
                style = NgType.display.copy(color = Color.Black, fontWeight = FontWeight.Light, fontSize = (size.value * 0.46f).sp, letterSpacing = 0.sp))
        }
    }
}

/** "Now Playing" icon: white tile with a red→pink waveform. */
@Composable
fun NowPlayingIcon(size: Dp, modifier: Modifier = Modifier) {
    SquircleIcon(null, listOf(Color.White, Color(0xFFF2F2F7)), size, modifier) {
        Canvas(Modifier.size(size * 0.62f)) {
            val heights = listOf(0.9f, 0.6f, 0.75f, 0.45f, 0.3f, 0.12f)
            val w = this.size.width / 11f
            heights.forEachIndexed { i, h ->
                val bh = this.size.height * h
                val x = i * w * 1.9f
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFFF2D55), Color(0xFFFF375F))),
                    Offset(x, (this.size.height - bh) / 2), Size(w, bh), CornerRadius(w / 2, w / 2),
                )
            }
        }
    }
}
