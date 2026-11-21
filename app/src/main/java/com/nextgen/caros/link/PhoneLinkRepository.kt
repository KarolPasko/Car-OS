package com.nextgen.caros.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.nextgen.caros.data.PhoneGpsMode
import com.nextgen.caros.data.PhoneLinkRole
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.nav.NavState
import com.nextgen.caros.nav.NavigationManager
import com.nextgen.caros.nav.Place
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

enum class LinkStatus(val label: String) {
    OFF("Off"), NO_PERMISSION("Permission needed"), UNSUPPORTED("No Bluetooth"), BT_OFF("Bluetooth is off"),
    WAITING("Waiting for phone"), NO_DEVICE("Choose your head unit"), CONNECTING("Connecting…"),
    CONNECTED("Connected"), RETRYING("Reconnecting…"),
}

data class PairedDevice(val name: String, val address: String)

data class RemoteNav(val instruction: String, val distance: String, val eta: String)

data class LinkState(
    val role: PhoneLinkRole = PhoneLinkRole.OFF,
    val status: LinkStatus = LinkStatus.OFF,
    val peerName: String? = null,
    val phoneBattery: Int? = null,
    val phoneCharging: Boolean = false,
    val lastPhoneFix: Long = 0L,
    val lastDestination: String? = null,
    val remoteNav: RemoteNav? = null,
    val paired: List<PairedDevice> = emptyList(),
    val error: String? = null,
)

/**
 * "Phone Link": a direct Bluetooth RFCOMM channel between NextGen Car OS on the head unit and
 * NextGen Car OS on the driver's Android phone (same APK, role chosen in Settings).
 *
 * Phone → head unit: GPS position/speed (to fill in when the head unit's GPS is weak),
 * destinations shared from Google Maps or other apps, and battery level.
 * Head unit → phone: the current turn instruction and ETA.
 *
 * Protocol: one JSON object per line, UTF-8. iPhones can't run this app, so they can't link.
 */
class PhoneLinkRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val location: LocationRepository,
    private val navigation: NavigationManager,
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("6f2b8a1e-3c4d-4e5f-9a0b-1c2d3e4f5a6b")
        const val SERVICE_NAME = "NextGen Car OS Link"
        private const val PROTOCOL = 1
    }

    private val adapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var out: OutputStream? = null

    init {
        scope.launch {
            settings.settings.map { it.phoneLinkRole to it.phoneLinkDevice }.distinctUntilChanged().collect { restart() }
        }
        // Head unit → phone: navigation summary.
        scope.launch {
            var lastSent = 0L
            navigation.state.collect { n ->
                if (_state.value.role != PhoneLinkRole.HEAD_UNIT || _state.value.status != LinkStatus.CONNECTED) return@collect
                val now = SystemClock.elapsedRealtime()
                if (now - lastSent < 2000 && n is NavState.Navigating) return@collect
                lastSent = now
                val imperial = settings.current.speedUnit == com.nextgen.caros.data.SpeedUnit.MPH
                send(buildJsonObject {
                    put("t", "nav")
                    if (n is NavState.Navigating) {
                        put("instruction", n.progress.nextStep?.instruction ?: "")
                        put("distance", Geo.formatDistance(n.progress.distanceToNextM, imperial))
                        put("eta", java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(n.progress.etaMillis)))
                    }
                })
            }
        }
    }

    fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Re-evaluates role/permissions; call after permissions change. */
    fun restart() {
        job?.cancel()
        closeAll()
        val role = settings.current.phoneLinkRole
        _state.value = _state.value.copy(role = role, peerName = null, remoteNav = null, error = null, paired = paired())
        val a = adapter
        val status = when {
            role == PhoneLinkRole.OFF -> LinkStatus.OFF
            a == null -> LinkStatus.UNSUPPORTED
            !hasPermission() -> LinkStatus.NO_PERMISSION
            !a.isEnabled -> LinkStatus.BT_OFF
            role == PhoneLinkRole.PHONE && settings.current.phoneLinkDevice.isBlank() -> LinkStatus.NO_DEVICE
            else -> null
        }
        if (status != null) { setStatus(status); updateService(); return }
        job = scope.launch(Dispatchers.IO) {
            if (role == PhoneLinkRole.HEAD_UNIT) hostLoop(a!!) else clientLoop(a!!, settings.current.phoneLinkDevice)
        }
        updateService()
        // Re-check when Bluetooth is switched on/off.
        if (!receiverRegistered) {
            receiverRegistered = true
            ContextCompat.registerReceiver(context, object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) { scope.launch { restart() } }
            }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        }
    }
    private var receiverRegistered = false

    private fun updateService() {
        val wantService = _state.value.role == PhoneLinkRole.PHONE && _state.value.status !in
            setOf(LinkStatus.OFF, LinkStatus.NO_PERMISSION, LinkStatus.UNSUPPORTED, LinkStatus.NO_DEVICE, LinkStatus.BT_OFF)
        PhoneLinkService.setRunning(context, wantService)
    }

    @SuppressLint("MissingPermission")
    fun paired(): List<PairedDevice> = if (!hasPermission()) emptyList() else runCatching {
        adapter?.bondedDevices.orEmpty().map { PairedDevice(it.name ?: it.address, it.address) }.sortedBy { it.name }
    }.getOrDefault(emptyList())

    private fun setStatus(s: LinkStatus, peer: String? = _state.value.peerName, error: String? = null) {
        _state.value = _state.value.copy(status = s, peerName = peer, error = error)
    }

    // ------------------------------------------------------------------ head unit

    @SuppressLint("MissingPermission")
    private suspend fun hostLoop(a: BluetoothAdapter) {
        while (scope.isActive && job?.isActive == true) {
            setStatus(LinkStatus.WAITING, null)
            val s = runCatching {
                val srv = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                server = srv
                try { srv.accept() } finally { runCatching { srv.close() }; server = null }
            }.getOrElse { delay(4000); null } ?: continue
            socket = s
            val peer = runCatching { s.remoteDevice.name }.getOrNull() ?: "Phone"
            setStatus(LinkStatus.CONNECTED, peer)
            sendHello()
            readLoop(s)
            closeAll()
            _state.value = _state.value.copy(phoneBattery = null, peerName = null)
        }
    }

    // ------------------------------------------------------------------ phone

    @SuppressLint("MissingPermission")
    private suspend fun clientLoop(a: BluetoothAdapter, address: String) {
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: run { setStatus(LinkStatus.NO_DEVICE); return }
        var attempt = 0
        while (job?.isActive == true) {
            setStatus(if (attempt == 0) LinkStatus.CONNECTING else LinkStatus.RETRYING, device.name)
            val s = runCatching {
                runCatching { a.cancelDiscovery() }
                device.createRfcommSocketToServiceRecord(SERVICE_UUID).also { it.connect() }
            }.getOrNull()
            if (s == null) {
                attempt++
                setStatus(LinkStatus.RETRYING, device.name, "Head unit not reachable. Is NextGen Car OS open there with Phone Link set to Head unit?")
                delay(if (attempt < 5) 5000 else 15000)
                continue
            }
            attempt = 0
            socket = s
            setStatus(LinkStatus.CONNECTED, device.name)
            sendHello()
            val sender = scope.launch(Dispatchers.IO) { phoneSenderLoop() }
            readLoop(s)
            sender.cancel()
            closeAll()
            delay(3000)
        }
    }

    private suspend fun phoneSenderLoop() {
        var lastBattery = 0L
        var lastFixSent = 0L
        while (true) {
            val st = location.state.value
            val loc = st.location
            if (settings.current.sharePhoneLocation && loc != null && st.source != "phone" && !st.isStale && loc.time != lastFixSent) {
                lastFixSent = loc.time
                send(buildJsonObject {
                    put("t", "loc"); put("lat", loc.latitude); put("lon", loc.longitude)
                    put("acc", loc.accuracy.toDouble()); put("time", loc.time)
                    st.speedMps?.let { put("spd", it) }
                    st.bearing?.let { put("brg", it.toDouble()) }
                    if (loc.hasAltitude()) put("alt", loc.altitude)
                })
            }
            if (SystemClock.elapsedRealtime() - lastBattery > 60_000) {
                lastBattery = SystemClock.elapsedRealtime()
                batteryLevel()?.let { (lvl, chg) -> send(buildJsonObject { put("t", "bat"); put("level", lvl); put("charging", chg) }) }
            }
            delay(1000)
        }
    }

    private fun batteryLevel(): Pair<Int, Boolean>? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return null
        return (level * 100 / scale) to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
    }

    /** Phone role: send a destination to the head unit. */
    fun sendDestination(p: Place): Boolean = send(buildJsonObject {
        put("t", "dest"); put("name", p.name); put("address", p.address); put("lat", p.lat); put("lon", p.lon)
    })

    // ------------------------------------------------------------------ shared

    private fun sendHello() = send(buildJsonObject {
        put("t", "hello"); put("v", PROTOCOL); put("role", settings.current.phoneLinkRole.name); put("model", Build.MODEL)
    })

    @Synchronized
    private fun send(o: JsonObject): Boolean {
        val stream = out ?: socket?.outputStream?.also { out = it } ?: return false
        return runCatching { stream.write((o.toString() + "\n").toByteArray()); stream.flush() }.isSuccess
    }

    private suspend fun readLoop(s: BluetoothSocket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
        while (true) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: break
            if (line.length > 64_000) continue
            val o = runCatching { Http.json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
            withContext(Dispatchers.Main) { handle(o) }
        }
    }

    private fun handle(o: JsonObject) {
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun dbl(k: String) = (o[k] as? JsonPrimitive)?.doubleOrNull
        when (str("t")) {
            "hello" -> {}
            "loc" -> {
                if (_state.value.role != PhoneLinkRole.HEAD_UNIT) return
                val mode = settings.current.phoneGpsMode
                val use = mode == PhoneGpsMode.ALWAYS || (mode == PhoneGpsMode.FALLBACK && !location.hasFreshOwnGps())
                _state.value = _state.value.copy(lastPhoneFix = SystemClock.elapsedRealtime())
                if (!use) return
                val lat = dbl("lat") ?: return; val lon = dbl("lon") ?: return
                location.injectExternal(Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = lat; longitude = lon
                    accuracy = (dbl("acc") ?: 20.0).toFloat()
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    dbl("spd")?.let { speed = it.toFloat() }
                    dbl("brg")?.let { bearing = it.toFloat() }
                    dbl("alt")?.let { altitude = it }
                })
            }
            "bat" -> _state.value = _state.value.copy(
                phoneBattery = (o["level"] as? JsonPrimitive)?.intOrNull,
                phoneCharging = (o["charging"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
            "dest" -> {
                if (_state.value.role != PhoneLinkRole.HEAD_UNIT) return
                val lat = dbl("lat") ?: return; val lon = dbl("lon") ?: return
                val p = Place(str("name") ?: "Shared destination", str("address") ?: "", lat, lon)
                _state.value = _state.value.copy(lastDestination = p.name)
                navigation.preview(p)
                UiEvents.request(UiEvents.Target.MAPS)
                com.nextgen.caros.ui.shell.Notifier.show("Destination from ${_state.value.peerName ?: "phone"}", p.name)
            }
            "nav" -> if (_state.value.role == PhoneLinkRole.PHONE) {
                val ins = str("instruction")
                _state.value = _state.value.copy(remoteNav = if (ins.isNullOrBlank()) null else RemoteNav(ins, str("distance") ?: "", str("eta") ?: ""))
            }
        }
    }

    private fun closeAll() {
        runCatching { server?.close() }; server = null
        runCatching { socket?.close() }; socket = null
        out = null
    }
}
