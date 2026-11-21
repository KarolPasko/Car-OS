package com.nextgen.caros.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import com.nextgen.caros.ui.theme.LocalNg
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * "Now playing" equaliser animation. It is a decorative indicator driven by play state – it
 * does not analyse the audio signal (that would require microphone-level permissions).
 */
@Composable
fun MusicVisualizer(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 24) {
    val ng = LocalNg.current
    val t = rememberInfiniteTransition(label = "viz")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val amp by animateFloatAsState(if (playing) 1f else 0.08f, tween(600), label = "amp")
    val brush = Brush.verticalGradient(listOf(ng.accent, ng.accent2))
    Canvas(modifier) {
        val w = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val x = i * w * 1.6f
            val h1 = abs(sin(phase * 2 + i * 0.55f)) * 0.55f + abs(sin(phase * 3.3f + i * 1.3f)) * 0.45f
            val h = (0.12f + 0.88f * h1 * amp).coerceIn(0.06f, 1f) * size.height
            drawRoundRect(brush, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2, w / 2), alpha = 0.85f)
        }
    }
}
