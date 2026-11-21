package com.nextgen.caros.ui.home

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.AppSettings
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.link.LinkStatus
import com.nextgen.caros.location.Geo
import com.nextgen.caros.nav.NavState
import com.nextgen.caros.speedlimit.ParsedLimit
import com.nextgen.caros.speedlimit.SpeedLimitState
import com.nextgen.caros.ui.components.CapsuleSlider
import com.nextgen.caros.ui.components.SpeedLimitSign
import com.nextgen.caros.ui.components.formatTime
import com.nextgen.caros.ui.components.amPm
import com.nextgen.caros.ui.components.liquidGlass
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.rememberNow
import com.nextgen.caros.ui.components.vector
import com.nextgen.caros.ui.maps.NgMap
import com.nextgen.caros.ui.maps.mapStyleUrl
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.shell.Screen
import com.nextgen.caros.ui.shell.ShellNav
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import com.nextgen.caros.weather.WeatherState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Instrument-cluster home screen: live speed and gauge, route summary with map, trip computer,
 * control tiles, weather and now-playing – all driven by real device data.
 */
@Composable
fun ClusterDashboard(s: AppSettings, nav: ShellNav) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Cluster(s, nav, Modifier.fillMaxWidth().weight(1.35f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ControlGrid(s, nav, onBrightness = { sheet = true }, modifier = Modifier.weight(0.95f).fillMaxHeight())
                WeatherCard(s, Modifier.weight(1.2f).fillMaxHeight())
                NowPlayingCard(nav, Modifier.weight(1.2f).fillMaxHeight())
            }
        }
        AnimatedVisibility(sheet, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
            ControlSheet(onClose = { sheet = false })
        }
    }
}

// ============================================================================ cluster

private fun cardBrush(ng: com.nextgen.caros.ui.theme.NgColors) =
    if (ng.isDark) Brush.verticalGradient(listOf(Color(0xFF1A1C22), Color(0xFF0C0D10)))
    else Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F5F8)))

@Composable
private fun Cluster(s: AppSettings, nav: ShellNav, modifier: Modifier) {
    val ng = LocalNg.current
    val drive = rememberDrivingInfo()
    val navState by appContainer.navigation.state.collectAsState()
    val imperial = s.speedUnit == SpeedUnit.MPH
    val now = rememberNow()
    val wx by appContainer.weather.state.collectAsState()

    Box(modifier) {
        // Bezel lines framing the gauge (like a physical cluster hood).
        Canvas(Modifier.fillMaxSize()) { drawBezel(ng.textPrimary.copy(alpha = 0.22f), ng.accent.copy(alpha = 0.35f)) }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                RouteMini(s, nav, navState, imperial, Modifier.weight(1f).fillMaxHeight())
                // Centre: gauge with the speed number sitting in its open top
                Box(Modifier.weight(1.3f).fillMaxHeight()) {
                    ClusterGauge(
                        fraction = ((drive.speed ?: 0.0) / if (imperial) 120.0 else 200.0).toFloat(),
                        overspeed = drive.overspeed,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(Modifier.align(Alignment.TopCenter), verticalAlignment = Alignment.Bottom) {
                        Text("SPEED", style = NgType.caption.copy(color = ng.textSecondary), modifier = Modifier.padding(bottom = 14.dp))
                        Spacer(Modifier.width(14.dp))
                        val shown by animateFloatAsState((drive.speed ?: 0.0).toFloat(), tween(400), label = "spd")
                        val color by animateColorAsState(if (drive.overspeed) ng.danger else ng.textPrimary, label = "spdc")
                        Text(
                            if (drive.speed == null) "--" else shown.toInt().toString(),
                            style = NgType.speedHuge.copy(color = color, fontSize = 66.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.widthIn(min = 120.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(s.speedUnit.label.uppercase(), style = NgType.caption.copy(color = ng.textSecondary), modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)) { SpeedLimitSign(drive.limit, s.speedUnit, size = 48.dp) }
                }
                DriveInfo(s, navState, imperial, drive.limit, nav, Modifier.weight(1f).fillMaxHeight())
            }
            // Bottom strip: date/time, status icons, temperature
            Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(now)), style = NgType.label.copy(color = ng.textTertiary))
                Spacer(Modifier.width(6.dp))
                Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(now)), style = NgType.headline.copy(color = ng.textPrimary))
                Spacer(Modifier.width(18.dp))
                Text(formatTime(now, s.use24h), style = NgType.headline.copy(color = ng.textPrimary))
                if (!s.use24h) Text(amPm(now).lowercase(), style = NgType.caption.copy(color = ng.textTertiary))
                Spacer(Modifier.weight(1f))
                StatusLights()
                Spacer(Modifier.weight(1f))
                Text((wx as? WeatherState.Ready)?.weather?.let { it.temp(s.speedUnit) + if (imperial) " F" else " C" } ?: "--°",
                    style = NgType.headline.copy(color = ng.textPrimary))
            }
        }
    }
}

