package com.nextgen.caros.nav

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.Locale

@Serializable
data class SavedPlaces(val home: Place? = null, val work: Place? = null, val recents: List<Place> = emptyList())

/**
 * Owns the route and turn-by-turn state: snapping to the route, distance/ETA, upcoming
 * manoeuvres, automatic re-routing and optional spoken guidance.
 */
class NavigationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<NavState>(NavState.Idle)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val placesFile = File(context.filesDir, "places.json")
    private val _places = MutableStateFlow(SavedPlaces())
    val places: StateFlow<SavedPlaces> = _places.asStateFlow()

    private var routeJob: Job? = null
    private var lastIndex = 0
    private var offRouteCount = 0
    private var lastRerouteAt = 0L
    private val announced = HashMap<Int, MutableSet<Int>>()

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { Http.json.decodeFromString(SavedPlaces.serializer(), placesFile.readText()) }.getOrNull()
            }?.let { _places.value = it }
        }
        scope.launch {
            location.state.collect { st ->
                val s = _state.value
                val p = st.latLon
                if (s is NavState.Navigating && p != null && !st.isStale) update(s, p, st.bearing?.toDouble(), st.location?.accuracy ?: 20f)
            }
        }
    }

    // ------------------------------------------------------------------ places

    private fun savePlaces(p: SavedPlaces) {
        _places.value = p
        scope.launch(Dispatchers.IO) { runCatching { placesFile.writeText(Http.json.encodeToString(SavedPlaces.serializer(), p)) } }
    }
    fun setHome(p: Place) = savePlaces(_places.value.copy(home = p))
    fun setWork(p: Place) = savePlaces(_places.value.copy(work = p))
    fun removeRecent(p: Place) = savePlaces(_places.value.copy(recents = _places.value.recents - p))
    private fun addRecent(p: Place) = savePlaces(
        _places.value.copy(recents = (listOf(p) + _places.value.recents.filterNot { it.lat == p.lat && it.lon == p.lon }).take(12))
    )

    // ------------------------------------------------------------------ routing

    /** Calculates a route and shows the overview; the user then presses Start. */
    fun preview(dest: Place) {
        val from = location.state.value.latLon
        if (from == null) { _state.value = NavState.Error(dest, "Waiting for GPS position…"); return }
        addRecent(dest)
        routeJob?.cancel()
        _state.value = NavState.Routing(dest)
        routeJob = scope.launch {
            runCatching { RoutingService.route(settings.current.routingServer, from, dest) }
                .onSuccess { _state.value = NavState.Preview(it) }
                .onFailure { _state.value = NavState.Error(dest, "Couldn't calculate route: ${it.message ?: "network error"}") }
        }
    }

    fun start() {
        val s = _state.value as? NavState.Preview ?: return
        lastIndex = 0; offRouteCount = 0; announced.clear()
        val p = location.state.value.latLon
        val progress = p?.let { computeProgress(s.route, it) } ?: initialProgress(s.route)
        _state.value = NavState.Navigating(s.route, progress)
        startService()
        speak("Starting route. ${s.route.steps.firstOrNull()?.instruction ?: ""}")
    }

    fun stop() {
        routeJob?.cancel()
        _state.value = NavState.Idle
        stopService()
        tts?.stop()
    }

    fun dismiss() { if (_state.value !is NavState.Navigating) _state.value = NavState.Idle }

    private fun initialProgress(r: Route) = NavProgress(
        r.distanceM, r.durationS, System.currentTimeMillis() + (r.durationS * 1000).toLong(),
        r.steps.getOrNull(1), r.steps.getOrNull(1)?.alongM ?: r.distanceM, r.steps.getOrNull(2), false, null, 0.0,
    )

    private fun computeProgress(route: Route, p: LatLon): NavProgress {
        val pts = route.points
        // Search a window ahead of the last matched segment (and a little behind).
        val from = (lastIndex - 5).coerceAtLeast(0)
        val to = minOf(pts.size - 1, lastIndex + 400)
        var bestI = lastIndex; var bestD = Double.MAX_VALUE; var bestT = 0.0
        for (i in from until to) {
            val (d, t) = Geo.distanceToSegment(p, pts[i], pts[i + 1])
            if (d < bestD) { bestD = d; bestI = i; bestT = t }
        }
        if (bestD > 80) { // lost: global search
            for (i in 0 until pts.size - 1) {
                val (d, t) = Geo.distanceToSegment(p, pts[i], pts[i + 1])
                if (d < bestD) { bestD = d; bestI = i; bestT = t }
            }
        }
        lastIndex = bestI
        val segLen = route.cumulative[minOf(bestI + 1, pts.size - 1)] - route.cumulative[bestI]
        val along = route.cumulative[bestI] + segLen * bestT
        val remaining = (route.distanceM.coerceAtLeast(route.cumulative.last()) - along).coerceAtLeast(0.0)
        val ratio = if (route.distanceM > 0) remaining / route.distanceM else 0.0
        val remainingS = route.durationS * ratio
        val nextIdx = route.steps.indexOfFirst { it.alongM > along + 3 }.takeIf { it >= 0 }
        val next = nextIdx?.let { route.steps[it] }
        return NavProgress(
            distanceRemainingM = remaining,
            durationRemainingS = remainingS,
            etaMillis = System.currentTimeMillis() + (remainingS * 1000).toLong(),
            nextStep = next,
            distanceToNextM = next?.let { it.alongM - along } ?: remaining,
            followingStep = nextIdx?.let { route.steps.getOrNull(it + 1) },
            offRoute = bestD > 45,
            snapped = Geo.interpolate(pts[bestI], pts[minOf(bestI + 1, pts.size - 1)], bestT),
            alongM = along,
        )
    }

    private fun update(s: NavState.Navigating, p: LatLon, bearing: Double?, accuracy: Float) {
        if (Geo.distance(p, s.route.destination.latLon) < 35) {
            _state.value = NavState.Arrived(s.route.destination)
            speak("You have arrived at ${s.route.destination.name}")
            stopService()
            return
        }
        val progress = computeProgress(s.route, p)
        offRouteCount = if (progress.offRoute && accuracy < 40) offRouteCount + 1 else 0
        _state.value = s.copy(progress = progress)
        maybeAnnounce(s.route, progress)
        NavigationService.update(context, progress, settings.current.speedUnit == SpeedUnit.MPH)

        if (offRouteCount >= 3 && !s.rerouting && SystemClock.elapsedRealtime() - lastRerouteAt > 15_000) {
            lastRerouteAt = SystemClock.elapsedRealtime()
            _state.value = s.copy(progress = progress, rerouting = true)
            speak("Recalculating")
            routeJob?.cancel()
            routeJob = scope.launch {
                runCatching { RoutingService.route(settings.current.routingServer, p, s.route.destination, bearing) }
                    .onSuccess { r ->
                        lastIndex = 0; offRouteCount = 0; announced.clear()
                        _state.value = NavState.Navigating(r, computeProgress(r, p))
                    }
                    .onFailure { (_state.value as? NavState.Navigating)?.let { _state.value = it.copy(rerouting = false) } }
            }
        }
    }

    // ------------------------------------------------------------------ voice

    private fun maybeAnnounce(route: Route, pr: NavProgress) {
        if (!settings.current.voiceGuidance) return
        val step = pr.nextStep ?: return
        val idx = route.steps.indexOf(step)
        val speed = location.state.value.speedMps ?: 10.0
        val thresholds = if (speed > 22) listOf(1600, 500, 120) else listOf(500, 150, 40)
        val done = announced.getOrPut(idx) { mutableSetOf() }
        for (t in thresholds) {
            if (pr.distanceToNextM <= t && t !in done) {
                done.addAll(thresholds.filter { it >= t })
                val imperial = settings.current.speedUnit == SpeedUnit.MPH
                val text = if (t == thresholds.last()) step.instruction
                else "In ${spokenDistance(pr.distanceToNextM, imperial)}, ${step.instruction.replaceFirstChar { it.lowercase() }}"
                speak(text)
                break
            }
        }
    }

    private fun spokenDistance(m: Double, imperial: Boolean) = if (imperial) {
        val mi = m / 1609.344
        when {
            mi >= 0.9 -> "${Math.round(mi)} mile${if (Math.round(mi) > 1) "s" else ""}"
            mi >= 0.4 -> "half a mile"
            mi >= 0.2 -> "a quarter of a mile"
            else -> "${(m * 3.28084 / 50).toInt() * 50} feet"
        }
    } else {
        if (m >= 1000) String.format(Locale.US, "%.1f kilometres", m / 1000) else "${(m / 50).toInt() * 50} metres"
    }

    fun speak(text: String) {
        if (!settings.current.voiceGuidance || text.isBlank()) return
        val engine = tts
        if (engine == null) {
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
                }
            }
        } else if (ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav")
        }
    }

    // ------------------------------------------------------------------ service

    private fun startService() {
        val i = Intent(context, NavigationService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }

    private fun stopService() {
        runCatching { context.stopService(Intent(context, NavigationService::class.java)) }
    }
}
