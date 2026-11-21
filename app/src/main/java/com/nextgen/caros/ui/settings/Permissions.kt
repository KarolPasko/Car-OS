package com.nextgen.caros.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nextgen.caros.appContainer

enum class PermStatus { GRANTED, DENIED, NOT_NEEDED, NOT_USED }

data class PermissionInfo(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val why: String,
    val status: PermStatus,
    val action: (() -> Unit)?,
)

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/** Recomposes when the activity resumes, so statuses update after returning from system settings. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return tick
}

/**
 * Every permission the app uses, why, and how to grant it. Runtime permissions use the
 * standard Android dialogs; notification-listener access opens the system screen.
 */
@Composable
fun rememberPermissions(): List<PermissionInfo> {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    var localTick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        appContainer.location.start()
        appContainer.bluetooth.start()
        appContainer.phoneLink.restart()
        localTick++
    }
    return remember(tick, localTick) {
        val loc = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
        val notif = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        val bt = Build.VERSION.SDK_INT < 31 || granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)
        val media = appContainer.media.hasAccess()
        listOf(
            PermissionInfo(
                "location", "Location (GPS)", Icons.Filled.LocationOn,
                "Shows your position on the map, measures your speed, finds the speed limit of the road you're on, " +
                    "warns about nearby camera locations and provides turn-by-turn guidance. Location is used on this device; " +
                    "only coordinates needed for map tiles, routes, weather and road data are sent to those services.",
                if (loc) PermStatus.GRANTED else PermStatus.DENIED,
                if (loc) null else ({
                    launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }),
            ),
            PermissionInfo(
                "notifications", "Notifications", Icons.Filled.Notifications,
                "Android requires a visible notification while navigation keeps running in the background " +
                    "(for example while you're in Spotify). It shows the next turn.",
                when { Build.VERSION.SDK_INT < 33 -> PermStatus.NOT_NEEDED; notif -> PermStatus.GRANTED; else -> PermStatus.DENIED },
                if (notif) null else ({ launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }),
            ),
            PermissionInfo(
                "bluetooth", "Nearby devices (Bluetooth)", Icons.Filled.Bluetooth,
                "Shows which phone or audio device is connected, and runs Phone Link – the Bluetooth connection to " +
                    "NextGen Car OS on your Android phone (GPS, destinations, battery). Pairing stays in Android's Bluetooth settings.",
                when { Build.VERSION.SDK_INT < 31 -> PermStatus.NOT_NEEDED; bt -> PermStatus.GRANTED; else -> PermStatus.DENIED },
                if (bt) null else ({ launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }),
            ),
            PermissionInfo(
                "media", "Media access (notification access)", Icons.Filled.MusicNote,
                "Android only lets an app see and control other apps' media sessions (track, artwork, progress, " +
                    "play/pause, skip) if you grant notification access. It is used only for media sessions; notification " +
                    "contents are not read or stored. Without it, play/pause/skip still work via media buttons.",
                if (media) PermStatus.GRANTED else PermStatus.DENIED,
                { runCatching { ctx.startActivity(appContainer.media.notificationAccessIntent()) }; Unit },
            ),
            PermissionInfo(
                "internet", "Internet", Icons.Filled.Public,
                "Needed for map tiles, place search, routing, weather, speed-limit data, camera data and Spotify. " +
                    "Granted automatically at install.",
                PermStatus.NOT_NEEDED, null,
            ),
            PermissionInfo(
                "accessibility", "Accessibility service", Icons.Filled.Accessibility,
                "Not used. Everything here works through Android's normal APIs (media sessions, launcher intents, " +
                    "location), so NextGen Car OS never asks to control your screen or other apps.",
                PermStatus.NOT_USED, null,
            ),
        )
    }
}

/** Fallback when a permission was permanently denied ("Don't ask again"). */
fun openAppSettings(ctx: Context) = runCatching {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
