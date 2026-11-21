package com.nextgen.caros.nav

import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.net.Http
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Driving routes from an OSRM server. The default is the public OSRM demo server, which is
 * suitable for personal/light use only; a self-hosted or commercial OSRM-compatible endpoint
 * can be configured in Settings > Maps.
 */
object RoutingService {

    suspend fun route(server: String, from: LatLon, to: Place, bearing: Double? = null): Route {
        val coords = "${from.lon},${from.lat};${to.lon},${to.lat}"
        val bearings = bearing?.let { "&bearings=${it.toInt()},60;" } ?: ""
        val url = "$server/route/v1/driving/$coords?overview=full&geometries=geojson&steps=true&alternatives=false$bearings"
        val root = Http.json.parseToJsonElement(Http.get(url)).jsonObject
        val code = root["code"]?.jsonPrimitive?.contentOrNull
        if (code != "Ok") error(root["message"]?.jsonPrimitive?.contentOrNull ?: "No route found ($code)")
        val r = root["routes"]!!.jsonArray.first().jsonObject
        val points = r["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map {
            val a = it.jsonArray
            LatLon(a[1].jsonPrimitive.doubleOrNull!!, a[0].jsonPrimitive.doubleOrNull!!)
        }
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + Geo.distance(points[i - 1], points[i])
        val steps = r["legs"]!!.jsonArray.flatMap { leg ->
            leg.jsonObject["steps"]!!.jsonArray.map { parseStep(it.jsonObject) }
        }
        // Position every manoeuvre along the polyline (searching forward keeps loops correct).
        var searchFrom = 0
        for (s in steps) {
            var bestI = searchFrom; var bestD = Double.MAX_VALUE
            val end = minOf(points.size, searchFrom + 4000)
            for (i in searchFrom until end) {
                val d = Geo.distance(points[i], s.location)
                if (d < bestD) { bestD = d; bestI = i }
                if (d < 1.0) break
            }
            s.alongM = cumulative[bestI]
            searchFrom = bestI
        }
        return Route(
            points = points, cumulative = cumulative,
            distanceM = r["distance"]?.jsonPrimitive?.doubleOrNull ?: cumulative.last(),
            durationS = r["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            steps = steps, destination = to,
        )
    }

    private fun parseStep(o: JsonObject): RouteStep {
        val m = o["maneuver"]!!.jsonObject
        val type = m["type"]?.jsonPrimitive?.contentOrNull ?: "turn"
        val modifier = m["modifier"]?.jsonPrimitive?.contentOrNull ?: "straight"
        val exit = m["exit"]?.jsonPrimitive?.intOrNull
        val loc = m["location"]!!.jsonArray
        val name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val ref = o["ref"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val destinations = o["destinations"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val road = when {
            name.isNotBlank() && ref.isNotBlank() -> "$name ($ref)"
            name.isNotBlank() -> name
            ref.isNotBlank() -> ref
            else -> ""
        }
        val onto = if (road.isNotBlank()) " onto $road" else ""
        val dir = when (modifier) {
            "uturn" -> "Make a U-turn"
            "sharp right" -> "Turn sharp right"
            "right" -> "Turn right"
            "slight right" -> "Bear right"
            "straight" -> "Continue straight"
            "slight left" -> "Bear left"
            "left" -> "Turn left"
            "sharp left" -> "Turn sharp left"
            else -> "Continue"
        }
        val side = if (modifier.contains("left")) "left" else "right"
        val ordinal = exit?.let { ordinal(it) }
        val (text, short) = when (type) {
            "depart" -> "Head ${compass(m["bearing_after"]?.jsonPrimitive?.doubleOrNull)}$onto" to "Start"
            "arrive" -> "You have arrived at your destination" to "Arrive"
            "roundabout", "rotary" -> (if (ordinal != null) "At the roundabout, take the $ordinal exit$onto" else "Enter the roundabout$onto") to
                (ordinal?.let { "$it exit" } ?: "Roundabout")
            "roundabout turn" -> "At the roundabout, ${dir.lowercase()}$onto" to dir
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto" to "Exit"
            "merge" -> "Merge $side$onto" to "Merge"
            "on ramp" -> "Take the ramp on the $side${if (destinations.isNotBlank()) " towards $destinations" else onto}" to "Ramp"
            "off ramp" -> "Take the exit on the $side${if (destinations.isNotBlank()) " towards $destinations" else onto}" to "Exit"
            "fork" -> "Keep $side at the fork$onto" to "Keep $side"
            "end of road" -> "At the end of the road, ${dir.lowercase()}$onto" to dir
            "new name", "continue" -> (if (modifier == "straight") "Continue$onto" else "$dir$onto") to dir
            else -> "$dir$onto" to dir
        }
        val icon = when (type) {
            "depart" -> ManeuverIcon.DEPART
            "arrive" -> ManeuverIcon.ARRIVE
            "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> ManeuverIcon.ROUNDABOUT
            "merge" -> ManeuverIcon.MERGE
            "fork" -> if (side == "left") ManeuverIcon.FORK_LEFT else ManeuverIcon.FORK_RIGHT
            "on ramp", "off ramp" -> if (side == "left") ManeuverIcon.RAMP_LEFT else ManeuverIcon.RAMP_RIGHT
            else -> when (modifier) {
                "uturn" -> ManeuverIcon.UTURN
                "sharp right" -> ManeuverIcon.SHARP_RIGHT
                "right" -> ManeuverIcon.RIGHT
                "slight right" -> ManeuverIcon.SLIGHT_RIGHT
                "slight left" -> ManeuverIcon.SLIGHT_LEFT
                "left" -> ManeuverIcon.LEFT
                "sharp left" -> ManeuverIcon.SHARP_LEFT
                else -> ManeuverIcon.STRAIGHT
            }
        }
        return RouteStep(
            instruction = text, shortInstruction = short, roadName = road, icon = icon,
            location = LatLon(loc[1].jsonPrimitive.doubleOrNull!!, loc[0].jsonPrimitive.doubleOrNull!!),
            distanceM = o["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            durationS = o["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    private fun ordinal(n: Int) = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private fun compass(b: Double?): String {
        b ?: return "out"
        val dirs = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        return dirs[(((b + 22.5) % 360) / 45).toInt()]
    }
}