private fun DrawScope.drawBezel(line: Color, glow: Color) {
    val w = size.width; val h = size.height
    val top = Path().apply {
        moveTo(w * 0.20f, 0f)
        cubicTo(w * 0.30f, h * 0.02f, w * 0.33f, h * 0.20f, w * 0.38f, h * 0.20f)
        lineTo(w * 0.62f, h * 0.20f)
        cubicTo(w * 0.67f, h * 0.20f, w * 0.70f, h * 0.02f, w * 0.80f, 0f)
    }
    val bottom = Path().apply {
        moveTo(w * 0.02f, h * 0.90f)
        cubicTo(w * 0.20f, h * 0.88f, w * 0.30f, h * 0.80f, w * 0.38f, h * 0.80f)
        lineTo(w * 0.62f, h * 0.80f)
        cubicTo(w * 0.70f, h * 0.80f, w * 0.80f, h * 0.88f, w * 0.98f, h * 0.90f)
    }
    drawPath(top, line, style = Stroke(2f))
    drawPath(bottom, line, style = Stroke(2f))
    drawLine(glow, Offset(w * 0.42f, h * 0.80f + 3f), Offset(w * 0.58f, h * 0.80f + 3f), strokeWidth = 2f)
}

/** Ring gauge with a car on a perspective road in the centre. */
@Composable
private fun ClusterGauge(fraction: Float, overspeed: Boolean, modifier: Modifier) {
    val ng = LocalNg.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "gauge")
    val hot by animateColorAsState(if (overspeed) ng.danger else ng.accent, label = "hot")
    val teal = Color(0xFF2DD4BF)
    val tickColor = ng.textPrimary
    Canvas(modifier) {
        // Ring centred low in the box with its top opened up for the speed readout.
        val r = minOf(size.height * 0.50f, size.width * 0.40f)
        val c = Offset(size.width / 2, size.height - r * 1.02f)
        // Outer tick ring
        val ticks = 64
        for (i in 0 until ticks) {
            val t = i / (ticks - 1f)
            val deg = 135 + 270 * t
            if (deg in 242f..298f) continue // leave the top open under the speed number
            val ang = Math.toRadians(deg.toDouble())
            val active = t <= f
            val major = i % 8 == 0
            val r1 = r * (if (major) 0.80f else 0.84f)
            val r2 = r * 0.93f
            drawLine(
                if (active) androidx.compose.ui.graphics.lerp(Color(0xFF2563EB), hot, t) else tickColor.copy(alpha = if (major) 0.35f else 0.14f),
                Offset(c.x + (r1 * cos(ang)).toFloat(), c.y + (r1 * sin(ang)).toFloat()),
                Offset(c.x + (r2 * cos(ang)).toFloat(), c.y + (r2 * sin(ang)).toFloat()),
                strokeWidth = if (active) 5f else if (major) 3f else 2f, cap = StrokeCap.Round,
            )
        }
        // Active sweep band
        val band = r * 0.97f
        drawArc(
            Brush.sweepGradient(listOf(Color(0xFF1D4ED8), hot, Color(0xFF1D4ED8))),
            135f, 270f * f, false, Offset(c.x - band, c.y - band), Size(band * 2, band * 2), style = Stroke(r * 0.03f, cap = StrokeCap.Round),
        )
        // Inner teal ring with glow
        val ir = r * 0.70f
        val ringTl = Offset(c.x - ir, c.y - ir); val ringSize = Size(ir * 2, ir * 2)
        drawArc(teal.copy(alpha = 0.10f), -50f, 280f, false, Offset(c.x - ir - 10f, c.y - ir - 10f), Size(ir * 2 + 20f, ir * 2 + 20f), style = Stroke(18f))
        drawArc(teal.copy(alpha = 0.9f), -50f, 280f, false, ringTl, ringSize, style = Stroke(3f, cap = StrokeCap.Round))
        // Road
        val road = Path().apply {
            moveTo(c.x - ir * 0.10f, c.y - ir * 0.55f)
            lineTo(c.x + ir * 0.10f, c.y - ir * 0.55f)
            lineTo(c.x + ir * 0.80f, c.y + ir * 0.62f)
            lineTo(c.x - ir * 0.80f, c.y + ir * 0.62f)
            close()
        }
        drawPath(road, Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x26FFFFFF)), startY = c.y - ir * 0.55f, endY = c.y + ir * 0.62f))
        // Lane dashes moving with speed impression
        for (k in 0..3) {
            val y0 = c.y - ir * 0.4f + k * ir * 0.28f
            drawLine(tickColor.copy(alpha = 0.08f + 0.05f * k), Offset(c.x, y0), Offset(c.x, y0 + ir * 0.12f), strokeWidth = 2f + k)
        }
        drawCar(Offset(c.x, c.y + ir * 0.18f), ir * 0.62f)
    }
}

