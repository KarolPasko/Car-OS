package com.nextgen.caros.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationState(
    val location: Location? = null,
    /** Metres per second; null when no fix or speed is unknown. */
    val speedMps: Double? = null,
    /** Degrees, only meaningful while moving. */
    val bearing: Float? = null,
    val hasGpsFix: Boolean = false,
    val permissionGranted: Boolean = false,
    val providerEnabled: Boolean = true,
    val lastFixElapsedMs: Long = 0L,
) {
    val latLon: LatLon? get() = location?.let { LatLon(it.latitude, it.longitude) }
    val isStale: Boolean get() = location == null || SystemClock.elapsedRealtime() - lastFixElapsedMs > 10_000
}

/**
 * Real GPS via the platform [LocationManager]. Deliberately does not depend on Google Play
 * Services, which many aftermarket head units do not ship.
 */
class LocationRepository(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _state = MutableStateFlow(LocationState(permissionGranted = hasPermission()))
    val state: StateFlow<LocationState> = _state.asStateFlow()

    private var started = false
    private var lastGpsFixAt = 0L
    private var previous: Location? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = onNewLocation(loc)
        override fun onProviderEnabled(provider: String) { refreshProviderState() }
        override fun onProviderDisabled(provider: String) { refreshProviderState() }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** Safe to call repeatedly (e.g. after the permission is granted). */
    @SuppressLint("MissingPermission")
    fun start() {
        val granted = hasPermission()
        _state.value = _state.value.copy(permissionGranted = granted)
        if (!granted || started) return
        started = true
        try {
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, listener, Looper.getMainLooper())
            }
            val last = listOfNotNull(
                runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
                runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull(),
            ).maxByOrNull { it.time }
            if (last != null) {
                _state.value = _state.value.copy(location = last, lastFixElapsedMs = SystemClock.elapsedRealtime() - 60_000)
            }
        } catch (e: SecurityException) {
            started = false
        }
        refreshProviderState()
    }

    fun stop() {
        if (!started) return
        lm.removeUpdates(listener)
        started = false
    }

    private fun refreshProviderState() {
        val enabled = runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        _state.value = _state.value.copy(providerEnabled = enabled)
    }

    private fun onNewLocation(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        val isGps = loc.provider == LocationManager.GPS_PROVIDER
        if (isGps) lastGpsFixAt = now
        // Ignore coarse network fixes while GPS is healthy.
        if (!isGps && now - lastGpsFixAt < 5_000) return

        val prev = previous
        val speed: Double? = when {
            loc.hasSpeed() -> loc.speed.toDouble()
            prev != null && loc.time > prev.time -> {
                val dt = (loc.time - prev.time) / 1000.0
                if (dt in 0.5..10.0) prev.distanceTo(loc) / dt else null
            }
            else -> null
        }
        val bearing: Float? = when {
            loc.hasBearing() && (speed ?: 0.0) > 1.0 -> loc.bearing
            prev != null && prev.distanceTo(loc) > 3 -> prev.bearingTo(loc).let { (it + 360) % 360 }
            else -> _state.value.bearing
        }
        previous = loc
        _state.value = _state.value.copy(
            location = loc,
            speedMps = speed?.takeIf { isGps || it < 70 },
            bearing = bearing,
            hasGpsFix = isGps,
            lastFixElapsedMs = now,
            permissionGranted = true,
        )
    }
}
