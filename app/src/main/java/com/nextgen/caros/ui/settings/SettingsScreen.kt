package com.nextgen.caros.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nextgen.caros.BuildConfig
import com.nextgen.caros.appContainer
import com.nextgen.caros.cameras.UpdateStatus
import com.nextgen.caros.cameras.Verification
import com.nextgen.caros.data.AccentColor
import com.nextgen.caros.data.ClockStyle
import com.nextgen.caros.data.DashboardLayout
import com.nextgen.caros.data.MapStyle
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.data.ThemeMode
import com.nextgen.caros.data.WallpaperPreset
import com.nextgen.caros.spotify.SpotifyRepository
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import java.text.DateFormat
import java.util.Date

enum class SettingsSection(val label: String, val icon: ImageVector, val color: Color) {
    PHONE_LINK("Phone Link", Icons.Filled.PhoneIphone, Color(0xFF30D158)),
    APPEARANCE("Appearance", Icons.Filled.Palette, Color(0xFF0A84FF)),
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Color(0xFF5E5CE6)),
    CLOCK("Clock", Icons.Filled.Schedule, Color(0xFF8E8E93)),
    DRIVING("Speed & units", Icons.Filled.Speed, Color(0xFFFF453A)),
    MAPS("Maps & navigation", Icons.Filled.Map, Color(0xFF34C759)),
    CAMERAS("Speed cameras", Icons.Filled.CameraAlt, Color(0xFFFF9F0A)),
    SOUND("Sound & alerts", Icons.Filled.VolumeUp, Color(0xFFFF375F)),
    MUSIC("Music & Spotify", Icons.Filled.MusicNote, Color(0xFFFF2D55)),
    WALLPAPER("Wallpaper", Icons.Filled.Wallpaper, Color(0xFF64D2FF)),
    PERMISSIONS("Permissions", Icons.Filled.Security, Color(0xFF636366)),
    ABOUT("About & data", Icons.Filled.Info, Color(0xFF8E8E93)),
}

@Composable
fun SettingsScreen(section: SettingsSection, onSection: (SettingsSection) -> Unit) {
    val ng = LocalNg.current
    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(280.dp).fillMaxHeight()) {
            com.nextgen.caros.ui.components.LargeTitle("Settings", Modifier.padding(start = 8.dp, bottom = 10.dp))
            LazyColumn(
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (ng.isDark) Color(0xFF1C1C1E).copy(alpha = 0.94f) else Color.White),
            ) {
                items(SettingsSection.entries) { s ->
                    val sel = s == section
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (sel) ng.accent.copy(alpha = if (ng.isDark) 0.28f else 0.16f) else Color.Transparent)
                            .pressable(pressedScale = 0.98f) { onSection(s) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.nextgen.caros.ui.components.IconBadge(s.icon, s.color)
                        Spacer(Modifier.width(14.dp))
                        Text(s.label, style = NgType.body.copy(color = ng.textPrimary), modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = ng.textTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        AnimatedContent(
            section, modifier = Modifier.weight(1f).fillMaxHeight(),
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically(tween(300)) { it / 20 }) togetherWith fadeOut(tween(120)) },
            label = "settings",
        ) { s ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                item {
                    when (s) {
                        SettingsSection.PHONE_LINK -> PhoneLinkSettings()
                        SettingsSection.APPEARANCE -> AppearanceSettings()
                        SettingsSection.DASHBOARD -> DashboardSettings()
                        SettingsSection.CLOCK -> ClockSettings()
                        SettingsSection.DRIVING -> DrivingSettings()
                        SettingsSection.MAPS -> MapSettings()
                        SettingsSection.CAMERAS -> CameraSettings()
                        SettingsSection.SOUND -> SoundSettings()
                        SettingsSection.MUSIC -> MusicSettings()
                        SettingsSection.WALLPAPER -> WallpaperSettings()
                        SettingsSection.PERMISSIONS -> PermissionSettings()
                        SettingsSection.ABOUT -> AboutSettings()
                    }
                }
            }
        }
    }
}

private val repo get() = appContainer.settings

@Composable
private fun AppearanceSettings() {
    val s by repo.settings.collectAsState()
    val ng = LocalNg.current
    SettingGroup("Theme") {
        ChoiceRow("Mode", "Dark is recommended for night driving", ThemeMode.entries.map { it to it.label }, s.themeMode, repo::setTheme)
    }
    SettingGroup("Accent colour") {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccentColor.entries.forEach { a ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { repo.setAccent(a) }) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(a.argb), Color(a.argb2))))
                            .border(3.dp, if (s.accent == a) ng.textPrimary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (s.accent == a) Icon(Icons.Filled.Check, null, tint = Color.White) }
                    Spacer(Modifier.height(6.dp))
                    Text(a.label, style = NgType.caption.copy(color = ng.textSecondary))
                }
            }
        }
    }
    SettingGroup("Display") {
        SwitchRow("Keep screen on", "Prevents the head unit from sleeping while the app is open", s.keepScreenOn, repo::setKeepScreenOn)
    }
}

