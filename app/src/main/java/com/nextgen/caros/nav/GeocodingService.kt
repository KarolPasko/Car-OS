package com.nextgen.caros.nav

import com.nextgen.caros.location.LatLon
import com.nextgen.caros.net.Http
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Place search using Photon (photon.komoot.io), an OpenStreetMap-based geocoder that is free
 * for fair use and needs no key. Results are biased towards the current position.
 */
object GeocodingService {
    suspend fun search(query: String, near: LatLon?): List<Place> {
        if (query.isBlank()) return emptyList()
        val sb = StringBuilder("https://photon.komoot.io/api/?limit=10&q=")
            .append(URLEncoder.encode(query.trim(), "UTF-8"))
        if (near != null) sb.append("&lat=${near.lat}&lon=${near.lon}")
        val root = Http.json.parseToJsonElement(Http.get(sb.toString())).jsonObject
        return root["features"]?.jsonArray?.mapNotNull { f ->
            val fo = f.jsonObject
            val coords = fo["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val p = fo["properties"]?.jsonObject ?: return@mapNotNull null
            fun s(k: String) = p[k]?.jsonPrimitive?.contentOrNull
            val street = listOfNotNull(s("housenumber"), s("street")).joinToString(" ").ifBlank { null }
            val name = s("name") ?: street ?: return@mapNotNull null
            val address = listOfNotNull(
                street.takeIf { it != name }, s("postcode"), s("city") ?: s("town") ?: s("village"), s("state"), s("country"),
            ).distinct().joinToString(", ")
            Place(name, address, lat, lon)
        }?.distinctBy { "${it.name}|${it.address}" } ?: emptyList()
    }
}