/** Stylised car seen from behind (white body, dark glass, red tail-lights). */
private fun DrawScope.drawCar(c: Offset, w: Float) {
    val h = w * 0.82f
    val left = c.x - w / 2
    val top = c.y - h / 2
    // shadow
    drawOval(Color.Black.copy(alpha = 0.55f), Offset(left - w * 0.05f, top + h * 0.85f), Size(w * 1.1f, h * 0.25f))
    // wheels
    drawRoundRect(Color(0xFF0B0B0D), Offset(left + w * 0.02f, top + h * 0.66f), Size(w * 0.18f, h * 0.30f), CornerRadius(8f))
    drawRoundRect(Color(0xFF0B0B0D), Offset(left + w * 0.80f, top + h * 0.66f), Size(w * 0.18f, h * 0.30f), CornerRadius(8f))
    // body
    val body = Path().apply {
        moveTo(left + w * 0.06f, top + h * 0.48f)
        cubicTo(left + w * 0.10f, top + h * 0.38f, left + w * 0.90f, top + h * 0.38f, left + w * 0.94f, top + h * 0.48f)
        lineTo(left + w, top + h * 0.80f)
        quadraticBezierTo(c.x, top + h * 0.95f, left, top + h * 0.80f)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFD4D7DD), Color(0xFF9CA3AF)), startY = top + h * 0.38f, endY = top + h * 0.92f))
    // cabin / roof
    val cabin = Path().apply {
        moveTo(left + w * 0.20f, top + h * 0.42f)
        cubicTo(left + w * 0.24f, top + h * 0.06f, left + w * 0.76f, top + h * 0.06f, left + w * 0.80f, top + h * 0.42f)
        close()
    }
    drawPath(cabin, Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFFCBD0D8)), startY = top, endY = top + h * 0.42f))
    // rear window
    val glass = Path().apply {
        moveTo(left + w * 0.27f, top + h * 0.40f)
        cubicTo(left + w * 0.30f, top + h * 0.16f, left + w * 0.70f, top + h * 0.16f, left + w * 0.73f, top + h * 0.40f)
        close()
    }
    drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF1F2937), Color(0xFF030712)), startY = top + h * 0.16f, endY = top + h * 0.40f))
    // tail-lights
    drawRoundRect(Color(0xFFEF4444), Offset(left + w * 0.06f, top + h * 0.52f), Size(w * 0.20f, h * 0.07f), CornerRadius(6f))
    drawRoundRect(Color(0xFFEF4444), Offset(left + w * 0.74f, top + h * 0.52f), Size(w * 0.20f, h * 0.07f), CornerRadius(6f))
    drawRoundRect(Color(0x55EF4444), Offset(left + w * 0.02f, top + h * 0.49f), Size(w * 0.28f, h * 0.13f), CornerRadius(10f))
    drawRoundRect(Color(0x55EF4444), Offset(left + w * 0.70f, top + h * 0.49f), Size(w * 0.28f, h * 0.13f), CornerRadius(10f))
    // plate
    drawRoundRect(Color(0xFF111827), Offset(c.x - w * 0.12f, top + h * 0.64f), Size(w * 0.24f, h * 0.08f), CornerRadius(4f))
}

