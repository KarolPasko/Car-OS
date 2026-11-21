package com.nextgen.caros.ui.apps

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextgen.caros.appContainer
import com.nextgen.caros.apps.CustomShortcut
import com.nextgen.caros.apps.InstalledApp
import com.nextgen.caros.ui.components.Chip
import com.nextgen.caros.ui.components.EmptyHint
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.components.SectionLabel
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.settings.SettingsSection
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.shell.Screen
import com.nextgen.caros.ui.shell.ShellNav
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import java.util.UUID

private enum class Tab(val label: String) { FAVOURITES("Favourites"), ALL("All apps"), SHORTCUTS("Shortcuts") }

private val shortcutColors = listOf(
    listOf(0xFF22D3EE, 0xFF3B82F6), listOf(0xFFA855F7, 0xFF6366F1), listOf(0xFF34D399, 0xFF0EA5E9),
    listOf(0xFFF59E0B, 0xFFEF4444), listOf(0xFFEC4899, 0xFF8B5CF6), listOf(0xFF64748B, 0xFF334155),
)

/** One launcher tile – an installed app, a built-in feature or a custom shortcut. */
private data class Tile(
    val key: String,
    val label: String,
    val app: InstalledApp? = null,
    val vector: ImageVector? = null,
    val colors: List<Long> = shortcutColors[0],
    val shortcut: CustomShortcut? = null,
    val action: () -> Unit,
)

@Composable
fun LauncherScreen(nav: ShellNav) {
    val ng = LocalNg.current
    val ctx = LocalContext.current
    val repo = appContainer.apps
    val apps by repo.apps.collectAsState()
    val loading by repo.loading.collectAsState()
    val s by appContainer.settings.settings.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.FAVOURITES) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    val shortcuts = repo.shortcuts(s.customShortcuts)

    fun launchApp(a: InstalledApp) { if (!repo.launch(a)) Notifier.show("Couldn't open ${a.label}") }

    val builtIns = listOf(
        Tile("builtin:maps", "Navigation", vector = Icons.Filled.Map, colors = shortcutColors[0]) { nav.go(Screen.MAPS) },
        Tile("builtin:music", "Music", vector = Icons.Filled.MusicNote, colors = shortcutColors[1]) { nav.go(Screen.MUSIC) },
        Tile("builtin:spotify", "Spotify", vector = Icons.Filled.LibraryMusic, colors = listOf(0xFF1DB954, 0xFF158A3E)) {
            if (!appContainer.spotify.openInSpotify()) nav.go(Screen.MUSIC)
        },
        Tile("builtin:cameras", "Speed cameras", vector = Icons.Filled.CameraAlt, colors = shortcutColors[3]) { nav.openSettings(SettingsSection.CAMERAS) },
        Tile("builtin:phone", "Phone", vector = Icons.Filled.Phone, colors = shortcutColors[2]) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Notifier.show("No dialer app on this device") }
        },
        Tile("builtin:settings", "Settings", vector = Icons.Filled.Settings, colors = shortcutColors[5]) { nav.go(Screen.SETTINGS) },
    )
    val appTiles = apps.map { a -> Tile(a.id, a.label, app = a) { launchApp(a) } }
    val shortcutTiles = shortcuts.map { sc ->
        Tile("shortcut:${sc.id}", sc.label, vector = Icons.Filled.Link, colors = shortcutColors[sc.colorIndex % shortcutColors.size], shortcut = sc) {
            if (!repo.launchShortcut(sc)) Notifier.show("Nothing can open ${sc.uri}")
        }
    }
    val all = builtIns + appTiles + shortcutTiles
    val favTiles = s.favourites.mapNotNull { id -> all.firstOrNull { it.key == id } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                Chip(t.label, selected = tab == t, modifier = Modifier.padding(end = 8.dp)) { tab = t }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.width(280.dp).clip(RoundedCornerShape(50)).background(ng.glass).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = ng.textTertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box {
                    if (query.isEmpty()) Text("Search apps", style = NgType.body.copy(color = ng.textTertiary))
                    BasicTextField(query, { query = it; if (it.isNotBlank()) tab = Tab.ALL }, singleLine = true,
                        textStyle = NgType.body.copy(color = ng.textPrimary), cursorBrush = SolidColor(ng.accent))
                }
            }
            Spacer(Modifier.width(10.dp))
            PillButton("Shortcut", icon = Icons.Filled.Add) { showAdd = true }
        }
        Spacer(Modifier.height(14.dp))

        AnimatedContent(tab, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "tab") { t ->
            val tiles = when (t) {
                Tab.FAVOURITES -> if (favTiles.isEmpty()) builtIns else favTiles
                Tab.ALL -> (builtIns + appTiles).filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                Tab.SHORTCUTS -> shortcutTiles
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (t == Tab.FAVOURITES && favTiles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel("Long-press any app to add it to favourites")
                }
                if (t == Tab.ALL && loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Loading apps…", style = NgType.label.copy(color = ng.textSecondary))
                }
                if (t == Tab.SHORTCUTS && tiles.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(Icons.Filled.Link, "No shortcuts yet",
                        "Create shortcuts to websites, phone numbers, map locations or Spotify playlists.") {
                        PillButton("Add shortcut", icon = Icons.Filled.Add, primary = true) { showAdd = true }
                    }
                }
                items(tiles, key = { it.key }) { tile ->
                    LauncherTile(tile, isFavourite = tile.key in s.favourites)
                }
            }
        }
    }

    if (showAdd) AddShortcutDialog(onDismiss = { showAdd = false }) { sc ->
        repo.addShortcut(sc); showAdd = false; tab = Tab.SHORTCUTS
        Notifier.show("Shortcut added", sc.label, Icons.Filled.Link)
    }
}

