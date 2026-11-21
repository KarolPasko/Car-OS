package com.nextgen.caros.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.speedlimit.ParsedLimit
import com.nextgen.caros.speedlimit.SpeedLimitState
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

/**
 * Road-sign style speed-limit indicator. Shows the number exactly as on the road sign
 * (with its unit if it differs from the user's unit). When no limit is known it shows a grey
 * dashed sign and the word "No data" – never a guessed value.
 */
@Composable
fun SpeedLimitSign(state: SpeedLimitState, unit: SpeedUnit, size: Dp = 84.dp, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    AnimatedContent(
        targetState = state,
        transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.85f)) togetherWith fadeOut(tween(150)) },
        contentKey = { s -> if (s is SpeedLimitState.Known) s.limit.toString() else s::class.simpleName },
        label = "limit",
        modifier = modifier,
    ) { st ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (st) {
                is SpeedLimitState.Known -> when (val l = st.limit) {
                    is ParsedLimit.Value -> {
                        val signUnitMatches = l.mphSign == (unit == SpeedUnit.MPH)
                        RoundSign(size) {
                            Text(
                                l.signValue.toString(),
                                style = NgType.title.copy(
                                    color = Color(0xFF111111), fontWeight = FontWeight.Bold,
                                    fontSize = (size.value * if (l.signValue >= 100) 0.30f else 0.36f).sp,
                                ),
                            )
                        }
                        if (!signUnitMatches) Text(if (l.mphSign) "mph" else "km/h", style = NgType.caption.copy(color = ng.textSecondary))
                    }
                    ParsedLimit.NoLimit -> DerestrictedSign(size)
                    ParsedLimit.Variable -> RoundSign(size) { Text("VAR", style = NgType.label.copy(color = Color(0xFF111111), fontWeight = FontWeight.Bold)) }
                    ParsedLimit.Walk -> RoundSign(size) { Text("WALK", style = NgType.caption.copy(color = Color(0xFF111111), fontWeight = FontWeight.Bold)) }
                    is ParsedLimit.Unknown -> UnknownSign(size, "No data")
                }
                is SpeedLimitState.Unavailable -> UnknownSign(size, "No data")
                SpeedLimitState.Searching -> UnknownSign(size, "Locating")
                SpeedLimitState.Disabled -> UnknownSign(size, "Off")
            }
        }
    }
}

@Composable
private fun RoundSign(size: Dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(Color.White)
            drawCircle(Color(0xFFE11D2E), radius = this.size.minDimension / 2 - this.size.minDimension * 0.06f, style = Stroke(this.size.minDimension * 0.12f))
        }
        content()
    }
}

@Composable
private fun DerestrictedSign(size: Dp) {
    Canvas(Modifier.size(size)) {
        drawCircle(Color.White)
        drawCircle(Color(0xFF222222), style = Stroke(this.size.minDimension * 0.04f))
        val d = this.size.minDimension
        for (k in -2..2) {
            val o = k * d * 0.06f
            drawLine(
                Color(0xFF222222), Offset(d * 0.22f + o, d * 0.78f + o), Offset(d * 0.78f + o, d * 0.22f + o),
                strokeWidth = d * 0.03f, cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun UnknownSign(size: Dp, text: String) {
    val ng = LocalNg.current
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            drawCircle(
                ng.textTertiary, radius = this.size.minDimension / 2 - 3f,
                style = Stroke(this.size.minDimension * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("—", style = NgType.headline.copy(color = ng.textSecondary))
            Text(text, style = NgType.caption.copy(color = ng.textTertiary, fontSize = (size.value * 0.13f).sp))
        }
    }
}