@Composable
private fun RouteMini(s: AppSettings, nav: ShellNav, navState: NavState, imperial: Boolean, modifier: Modifier) {
    val ng = LocalNg.current
    val loc by appContainer.location.state.collectAsState()
    val trip by appContainer.trip.stats.collectAsState()
    val route = (navState as? NavState.Navigating)?.route ?: (navState as? NavState.Preview)?.route
    Box(modifier.pressable(pressedScale = 0.98f) { nav.go(Screen.MAPS) }) {
        // Live map, faded into the cluster at the edges.
        Box(Modifier.fillMaxSize().padding(start = 18.dp, top = 52.dp, end = 4.dp, bottom = 8.dp).clip(RoundedCornerShape(28.dp))) {
            NgMap(
                modifier = Modifier.fillMaxSize(),
                styleUrl = s.mapStyleUrl(ng.isDark), location = loc.location, bearing = loc.bearing,
                follow = true, tilt3d = true, followZoom = 15.5, route = route?.points,
                destination = route?.destination?.latLon, interactive = false, hasLocationPermission = loc.permissionGranted,
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent, ng.background.copy(alpha = 0.9f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ng.background.copy(alpha = 0.8f), Color.Transparent, Color.Transparent, ng.background.copy(alpha = 0.9f)))))
        }
        Column(Modifier.padding(start = 26.dp, top = 6.dp)) {
            when (navState) {
                is NavState.Navigating -> {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = ng.textSecondary)) { append("Remain : ") }
                        withStyle(SpanStyle(color = ng.textPrimary, fontWeight = FontWeight.Bold)) {
                            append(Geo.formatDistance(navState.progress.distanceRemainingM, imperial))
                        }
                    }, style = NgType.body)
                    val step = navState.progress.nextStep
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(step?.icon?.vector() ?: Icons.Filled.Navigation, null, tint = ng.textPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${Geo.formatDistance(navState.progress.distanceToNextM, imperial)} : ${step?.shortInstruction ?: ""}",
                            style = NgType.label.copy(color = ng.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                else -> {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = ng.textSecondary)) { append("Trip : ") }
                        withStyle(SpanStyle(color = ng.textPrimary, fontWeight = FontWeight.Bold)) { append(Geo.formatDistance(trip.distanceM, imperial)) }
                    }, style = NgType.body)
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(ng.glassStrong)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = ng.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Where to?", style = NgType.label.copy(color = ng.textPrimary))
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveInfo(s: AppSettings, navState: NavState, imperial: Boolean, limit: SpeedLimitState, nav: ShellNav, modifier: Modifier) {
    val ng = LocalNg.current
    val trip by appContainer.trip.stats.collectAsState()
    val loc by appContainer.location.state.collectAsState()
    val link by appContainer.phoneLink.state.collectAsState()
    val n = navState as? NavState.Navigating
    Column(modifier.padding(top = 34.dp, end = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Drive info", style = NgType.caption.copy(color = ng.textSecondary, letterSpacing = 0.sp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (n != null) {
                InfoTile("${(n.progress.durationRemainingS / 60).toInt()}", "min", Icons.Filled.Schedule)
                InfoTile(Geo.formatDistance(n.progress.distanceRemainingM, imperial).substringBefore(' '),
                    Geo.formatDistance(n.progress.distanceRemainingM, imperial).substringAfter(' '), Icons.Filled.Route)
            } else {
                InfoTile("${(trip.movingSeconds / 60).toInt()}", "min", Icons.Filled.Schedule)
                InfoTile(Geo.formatDistance(trip.distanceM, imperial).substringBefore(' '),
                    Geo.formatDistance(trip.distanceM, imperial).substringAfter(' '), Icons.Filled.Route)
            }
            InfoTile(trip.averageMps?.let { s.speedUnit.fromMps(it).toInt().toString() } ?: "--", "avg ${s.speedUnit.label}", Icons.Filled.Speed)
            InfoTile(
                trip.altitudeM?.let { (if (imperial) it * 3.28084 else it).toInt().toString() } ?: "--",
                if (imperial) "ft alt" else "m alt", Icons.Filled.Height,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.widthIn(max = 260.dp)) {
            StatusLine("SPEED LIMIT", when (limit) {
                is SpeedLimitState.Known -> (limit.limit as? ParsedLimit.Value)?.let { "${it.signValue} ${if (it.mphSign) "MPH" else "KM/H"}" } ?: "SIGNED"
                is SpeedLimitState.Unavailable -> "NO DATA"
                SpeedLimitState.Searching -> "LOCATING"
                SpeedLimitState.Disabled -> "OFF"
            }, limit is SpeedLimitState.Known)
            StatusLine("CAMERA ALERTS", if (s.camerasEnabled) "ON" else "OFF", s.camerasEnabled)
            StatusLine("GPS", when { loc.isStale -> "SEARCHING"; loc.source == "phone" -> "PHONE"; else -> "LOCKED" }, !loc.isStale)
            StatusLine("PHONE LINK", if (link.status == LinkStatus.CONNECTED) (link.peerName ?: "ON").uppercase() else link.status.label.uppercase(),
                link.status == LinkStatus.CONNECTED, onClick = { nav.openSettings(SettingsSection.PHONE_LINK) })
        }
    }
}

