package com.nextgen.caros.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nextgen.caros.appContainer
import com.nextgen.caros.data.PhoneGpsMode
import com.nextgen.caros.data.PhoneLinkRole
import com.nextgen.caros.link.LinkStatus
import com.nextgen.caros.nav.GeocodingService
import com.nextgen.caros.ui.components.IconBadge
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.shell.Notifier
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType
import kotlinx.coroutines.launch

@Composable
fun PhoneLinkSettings() {
    val ng = LocalNg.current
    val ctx = LocalContext.current
    val s by appContainer.settings.settings.collectAsState()
    val link by appContainer.phoneLink.state.collectAsState()
    val perms = rememberPermissions()
    val repo = appContainer.settings
    val scope = rememberCoroutineScope()

    SettingGroup(
        "Phone Link",
        footer = "Install this same NextGen Car OS APK on your Android phone, pair the phone with the head unit in Android's " +
            "Bluetooth settings, then set this device to \"Head unit\" and the phone to \"Phone\". iPhones can't run Android apps, so they can't use Phone Link.",
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val ok = link.status == LinkStatus.CONNECTED
            Box(Modifier.size(54.dp).clip(CircleShape).background(if (ok) Color(0xFF30D158) else ng.glassStrong), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhoneIphone, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (ok) "Connected to ${link.peerName ?: "device"}" else link.status.label,
                    style = NgType.headline.copy(color = ng.textPrimary),
                )
                val details = buildList {
                    link.phoneBattery?.let { add("Phone battery $it%${if (link.phoneCharging) " (charging)" else ""}") }
                    if (link.lastPhoneFix > 0 && android.os.SystemClock.elapsedRealtime() - link.lastPhoneFix < 10_000) add("receiving phone GPS")
                    link.lastDestination?.let { add("last destination: $it") }
                    link.error?.let { add(it) }
                }
                if (details.isNotEmpty()) Text(details.joinToString(" · "), style = NgType.caption.copy(color = ng.textSecondary))
            }
        }
        ChoiceRow("This device is the…", null, PhoneLinkRole.entries.map { it to it.label }, s.phoneLinkRole, repo::setPhoneLinkRole)
        perms.firstOrNull { it.id == "bluetooth" && it.status == PermStatus.DENIED }?.let { p ->
            ActionRow("Bluetooth permission", "Needed for Phone Link", "Allow", primary = true) { p.action?.invoke() }
        }
        if (link.status == LinkStatus.BT_OFF) ActionRow("Bluetooth is off", null, "Settings") {
            runCatching { ctx.startActivity(appContainer.bluetooth.settingsIntent()) }
        }
    }

    when (s.phoneLinkRole) {
        PhoneLinkRole.HEAD_UNIT -> SettingGroup("On the head unit", footer = "Destinations shared from your phone (Google Maps → Share → \"Send to car\") open here as a route preview.") {
            ChoiceRow("Use the phone's GPS", PhoneGpsMode.entries.first { it == s.phoneGpsMode }.description,
                PhoneGpsMode.entries.map { it to it.label }, s.phoneGpsMode, repo::setPhoneGpsMode)
        }
        PhoneLinkRole.PHONE -> {
            SettingGroup("Your head unit", footer = "Only devices already paired in Android's Bluetooth settings are listed.") {
                if (link.paired.isEmpty()) InfoRow("No paired devices", "Pair the head unit first, then come back here.")
                link.paired.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().pressable(pressedScale = 0.99f) { repo.setPhoneLinkDevice(d.address) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(Icons.Filled.Smartphone, Color(0xFF0A84FF))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = NgType.body.copy(color = ng.textPrimary))
                            Text(d.address, style = NgType.caption.copy(color = ng.textTertiary))
                        }
                        if (d.address == s.phoneLinkDevice) Icon(Icons.Filled.Check, null, tint = ng.accent)
                    }
                }
            }
            SettingGroup("Sharing") {
                SwitchRow("Share location", "Sends this phone's GPS position and speed to the head unit every second", s.sharePhoneLocation, repo::setSharePhoneLocation)
                link.remoteNav?.let { InfoRow("Car navigation: ${it.distance}", "${it.instruction} · arrive ${it.eta}", ng.accent) }
                TextFieldRow(
                    "Send a destination to the car",
                    "Or use Share → \"Send to car\" in Google Maps or any app",
                    "", "Address or place", button = "Send", onSave = { q ->
                    scope.launch {
                        val p = runCatching { GeocodingService.search(q, appContainer.location.state.value.latLon).firstOrNull() }.getOrNull()
                        when {
                            p == null -> Notifier.show("Place not found", q)
                            appContainer.phoneLink.sendDestination(p) -> Notifier.show("Sent to car", p.name)
                            else -> Notifier.show("Not connected to the head unit")
                        }
                    }
                })
            }
        }
        PhoneLinkRole.OFF -> {}
    }
    @Suppress("UNUSED_EXPRESSION") Arrangement.Center
}
