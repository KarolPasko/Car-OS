package com.nextgen.caros.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import com.nextgen.caros.ui.theme.LocalNg
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Current time in millis, ticking every second. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    return now
}

fun formatTime(millis: Long, use24h: Boolean, seconds: Boolean = false): String {
    val p = when {
        use24h && seconds -> "HH:mm:ss"
        use24h -> "HH:mm"
        seconds -> "h:mm:ss"
        else -> "h:mm"
    }
    return SimpleDateFormat(p, Locale.getDefault()).format(Date(millis))
}

fun amPm(millis: Long): String = SimpleDateFormat("a", Locale.getDefault()).format(Date(millis))

fun formatDate(millis: Long): String = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(millis))

@Composable
fun AnalogClock(now: Long, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f
    val m = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
    val s = cal.get(Calendar.SECOND)
    Canvas(modifier.aspectRatio(1f)) {
        val r = size.minDimension / 2
        drawCircle(ng.glass, r)
        for (i in 0 until 60) {
            rotate(i * 6f, center) {
                val major = i % 5 == 0
                drawLine(
                    ng.textPrimary.copy(alpha = if (major) 0.8f else 0.25f),
                    Offset(center.x, center.y - r * 0.92f), Offset(center.x, center.y - r * (if (major) 0.80f else 0.86f)),
                    strokeWidth = if (major) 4f else 2f, cap = StrokeCap.Round,
                )
            }
        }
        rotate(h * 30f, center) { drawLine(ng.textPrimary, center, Offset(center.x, center.y - r * 0.5f), 10f, StrokeCap.Round) }
        rotate(m * 6f, center) { drawLine(ng.textPrimary, center, Offset(center.x, center.y - r * 0.75f), 6f, StrokeCap.Round) }
        rotate(s * 6f, center) { drawLine(ng.accent, Offset(center.x, center.y + r * 0.12f), Offset(center.x, center.y - r * 0.82f), 3f, StrokeCap.Round) }
        drawCircle(ng.accent, 8f, center)
    }
}
