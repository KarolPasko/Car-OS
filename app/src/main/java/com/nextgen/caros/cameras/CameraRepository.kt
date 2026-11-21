package com.nextgen.caros.cameras

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import com.nextgen.caros.data.SettingsRepository
import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.location.LocationRepository
import com.nextgen.caros.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URLEncoder
import java.util.UUID

sealed interface CameraAlert {
    data class Approaching(val camera: SpeedCamera, val distanceM: Double) : CameraAlert
    data class ZoneAhead(val zone: AverageSpeedZone, val distanceM: Double) : CameraAlert
    data class InZone(val zone: AverageSpeedZone, val averageMps: Double?, val remainingM: Double) : CameraAlert
}

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Running : UpdateStatus
    data class Done(val message: String) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Local database of known enforcement camera locations with three clearly separated trust levels.
 * Nothing here claims to be complete or live: the UI always shows when and where data was loaded.
 */
class CameraRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val location: LocationRepository,
    private val settings: SettingsRepository,
) {
    private val file = File(context.filesDir, "cameras.json")

    private val _db = MutableStateFlow(CameraDatabase())
    val db: StateFlow<CameraDatabase> = _db.asStateFlow()

    private val _alert = MutableStateFlow<CameraAlert?>(null)
    val alert: StateFlow<CameraAlert?> = _alert.asStateFlow()

    private val _update = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val update: StateFlow<UpdateStatus> = _update.asStateFlow()

    private var lastAlertedId: String? = null
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70) }.getOrNull() }

    // Average-speed zone tracking.
    private var activeZoneId: String? = null
    private var zoneEnteredAt = 0L
    private var zoneDistance = 0.0
    private var lastZonePoint: LatLon? = null

    init {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) Http.json.decodeFromString(CameraDatabase.serializer(), file.readText()) else null }
                    .getOrNull()
            }?.let { _db.value = it }
            location.state.collect { st ->
                val p = st.latLon
                if (p == null || st.isStale || !settings.current.camerasEnabled) { setAlert(null); return@collect }
                evaluate(p, st.bearing?.toDouble(), st.speedMps ?: 0.0)
            }
        }
    }

    private suspend fun persist(db: CameraDatabase) {
        _db.value = db
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, "cameras.json.tmp")
            tmp.writeText(Http.json.encodeToString(CameraDatabase.serializer(), db))
            tmp.renameTo(file)
        }
    }

    fun visibleCameras(): List<SpeedCamera> =
        _db.value.cameras.filter { settings.current.showUnverifiedCameras || it.verification != Verification.UNVERIFIED }

    // ---------------------------------------------------------------- alert engine

    private fun setAlert(a: CameraAlert?) {
        val id = when (a) {
            is CameraAlert.Approaching -> a.camera.id
            is CameraAlert.ZoneAhead -> "zone-ahead-" + a.zone.id
            is CameraAlert.InZone -> "zone-in-" + a.zone.id
            null -> null
        }
        if (id != null && id != lastAlertedId && a !is CameraAlert.InZone && settings.current.alertSounds) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        }
        lastAlertedId = id
        _alert.value = a
    }

    private fun alertable(v: Verification) =
        v != Verification.UNVERIFIED || settings.current.alertForUnverified

    private fun evaluate(p: LatLon, heading: Double?, speed: Double) {
        val s = settings.current
        val db = _db.value
        val now = System.currentTimeMillis()

        // 1) Inside an average-speed zone?
        activeZoneId?.let { zid ->
            val zone = db.zones.firstOrNull { it.id == zid }
            if (zone == null) { activeZoneId = null; return@let }
            val end = LatLon(zone.endLat, zone.endLon)
            val start = LatLon(zone.startLat, zone.startLon)
            lastZonePoint?.let { zoneDistance += Geo.distance(it, p) }
            lastZonePoint = p
            val remaining = Geo.distance(p, end)
            val straight = Geo.distance(start, end)
            if (remaining < 40 || zoneDistance > straight * 1.6 + 500) {
                activeZoneId = null
            } else {
                val elapsed = (now - zoneEnteredAt) / 1000.0
                setAlert(CameraAlert.InZone(zone, if (elapsed > 5) zoneDistance / elapsed else null, remaining))
                return
            }
        }

        val moving = heading != null && speed > 2.0
        val range = s.cameraAlertDistanceM.toDouble()

        // 2) Entering an average-speed zone start point.
        for (z in db.zones) {
            if (!alertable(z.verification)) continue
            val start = LatLon(z.startLat, z.startLon)
            val d = Geo.distance(p, start)
            if (d < 40) {
                activeZoneId = z.id; zoneEnteredAt = now; zoneDistance = 0.0; lastZonePoint = p
                setAlert(CameraAlert.InZone(z, null, Geo.distance(p, LatLon(z.endLat, z.endLon))))
                return
            }
        }

        if (!moving) { setAlert(null); return }

        // 3) Approaching a camera or zone start that is ahead of us.
        var bestCam: Pair<SpeedCamera, Double>? = null
        for (c in db.cameras) {
            if (!alertable(c.verification)) continue
            val cp = LatLon(c.lat, c.lon)
            val d = Geo.distance(p, cp)
            if (d > range || d < 5) continue
            if (Geo.headingDiff(heading!!, Geo.bearing(p, cp)) > 35) continue
            if (c.direction != null && Geo.headingDiff(heading, c.direction) > 70) continue
            if (bestCam == null || d < bestCam.second) bestCam = c to d
        }
        var bestZone: Pair<AverageSpeedZone, Double>? = null
        for (z in db.zones) {
            if (!alertable(z.verification)) continue
            val zp = LatLon(z.startLat, z.startLon)
            val d = Geo.distance(p, zp)
            if (d > range) continue
            if (Geo.headingDiff(heading!!, Geo.bearing(p, zp)) > 35) continue
            if (bestZone == null || d < bestZone.second) bestZone = z to d
        }
        when {
            bestCam != null && (bestZone == null || bestCam.second <= bestZone.second) ->
                setAlert(CameraAlert.Approaching(bestCam.first, bestCam.second))
            bestZone != null -> setAlert(CameraAlert.ZoneAhead(bestZone.first, bestZone.second))
            else -> setAlert(null)
        }
    }

    // ---------------------------------------------------------------- data updates

    /**
     * Downloads camera locations mapped in OpenStreetMap around the current position:
     * `highway=speed_camera` nodes and `type=enforcement` relations (incl. average-speed sections).
     */
    fun updateFromOpenStreetMap() {
        val here = location.state.value.latLon ?: run {
            _update.value = UpdateStatus.Failed("Need a location fix first"); return
        }
        if (_update.value == UpdateStatus.Running) return
        val radiusKm = settings.current.cameraDownloadRadiusKm
        _update.value = UpdateStatus.Running
        scope.launch {
            runCatching {
                val r = radiusKm * 1000
                val q = """[out:json][timeout:90];
                    (node(around:$r,${here.lat},${here.lon})[highway=speed_camera];
                     relation(around:$r,${here.lat},${here.lon})[type=enforcement][enforcement~"^(maxspeed|average_speed|traffic_signals)$"];);
                    out body;>;out skel qt;""".trimIndent()
                val url = settings.current.overpassServer + "?data=" + URLEncoder.encode(q, "UTF-8")
                parseOsm(Http.json.parseToJsonElement(Http.get(url)).jsonObject)
            }.onSuccess { (cams, zones) ->
                val old = _db.value
                val merged = old.copy(
                    cameras = old.cameras.filter { it.verification != Verification.MAPPED } + cams,
                    zones = old.zones.filter { it.verification != Verification.MAPPED } + zones,
                    osmUpdatedAt = System.currentTimeMillis(),
                    osmCenterLat = here.lat, osmCenterLon = here.lon, osmRadiusKm = radiusKm,
                )
                persist(merged)
                _update.value = UpdateStatus.Done(
                    "Loaded ${cams.size} camera locations and ${zones.size} average-speed sections within $radiusKm km from OpenStreetMap."
                )
            }.onFailure { _update.value = UpdateStatus.Failed("Update failed: ${it.message ?: "network error"}") }
        }
    }

    private fun parseOsm(root: JsonObject): Pair<List<SpeedCamera>, List<AverageSpeedZone>> {
        val elements = root["elements"]?.jsonArray ?: return emptyList<SpeedCamera>() to emptyList()
        val nodes = HashMap<Long, Pair<LatLon, Map<String, String>>>()
        val relations = mutableListOf<JsonObject>()
        for (e in elements) {
            val o = e.jsonObject
            val type = o["type"]?.jsonPrimitive?.contentOrNull
            val tags = (o["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() } ?: emptyMap()
            when (type) {
                "node" -> {
                    val id = o["id"]?.jsonPrimitive?.longOrNull ?: continue
                    val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: continue
                    val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: continue
                    val existing = nodes[id]
                    nodes[id] = LatLon(lat, lon) to (if (tags.isEmpty() && existing != null) existing.second else tags)
                }
                "relation" -> relations += o
            }
        }
        val cams = LinkedHashMap<Long, SpeedCamera>()
        val zones = mutableListOf<AverageSpeedZone>()
        fun camFromNode(id: Long, type: CameraType, relTags: Map<String, String> = emptyMap()) {
            val (p, tags) = nodes[id] ?: return
            if (cams.containsKey(id)) return
            cams[id] = SpeedCamera(
                id = "osm-n$id", lat = p.lat, lon = p.lon, type = type,
                maxspeed = tags["maxspeed"] ?: relTags["maxspeed"],
                direction = (tags["direction"] ?: tags["camera:direction"])?.toDoubleOrNull(),
                verification = Verification.MAPPED, sourceName = "OpenStreetMap",
            )
        }
        for ((id, v) in nodes) if (v.second["highway"] == "speed_camera") camFromNode(id, CameraType.FIXED)
        for (r in relations) {
            val tags = (r["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() } ?: emptyMap()
            val members = r["members"]?.jsonArray?.map { it.jsonObject } ?: continue
            fun nodeIds(role: String) = members.filter {
                it["type"]?.jsonPrimitive?.contentOrNull == "node" && it["role"]?.jsonPrimitive?.contentOrNull == role
            }.mapNotNull { it["ref"]?.jsonPrimitive?.longOrNull }
            when (tags["enforcement"]) {
                "average_speed" -> {
                    nodeIds("device").forEach { camFromNode(it, CameraType.AVERAGE_SPEED, tags) }
                    val from = nodeIds("from").firstOrNull()?.let { nodes[it]?.first }
                    val to = nodeIds("to").firstOrNull()?.let { nodes[it]?.first }
                    if (from != null && to != null) {
                        zones += AverageSpeedZone(
                            id = "osm-r" + r["id"]?.jsonPrimitive?.contentOrNull,
                            startLat = from.lat, startLon = from.lon, endLat = to.lat, endLon = to.lon,
                            maxspeed = tags["maxspeed"], name = tags["name"] ?: tags["description"],
                            verification = Verification.MAPPED, sourceName = "OpenStreetMap",
                        )
                    }
                }
                "traffic_signals" -> nodeIds("device").forEach { camFromNode(it, CameraType.RED_LIGHT, tags) }
                else -> nodeIds("device").forEach { camFromNode(it, CameraType.FIXED, tags) }
            }
        }
        return cams.values.toList() to zones
    }

    /**
     * Imports a CSV file (`lat,lon[,type][,maxspeed][,direction]` with a header row).
     * The user states whether the file comes from an official authority; only then is it
     * labelled VERIFIED.
     */
    fun importCsv(uri: Uri, sourceName: String, official: Boolean) {
        _update.value = UpdateStatus.Running
        scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: error("Could not read file")
                parseCsv(text, sourceName.ifBlank { "Imported file" }, if (official) Verification.VERIFIED else Verification.UNVERIFIED)
            }.onSuccess { list ->
                if (list.isEmpty()) {
                    _update.value = UpdateStatus.Failed("No valid rows found. Expected a header with lat and lon columns.")
                    return@onSuccess
                }
                val old = _db.value
                val name = sourceName.ifBlank { "Imported file" }
                persist(old.copy(
                    cameras = old.cameras.filter { it.sourceName != name } + list,
                    importedSources = (old.importedSources - name) + name,
                ))
                _update.value = UpdateStatus.Done("Imported ${list.size} locations from \"$name\".")
            }.onFailure { _update.value = UpdateStatus.Failed("Import failed: ${it.message}") }
        }
    }

    internal fun parseCsv(text: String, source: String, verification: Verification): List<SpeedCamera> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        if (lines.isEmpty()) return emptyList()
        val sep = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val header = lines.first().split(sep).map { it.trim().trim('"').lowercase() }
        fun col(vararg names: String) = header.indexOfFirst { it in names }
        val iLat = col("lat", "latitude", "y")
        val iLon = col("lon", "lng", "long", "longitude", "x")
        if (iLat < 0 || iLon < 0) return emptyList()
        val iType = col("type", "camera_type")
        val iSpeed = col("maxspeed", "speed", "limit", "speed_limit")
        val iDir = col("direction", "heading", "bearing")
        return lines.drop(1).mapNotNull { line ->
            val c = line.split(sep).map { it.trim().trim('"') }
            val lat = c.getOrNull(iLat)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = c.getOrNull(iLon)?.toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            val t = c.getOrNull(iType)?.lowercase().orEmpty()
            SpeedCamera(
                id = "imp-" + UUID.randomUUID(),
                lat = lat, lon = lon,
                type = when {
                    "red" in t || "light" in t -> CameraType.RED_LIGHT
                    "average" in t || "avg" in t || "section" in t -> CameraType.AVERAGE_SPEED
                    "mobile" in t -> CameraType.MOBILE
                    else -> CameraType.FIXED
                },
                maxspeed = c.getOrNull(iSpeed)?.takeIf { it.isNotBlank() },
                direction = c.getOrNull(iDir)?.toDoubleOrNull(),
                verification = verification, sourceName = source,
            )
        }
    }

    /** Saves the current position as an unverified, user-reported camera location. */
    fun reportHere(): Boolean {
        val st = location.state.value
        val p = st.latLon ?: return false
        scope.launch {
            val old = _db.value
            persist(old.copy(cameras = old.cameras + SpeedCamera(
                id = "usr-" + UUID.randomUUID(), lat = p.lat, lon = p.lon, type = CameraType.FIXED,
                direction = st.bearing?.toDouble(), verification = Verification.UNVERIFIED, sourceName = "Your report",
            )))
        }
        return true
    }

    fun deleteCamera(id: String) = scope.launch {
        val old = _db.value
        persist(old.copy(cameras = old.cameras.filterNot { it.id == id }))
    }

    fun clearSource(sourceName: String) = scope.launch {
        val old = _db.value
        persist(old.copy(
            cameras = old.cameras.filterNot { it.sourceName == sourceName },
            zones = old.zones.filterNot { it.sourceName == sourceName },
            importedSources = old.importedSources - sourceName,
            osmUpdatedAt = if (sourceName == "OpenStreetMap") 0 else old.osmUpdatedAt,
        ))
    }

    fun clearReports() = scope.launch {
        val old = _db.value
        persist(old.copy(cameras = old.cameras.filterNot { it.sourceName == "Your report" }))
    }

    fun dismissStatus() { _update.value = UpdateStatus.Idle }
}
