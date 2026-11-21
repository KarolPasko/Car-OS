package com.nextgen.caros.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated arc speedometer. [speed] is in the user's unit; null shows "--" (no GPS speed).
 * Turns red when [overspeed] is true.
 */
@Composable
fun Speedometer(
    speed: Double?,
    unitLabel: String,
    maxSpeed: Float,
    overspeed: Boolean,
    modifier: Modifier = Modifier,
    numberSize: TextUnit = 64.sp,
) {
    val ng = LocalNg.current
    val target = ((speed ?: 0.0).toFloat() / maxSpeed).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(target, tween(700), label = "speed")
    val shown by animateFloatAsState((speed ?: 0.0).toFloat(), tween(500), label = "speedNum")
    val c1 by animateColorAsState(if (overspeed) ng.danger else ng.accent, tween(400), label = "c1")
    val c2 by animateColorAsState(if (overspeed) Color(0xFFFB923C) else ng.accent2, tween(400), label = "c2")
    val track = ng.textPrimary.copy(alpha = 0.08f)
    val tick = ng.textPrimary.copy(alpha = 0.25f)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = size.minDimension * 0.07f
            val inset = stroke / 2 + size.minDimension * 0.04f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val start = 135f; val sweep = 270f
            drawArc(track, start, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(c1, c2, c1)), start, sweep * fraction, false, tl, arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Ticks
            val r = size.minDimension / 2
            for (i in 0..18) {
                val a = Math.toRadians((start + sweep * i / 18f).toDouble())
                val major = i % 3 == 0
                val r1 = r - inset - stroke * (if (major) 1.4f else 1.1f)
                val r2 = r - inset - stroke * 0.9f
                drawLine(
                    tick, Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()),
                    Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()),
                    strokeWidth = if (major) 3f else 1.5f, cap = StrokeCap.Round,
                )
            }
            // Glowing tip
            if (fraction > 0.005f) {
                rotate(start + sweep * fraction, center) {
                    drawCircle(c2.copy(alpha = 0.35f), stroke * 0.9f, Offset(center.x + r - inset, center.y))
                    drawCircle(Color.White, stroke * 0.32f, Offset(center.x + r - inset, center.y))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (speed == null) "--" else shown.toInt().toString(),
                style = NgType.display.copy(color = if (overspeed) ng.danger else ng.textPrimary, fontSize = numberSize),
            )
            Text(unitLabel, style = NgType.caption.copy(color = ng.textSecondary))
        }
    }
}
