package com.nextgen.caros.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.AppSettings
import com.nextgen.caros.data.ClockStyle
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.media.NowPlaying
import com.nextgen.caros.speedlimit.ParsedLimit
import com.nextgen.caros.speedlimit.SpeedLimitState
import com.nextgen.caros.ui.components.AnalogClock
import com.nextgen.caros.ui.components.GlassIconButton
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.MusicVisualizer
import com.nextgen.caros.ui.components.SectionLabel
import com.nextgen.caros.ui.components.SpeedLimitSign
import com.nextgen.caros.ui.components.Speedometer
import com.nextgen.caros.ui.components.amPm
import com.nextgen.caros.ui.components.formatDate
import com.nextgen.caros.ui.components.formatTime
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.rememberNow
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import com.nextgen.caros.weather.Weather
import com.nextgen.caros.weather.WeatherState
import kotlinx.coroutines.delay

/** Speed in the user's unit plus whether it exceeds the current known limit. */
data class DrivingInfo(val speed: Double?, val overspeed: Boolean, val limit: SpeedLimitState)

@Composable
fun rememberDrivingInfo(): DrivingInfo {
    val loc by appContainer.location.state.collectAsState()
    val limit by appContainer.speedLimits.state.collectAsState()
    val s by appContainer.settings.settings.collectAsState()
    val speed = if (loc.isStale) null else loc.speedMps?.let { s.speedUnit.fromMps(it) }
    val over = s.overspeedWarning && speed != null && limit.let { l ->
        l is SpeedLimitState.Known && l.limit is ParsedLimit.Value &&
            speed > s.speedUnit.fromMps((l.limit as ParsedLimit.Value).kmh / 3.6) + s.overspeedTolerance
    }
    return DrivingInfo(speed, over, limit)
}

@Composable
fun ClockWidget(s: AppSettings, modifier: Modifier = Modifier, big: Boolean = true) {
    val ng = LocalNg.current
    val now = rememberNow()
    GlassPanel(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            if (s.clockStyle == ClockStyle.ANALOG) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnalogClock(now, Modifier.weight(1f, fill = false).fillMaxHeight(0.8f))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(formatTime(now, s.use24h), style = NgType.title.copy(color = ng.textPrimary))
                        Text(formatDate(now), style = NgType.label.copy(color = ng.textSecondary))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        formatTime(now, s.use24h, s.clockStyle == ClockStyle.DIGITAL_SECONDS),
                        style = NgType.clockHuge.copy(color = ng.textPrimary, fontSize = if (big) 88.sp else 60.sp),
                        maxLines = 1,
                    )
                    if (!s.use24h) {
                        Spacer(Modifier.width(8.dp))
                        Text(amPm(now), style = NgType.headline.copy(color = ng.textSecondary), modifier = Modifier.padding(bottom = 18.dp))
                    }
                }
                Text(formatDate(now), style = NgType.headline.copy(color = ng.accent))
            }
        }
    }
}

