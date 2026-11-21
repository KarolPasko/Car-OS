package com.nextgen.caros.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.WallpaperPreset
import com.nextgen.caros.ui.apps.LauncherScreen
import com.nextgen.caros.ui.components.formatTime
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.components.rememberNow
import com.nextgen.caros.ui.home.HomeScreen
import com.nextgen.caros.ui.maps.MapScreen
import com.nextgen.caros.ui.music.MusicScreen
import com.nextgen.caros.ui.settings.OnboardingScreen
import com.nextgen.caros.ui.settings.SettingsScreen
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import kotlinx.coroutines.delay

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MAPS("Maps", Icons.Filled.Map),
    MUSIC("Music", Icons.Filled.MusicNote),
    APPS("Apps", Icons.Filled.Apps),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** Navigation actions available to every screen. */
class ShellNav(
    val go: (Screen) -> Unit,
    val openSettings: (SettingsSection) -> Unit,
)

@Composable
fun NextGenRoot() {
    val settings by appContainer.settings.settings.collectAsState()
    var splash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(1600); splash = false }

    Box(Modifier.fillMaxSize().background(LocalNg.current.background)) {
        Wallpaper()
        AnimatedContent(
            targetState = when {
                splash -> 0
                !settings.onboardingDone -> 1
                else -> 2
            },
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(400)) },
            label = "root",
        ) { stage ->
            when (stage) {
                0 -> SplashScreen()
                1 -> OnboardingScreen(onDone = { appContainer.settings.setOnboardingDone(true) })
                else -> AppShell()
            }
        }
    }
}

@Composable
fun Wallpaper() {
    val s by appContainer.settings.settings.collectAsState()
    val ng = LocalNg.current
    Box(Modifier.fillMaxSize()) {
        if (s.wallpaper == WallpaperPreset.IMAGE && s.wallpaperUri.isNotBlank()) {
            AsyncImage(model = s.wallpaperUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(ng.background.copy(alpha = s.wallpaperDim / 100f)))
        } else {
            val colors = (if (s.wallpaper == WallpaperPreset.IMAGE) WallpaperPreset.NEBULA else s.wallpaper).colors.map { Color(it) }
            val cs = if (ng.isDark) colors else colors.map { lerpToLight(it) }
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(cs)))
            // soft accent glows
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(ng.accent.copy(alpha = if (ng.isDark) 0.16f else 0.10f), Color.Transparent), radius = 1100f)
                )
            )
        }
    }
}

private fun lerpToLight(c: Color) = Color(
    red = 0.92f + c.red * 0.08f, green = 0.94f + c.green * 0.06f, blue = 0.97f + c.blue * 0.03f,
)

