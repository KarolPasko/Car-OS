package com.nextgen.caros.ui.shell

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.appContainer
import com.nextgen.caros.link.LinkStatus
import com.nextgen.caros.ui.components.CalendarIcon
import com.nextgen.caros.ui.components.NowPlayingIcon
import com.nextgen.caros.ui.components.SquircleIcon
import com.nextgen.caros.ui.components.formatTime
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.rememberNow
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

object IconPalette {
    val phone = listOf(Color(0xFF5BF675), Color(0xFF1DBA3B))
    val music = listOf(Color(0xFFFF5F6D), Color(0xFFF2233F))
    val maps = listOf(Color(0xFF4FC3F7), Color(0xFF1E88E5))
    val messages = listOf(Color(0xFF5BF675), Color(0xFF1DBA3B))
    val cameras = listOf(Color(0xFFFFB340), Color(0xFFFF8A00))
    val spotify = listOf(Color(0xFF2A2A2A), Color(0xFF121212))
    val gray = listOf(Color(0xFFAEAEB2), Color(0xFF6C6C70))
    val purple = listOf(Color(0xFFD07CFF), Color(0xFF8E3BE6))
}

/** CarPlay-style status rail: time, signal, battery, recent apps and the dashboard/grid toggle. */
@Composable
fun CarPlaySidebar(current: Screen, homeGrid: Boolean, onSelect: (Screen) -> Unit, onToggleHome: () -> Unit) {
    val ng = LocalNg.current
    val s by appContainer.settings.settings.collectAsState()
    val sys by appContainer.system.state.collectAsState()
    val link by appContainer.phoneLink.state.collectAsState()
    val media by appContainer.media.state.collectAsState()
    val now = rememberNow()
    Column(
        Modifier.fillMaxHeight().width(98.dp)
            .background(if (ng.isDark) Color(0xE62C2C2E) else Color(0xE6F2F2F7))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatTime(now, s.use24h), style = NgType.title.copy(color = ng.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 26.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when { sys.wifi -> Icons.Filled.Wifi; sys.cellular -> Icons.Filled.SignalCellularAlt; else -> Icons.Filled.SignalCellularOff },
                null, tint = if (sys.online) ng.textPrimary else ng.textTertiary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(when { sys.wifi -> "Wi-Fi"; sys.cellular -> "Cell"; else -> "Offline" },
                style = NgType.label.copy(color = if (sys.online) ng.textPrimary else ng.textTertiary, fontWeight = FontWeight.SemiBold))
        }
        // Battery: head unit's own (rare) or the linked phone's.
        val (level, charging, phone) = when {
            sys.batteryPresent && sys.batteryLevel != null -> Triple(sys.batteryLevel, sys.charging, false)
            link.status == LinkStatus.CONNECTED && link.phoneBattery != null -> Triple(link.phoneBattery, link.phoneCharging, true)
            else -> Triple(null, false, false)
        }
        if (level != null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (phone) Icon(Icons.Filled.PhoneIphone, "Phone", tint = ng.textSecondary, modifier = Modifier.size(14.dp))
            Icon(batteryIcon(level, charging), "Battery", tint = if (level <= 20 && !charging) ng.danger else ng.textPrimary,
                modifier = Modifier.size(18.dp))
            Text("$level%", style = NgType.caption.copy(color = ng.textPrimary, fontWeight = FontWeight.SemiBold))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            SideApp(current == Screen.MAPS) {
                SquircleIcon(Icons.Filled.Navigation, IconPalette.maps, 62.dp, Modifier.pressable { onSelect(Screen.MAPS) })
            }
            SideApp(current == Screen.MUSIC) {
                val art = media.nowPlaying?.art
                if (art != null) Image(art.asImageBitmap(), "Now playing", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).pressable { onSelect(Screen.MUSIC) })
                else SquircleIcon(Icons.Filled.MusicNote, IconPalette.music, 62.dp, Modifier.pressable { onSelect(Screen.MUSIC) })
            }
            SideApp(current == Screen.APPS) {
                SquircleIcon(Icons.Filled.Apps, IconPalette.gray, 62.dp, Modifier.pressable { onSelect(Screen.APPS) })
            }
        }
        // Dashboard ⇄ app grid toggle (CarPlay's bottom button)
        Box(
            Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)).pressable {
                if (current == Screen.HOME) onToggleHome() else onSelect(Screen.HOME)
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (current == Screen.HOME && !homeGrid) Icons.Filled.GridView else Icons.Filled.Dashboard,
                if (homeGrid) "Show dashboard" else "Show apps", tint = ng.textPrimary, modifier = Modifier.size(38.dp))
        }
    }
}