@Composable
private fun DashboardSettings() {
    val s by repo.settings.collectAsState()
    val ng = LocalNg.current
    SettingGroup("Home screen layout") {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardLayout.entries.forEach { l ->
                val sel = s.dashboardLayout == l
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) ng.accent.copy(alpha = 0.18f) else ng.glass)
                        .border(1.5.dp, if (sel) ng.accent else ng.glassBorder, RoundedCornerShape(18.dp))
                        .pressable { repo.setLayout(l) }.padding(14.dp),
                ) {
                    LayoutThumb(l)
                    Spacer(Modifier.height(10.dp))
                    Text(l.label, style = NgType.label.copy(color = ng.textPrimary))
                    Text(l.description, style = NgType.caption.copy(color = ng.textSecondary))
                }
            }
        }
    }
    SettingGroup("Navigation bar") {
        ChoiceRow("Style", "CarPlay sidebar shows time, signal and battery with your recent apps; the glass bar floats at the bottom",
            com.nextgen.caros.data.NavBarStyle.entries.map { it to it.label }, s.navBarStyle, repo::setNavBarStyle)
    }
    SettingGroup("Head unit integration") {
        SwitchRow(
            "Use as home screen",
            "Adds NextGen Car OS to Android's list of home apps so the head unit can boot straight into it.",
            s.homeLauncherEnabled,
        ) { appContainer.apps.setHomeLauncherEnabled(it) }
        if (s.homeLauncherEnabled) ActionRow("Choose default home app", "Pick NextGen Car OS in Android's home-app settings", "Open") {
            if (!appContainer.apps.openHomeSettings()) Notifier.show("Press the Home button and choose NextGen Car OS")
        }
    }
}

@Composable
private fun LayoutThumb(l: DashboardLayout) {
    val ng = LocalNg.current
    val c = ng.textPrimary.copy(alpha = 0.18f)
    val a = ng.accent.copy(alpha = 0.5f)
    Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (l) {
            DashboardLayout.CLUSTER -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().weight(1.1f).clip(RoundedCornerShape(6.dp)).background(a))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c)) }
                }
            }
            DashboardLayout.STANDARD -> {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(c))
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(c))
                }
                Box(Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(a))
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c))
            }
            DashboardLayout.MAP_FOCUS -> {
                Box(Modifier.weight(2f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(a))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(c)) }
                }
            }
            DashboardLayout.MINIMAL -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp)).background(a))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c))
                    Box(Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
        }
    }
}

@Composable
private fun ClockSettings() {
    val s by repo.settings.collectAsState()
    SettingGroup("Clock") {
        ChoiceRow("Style", null, ClockStyle.entries.map { it to it.label }, s.clockStyle, repo::setClockStyle)
        SwitchRow("24-hour time", null, s.use24h, repo::setUse24h)
    }
}

@Composable
private fun DrivingSettings() {
    val s by repo.settings.collectAsState()
    SettingGroup("Units") {
        ChoiceRow("Speed unit", "Also sets distances (miles/km) and temperature (°F/°C)", SpeedUnit.entries.map { it to it.label.uppercase() }, s.speedUnit, repo::setSpeedUnit)
    }
    SettingGroup("Speed limits") {
        SwitchRow(
            "Show road speed limits",
            "Uses OpenStreetMap maxspeed tags for the road you're on. Where a road has no mapped limit the app shows \"No data\" instead of guessing.",
            s.speedLimitsEnabled, repo::setSpeedLimitsEnabled,
        )
        SwitchRow("Over-limit indication", "Speedometer turns red above the known limit", s.overspeedWarning, repo::setOverspeedWarning)
        SliderRow("Tolerance", s.overspeedTolerance, 0..10, valueLabel = { "+$it ${s.speedUnit.label}" }, onChange = repo::setOverspeedTolerance)
    }
}