@Composable
private fun InfoTile(value: String, unit: String, icon: ImageVector) {
    val ng = LocalNg.current
    Column(
        Modifier.width(58.dp).clip(RoundedCornerShape(12.dp))
            .background(cardBrush(ng))
            .border(1.dp, ng.glassBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = NgType.headline.copy(color = ng.textPrimary, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(unit, style = NgType.caption.copy(color = ng.textSecondary, fontSize = 9.sp, letterSpacing = 0.sp), maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Icon(icon, null, tint = ng.textTertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun StatusLine(label: String, value: String, on: Boolean, onClick: (() -> Unit)? = null) {
    val ng = LocalNg.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = NgType.caption.copy(color = ng.textTertiary, fontSize = 10.sp), modifier = Modifier.weight(1f))
        Text(value, style = NgType.caption.copy(color = if (on) Color(0xFF2DD4BF) else ng.textTertiary, fontSize = 10.sp), maxLines = 1)
    }
}

/** Row of status lights (only real states: GPS, phone link, Bluetooth, network, cameras, overspeed). */
@Composable
private fun StatusLights() {
    val ng = LocalNg.current
    val loc by appContainer.location.state.collectAsState()
    val bt by appContainer.bluetooth.state.collectAsState()
    val sys by appContainer.system.state.collectAsState()
    val link by appContainer.phoneLink.state.collectAsState()
    val alert by appContainer.cameras.alert.collectAsState()
    val drive = rememberDrivingInfo()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Light(Icons.Filled.Navigation, if (loc.isStale) ng.warning else Color(0xFF2DD4BF))
        Light(Icons.Filled.PhoneIphone, if (link.status == LinkStatus.CONNECTED) Color(0xFF2DD4BF) else ng.textTertiary)
        Light(if (bt.enabled) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled, if (bt.connectedDevices.isNotEmpty()) Color(0xFF60A5FA) else ng.textTertiary)
        Light(if (sys.online) Icons.Filled.Wifi else Icons.Filled.WifiOff, if (sys.online) Color(0xFF60A5FA) else ng.danger)
        Light(Icons.Filled.CameraAlt, if (alert != null) ng.warning else ng.textTertiary)
        Light(Icons.Filled.Speed, if (drive.overspeed) ng.danger else ng.textTertiary)
    }
}

@Composable
private fun Light(icon: ImageVector, color: Color) {
    val c by animateColorAsState(color, label = "light")
    Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
}

// ============================================================================ control tiles

private data class ControlItem(val label: String, val icon: ImageVector, val active: Boolean, val onClick: () -> Unit)

@Composable
private fun ControlGrid(s: AppSettings, nav: ShellNav, onBrightness: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val bt by appContainer.bluetooth.state.collectAsState()
    val sys by appContainer.system.state.collectAsState()
    val link by appContainer.phoneLink.state.collectAsState()
    val media by appContainer.media.state.collectAsState()
    val navState by appContainer.navigation.state.collectAsState()
    val items = listOf(
        ControlItem("Bluetooth", Icons.Filled.Bluetooth, bt.enabled) { runCatching { ctx.startActivity(appContainer.bluetooth.settingsIntent()) } },
        ControlItem("Phone", Icons.Filled.PhoneIphone, link.status == LinkStatus.CONNECTED) { nav.openSettings(SettingsSection.PHONE_LINK) },
        ControlItem("Music", Icons.Filled.Headphones, media.nowPlaying?.isPlaying == true) { nav.go(Screen.MUSIC) },
        ControlItem("Brightness", if (sys.brightness == null) Icons.Filled.BrightnessAuto else Icons.Filled.BrightnessHigh, sys.brightness != null, onBrightness),
        ControlItem("Voice", Icons.Filled.RecordVoiceOver, s.voiceGuidance) {
            appContainer.settings.setVoiceGuidance(!s.voiceGuidance)
            Notifier.show(if (s.voiceGuidance) "Voice guidance off" else "Voice guidance on")
        },
        ControlItem("Wi-Fi", if (sys.wifi) Icons.Filled.Wifi else Icons.Filled.WifiOff, sys.wifi) {
            runCatching { ctx.startActivity(appContainer.system.wifiSettingsIntent()) }
        },
        ControlItem("Cameras", Icons.Filled.CameraAlt, s.camerasEnabled) { appContainer.settings.setCamerasEnabled(!s.camerasEnabled) },
        ControlItem("Apps", Icons.Filled.Apps, false) { nav.go(Screen.APPS) },
        ControlItem("Maps", Icons.Filled.Map, navState is NavState.Navigating) { nav.go(Screen.MAPS) },
    )
    BoxWithConstraints(modifier) {
        val gap = 10.dp
        val tile = minOf((maxWidth - gap * 2) / 3, (maxHeight - gap * 2) / 3)
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { ControlTile(it, tile) } }
            }
        }
    }
}

