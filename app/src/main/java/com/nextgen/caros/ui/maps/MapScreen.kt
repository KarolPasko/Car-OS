package com.nextgen.caros.ui.maps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.appContainer
import com.nextgen.caros.cameras.SpeedCamera
import com.nextgen.caros.cameras.Verification
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.location.Geo
import com.nextgen.caros.nav.GeocodingService
import com.nextgen.caros.nav.NavState
import com.nextgen.caros.nav.Place
import com.nextgen.caros.speedlimit.MaxSpeedParser
import com.nextgen.caros.speedlimit.ParsedLimit
import com.nextgen.caros.ui.components.GlassIconButton
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.components.SectionLabel
import com.nextgen.caros.ui.components.SpeedLimitSign
import com.nextgen.caros.ui.components.Speedometer
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.vector
import com.nextgen.caros.ui.home.rememberDrivingInfo
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.shell.ShellNav
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapScreen(nav: ShellNav) {
    val ng = LocalNg.current
    val s by appContainer.settings.settings.collectAsState()
    val loc by appContainer.location.state.collectAsState()
    val navState by appContainer.navigation.state.collectAsState()
    val camDb by appContainer.cameras.db.collectAsState()
    val controller = remember { MapController() }
    var follow by rememberSaveable { mutableStateOf(true) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedCamera by remember { mutableStateOf<SpeedCamera?>(null) }
    var showCameras by rememberSaveable { mutableStateOf(true) }
    val density = LocalDensity.current
    val imperial = s.speedUnit == SpeedUnit.MPH

    val route = when (val n = navState) {
        is NavState.Navigating -> n.route
        is NavState.Preview -> n.route
        else -> null
    }

    // Route overview when a preview arrives.
    LaunchedEffect(route, navState is NavState.Preview) {
        if (navState is NavState.Preview && route != null) {
            follow = false
            delay(300)
            controller.fit(route.points, with(density) { 120.dp.roundToPx() })
        }
        if (navState is NavState.Navigating) follow = true
    }

    Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(28.dp))) {
        NgMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = s.mapStyleUrl(ng.isDark),
            controller = controller,
            location = loc.location,
            bearing = loc.bearing,
            follow = follow,
            tilt3d = s.map3d,
            followZoom = s.followZoom.toDouble(),
            route = route?.points,
            cameras = if (s.camerasEnabled && showCameras) appContainer.cameras.visibleCameras() else emptyList(),
            zones = if (s.camerasEnabled && showCameras) camDb.zones else emptyList(),
            destination = route?.destination?.latLon,
            hasLocationPermission = loc.permissionGranted,
            onLongPress = { p ->
                if (navState !is NavState.Navigating) {
                    appContainer.navigation.preview(Place("Dropped pin", String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon), p.lat, p.lon))
                }
            },
            onUserGesture = { follow = false },
            onCameraTapped = { id -> selectedCamera = camDb.cameras.firstOrNull { it.id == id } },
        )

        // ---------------- top-left: search or manoeuvre
        Column(Modifier.align(Alignment.TopStart).padding(14.dp).widthIn(max = 440.dp)) {
            when (val n = navState) {
                is NavState.Navigating -> ManeuverBanner(n, imperial)
                else -> {
                    if (searchOpen) SearchPanel(onClose = { searchOpen = false }, onPick = {
                        searchOpen = false
                        appContainer.navigation.preview(it)
                    })
                    else SearchPill { searchOpen = true }
                }
            }
        }

        // ---------------- right controls
        Column(
            Modifier.align(Alignment.CenterEnd).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(Icons.Filled.Add, "Zoom in") { controller.zoomBy(1.0) }
            GlassIconButton(Icons.Filled.Remove, "Zoom out") { controller.zoomBy(-1.0) }
            GlassIconButton(if (s.map3d) Icons.Filled.ViewInAr else Icons.Filled.Map, "2D/3D") {
                appContainer.settings.setMap3d(!s.map3d)
            }
            GlassIconButton(Icons.Filled.MyLocation, "Recenter", accent = !follow) { follow = true }
            if (s.camerasEnabled) {
                GlassIconButton(Icons.Filled.CameraAlt, "Toggle cameras", tint = if (showCameras) ng.accent else ng.textTertiary) {
                    showCameras = !showCameras
                }
            }
        }

        // ---------------- bottom-left: speed + limit
        if (navState !is NavState.Preview) {
            SpeedCluster(Modifier.align(Alignment.BottomStart).padding(14.dp))
        }

        // ---------------- bottom panels
        Box(Modifier.align(Alignment.BottomCenter).padding(14.dp)) {
            when (val n = navState) {
                is NavState.Routing -> StatusCard {
                    CircularProgressIndicator(color = ng.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Finding the best route to ${n.destination.name}…", style = NgType.body.copy(color = ng.textPrimary))
                }
                is NavState.Preview -> RoutePreviewCard(n, imperial)
                is NavState.Navigating -> TripBar(n, imperial, onOverview = {
                    follow = false
                    controller.fit(n.route.points, with(density) { 120.dp.roundToPx() })
                })
                is NavState.Arrived -> StatusCard {
                    Icon(Icons.Filled.Flag, null, tint = ng.accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("You've arrived at ${n.destination.name}", style = NgType.headline.copy(color = ng.textPrimary))
                    Spacer(Modifier.width(20.dp))
                    PillButton("Done", primary = true) { appContainer.navigation.stop() }
                }
                is NavState.Error -> StatusCard {
                    Text(n.message, style = NgType.body.copy(color = ng.textPrimary), modifier = Modifier.widthIn(max = 420.dp))
                    Spacer(Modifier.width(14.dp))
                    n.destination?.let { d -> PillButton("Retry", primary = true) { appContainer.navigation.preview(d) } }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Close") { appContainer.navigation.dismiss() }
                }
                NavState.Idle -> {
                    if (!loc.permissionGranted) StatusCard {
                        Text("Location permission is needed for GPS, speed and navigation.", style = NgType.body.copy(color = ng.textPrimary))
                    } else if (loc.location == null) StatusCard {
                        CircularProgressIndicator(color = ng.accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Acquiring GPS…  Long-press the map to drop a destination.", style = NgType.body.copy(color = ng.textPrimary))
                    }
                }
            }
        }

        // ---------------- camera details
        selectedCamera?.let { cam ->
            CameraInfoCard(cam, Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 90.dp)) { selectedCamera = null }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    val ng = LocalNg.current
    Row(
        Modifier.width(360.dp).pressable(onClick = onClick).clip(RoundedCornerShape(50))
            .background(if (ng.isDark) Color(0xF00E1320) else Color(0xF5FFFFFF))
            .border(1.dp, ng.glassBorder, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = ng.accent)
        Spacer(Modifier.width(12.dp))
        Text("Search destination", style = NgType.headline.copy(color = ng.textSecondary))
    }
}

@Composable
private fun SearchPanel(onClose: () -> Unit, onPick: (Place) -> Unit) {
    val ng = LocalNg.current
    val places by appContainer.navigation.places.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runSearch() {
        if (query.isBlank()) return
        loading = true; error = null
        scope.launch {
            runCatching { GeocodingService.search(query, appContainer.location.state.value.latLon) }
                .onSuccess { results = it; if (it.isEmpty()) error = "No places found" }
                .onFailure { error = "Search unavailable – check the internet connection" }
            loading = false
        }
    }
    LaunchedEffect(query) {
        if (query.trim().length >= 3) { delay(700); runSearch() } else { results = emptyList(); error = null }
    }

    Column(
        Modifier.width(440.dp).heightIn(max = 520.dp).clip(RoundedCornerShape(26.dp))
            .background(if (ng.isDark) Color(0xF50B101C) else Color(0xFAFFFFFF))
            .border(1.dp, ng.glassBorder, RoundedCornerShape(26.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Filled.ArrowBack, "Close", size = 46.dp) { focus.clearFocus(); onClose() }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(ng.glass).padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                if (query.isEmpty()) Text("Address, place or postcode", style = NgType.body.copy(color = ng.textTertiary))
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = NgType.body.copy(color = ng.textPrimary, fontSize = 18.sp),
                    cursorBrush = SolidColor(ng.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); runSearch() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                GlassIconButton(Icons.Filled.Close, "Clear", size = 46.dp) { query = "" }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (loading) Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = ng.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Searching…", style = NgType.label.copy(color = ng.textSecondary))
        }
        error?.let { Text(it, style = NgType.label.copy(color = ng.textSecondary), modifier = Modifier.padding(8.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (results.isEmpty() && query.isBlank()) {
                val hw = listOfNotNull(places.home?.let { Triple(Icons.Filled.Home, "Home", it) }, places.work?.let { Triple(Icons.Filled.Work, "Work", it) })
                items(hw) { (icon, label, p) -> PlaceRow(icon, label, p.name, onClick = { onPick(p) }) }
                if (places.recents.isNotEmpty()) item { SectionLabel("Recent", Modifier.padding(8.dp)) }
                items(places.recents) { p ->
                    PlaceRow(Icons.Filled.History, p.name, p.address, onClick = { onPick(p) },
                        trailing = { RowActions(p, deletable = true) })
                }
                if (hw.isEmpty() && places.recents.isEmpty()) item {
                    Text("Search for a place, or long-press anywhere on the map.", style = NgType.label.copy(color = ng.textTertiary), modifier = Modifier.padding(8.dp))
                }
            }
            items(results) { p ->
                val d = appContainer.location.state.value.latLon?.let { Geo.distance(it, p.latLon) }
                PlaceRow(
                    Icons.Filled.Place, p.name,
                    listOfNotNull(d?.let { Geo.formatDistance(it, appContainer.settings.current.speedUnit == SpeedUnit.MPH) }, p.address.ifBlank { null }).joinToString(" · "),
                    onClick = { onPick(p) }, trailing = { RowActions(p, deletable = false) },
                )
            }
        }
    }
}

@Composable
private fun RowActions(p: Place, deletable: Boolean) {
    val ng = LocalNg.current
    Row {
        Icon(Icons.Filled.Home, "Set as Home", tint = ng.textTertiary, modifier = Modifier.size(40.dp).pressable {
            appContainer.navigation.setHome(p); Notifier.show("Home saved", p.name, Icons.Filled.Home)
        }.padding(9.dp))
        Icon(Icons.Filled.Work, "Set as Work", tint = ng.textTertiary, modifier = Modifier.size(40.dp).pressable {
            appContainer.navigation.setWork(p); Notifier.show("Work saved", p.name, Icons.Filled.Work)
        }.padding(9.dp))
        if (deletable) Icon(Icons.Filled.Delete, "Remove", tint = ng.textTertiary, modifier = Modifier.size(40.dp).pressable {
            appContainer.navigation.removeRecent(p)
        }.padding(9.dp))
    }
}

@Composable
private fun PlaceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val ng = LocalNg.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(ng.glass), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ng.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NgType.body.copy(color = ng.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = NgType.caption.copy(color = ng.textTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ManeuverBanner(n: NavState.Navigating, imperial: Boolean) {
    val ng = LocalNg.current
    val step = n.progress.nextStep
    AnimatedVisibility(true, enter = slideInHorizontally { -it } + fadeIn()) {
        Column(Modifier.width(420.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(ng.accentBrush).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(step?.icon?.vector() ?: Icons.Filled.Navigation, null, tint = Color(0xFF020617), modifier = Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(Geo.formatDistance(n.progress.distanceToNextM, imperial), style = NgType.display.copy(color = Color(0xFF020617), fontSize = 40.sp))
                    Text(step?.instruction ?: "Follow the route", style = NgType.headline.copy(color = Color(0xE6020617)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            n.progress.followingStep?.let { f ->
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(if (ng.isDark) Color(0xF00E1320) else Color(0xF5FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Then", style = NgType.label.copy(color = ng.textSecondary))
                    Spacer(Modifier.width(8.dp))
                    Icon(f.icon.vector(), null, tint = ng.textPrimary, modifier = Modifier.size(24.dp))
                }
            }
            if (n.rerouting || n.progress.offRoute) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (n.rerouting) "Recalculating route…" else "Off route",
                    style = NgType.label.copy(color = ng.warning),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xE60E1320)).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SpeedCluster(modifier: Modifier) {
    val ng = LocalNg.current
    val s by appContainer.settings.settings.collectAsState()
    val d = rememberDrivingInfo()
    Row(
        modifier.clip(RoundedCornerShape(30.dp)).background(if (ng.isDark) Color(0xF00E1320) else Color(0xF5FFFFFF))
            .border(1.dp, if (d.overspeed) ng.danger else ng.glassBorder, RoundedCornerShape(30.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Speedometer(d.speed, s.speedUnit.label, if (s.speedUnit == SpeedUnit.MPH) 120f else 200f, d.overspeed, Modifier.size(110.dp), numberSize = 34.sp)
        Spacer(Modifier.width(8.dp))
        SpeedLimitSign(d.limit, s.speedUnit, size = 72.dp)
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    val ng = LocalNg.current
    AnimatedVisibility(true, enter = slideInVertically { it } + fadeIn()) {
        Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(if (ng.isDark) Color(0xF00E1320) else Color(0xF5FFFFFF))
                .border(1.dp, ng.glassBorder, RoundedCornerShape(24.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

private fun fmtDuration(seconds: Double): String {
    val m = (seconds / 60).toInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "${m.coerceAtLeast(1)} min"
}

@Composable
private fun RoutePreviewCard(p: NavState.Preview, imperial: Boolean) {
    val ng = LocalNg.current
    val r = p.route
    val eta = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + (r.durationS * 1000).toLong()))
    StatusCard {
        Column(Modifier.widthIn(max = 380.dp)) {
            Text(r.destination.name, style = NgType.title.copy(color = ng.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.destination.address.isNotBlank())
                Text(r.destination.address, style = NgType.label.copy(color = ng.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Stat(fmtDuration(r.durationS), "Duration")
                Stat(Geo.formatDistance(r.distanceM, imperial), "Distance")
                Stat(eta, "Arrival")
            }
        }
        Spacer(Modifier.width(22.dp))
        PillButton("Cancel") { appContainer.navigation.stop() }
        Spacer(Modifier.width(10.dp))
        PillButton("Start", icon = Icons.Filled.Navigation, primary = true) { appContainer.navigation.start() }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val ng = LocalNg.current
    Column {
        Text(value, style = NgType.headline.copy(color = ng.textPrimary))
        Text(label, style = NgType.caption.copy(color = ng.textTertiary))
    }
}

@Composable
private fun TripBar(n: NavState.Navigating, imperial: Boolean, onOverview: () -> Unit) {
    val ng = LocalNg.current
    val s by appContainer.settings.settings.collectAsState()
    StatusCard {
        Stat(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(n.progress.etaMillis)), "Arrival")
        Spacer(Modifier.width(22.dp))
        Stat(fmtDuration(n.progress.durationRemainingS), "Remaining")
        Spacer(Modifier.width(22.dp))
        Stat(Geo.formatDistance(n.progress.distanceRemainingM, imperial), "Distance")
        Spacer(Modifier.width(22.dp))
        GlassIconButton(if (s.voiceGuidance) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, "Voice", size = 50.dp) {
            appContainer.settings.setVoiceGuidance(!s.voiceGuidance)
        }
        Spacer(Modifier.width(10.dp))
        GlassIconButton(Icons.Filled.Route, "Overview", size = 50.dp) { onOverview() }
        Spacer(Modifier.width(10.dp))
        GlassIconButton(Icons.Filled.AddAPhoto, "Report camera here", size = 50.dp) {
            if (appContainer.cameras.reportHere()) Notifier.show("Camera reported", "Saved on this device as an unverified report", Icons.Filled.CameraAlt)
        }
        Spacer(Modifier.width(14.dp))
        PillButton("End", danger = true) { appContainer.navigation.stop() }
    }
}

@Composable
private fun CameraInfoCard(cam: SpeedCamera, modifier: Modifier, onClose: () -> Unit) {
    val ng = LocalNg.current
    val color = when (cam.verification) {
        Verification.VERIFIED -> ng.verified
        Verification.MAPPED -> ng.mapped
        Verification.UNVERIFIED -> ng.unverified
    }
    val s by appContainer.settings.settings.collectAsState()
    GlassPanel(modifier.width(340.dp), strong = true) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CameraAlt, null, tint = color)
                Spacer(Modifier.width(10.dp))
                Text(cam.type.label, style = NgType.headline.copy(color = ng.textPrimary), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Close", tint = ng.textTertiary, modifier = Modifier.size(36.dp).pressable(onClick = onClose).padding(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(cam.verification.label, style = NgType.label.copy(color = color))
            Text(cam.verification.explanation, style = NgType.caption.copy(color = ng.textSecondary))
            Spacer(Modifier.height(8.dp))
            Text("Source: ${cam.sourceName}", style = NgType.caption.copy(color = ng.textSecondary))
            val lim = MaxSpeedParser.parse(cam.maxspeed)
            if (lim is ParsedLimit.Value) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Limit recorded at camera:", style = NgType.caption.copy(color = ng.textSecondary))
                    Spacer(Modifier.width(8.dp))
                    SpeedLimitSign(com.nextgen.caros.speedlimit.SpeedLimitState.Known(lim, null, cam.sourceName), s.speedUnit, size = 44.dp)
                }
            }
            if (cam.verification == Verification.UNVERIFIED) {
                Spacer(Modifier.height(10.dp))
                PillButton("Delete this report", icon = Icons.Filled.Delete) { appContainer.cameras.deleteCamera(cam.id); onClose() }
            }
        }
    }
}