private fun batteryIcon(level: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Filled.BatteryChargingFull
    level >= 90 -> Icons.Filled.BatteryFull
    level >= 65 -> Icons.Filled.Battery6Bar
    level >= 40 -> Icons.Filled.Battery4Bar
    level >= 15 -> Icons.Filled.Battery2Bar
    else -> Icons.Filled.Battery0Bar
}

@Composable
private fun SideApp(selected: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(vertical = 8.dp).size(70.dp)
            .border(if (selected) 2.5.dp else 0.dp, if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent, RoundedCornerShape(19.dp)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private data class GridApp(val label: String, val icon: @Composable (Dp) -> Unit, val onClick: () -> Unit)

/** CarPlay-style home page: two rows of large app icons with pill labels. */
@Composable
fun CarPlayHome(nav: ShellNav) {
    val ctx = LocalContext.current
    val now = rememberNow()
    fun launch(i: Intent, what: String) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Notifier.show("No $what app on this device") }
    val apps = listOf(
        GridApp("Phone", { SquircleIcon(Icons.Filled.Phone, IconPalette.phone, it) }) { launch(Intent(Intent.ACTION_DIAL), "phone") },
        GridApp("Music", { SquircleIcon(Icons.Filled.MusicNote, IconPalette.music, it) }) { nav.go(Screen.MUSIC) },
        GridApp("Maps", { SquircleIcon(Icons.Filled.Navigation, IconPalette.maps, it) }) { nav.go(Screen.MAPS) },
        GridApp("Messages", { SquircleIcon(Icons.Filled.Chat, IconPalette.messages, it) }) {
            launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING), "messaging")
        },
        GridApp("Now Playing", { NowPlayingIcon(it) }) { nav.go(Screen.MUSIC) },
        GridApp("Spotify", {
            SquircleIcon(Icons.Filled.GraphicEq, IconPalette.spotify, it, glyphTint = com.nextgen.caros.ui.music.SpotifyGreen)
        }) { if (!appContainer.spotify.openInSpotify()) nav.go(Screen.MUSIC) },
        GridApp("Cameras", { SquircleIcon(Icons.Filled.CameraAlt, IconPalette.cameras, it) }) { nav.openSettings(SettingsSection.CAMERAS) },
        GridApp("Calendar", { CalendarIcon(now, it) }) {
            launch(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), "calendar")
        },
        GridApp("Apps", { SquircleIcon(Icons.Filled.Apps, IconPalette.purple, it) }) { nav.go(Screen.APPS) },
        GridApp("Settings", { SquircleIcon(Icons.Filled.Settings, IconPalette.gray, it) }) { nav.go(Screen.SETTINGS) },
    )
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        val cols = 5
        val cellW = maxWidth / cols
        val icon = minOf(cellW * 0.68f, (maxHeight / 2) * 0.62f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            apps.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxSize().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { app ->
                        Column(
                            Modifier.width(cellW).pressable(onClick = app.onClick),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            app.icon(icon)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                app.label,
                                style = NgType.headline.copy(color = Color.White, fontWeight = FontWeight.Medium, fontSize = (icon.value * 0.19f).coerceIn(13f, 22f).sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.32f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