@Composable
private fun ControlTile(item: ControlItem, size: androidx.compose.ui.unit.Dp) {
    val ng = LocalNg.current
    val activeBg = Color(0xFFB9E3F8)
    val bg by animateColorAsState(if (item.active) activeBg else if (ng.isDark) Color(0xFF16181D) else Color.White, tween(250), label = "tileBg")
    val fg by animateColorAsState(if (item.active) Color(0xFF0B1220) else ng.textPrimary.copy(alpha = 0.8f), tween(250), label = "tileFg")
    Column(
        Modifier.size(size).pressable(onClick = item.onClick).clip(RoundedCornerShape(size * 0.24f))
            .background(bg)
            .border(1.dp, if (item.active) Color.White.copy(alpha = 0.6f) else ng.glassBorder, RoundedCornerShape(size * 0.24f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, item.label, tint = fg, modifier = Modifier.size(size * 0.34f))
        Spacer(Modifier.height(size * 0.05f))
        Text(item.label, style = NgType.caption.copy(color = fg, fontSize = (size.value * 0.15f).coerceIn(9f, 13f).sp, letterSpacing = 0.sp), maxLines = 1)
    }
    @Suppress("UNUSED_EXPRESSION") ng
}

/** iOS Control-Centre style sheet: in-app brightness and media volume. */
@Composable
private fun ControlSheet(onClose: () -> Unit) {
    val ng = LocalNg.current
    val sys by appContainer.system.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).pressable(pressedScale = 1f, onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(460.dp).liquidGlass(RoundedCornerShape(32.dp), ng.isDark).pressable(pressedScale = 1f) {}.padding(22.dp),
        ) {
            Text("Display & Sound", style = NgType.title.copy(color = ng.textPrimary))
            Spacer(Modifier.height(16.dp))
            CapsuleSlider(sys.brightness ?: 0.7f, { appContainer.system.setBrightness(it) }, Icons.Filled.BrightnessHigh, Modifier.fillMaxWidth(),
                label = if (sys.brightness == null) "Automatic" else "${((sys.brightness ?: 0f) * 100).toInt()}%")
            Spacer(Modifier.height(12.dp))
            CapsuleSlider(sys.volume, { appContainer.system.setVolume(it) }, Icons.Filled.VolumeUp, Modifier.fillMaxWidth(), label = "Media ${(sys.volume * 100).toInt()}%")
            Spacer(Modifier.height(16.dp))
            Row {
                com.nextgen.caros.ui.components.PillButton("Auto brightness", icon = Icons.Filled.BrightnessAuto, primary = sys.brightness == null) {
                    appContainer.system.setBrightness(null)
                }
                Spacer(Modifier.weight(1f))
                com.nextgen.caros.ui.components.PillButton("Done", primary = true, onClick = onClose)
            }
            Text("Brightness applies while NextGen Car OS is on screen.", style = NgType.caption.copy(color = ng.textTertiary), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

// ============================================================================ weather

@Composable
private fun WeatherCard(s: AppSettings, modifier: Modifier) {
    val ng = LocalNg.current
    val st by appContainer.weather.state.collectAsState()
    val now = rememberNow()
    val w = (st as? WeatherState.Ready)?.weather
    Column(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(cardBrush(ng))
            .border(1.dp, Brush.verticalGradient(listOf(ng.glassBorder.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(28.dp))
            .pressable(pressedScale = 0.98f) { appContainer.weather.refresh() },
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Sky body: moon at night, sun by day, cloud when overcast.
            Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).fillMaxHeight(0.92f).aspectRatio(1f)) {
                when {
                    w == null -> {}
                    !w.isDay -> drawMoon()
                    w.code >= 3 -> drawCloud()
                    else -> drawSun()
                }
            }
            Column(Modifier.padding(start = 20.dp, top = 14.dp)) {
                Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now)), style = NgType.label.copy(color = ng.textSecondary))
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(w?.temp(s.speedUnit)?.trimEnd('°') ?: "--", style = NgType.speedHuge.copy(color = ng.textPrimary, fontSize = 50.sp))
                    Text("°", style = NgType.title.copy(color = ng.textPrimary, fontSize = 30.sp))
                }
                Text(
                    when (st) {
                        is WeatherState.Ready -> w!!.description
                        WeatherState.Loading -> "Loading…"
                        WeatherState.NoLocation -> "Needs location"
                        is WeatherState.Error -> "Unavailable"
                    },
                    style = NgType.title.copy(color = ng.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ng.glassBorder))
        Text(
            buildAnnotatedString {
                if (w == null) withStyle(SpanStyle(color = ng.textSecondary)) { append("Weather from Open-Meteo once a location is available") }
                else {
                    withStyle(SpanStyle(color = ng.textSecondary)) { append("High ") }
                    withStyle(SpanStyle(color = ng.textPrimary, fontWeight = FontWeight.Bold)) { append(w.highC?.let { w.temp(s.speedUnit, it) } ?: "--") }
                    withStyle(SpanStyle(color = ng.textSecondary)) { append("  ·  Low ") }
                    withStyle(SpanStyle(color = ng.textPrimary, fontWeight = FontWeight.Bold)) { append(w.lowC?.let { w.temp(s.speedUnit, it) } ?: "--") }
                    w.windKmh?.let {
                        withStyle(SpanStyle(color = ng.textSecondary)) { append("  ·  Wind ") }
                        withStyle(SpanStyle(color = ng.textPrimary, fontWeight = FontWeight.Bold)) {
                            append(if (s.speedUnit == SpeedUnit.MPH) "${(it / 1.609).toInt()} mph" else "${it.toInt()} km/h")
                        }
                    }
                }
            },
            style = NgType.label.copy(textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private fun DrawScope.drawMoon() {
    val r = size.minDimension / 2 * 0.92f
    drawCircle(Color.White.copy(alpha = 0.06f), r * 1.08f, center)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFE5E7EB), Color(0xFFB8BCC4), Color(0xFF6B7280)), center = center - Offset(r * 0.3f, r * 0.3f), radius = r * 1.6f), r, center)
    val craters = listOf(Triple(-0.35f, -0.2f, 0.18f), Triple(0.25f, -0.35f, 0.12f), Triple(0.1f, 0.25f, 0.22f), Triple(-0.2f, 0.45f, 0.1f),
        Triple(0.45f, 0.1f, 0.09f), Triple(-0.5f, 0.15f, 0.08f), Triple(0.3f, 0.55f, 0.07f))
    craters.forEach { (x, y, cr) -> drawCircle(Color(0xFF6B7280).copy(alpha = 0.35f), r * cr, center + Offset(r * x, r * y)) }
    // terminator shading
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center = center - Offset(r * 0.4f, r * 0.4f), radius = r * 1.9f), r, center)
}

