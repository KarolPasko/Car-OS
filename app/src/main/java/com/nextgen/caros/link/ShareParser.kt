package com.nextgen.caros.link

import android.net.Uri
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.nav.GeocodingService
import com.nextgen.caros.nav.Place
import com.nextgen.caros.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLDecoder

/**
 * Turns whatever a maps app shares (geo: URIs, Google Maps / Apple Maps links, plain addresses)
 * into a [Place]. Coordinates are taken from the link when present; otherwise the text is geocoded.
 */
object ShareParser {

    data class Parsed(val name: String?, val lat: Double?, val lon: Double?, val query: String?)

    private val reBang = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val reAt = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val reParam = Regex("""[?&](?:q|query|destination|daddr|ll|sll|center)=(-?\d+\.\d+)(?:,|%2C)\s*(-?\d+\.\d+)""")
    private val reUrl = Regex("""https?://\S+""")
    private val rePlace = Regex("""/place/([^/@?]+)""")

    fun parseLocal(text: String): Parsed {
        val t = text.trim()
        if (t.startsWith("geo:", ignoreCase = true)) return parseGeo(t)
        val url = reUrl.find(t)?.value
        val nonUrl = t.replace(reUrl, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = nonUrl.firstOrNull()
        return if (url != null) parseUrl(url, name) else Parsed(name, null, null, nonUrl.joinToString(", ").ifBlank { null })
    }

    private fun parseGeo(geo: String): Parsed {
        // geo: URIs are opaque, so Uri.getQueryParameter() can't be used – parse by hand.
        val body = geo.substringAfter(':')
        val ssp = body.substringBefore('?')
        val (la, lo) = ssp.split(',').let { it.getOrNull(0)?.toDoubleOrNull() to it.getOrNull(1)?.substringBefore(';')?.toDoubleOrNull() }
        val q = body.substringAfter('?', "").split('&').firstOrNull { it.startsWith("q=") }
            ?.removePrefix("q=")?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        // geo:0,0?q=lat,lon(Label)
        val qm = q?.let { Regex("""^\s*(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)\s*(?:\((.*)\))?""").find(it) }
        if (qm != null) return Parsed(qm.groupValues[3].ifBlank { null }, qm.groupValues[1].toDouble(), qm.groupValues[2].toDouble(), null)
        val hasCoords = la != null && lo != null && !(la == 0.0 && lo == 0.0)
        return Parsed(q, if (hasCoords) la else null, if (hasCoords) lo else null, if (hasCoords) null else q)
    }

    fun parseUrl(url: String, fallbackName: String? = null): Parsed {
        var u = url
        // Google consent interstitial wraps the real URL.
        Uri.parse(u).getQueryParameter("continue")?.let { u = it }
        val decoded = runCatching { URLDecoder.decode(u, "UTF-8") }.getOrDefault(u)
        val placeName = rePlace.find(decoded)?.groupValues?.get(1)?.replace('+', ' ')
        val name = placeName ?: Uri.parse(u).getQueryParameter("q")?.takeIf { !it.first().isDigit() && !it.startsWith("-") }
            ?: fallbackName
        val coords = reBang.find(decoded) ?: reParam.find(decoded) ?: reAt.find(decoded)
        if (coords != null) return Parsed(name, coords.groupValues[1].toDouble(), coords.groupValues[2].toDouble(), null)
        val query = Uri.parse(u).getQueryParameter("q") ?: Uri.parse(u).getQueryParameter("address")
            ?: Uri.parse(u).getQueryParameter("daddr") ?: placeName ?: fallbackName
        return Parsed(name, null, null, query)
    }

    /** Follows short links (maps.app.goo.gl, goo.gl/maps) to the full URL. */
    private suspend fun expand(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            Http.client.newCall(Request.Builder().url(url).get().build()).execute().use { it.request.url.toString() }
        }.getOrDefault(url)
    }

    suspend fun toPlace(text: String, near: LatLon?): Place? {
        var parsed = parseLocal(text)
        val url = reUrl.find(text)?.value
        if (parsed.lat == null && url != null && (url.contains("goo.gl") || url.contains("g.co/") || url.contains("maps.app"))) {
            parsed = parseUrl(expand(url), parsed.name)
        }
        if (parsed.lat != null && parsed.lon != null) {
            return Place(parsed.name ?: "Shared location", String.format(java.util.Locale.US, "%.5f, %.5f", parsed.lat, parsed.lon), parsed.lat, parsed.lon)
        }
        val q = parsed.query ?: parsed.name ?: return null
        return GeocodingService.search(q, near).firstOrNull()
    }
}
