package com.nextgen.caros.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.AppSettings
import com.nextgen.caros.data.DashboardLayout
import com.nextgen.caros.location.Geo
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.nav.NavState
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.vector
import com.nextgen.caros.ui.maps.NgMap
import com.nextgen.caros.ui.maps.mapStyleUrl
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.shell.Screen
import com.nextgen.caros.ui.shell.ShellNav
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(nav: ShellNav) {
    val s by appContainer.settings.settings.collectAsState()
    val gap = 14.dp
    Box(Modifier.fillMaxSize().padding(gap)) {
        when (s.dashboardLayout) {
            DashboardLayout.STANDARD -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    ClockWidget(s, Modifier.fillMaxWidth().weight(1f))
                    SpeedWidget(s, Modifier.fillMaxWidth().weight(1.1f))
                }
                Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    NavPreviewWidget(s, nav, Modifier.fillMaxWidth().weight(1f))
                    QuickActions(nav, Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    MusicWidget(s, onOpen = { nav.go(Screen.MUSIC) }, modifier = Modifier.fillMaxWidth().weight(1f))
                    WeatherWidget(s, Modifier.fillMaxWidth().height(96.dp))
                }
            }
            DashboardLayout.MAP_FOCUS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                NavPreviewWidget(s, nav, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    ClockWidget(s, Modifier.fillMaxWidth().weight(0.8f), big = false)
                    SpeedWidget(s, Modifier.fillMaxWidth().weight(1f))
                    MusicWidget(s, onOpen = { nav.go(Screen.MUSIC) }, modifier = Modifier.fillMaxWidth().weight(1f), compact = true)
                }
            }
            DashboardLayout.MINIMAL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                ClockWidget(s, Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    SpeedWidget(s, Modifier.weight(1f).fillMaxHeight())
                    MusicWidget(s, onOpen = { nav.go(Screen.MUSIC) }, modifier = Modifier.weight(1.3f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun NavPreviewWidget(s: AppSettings, nav: ShellNav, modifier: Modifier) {
    val ng = LocalNg.current
    val loc by appContainer.location.state.collectAsState()
    val navState by appContainer.navigation.state.collectAsState()
    val places by appContainer.navigation.places.collectAsState()
    val camDb by appContainer.cameras.db.collectAsState()
    val imperial = s.speedUnit == SpeedUnit.MPH

    GlassPanel(modifier, padding = 0.dp) {
        val route = when (val n = navState) {
            is NavState.Navigating -> n.route
            is NavState.Preview -> n.route
            else -> null
        }
        NgMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = s.mapStyleUrl(ng.isDark),
            location = loc.location,
            bearing = loc.bearing,
            follow = true,
            tilt3d = s.map3d && navState is NavState.Navigating,
            followZoom = 15.0,
            route = route?.points,
            cameras = if (s.camerasEnabled) appContainer.cameras.visibleCameras() else emptyList(),
            zones = if (s.camerasEnabled) camDb.zones else emptyList(),
            destination = route?.destination?.latLon,
            interactive = false,
            hasLocationPermission = loc.permissionGranted,
        )
        // Tap anywhere → full map
        Box(Modifier.fillMaxSize().pressable(pressedScale = 0.99f) { nav.go(Screen.MAPS) })

        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            when (val n = navState) {
                is NavState.Navigating -> {
                    val step = n.progress.nextStep
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ng.accentBrush).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        step?.let { Icon(it.icon.vector(), null, tint = Color(0xFF020617), modifier = Modifier.size(40.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Geo.formatDistance(n.progress.distanceToNextM, imperial), style = NgType.title.copy(color = Color(0xFF020617)))
                            Text(step?.instruction ?: "", style = NgType.label.copy(color = Color(0xCC020617)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                else -> Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(if (ng.isDark) Color(0xE60E1320) else Color(0xF2FFFFFF))
                        .pressable { nav.go(Screen.MAPS) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = ng.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Where to?", style = NgType.headline.copy(color = ng.textSecondary))
                }
            }
        }
        if (navState is NavState.Navigating) {
            val n = navState as NavState.Navigating
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp)
                    .clip(RoundedCornerShape(16.dp)).background(if (ng.isDark) Color(0xE60E1320) else Color(0xF2FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "Arrive ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.progress.etaMillis))} · " +
                        Geo.formatDistance(n.progress.distanceRemainingM, imperial),
                    style = NgType.label.copy(color = ng.textPrimary),
                )
            }
        } else {
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                places.home?.let { p -> PlaceChip(Icons.Filled.Home, "Home") { appContainer.navigation.preview(p); nav.go(Screen.MAPS) } }
                places.work?.let { p -> PlaceChip(Icons.Filled.Work, "Work") { appContainer.navigation.preview(p); nav.go(Screen.MAPS) } }
            }
        }
    }
}

@Composable
private fun PlaceChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val ng = LocalNg.current
    Row(
        Modifier.pressable(onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (ng.isDark) Color(0xE60E1320) else Color(0xF2FFFFFF))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ng.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = NgType.label.copy(color = ng.textPrimary))
    }
}

@Composable
private fun QuickActions(nav: ShellNav, modifier: Modifier) {
    val spotify = appContainer.spotify
    Row(modifier.height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickTile(Icons.Filled.Apps, "Apps", Modifier.weight(1f)) { nav.go(Screen.APPS) }
        QuickTile(Icons.Filled.LibraryMusic, "Spotify", Modifier.weight(1f)) {
            if (!spotify.openInSpotify()) nav.go(Screen.MUSIC)
        }
        QuickTile(Icons.Filled.CameraAlt, "Cameras", Modifier.weight(1f)) { nav.openSettings(SettingsSection.CAMERAS) }
        QuickTile(Icons.Filled.Settings, "Settings", Modifier.weight(1f)) { nav.go(Screen.SETTINGS) }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ng = LocalNg.current
    GlassPanel(modifier.fillMaxHeight().pressable(onClick = onClick), corner = 22.dp, padding = 8.dp) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(ng.accent, ng.accent2))),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = Color(0xFF020617), modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.height(4.dp))
            Text(label, style = NgType.caption.copy(color = ng.textSecondary), maxLines = 1)
        }
    }
}
