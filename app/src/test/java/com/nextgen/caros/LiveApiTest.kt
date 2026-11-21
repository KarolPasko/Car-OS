package com.nextgen.caros

import com.nextgen.caros.location.LatLon
import com.nextgen.caros.nav.GeocodingService
import com.nextgen.caros.nav.Place
import com.nextgen.caros.nav.RoutingService
import com.nextgen.caros.net.Http
import com.nextgen.caros.speedlimit.MaxSpeedParser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLEncoder

/**
 * Calls the real public services the app uses. Skipped unless run with -PliveTests
 * (they need internet and are subject to the services' rate limits).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class LiveApiTest {
    private fun live() = assumeTrue(System.getProperty("liveTests") == "true")

    @Test fun photonSearch() = runBlocking {
        live()
        val r = GeocodingService.search("Buckingham Palace", LatLon(51.5, -0.12))
        println("Photon: ${r.take(3)}")
        assertTrue(r.isNotEmpty())
    }

    @Test fun osrmRoute() = runBlocking {
        live()
        val r = RoutingService.route(
            "https://router.project-osrm.org", LatLon(51.5007, -0.1246),
            Place("Buckingham Palace", "", 51.5014, -0.1419),
        )
        println("OSRM: ${r.distanceM} m, ${r.durationS} s, ${r.points.size} pts")
        r.steps.forEach { println("  ${"%.0f".format(it.alongM)} m  ${it.icon}  ${it.instruction}") }
        assertTrue(r.points.size > 5 && r.steps.size >= 2 && r.steps.last().alongM > r.steps.first().alongM)
    }

    @Test fun overpassSpeedLimits() = runBlocking {
        live()
        val q = """[out:json][timeout:20];way(51.498,-0.130,51.503,-0.120)[highway~"^(primary|secondary|trunk)$"];out tags;"""
        val root = Http.json.parseToJsonElement(
            Http.get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8"))
        ).jsonObject
        val raw = root["elements"]!!.jsonArray.mapNotNull { it.jsonObject["tags"]?.jsonObject?.get("maxspeed")?.jsonPrimitive?.content }
        println("Overpass maxspeed values: ${raw.distinct()} -> ${raw.distinct().map { MaxSpeedParser.parse(it) }}")
        assertTrue(raw.isNotEmpty())
    }

    @Test fun mapStylesReachable() = runBlocking {
        live()
        listOf(
            "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json",
            "https://tiles.openfreemap.org/styles/liberty",
        ).forEach { url ->
            val o = Http.json.parseToJsonElement(Http.get(url)).jsonObject
            println("$url -> version ${o["version"]}, ${o["layers"]?.jsonArray?.size} layers")
            assertTrue(o["layers"]!!.jsonArray.isNotEmpty())
        }
    }
}
