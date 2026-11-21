package com.nextgen.caros.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

data class InstalledApp(
    val id: String,
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap?,
)

/** A user-defined shortcut: any URI Android can open (web page, geo:, tel:, spotify:, app deep link). */
@Serializable
data class CustomShortcut(
    val id: String,
    val label: String,
    val uri: String,
    val colorIndex: Int = 0,
)

class AppsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val shortcutSerializer = ListSerializer(CustomShortcut.serializer())

    init {
        reload()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = reload()
        }, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { ri ->
                        val ai = ri.activityInfo
                        InstalledApp(
                            id = "${ai.packageName}/${ai.name}",
                            label = ri.loadLabel(pm).toString(),
                            packageName = ai.packageName,
                            activityName = ai.name,
                            icon = runCatching { ri.loadIcon(pm).toBitmap(144).asImageBitmap() }.getOrNull(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = list
            _loading.value = false
        }
    }

    fun launch(app: InstalledApp): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(app.packageName, app.activityName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
    }.isSuccess

    fun appInfo(app: InstalledApp) = runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ------------------------------------------------------------------ custom shortcuts

    fun shortcuts(json: String): List<CustomShortcut> =
        runCatching { Http.json.decodeFromString(shortcutSerializer, json) }.getOrDefault(emptyList())

    fun addShortcut(s: CustomShortcut) {
        val list = shortcuts(settings.current.customShortcuts) + s
        settings.setCustomShortcuts(Http.json.encodeToString(shortcutSerializer, list))
    }

    fun removeShortcut(id: String) {
        val list = shortcuts(settings.current.customShortcuts).filterNot { it.id == id }
        settings.setCustomShortcuts(Http.json.encodeToString(shortcutSerializer, list))
        if (id in settings.current.favourites) settings.toggleFavourite("shortcut:$id")
    }

    fun launchShortcut(s: CustomShortcut): Boolean = runCatching {
        var uri = s.uri.trim()
        if (!uri.contains(":")) uri = "https://$uri"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    // ------------------------------------------------------------------ home launcher

    fun setHomeLauncherEnabled(enabled: Boolean) {
        val cn = ComponentName(context, "com.nextgen.caros.HomeAlias")
        context.packageManager.setComponentEnabledSetting(
            cn,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        settings.setHomeLauncher(enabled)
    }

    fun openHomeSettings() = runCatching {
        context.startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

fun Drawable.toBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width == size) return bitmap
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(c)
    return bmp
}