@Composable
private fun MapSettings() {
    val s by repo.settings.collectAsState()
    val places by appContainer.navigation.places.collectAsState()
    SettingGroup("Map") {
        ChoiceRow("Map style", MapStyle.entries.first { it == s.mapStyle }.note, MapStyle.entries.map { it to it.label }, s.mapStyle, repo::setMapStyle)
        if (s.mapStyle == MapStyle.CUSTOM) TextFieldRow(
            "Custom style URL", "MapLibre style JSON, e.g. https://api.maptiler.com/maps/streets-v2-dark/style.json?key=YOUR_KEY",
            s.customMapStyleUrl, "https://…/style.json", repo::setCustomMapStyleUrl,
        )
        SwitchRow("3D driving view", "Tilted map that rotates with your heading", s.map3d, repo::setMap3d)
        SliderRow("Follow zoom", s.followZoom, 13..18, valueLabel = { "$it" }, onChange = repo::setFollowZoom)
    }
    SettingGroup("Navigation") {
        SwitchRow("Voice guidance", "Spoken turn instructions using the device's text-to-speech engine", s.voiceGuidance, repo::setVoiceGuidance)
        TextFieldRow(
            "Routing server (OSRM)",
            "Default is the public OSRM demo server – fine for personal use, but it has no uptime guarantee. Point this at your own OSRM-compatible server for reliability.",
            s.routingServer, "https://router.project-osrm.org", repo::setRoutingServer,
        )
        TextFieldRow(
            "Overpass API server",
            "Used for speed limits and camera data from OpenStreetMap",
            s.overpassServer, "https://overpass-api.de/api/interpreter", repo::setOverpassServer,
        )
    }
    SettingGroup("Saved places") {
        InfoRow("Home", places.home?.let { "${it.name} – ${it.address}" } ?: "Not set – use the house icon on a search result")
        InfoRow("Work", places.work?.let { "${it.name} – ${it.address}" } ?: "Not set – use the briefcase icon on a search result")
    }
}