@Composable
private fun LauncherTile(tile: Tile, isFavourite: Boolean) {
    val ng = LocalNg.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().pressable(onLongClick = { menu = true }, onClick = tile.action).padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
                if (tile.app?.icon != null) {
                    Image(tile.app.icon, tile.label, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)))
                } else {
                    Box(
                        Modifier.size(80.dp).clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(tile.colors.map { Color(it) })),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tile.vector ?: Icons.Filled.Apps, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
                if (isFavourite) Icon(
                    Icons.Filled.Star, null, tint = ng.warning,
                    modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tile.label, style = NgType.label.copy(color = ng.textPrimary, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isFavourite) "Remove from favourites" else "Add to favourites") },
                leadingIcon = { Icon(if (isFavourite) Icons.Filled.StarBorder else Icons.Filled.Star, null) },
                onClick = { appContainer.settings.toggleFavourite(tile.key); menu = false },
            )
            tile.app?.let { app ->
                DropdownMenuItem(
                    text = { Text("App info") }, leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = { appContainer.apps.appInfo(app); menu = false },
                )
            }
            tile.shortcut?.let { sc ->
                DropdownMenuItem(
                    text = { Text("Delete shortcut") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { appContainer.apps.removeShortcut(sc.id); menu = false },
                )
            }
        }
    }
}

@Composable
private fun AddShortcutDialog(onDismiss: () -> Unit, onAdd: (CustomShortcut) -> Unit) {
    val ng = LocalNg.current
    var label by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0) }
    val templates = listOf("Website" to "https://", "Phone" to "tel:", "Location" to "geo:0,0?q=", "Spotify" to "spotify:playlist:")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New shortcut") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    templates.forEach { (n, prefix) -> Chip(n, selected = uri.startsWith(prefix) && prefix.isNotEmpty()) { uri = prefix } }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(uri, { uri = it }, label = { Text("Link or URI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    shortcutColors.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(if (i == color) 40.dp else 34.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(c.map { Color(it) })).pressable { color = i },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Examples: https://www.bbc.co.uk/sounds · tel:+441234567890 · geo:0,0?q=Heathrow", style = NgType.caption.copy(color = ng.textTertiary))
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank() && uri.length > 4, onClick = {
                onAdd(CustomShortcut(UUID.randomUUID().toString(), label.trim(), uri.trim(), color))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
