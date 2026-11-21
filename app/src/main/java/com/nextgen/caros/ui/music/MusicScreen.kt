package com.nextgen.caros.ui.music

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextgen.caros.appContainer
import com.nextgen.caros.media.NowPlaying
import com.nextgen.caros.ui.components.Chip
import com.nextgen.caros.ui.components.EmptyHint
import com.nextgen.caros.ui.components.GlassIconButton
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.MusicVisualizer
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.components.SectionLabel
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.home.AlbumArt
import com.nextgen.caros.ui.home.PlaybackProgress
import com.nextgen.caros.ui.home.fmtMs
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.shell.ShellNav
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import kotlinx.coroutines.delay

@Composable
fun MusicScreen(nav: ShellNav) {
    Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        NowPlayingPanel(Modifier.weight(1.25f).fillMaxHeight(), nav)
        SpotifyPanel(Modifier.weight(1f).fillMaxHeight(), nav)
    }
}

@Composable
private fun NowPlayingPanel(modifier: Modifier, nav: ShellNav) {
    val ng = LocalNg.current
    val media by appContainer.media.state.collectAsState()
    val s by appContainer.settings.settings.collectAsState()
    val ctx = LocalContext.current
    val np = media.nowPlaying
    val m = appContainer.media

    val isSpotify = np?.packageName == com.nextgen.caros.spotify.SpotifyRepository.SPOTIFY_PACKAGE
    val tint by androidx.compose.animation.animateColorAsState(
        rememberArtColor(np?.art, np?.artUri, Color(0xFF1E1E1E)), androidx.compose.animation.core.tween(800), label = "musicTint",
    )
    GlassPanel(modifier, padding = 22.dp) {
        // Spotify-style artwork-tinted backdrop
        if (np != null) Box(Modifier.matchParentSize().background(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tint.copy(alpha = 0.85f), SpotifyBlack.copy(alpha = 0.6f)))
        ))
        // Ambient blurred artwork glow (Android 12+ supports blur natively)
        if (np?.art != null && Build.VERSION.SDK_INT >= 31) {
            Box(Modifier.fillMaxSize().blur(60.dp).background(Color.Transparent)) {
                AlbumArt(np, Modifier.fillMaxSize(), corner = 0)
            }
            Box(Modifier.fillMaxSize().background(ng.background.copy(alpha = 0.55f)))
        }
        Column(Modifier.fillMaxSize()) {
            if (!media.hasAccess) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ng.accent.copy(alpha = 0.14f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = ng.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Allow media access to show what's playing in Spotify and other apps. Buttons already work without it.",
                        style = NgType.caption.copy(color = ng.textPrimary), modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton("Allow", primary = true) { runCatching { ctx.startActivity(m.notificationAccessIntent()) } }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    np?.title to np?.artist,
                    transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(200)) },
                    label = "art",
                ) { _ -> AlbumArt(np, Modifier.fillMaxHeight(0.92f), corner = 26) }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel(if (isSpotify) "Playing on Spotify" else np?.appName ?: "Not playing")
                    Spacer(Modifier.height(6.dp))
                    Text(np?.title ?: "Nothing playing", style = NgType.title.copy(color = ng.textPrimary, fontSize = 28.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(np?.artist ?: "Open Spotify or any music app", style = NgType.headline.copy(color = ng.accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!np?.album.isNullOrBlank()) Text(np!!.album, style = NgType.label.copy(color = ng.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    if (s.showVisualiser) MusicVisualizer(np?.isPlaying == true, Modifier.fillMaxWidth().height(46.dp), bars = 30)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (np != null) {
                if (np.canSeek && np.durationMs > 0) SeekBar(np) else PlaybackProgress(np)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Filled.SkipPrevious, "Previous", size = 68.dp) { m.previous() }
                Spacer(Modifier.width(28.dp))
                if (isSpotify) Box(
                    Modifier.size(86.dp).pressable { m.playPause() }.clip(androidx.compose.foundation.shape.CircleShape).background(SpotifyGreen),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(44.dp)) }
                else GlassIconButton(if (np?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", size = 86.dp, accent = true) { m.playPause() }
                Spacer(Modifier.width(28.dp))
                GlassIconButton(Icons.Filled.SkipNext, "Next", size = 68.dp) { m.next() }
                Spacer(Modifier.width(28.dp))
                GlassIconButton(Icons.Filled.OpenInNew, "Open player app", size = 56.dp) {
                    if (!m.openActiveApp()) Notifier.show("No music app found", "Install Spotify or choose a player in Settings")
                }
            }
            if (media.sessions.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(media.sessions) { pkg ->
                        Chip(appLabel(pkg), selected = pkg == np?.packageName) { m.selectSession(pkg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun appLabel(pkg: String): String {
    val ctx = LocalContext.current
    return remember(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
}

@Composable
private fun SeekBar(np: NowPlaying) {
    val ng = LocalNg.current
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(np) {
        while (true) { if (!dragging) value = np.positionNow().toFloat(); delay(500) }
    }
    Column {
        Slider(
            value = value.coerceIn(0f, np.durationMs.toFloat()),
            onValueChange = { dragging = true; value = it },
            onValueChangeFinished = { appContainer.media.seekTo(value.toLong()); dragging = false },
            valueRange = 0f..np.durationMs.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ng.accent, inactiveTrackColor = ng.textPrimary.copy(alpha = 0.15f)),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtMs(value.toLong()), style = NgType.caption.copy(color = ng.textTertiary))
            Spacer(Modifier.weight(1f))
            Text("-" + fmtMs(np.durationMs - value.toLong()), style = NgType.caption.copy(color = ng.textTertiary))
        }
    }
}

@Composable
private fun SpotifyPanel(modifier: Modifier, nav: ShellNav) {
    val ng = LocalNg.current
    val sp by appContainer.spotify.state.collectAsState()
    val ctx = LocalContext.current
    val spotify = appContainer.spotify
    val spotifyGreen = Color(0xFF1DB954)

    LaunchedEffect(sp.message) {
        sp.message?.let { Notifier.show("Spotify", it, Icons.Filled.LibraryMusic); spotify.clearMessage() }
    }

    GlassPanel(modifier, padding = 18.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(spotifyGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LibraryMusic, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Spotify", style = NgType.headline.copy(color = ng.textPrimary))
                    Text(
                        when {
                            sp.connected && sp.displayName != null -> "${sp.displayName} · ${if (sp.premium) "Premium" else "Free"}"
                            sp.connected -> "Connected"
                            else -> "Not connected"
                        },
                        style = NgType.caption.copy(color = ng.textSecondary),
                    )
                }
                if (sp.connected) {
                    GlassIconButton(Icons.Filled.Refresh, "Refresh", size = 44.dp) { spotify.loadProfile() }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Icons.Filled.Logout, "Disconnect", size = 44.dp) { spotify.logout() }
                }
            }
            Spacer(Modifier.height(14.dp))
            when {
                !sp.clientIdConfigured -> EmptyHint(
                    Icons.Filled.Settings, "Set up Spotify",
                    "Spotify library access needs your own free Spotify developer Client ID (see README). " +
                        "Playback controls on the left already work with the Spotify app.",
                    Modifier.fillMaxWidth(),
                ) { PillButton("Open music settings", primary = true) { nav.openSettings(SettingsSection.MUSIC) } }

                !sp.connected -> EmptyHint(
                    Icons.Filled.Login, "Connect your account",
                    "You'll sign in on Spotify's own page. NextGen Car OS never sees your password.",
                    Modifier.fillMaxWidth(),
                ) { PillButton("Connect Spotify", icon = Icons.Filled.Login, primary = true) { spotify.login(ctx) } }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Liked Songs", icon = Icons.Filled.Favorite) { spotify.openInSpotify("spotify:collection") }
                        Chip("Open Spotify", icon = Icons.Filled.OpenInNew) { spotify.openInSpotify() }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("Your playlists")
                    Spacer(Modifier.height(8.dp))
                    if (sp.playlists.isEmpty()) {
                        Text(if (sp.busy) "Loading…" else "No playlists found.", style = NgType.label.copy(color = ng.textSecondary))
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(128.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(sp.playlists, key = { it.id }) { p ->
                            Column(Modifier.pressable { spotify.play(p.uri); Notifier.show("Playing ${p.name}", if (sp.premium) null else "Opened in the Spotify app", Icons.Filled.QueueMusic) }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(ng.glass)) {
                                    if (p.imageUrl != null) AsyncImage(p.imageUrl, p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Icon(Icons.Filled.QueueMusic, null, tint = ng.textTertiary, modifier = Modifier.align(Alignment.Center).size(40.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(p.name, style = NgType.label.copy(color = ng.textPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${p.tracks} tracks", style = NgType.caption.copy(color = ng.textTertiary))
                            }
                        }
                    }
                }
            }
            if (!sp.spotifyInstalled) {
                Spacer(Modifier.height(8.dp))
                Text("The Spotify app isn't installed on this head unit.", style = NgType.caption.copy(color = ng.warning))
            }
        }
    }
}