@Composable
fun AppShell(initial: Screen = Screen.HOME, initialSection: SettingsSection = SettingsSection.APPEARANCE) {
    var screen by rememberSaveable { mutableStateOf(initial) }
    var settingsSection by rememberSaveable { mutableStateOf(initialSection) }
    val nav = ShellNav(
        go = { screen = it },
        openSettings = { settingsSection = it; screen = Screen.SETTINGS },
    )
    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Dock(screen, onSelect = { screen = it })
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 14 * dir } +
                            scaleIn(tween(380), initialScale = 0.985f)) togetherWith
                            (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.985f))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(nav)
                        Screen.MAPS -> MapScreen(nav)
                        Screen.MUSIC -> MusicScreen(nav)
                        Screen.APPS -> LauncherScreen(nav)
                        Screen.SETTINGS -> SettingsScreen(settingsSection, onSection = { settingsSection = it })
                    }
                }
            }
        }
        AlertOverlay(Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 110.dp))
        com.nextgen.caros.ui.components.CameraAlertCard(
            Modifier.align(if (screen == Screen.MAPS) Alignment.BottomEnd else Alignment.TopEnd)
                .padding(top = 20.dp, end = if (screen == Screen.MAPS) 100.dp else 24.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun Dock(current: Screen, onSelect: (Screen) -> Unit) {
    val ng = LocalNg.current
    val s by appContainer.settings.settings.collectAsState()
    val loc by appContainer.location.state.collectAsState()
    val bt by appContainer.bluetooth.state.collectAsState()
    val media by appContainer.media.state.collectAsState()
    val now = rememberNow()

    Column(
        Modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.verticalGradient(listOf(ng.glassStrong, ng.glass)))
            .border(1.dp, ng.glassBorder, RoundedCornerShape(30.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatTime(now, s.use24h), style = NgType.headline.copy(color = ng.textPrimary))
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Screen.entries.forEach { item ->
                val selected = item == current
                val bgAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(250), label = "dockSel")
                Box(
                    Modifier
                        .size(62.dp)
                        .pressable { onSelect(item) }
                        .clip(RoundedCornerShape(20.dp))
                        .background(ng.accentBrush, alpha = bgAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, item.label, tint = if (selected) Color(0xFF020617) else ng.textPrimary, modifier = Modifier.size(30.dp))
                }
            }
            // Now playing mini art (CarPlay-style)
            media.nowPlaying?.art?.let { art ->
                Image(
                    art.asImageBitmap(), "Now playing",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).pressable { onSelect(Screen.MUSIC) }.clip(RoundedCornerShape(14.dp)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                when {
                    !loc.permissionGranted || !loc.providerEnabled -> Icons.Filled.GpsOff
                    loc.hasGpsFix && !loc.isStale -> Icons.Filled.GpsFixed
                    else -> Icons.Filled.GpsNotFixed
                },
                "GPS",
                tint = if (loc.hasGpsFix && !loc.isStale) ng.success else ng.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            if (bt.supported) Icon(
                if (bt.connectedDevices.isNotEmpty()) Icons.Filled.BluetoothConnected else Icons.Filled.Bluetooth, "Bluetooth",
                tint = if (bt.connectedDevices.isNotEmpty()) ng.accent else ng.textTertiary, modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AlertOverlay(modifier: Modifier) {
    val ng = LocalNg.current
    val banner by Notifier.banner.collectAsState()
    val s by appContainer.settings.settings.collectAsState()
    LaunchedEffect(banner?.id) {
        val b = banner ?: return@LaunchedEffect
        delay(4000); Notifier.dismiss(b.id)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = banner != null && s.showNotificationsBanner,
            enter = slideInVertically(tween(350)) { -it } + fadeIn(),
            exit = slideOutVertically(tween(250)) { -it } + fadeOut(),
        ) {
            val b = banner ?: return@AnimatedVisibility
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (ng.isDark) Color(0xF00E1320) else Color(0xF5FFFFFF))
                    .border(1.dp, ng.glassBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(ng.accentBrush), contentAlignment = Alignment.Center) {
                    Icon(b.icon ?: Icons.Filled.Info, null, tint = Color(0xFF020617), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(b.title, style = NgType.label.copy(color = ng.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    b.body?.let { Text(it, style = NgType.caption.copy(color = ng.textSecondary), maxLines = 2) }
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.Close, "Dismiss", tint = ng.textTertiary, modifier = Modifier.size(22.dp).pressable { Notifier.dismiss(b.id) })
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    val ng = LocalNg.current
    var go by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val progress by animateFloatAsState(if (go) 1f else 0f, tween(1300, easing = FastOutSlowInEasing), label = "splash")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                com.nextgen.caros.ui.components.Speedometer(
                    speed = (progress * 88).toDouble(), unitLabel = "", maxSpeed = 100f, overspeed = false,
                    modifier = Modifier.fillMaxSize(), numberSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text("NextGen", style = NgType.display.copy(color = ng.textPrimary.copy(alpha = progress)))
            Text("CAR OS", style = NgType.label.copy(color = ng.accent.copy(alpha = progress), letterSpacing = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)))
            Spacer(Modifier.height(28.dp))
            Box(Modifier.width(180.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(ng.glass)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(ng.accentBrush))
            }
        }
    }
}
