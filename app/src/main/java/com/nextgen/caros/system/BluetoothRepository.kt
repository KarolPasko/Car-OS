package com.nextgen.caros.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BluetoothState(
    val supported: Boolean = false,
    val permissionGranted: Boolean = false,
    val enabled: Boolean = false,
    val connectedDevices: List<String> = emptyList(),
)

/**
 * Read-only Bluetooth status: whether it's on and which audio/phone devices are connected.
 * Pairing and connecting are left to Android's own Bluetooth settings (opened from the UI).
 */
class BluetoothRepository(private val context: Context, scope: CoroutineScope) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val _state = MutableStateFlow(BluetoothState(supported = adapter != null))
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private var a2dp: BluetoothProfile? = null
    private var headset: BluetoothProfile? = null
    private var registered = false

    fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }

    fun start() {
        val adapter = adapter ?: return
        if (!hasPermission()) { _state.value = _state.value.copy(permissionGranted = false); return }
        if (!registered) {
            registered = true
            val f = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            }
            ContextCompat.registerReceiver(context, receiver, f, ContextCompat.RECEIVER_EXPORTED)
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.A2DP) a2dp = proxy else headset = proxy
                    refresh()
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.A2DP) a2dp = null else headset = null
                    refresh()
                }
            }
            runCatching {
                adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
                adapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
            }
        }
        refresh()
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        val adapter = adapter ?: return
        val granted = hasPermission()
        val names = if (granted) runCatching {
            (a2dp?.connectedDevices.orEmpty() + headset?.connectedDevices.orEmpty())
                .distinctBy { it.address }.map { it.name ?: it.address }
        }.getOrDefault(emptyList()) else emptyList()
        _state.value = BluetoothState(
            supported = true,
            permissionGranted = granted,
            enabled = runCatching { adapter.isEnabled }.getOrDefault(false),
            connectedDevices = names,
        )
    }

    fun settingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
