package com.nextgen.caros.speedlimit

import android.os.SystemClock
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

data class RoadWay(
    val id: Long,
    val tags: Map<String, String>,
    val points: List<LatLon>,
) {
    val name: String? get() = tags["name"] ?: tags["ref"]
    val oneway: Boolean get() = tags["oneway"] == "yes" || tags["junction"] == "roundabout" || tags["highway"] == "motorway"
}

sealed interface SpeedLimitState {
    /** Feature disabled in settings. */
    data object Disabled : SpeedLimitState
    /** Waiting for a GPS fix or the first road-data download. */
    data object Searching : SpeedLimitState
    data class Known(val limit: ParsedLimit, val road: String?, val source: String) : SpeedLimitState
    /** No trustworthy limit for the current position – show "unavailable", never a guess. */
    data class Unavailable(val reason: String, val road: String? = null) : SpeedLimitState
}

/**
 * Determines the legal speed limit for the road the vehicle is currently on, using
 * OpenStreetMap `maxspeed` tags downloaded from an Overpass API server. Road geometry for a
 * ~1.2 km square is cached and matched locally on each GPS fix, so the displayed limit changes
 * as the vehicle moves between roads without a network request per fix.
 */
class SpeedLimitRepository(
    private val scope: CoroutineScope,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
) {
    private val _state = MutableStateFlow<SpeedLimitState>(SpeedLimitState.Searching)
    val state: StateFlow<SpeedLimitState> = _state.asStateFlow()

    private val _currentWay = MutableStateFlow<RoadWay?>(null)
    val currentWay: StateFlow<RoadWay?> = _currentWay.asStateFlow()

    private var ways: List<RoadWay> = emptyList()
    private var cacheCenter: LatLon? = null
    private var fetchJob: Job? = null
    private var lastFetchAttempt = 0L
    private var failureBackoffUntil = 0L
    private var lastError: String? = null

    private companion object {
        const val FETCH_RADIUS_M = 600.0
        const val REFETCH_DISTANCE_M = 350.0
        const val MATCH_MAX_M = 30.0
        val HIGHWAYS = "motorway|trunk|primary|secondary|tertiary|unclassified|residential|motorway_link|" +
            "trunk_link|primary_link|secondary_link|tertiary_link|living_street|service|road|busway"
    }

    init {
        scope.launch {
            location.state.collect { st ->
                if (!settings.current.speedLimitsEnabled) { _state.value = SpeedLimitState.Disabled; return@collect }
                val here = st.latLon
                if (here == null || st.isStale) {
                    _state.value = if (st.permissionGranted) SpeedLimitState.Searching
                    else SpeedLimitState.Unavailable("Location permission needed")
                    return@collect
                }
                ensureCache(here)
                if (ways.isEmpty()) {
                    _state.value = lastError?.let { SpeedLimitState.Unavailable(it) } ?: SpeedLimitState.Searching
                    return@collect
                }
                match(here, st.bearing?.toDouble(), st.speedMps ?: 0.0, st.location?.accuracy?.toDouble() ?: 10.0)
            }
        }
        scope.launch {
            settings.settings.collect { if (!it.speedLimitsEnabled) _state.value = SpeedLimitState.Disabled }
        }
    }

    private fun ensureCache(here: LatLon) {
        val center = cacheCenter
        val needs = center == null || Geo.distance(center, here) > REFETCH_DISTANCE_M
        val now = SystemClock.elapsedRealtime()
        if (!needs || fetchJob?.isActive == true || now < failureBackoffUntil || now - lastFetchAttempt < 8_000) return
        lastFetchAttempt = now
        fetchJob = scope.launch {
            runCatching { download(here) }
                .onSuccess { ways = it; cacheCenter = here; lastError = null }
                .onFailure {
                    failureBackoffUntil = SystemClock.elapsedRealtime() + 45_000
                    lastError = "Speed-limit data unavailable (offline or server busy)"
                }
        }
    }

    private suspend fun download(c: LatLon): List<RoadWay> {
        val b = Geo.bbox(c, FETCH_RADIUS_M)
        val q = """[out:json][timeout:20];way(${b[0]},${b[1]},${b[2]},${b[3]})[highway~"^($HIGHWAYS)$"];out tags geom;"""
        val url = settings.current.overpassServer + "?data=" + URLEncoder.encode(q, "UTF-8")
        val root = Http.json.parseToJsonElement(Http.get(url)).jsonObject
        return root["elements"]?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val tags = (o["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() } ?: emptyMap()
            val geom = o["geometry"]?.jsonArray?.mapNotNull { p ->
                val po = p.jsonObject
                val lat = po["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = po["lon"]?.jsonPrimitive?.doubleOrNull
                if (lat != null && lon != null) LatLon(lat, lon) else null
            } ?: return@mapNotNull null
            if (geom.size < 2) null else RoadWay(id, tags, geom)
        } ?: emptyList()
    }

    private data class Candidate(val way: RoadWay, val dist: Double, val forward: Boolean, val score: Double)

    private fun match(p: LatLon, heading: Double?, speed: Double, accuracy: Double) {
        val useHeading = heading != null && speed > 2.5
        val maxDist = MATCH_MAX_M + accuracy.coerceAtMost(30.0)
        var best: Candidate? = null
        var currentCand: Candidate? = null
        val cur = _currentWay.value
        for (w in ways) {
            var wBest: Candidate? = null
            for (i in 0 until w.points.size - 1) {
                val a = w.points[i]; val b = w.points[i + 1]
                val (d, _) = Geo.distanceToSegment(p, a, b)
                if (d > maxDist) continue
                val segBearing = Geo.bearing(a, b)
                var forward = true
                var penalty = 0.0
                if (useHeading) {
                    val fwdDiff = Geo.headingDiff(heading!!, segBearing)
                    val bwdDiff = Geo.headingDiff(heading, (segBearing + 180) % 360)
                    forward = fwdDiff <= bwdDiff
                    val diff = if (w.oneway) fwdDiff else minOf(fwdDiff, bwdDiff)
                    if (w.oneway && fwdDiff > 100) continue // driving against a one-way: wrong road
                    penalty = diff / 3.0 // 30° off ≈ 10 m further away
                }
                val score = d + penalty
                if (wBest == null || score < wBest.score) wBest = Candidate(w, d, forward, score)
            }
            wBest ?: continue
            if (best == null || wBest.score < best.score) best = wBest
            if (cur != null && w.id == cur.id) currentCand = wBest
        }
        // Hysteresis: stick to the current road unless another is clearly better.
        val chosen = if (currentCand != null && best != null && currentCand.score - best.score < 8.0) currentCand else best
        if (chosen == null) {
            _currentWay.value = null
            _state.value = SpeedLimitState.Unavailable("Not on a mapped road")
            return
        }
        _currentWay.value = chosen.way
        val tags = chosen.way.tags
        val dirKey = if (chosen.forward) "maxspeed:forward" else "maxspeed:backward"
        val raw = tags[dirKey] ?: tags["maxspeed"] ?: tags["maxspeed:type"] ?: tags["zone:maxspeed"]
        val parsed = MaxSpeedParser.parse(raw)
        _state.value = when (parsed) {
            null -> SpeedLimitState.Unavailable("Limit not mapped for this road", chosen.way.name)
            is ParsedLimit.Unknown -> SpeedLimitState.Unavailable("Limit tag not recognised (${parsed.raw})", chosen.way.name)
            else -> SpeedLimitState.Known(
                parsed, chosen.way.name,
                if (parsed is ParsedLimit.Value && parsed.implicitZone != null)
                    "OpenStreetMap · legal default for ${parsed.implicitZone}" else "OpenStreetMap maxspeed tag",
            )
        }
    }
}
