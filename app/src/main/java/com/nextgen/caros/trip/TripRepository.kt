package com.nextgen.caros.trip

import android.os.SystemClock
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.location.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripStats(
    val distanceM: Double = 0.0,
    val movingSeconds: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    val altitudeM: Double? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val averageMps: Double? get() = if (movingSeconds > 30) distanceM / movingSeconds else null
}

/** Trip computer driven purely by GPS fixes (no vehicle data is assumed). */
class TripRepository(scope: CoroutineScope, location: LocationRepository) {
    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private var last: LatLon? = null
    private var lastAt = 0L

    init {
        scope.launch {
            location.state.collect { st ->
                val p = st.latLon ?: return@collect
                if (st.isStale) return@collect
                val now = SystemClock.elapsedRealtime()
                val acc = st.location?.accuracy ?: 50f
                val speed = st.speedMps ?: 0.0
                val prev = last
                var s = _stats.value
                if (prev != null && acc < 30 && now > lastAt) {
                    val d = Geo.distance(prev, p)
                    val dt = (now - lastAt) / 1000.0
                    // Ignore jitter while stationary and implausible jumps.
                    if (speed > 1.0 && d < 100 * dt) s = s.copy(distanceM = s.distanceM + d, movingSeconds = s.movingSeconds + dt)
                }
                s = s.copy(
                    maxSpeedMps = maxOf(s.maxSpeedMps, if (acc < 30) speed else 0.0),
                    altitudeM = st.location?.takeIf { it.hasAltitude() }?.altitude ?: s.altitudeM,
                )
                _stats.value = s
                last = p; lastAt = now
            }
        }
    }

    fun reset() { _stats.value = TripStats(); last = null }
}