@Composable
fun SpeedWidget(s: AppSettings, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    val d = rememberDrivingInfo()
    val loc by appContainer.location.state.collectAsState()
    GlassPanel(modifier, padding = 14.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Speedometer(
                speed = d.speed, unitLabel = s.speedUnit.label,
                maxSpeed = if (s.speedUnit == SpeedUnit.MPH) 120f else 200f,
                overspeed = d.overspeed, modifier = Modifier.weight(1f).fillMaxHeight(),
                numberSize = 52.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SpeedLimitSign(d.limit, s.speedUnit, size = 76.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !loc.permissionGranted -> "No GPS permission"
                        d.speed == null -> "Waiting for GPS"
                        d.limit is SpeedLimitState.Known -> (d.limit as SpeedLimitState.Known).road ?: "Limit"
                        d.limit is SpeedLimitState.Unavailable -> "Limit unavailable"
                        else -> "Speed limit"
                    },
                    style = NgType.caption.copy(color = ng.textTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

fun weatherIcon(w: Weather): ImageVector = when (w.code) {
    0, 1 -> if (w.isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    2 -> Icons.Filled.FilterDrama
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Dehaze
    in 51..67, in 80..82 -> Icons.Filled.Grain
    in 71..77, 85, 86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

@Composable
fun WeatherWidget(s: AppSettings, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    val st by appContainer.weather.state.collectAsState()
    GlassPanel(modifier.pressable { appContainer.weather.refresh() }, padding = 16.dp) {
        AnimatedContent(st, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "wx",
            contentKey = { it::class }) { w ->
            when (w) {
                is WeatherState.Ready -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(weatherIcon(w.weather), null, tint = ng.accent, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(w.weather.temp(s.speedUnit), style = NgType.title.copy(color = ng.textPrimary, fontSize = 30.sp))
                        Text(w.weather.description, style = NgType.label.copy(color = ng.textSecondary), maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        w.weather.highC?.let { Text("H ${w.weather.temp(s.speedUnit, it)}", style = NgType.caption.copy(color = ng.textSecondary)) }
                        w.weather.lowC?.let { Text("L ${w.weather.temp(s.speedUnit, it)}", style = NgType.caption.copy(color = ng.textTertiary)) }
                    }
                }
                is WeatherState.Error -> Text("Weather unavailable – tap to retry", style = NgType.label.copy(color = ng.textSecondary))
                WeatherState.Loading -> Text("Loading weather…", style = NgType.label.copy(color = ng.textSecondary))
                WeatherState.NoLocation -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOff, null, tint = ng.textTertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("Weather needs your location", style = NgType.label.copy(color = ng.textSecondary))
                }
            }
        }
    }
}

@Composable
fun AlbumArt(np: NowPlaying?, modifier: Modifier = Modifier, corner: Int = 18) {
    val ng = LocalNg.current
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(corner.dp))
            .background(Brush.linearGradient(listOf(ng.accent.copy(alpha = 0.5f), ng.accent2.copy(alpha = 0.5f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            np?.art != null -> Image(np.art.asImageBitmap(), "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            np?.artUri != null -> AsyncImage(np.artUri, "Album artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.4f))
        }
    }
}

@Composable
fun PlaybackProgress(np: NowPlaying, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    var pos by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(np) {
        while (true) {
            pos = np.positionNow().toFloat(); delay(500)
        }
    }
    val frac = if (np.durationMs > 0) (pos / np.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(ng.textPrimary.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ng.accentBrush))
        }
        if (np.durationMs > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(fmtMs(pos.toLong()), style = NgType.caption.copy(color = ng.textTertiary))
                Spacer(Modifier.weight(1f))
                Text("-" + fmtMs(np.durationMs - pos.toLong()), style = NgType.caption.copy(color = ng.textTertiary))
            }
        }
    }
}

fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MusicWidget(s: AppSettings, onOpen: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ng = LocalNg.current
    val media by appContainer.media.state.collectAsState()
    val np = media.nowPlaying
    val m = appContainer.media
    GlassPanel(modifier, padding = 16.dp) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Artwork never takes more than ~40% of the width, so the title always has room.
            val art = minOf(maxHeight, maxWidth * 0.4f)
            Row(Modifier.fillMaxSize().pressable(pressedScale = 0.98f, onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(np, Modifier.size(art))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel(np?.appName ?: if (media.hasAccess) "Nothing playing" else "Music")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        np?.title ?: if (media.hasAccess) "Start music in Spotify or any player" else "Tap to set up music",
                        style = NgType.headline.copy(color = ng.textPrimary), maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (np != null) Text(np.artist, style = NgType.label.copy(color = ng.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.showVisualiser && np != null && !compact) {
                        Spacer(Modifier.height(8.dp))
                        MusicVisualizer(np.isPlaying, Modifier.fillMaxWidth().height(22.dp), bars = 18)
                    }
                }
            }
            }
            if (np != null && !compact) {
                Spacer(Modifier.height(10.dp))
                PlaybackProgress(np)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Filled.SkipPrevious, "Previous", size = 50.dp) { m.previous() }
                GlassIconButton(if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", size = 60.dp, accent = true) { m.playPause() }
                GlassIconButton(Icons.Filled.SkipNext, "Next", size = 50.dp) { m.next() }
            }
        }
    }
}
