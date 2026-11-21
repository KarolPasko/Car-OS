package com.nextgen.caros.system

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemStatus(
    val online: Boolean = false,
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val volume: Float = 0.5f,
    /** App window brightness 0..1, or null to follow the system setting. */
    val brightness: Float? = null,
)

/** Connectivity, media volume and in-app brightness for the control tiles. */
class SystemStatusRepository(private val context: Context, scope: CoroutineScope) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _state = MutableStateFlow(SystemStatus())
    val state: StateFlow<SystemStatus> = _state.asStateFlow()

    init {
        refresh()
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh()
                override fun onLost(network: Network) = refresh()
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            })
        }
    }

    fun refresh() {
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        _state.value = _state.value.copy(
            online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max,
        )
    }

    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).toInt().coerceIn(0, max), 0) }
        refresh()
    }

    /** Applied to the activity window by MainActivity (no WRITE_SETTINGS permission needed). */
    fun setBrightness(f: Float?) { _state.value = _state.value.copy(brightness = f?.coerceIn(0.05f, 1f)) }

    fun wifiSettingsIntent(): Intent =
        (if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