@Composable
private fun CameraSettings() {
    val s by repo.settings.collectAsState()
    val db by appContainer.cameras.db.collectAsState()
    val status by appContainer.cameras.update.collectAsState()
    val ng = LocalNg.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    SettingGroup("Speed camera awareness") {
        SwitchRow("Camera awareness", "Informational alerts when approaching a known camera location in your direction of travel", s.camerasEnabled, repo::setCamerasEnabled)
        SliderRow("Alert distance", s.cameraAlertDistanceM, 200..1500, step = 50, valueLabel = { "$it m" }, onChange = repo::setCameraAlertDistance)
        SwitchRow("Show unverified reports on map", null, s.showUnverifiedCameras, repo::setShowUnverified)
        SwitchRow("Alert for unverified reports", "Off by default – unverified locations are shown but don't trigger alerts", s.alertForUnverified, repo::setAlertForUnverified)
    }
    SettingGroup("Data sources") {
        val osmCount = db.cameras.count { it.sourceName == "OpenStreetMap" }
        val osmZones = db.zones.count { it.sourceName == "OpenStreetMap" }
        InfoRow(
            "OpenStreetMap (community mapped)",
            if (db.osmUpdatedAt == 0L) "Not downloaded yet."
            else "$osmCount cameras and $osmZones average-speed sections within ${db.osmRadiusKm} km of where you were on " +
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(db.osmUpdatedAt)) +
                ". Coverage depends on local mappers and is not complete or live.",
            ng.mapped,
        )
        SliderRow("Download radius", s.cameraDownloadRadiusKm, 10..150, step = 10, valueLabel = { "$it km" }, onChange = repo::setCameraRadius)
        ActionRow("Update from OpenStreetMap", "Downloads camera nodes and enforcement relations around your current position", "Update", primary = true,
            enabled = status != UpdateStatus.Running) { appContainer.cameras.updateFromOpenStreetMap() }
        when (val st = status) {
            UpdateStatus.Running -> InfoRow("Working…", "Downloading / importing camera data")
            is UpdateStatus.Done -> InfoRow("Done", st.message, ng.success)
            is UpdateStatus.Failed -> InfoRow("Problem", st.message, ng.warning)
            UpdateStatus.Idle -> {}
        }
        ActionRow(
            "Import CSV file",
            "Columns: lat, lon and optionally type, maxspeed, direction. Use this for an official dataset published by a road or police authority.",
            "Import",
        ) { picker.launch(arrayOf("text/*", "application/csv", "application/octet-stream")) }
        db.importedSources.forEach { src ->
            val n = db.cameras.count { it.sourceName == src }
            val v = db.cameras.firstOrNull { it.sourceName == src }?.verification
            ActionRow(src, "$n locations · ${v?.label ?: ""}", "Remove") { appContainer.cameras.clearSource(src) }
        }
        val reports = db.cameras.count { it.sourceName == "Your report" }
        ActionRow("Your reports", "$reports unverified locations reported from this device", "Clear", enabled = reports > 0) { appContainer.cameras.clearReports() }
        if (osmCount + osmZones > 0) ActionRow("OpenStreetMap data", "Remove downloaded OSM camera data", "Remove") { appContainer.cameras.clearSource("OpenStreetMap") }
    }
    SettingGroup("How locations are labelled") {
        Verification.entries.forEach { v ->
            InfoRow(v.label, v.explanation, when (v) { Verification.VERIFIED -> ng.verified; Verification.MAPPED -> ng.mapped; Verification.UNVERIFIED -> ng.unverified })
        }
        InfoRow(
            "Important",
            "NextGen Car OS does not have a complete or live camera database. Mobile and temporary cameras are generally not included. " +
                "Alerts are informational only – always obey posted limits. Check that camera-location alerts are legal where you drive " +
                "(they are restricted in some countries, e.g. France, Germany and Switzerland).",
            ng.warning,
        )
    }

    pendingImport?.let { uri ->
        var name by remember { mutableStateOf("") }
        var official by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import camera locations") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Source name (e.g. publisher of the data)") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.pressable { official = !official }) {
                        Checkbox(official, { official = it })
                        Text("This file is an official dataset published by a road or police authority. Mark these locations as Verified.")
                    }
                    if (!official) Text("Locations will be labelled Unverified.", style = NgType.caption)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    appContainer.cameras.importCsv(uri, name.trim(), official); pendingImport = null
                }) { Text("Import") }
            },
            dismissButton = { TextButton({ pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SoundSettings() {
    val s by repo.settings.collectAsState()
    SettingGroup("Sound") {
        SwitchRow("Alert chime", "A short tone when a camera alert appears", s.alertSounds, repo::setAlertSounds)
        SwitchRow("Voice guidance", "Spoken navigation instructions", s.voiceGuidance, repo::setVoiceGuidance)
    }
    SettingGroup("Notifications") {
        SwitchRow("In-app banners", "Brief confirmations at the top of the screen", s.showNotificationsBanner, repo::setBanners)
        val perms = rememberPermissions()
        perms.firstOrNull { it.id == "notifications" }?.let { p ->
            ActionRow(
                "Android notification permission",
                when (p.status) { PermStatus.GRANTED -> "Granted"; PermStatus.NOT_NEEDED -> "Not required on this Android version"; else -> "Needed for the navigation notification" },
                if (p.status == PermStatus.DENIED) "Allow" else "OK", enabled = p.action != null,
            ) { p.action?.invoke() }
        }
    }
}

@Composable
private fun MusicSettings() {
    val s by repo.settings.collectAsState()
    val sp by appContainer.spotify.state.collectAsState()
    val media by appContainer.media.state.collectAsState()
    val ctx = LocalContext.current
    val ng = LocalNg.current

    SettingGroup("Media control") {
        ActionRow(
            "Media access",
            if (media.hasAccess) "Granted – now-playing info from Spotify and other players is available"
            else "Needed to show track, artwork and progress from other apps",
            if (media.hasAccess) "Manage" else "Allow", primary = !media.hasAccess,
        ) { runCatching { ctx.startActivity(appContainer.media.notificationAccessIntent()) } }
        val players = remember(media.sessions) {
            (listOf(SpotifyRepository.SPOTIFY_PACKAGE) + media.sessions).distinct()
        }
        ChoiceRow(
            "Preferred player", "Used when several apps have a media session and for the Open button",
            players.map { pkg ->
                pkg to (runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
                    ?: if (pkg == SpotifyRepository.SPOTIFY_PACKAGE) "Spotify (not installed)" else pkg)
            },
            s.preferredMediaPackage, repo::setPreferredMediaPackage,
        )
        SwitchRow("Music visualiser", "Animated playing indicator (decorative – not audio analysis)", s.showVisualiser, repo::setVisualiser)
    }
    SettingGroup("Spotify account") {
        TextFieldRow(
            "Spotify Client ID",
            "Create a free app at developer.spotify.com/dashboard, add the redirect URI below, and paste its Client ID here." +
                if (BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) " (A build-time Client ID is already configured.)" else "",
            s.spotifyClientId, "32-character Client ID", repo::setSpotifyClientId,
        )
        InfoRow("Redirect URI", BuildConfig.SPOTIFY_REDIRECT_URI, ng.accent)
        if (sp.connected) {
            ActionRow("Connected", "${sp.displayName ?: "Spotify user"} · ${if (sp.premium) "Premium" else "Free"}", "Disconnect") { appContainer.spotify.logout() }
        } else {
            ActionRow("Connect Spotify", "Sign in on Spotify's own page – your password is never seen by this app", "Connect",
                primary = true, enabled = sp.clientIdConfigured) { appContainer.spotify.login(ctx) }
        }
        InfoRow(
            "What works with which account",
            "Free & Premium: now playing, artwork, play/pause, skip and seek via the Spotify app's media session; browsing your playlists. " +
                "Premium only: starting a playlist directly from NextGen Car OS (Spotify Web API restriction). On Free, playlists open in the Spotify app.",
        )
    }
}

@Composable
private fun WallpaperSettings() {
    val s by repo.settings.collectAsState()
    val ng = LocalNg.current
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            repo.setWallpaperUri(uri.toString())
        }
    }
    SettingGroup("Wallpaper") {
        LazyRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(WallpaperPreset.entries.filter { it != WallpaperPreset.IMAGE }) { w ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { repo.setWallpaper(w) }) {
                    Box(
                        Modifier.size(width = 150.dp, height = 88.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(w.colors.map { Color(it) }))
                            .border(2.dp, if (s.wallpaper == w) ng.accent else ng.glassBorder, RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(w.label, style = NgType.caption.copy(color = ng.textSecondary))
                }
            }
        }
        ActionRow(
            "Custom image",
            if (s.wallpaper == WallpaperPreset.IMAGE) "Using your image" else "Choose any picture stored on the head unit",
            "Choose image", primary = true,
        ) { picker.launch(arrayOf("image/*")) }
        if (s.wallpaper == WallpaperPreset.IMAGE) {
            SliderRow("Dim image", s.wallpaperDim, 0..90, step = 5, valueLabel = { "$it%" }, onChange = repo::setWallpaperDim)
        }
    }
}

@Composable
private fun PermissionSettings() {
    PermissionList()
}

@Composable
fun PermissionList() {
    val ng = LocalNg.current
    val ctx = LocalContext.current
    val perms = rememberPermissions()
    SettingGroup("Permissions and why they're needed") {
        perms.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(ng.glassStrong), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = ng.accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = NgType.body.copy(color = ng.textPrimary))
                    Text(p.why, style = NgType.caption.copy(color = ng.textSecondary))
                }
                Spacer(Modifier.width(12.dp))
                when (p.status) {
                    PermStatus.GRANTED -> if (p.id == "media") PillButton("Manage") { p.action?.invoke() }
                        else Text("Granted", style = NgType.label.copy(color = ng.success))
                    PermStatus.DENIED -> Column(horizontalAlignment = Alignment.End) {
                        PillButton("Allow", primary = true) { p.action?.invoke() }
                        if (p.id != "media") Text(
                            "App settings", style = NgType.caption.copy(color = ng.textTertiary),
                            modifier = Modifier.padding(top = 6.dp).pressable { openAppSettings(ctx) },
                        )
                    }
                    PermStatus.NOT_NEEDED -> Text("Automatic", style = NgType.label.copy(color = ng.textTertiary))
                    PermStatus.NOT_USED -> Text("Not used", style = NgType.label.copy(color = ng.textTertiary))
                }
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    val ng = LocalNg.current
    SettingGroup("NextGen Car OS") {
        InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        InfoRow("Built for", "Standard Android head units (Android 7.0+), landscape touchscreens. Does not require Google Play Services or Android Automotive OS.")
    }
    SettingGroup("Data sources & attribution") {
        InfoRow("Map data", "© OpenStreetMap contributors (ODbL). Rendered with MapLibre Native.")
        InfoRow("Map tiles", "OpenFreeMap (openfreemap.org) or CARTO basemaps (carto.com), depending on the selected style.")
        InfoRow("Search", "Photon geocoder by komoot (photon.komoot.io), based on OpenStreetMap.")
        InfoRow("Routing", "OSRM (project-osrm.org). The default public server is a demo with fair-use limits.")
        InfoRow("Speed limits & cameras", "OpenStreetMap via the Overpass API. Limits are shown only where mapped; missing data is shown as \"No data\".")
        InfoRow("Weather", "Open-Meteo.com (CC BY 4.0).")
        InfoRow("Music", "Android MediaSession and the official Spotify Web API. Spotify is a trademark of Spotify AB; this app is not affiliated with Spotify.")
    }
    SettingGroup("Safety") {
        InfoRow(
            "Drive responsibly",
            "Set destinations and adjust settings while parked. Speed limits and camera data come from community and third-party sources " +
                "and may be incomplete or out of date – road signs always take precedence.",
            ng.warning,
        )
    }
    SettingGroup("Setup") {
        ActionRow("Show welcome & permissions again", null, "Open") { repo.setOnboardingDone(false) }
    }
}