private fun DrawScope.drawSun() {
    val r = size.minDimension / 2 * 0.55f
    drawCircle(Brush.radialGradient(listOf(Color(0x66FDBA74), Color.Transparent), center = center, radius = r * 1.9f), r * 1.9f, center)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF7AE), Color(0xFFFDE047), Color(0xFFF59E0B)), center = center, radius = r), r, center)
}

private fun DrawScope.drawCloud() {
    val r = size.minDimension / 2
    val c = Color(0xFFD1D5DB)
    val brush = Brush.verticalGradient(listOf(Color(0xFFF3F4F6), Color(0xFF9CA3AF)))
    drawCircle(brush, r * 0.34f, center + Offset(-r * 0.25f, r * 0.05f))
    drawCircle(brush, r * 0.44f, center + Offset(r * 0.12f, -r * 0.08f))
    drawCircle(brush, r * 0.30f, center + Offset(r * 0.48f, r * 0.12f))
    drawRoundRect(brush, Offset(center.x - r * 0.58f, center.y + r * 0.05f), Size(r * 1.36f, r * 0.36f), CornerRadius(r * 0.18f))
    @Suppress("UNUSED_EXPRESSION") c
}

// ============================================================================ music

@Composable
private fun NowPlayingCard(nav: ShellNav, modifier: Modifier) {
    val ng = LocalNg.current
    val media by appContainer.media.state.collectAsState()
    val np = media.nowPlaying
    val m = appContainer.media
    var pos by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(np) { while (true) { pos = np?.positionNow()?.toFloat() ?: 0f; delay(500) } }
    val frac = if (np != null && np.durationMs > 0) (pos / np.durationMs).coerceIn(0f, 1f) else 0f

    Column(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(cardBrush(ng))
            .border(1.dp, Brush.verticalGradient(listOf(ng.glassBorder.copy(alpha = 0.6f), Color.Transparent)), RoundedCornerShape(28.dp))
            .padding(12.dp),
    ) {
        // Wide artwork banner
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(ng.accent.copy(alpha = 0.35f), Color(0xFF7C3AED).copy(alpha = 0.35f))))
                .pressable(pressedScale = 0.98f) { nav.go(Screen.MUSIC) },
            contentAlignment = Alignment.Center,
        ) {
            when {
                np?.art != null -> Image(np.art.asImageBitmap(), "Artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                np?.artUri != null -> AsyncImage(np.artUri, "Artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else -> Icon(Icons.Filled.MusicNote, null, tint = ng.textSecondary, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(np?.title ?: if (media.hasAccess) "Nothing playing" else "Tap Music to set up", style = NgType.title.copy(color = ng.textPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
        Text(np?.artist?.ifBlank { np.appName } ?: "Spotify · any player", style = NgType.label.copy(color = ng.textSecondary),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.padding(horizontal = 6.dp)) {
            Text(fmtMs(pos.toLong()), style = NgType.caption.copy(color = ng.textSecondary, fontSize = 10.sp))
            Spacer(Modifier.weight(1f))
            Text(if (np != null && np.durationMs > 0) fmtMs(np.durationMs) else "--:--", style = NgType.caption.copy(color = ng.textSecondary, fontSize = 10.sp))
        }
        Box(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(ng.textPrimary.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Color(0xFF2DD4BF)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val seekable = np?.canSeek == true
            TransportIcon(Icons.Filled.FastRewind, "Back 15 seconds", enabled = seekable) { m.seekBy(-15_000) }
            TransportIcon(Icons.Filled.SkipPrevious, "Previous") { m.previous() }
            val ringTrack = ng.textPrimary.copy(alpha = 0.12f)
            Box(Modifier.size(46.dp).pressable { m.playPause() }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(ringTrack, style = Stroke(3f))
                    drawArc(Color(0xFF2DD4BF), -90f, 360f * frac.coerceAtLeast(0.02f), false, style = Stroke(4f, cap = StrokeCap.Round),
                        topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f))
                }
                Icon(if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = ng.textPrimary, modifier = Modifier.size(26.dp))
            }
            TransportIcon(Icons.Filled.SkipNext, "Next") { m.next() }
            TransportIcon(Icons.Filled.FastForward, "Forward 15 seconds", enabled = seekable) { m.seekBy(15_000) }
        }
    }
}

@Composable
private fun TransportIcon(icon: ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    Icon(icon, desc, tint = LocalNg.current.textPrimary.copy(alpha = if (enabled) 1f else 0.3f),
        modifier = Modifier.size(40.dp).pressable(enabled = enabled, onClick = onClick).padding(7.dp))
}

@Suppress("unused") private val unusedIntent = Intent::class
@Suppress("unused") private val unusedShape = CircleShape
