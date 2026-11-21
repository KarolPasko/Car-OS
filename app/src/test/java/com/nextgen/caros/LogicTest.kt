package com.nextgen.caros

import com.nextgen.caros.location.Geo
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.speedlimit.MaxSpeedParser
import com.nextgen.caros.speedlimit.ParsedLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    @Test fun parsesPlainKmh() {
        val p = MaxSpeedParser.parse("50") as ParsedLimit.Value
        assertEquals(50.0, p.kmh, 0.01); assertEquals(false, p.mphSign); assertEquals(50, p.signValue)
    }

    @Test fun parsesMph() {
        val p = MaxSpeedParser.parse("30 mph") as ParsedLimit.Value
        assertEquals(48.28, p.kmh, 0.01); assertTrue(p.mphSign); assertEquals(30, p.signValue)
    }

    @Test fun parsesImplicitZones() {
        val p = MaxSpeedParser.parse("GB:nsl_single") as ParsedLimit.Value
        assertEquals(60, p.signValue); assertTrue(p.mphSign); assertEquals("GB:nsl_single", p.implicitZone)
        assertEquals(30, (MaxSpeedParser.parse("DE:zone30") as ParsedLimit.Value).signValue)
        assertEquals(20, (MaxSpeedParser.parse("GB:zone20") as ParsedLimit.Value).let { assertTrue(it.mphSign); it.signValue })
        assertEquals(ParsedLimit.NoLimit, MaxSpeedParser.parse("DE:motorway"))
    }

    @Test fun neverGuesses() {
        assertNull(MaxSpeedParser.parse(null))
        assertNull(MaxSpeedParser.parse(""))
        assertTrue(MaxSpeedParser.parse("50;30") is ParsedLimit.Unknown)
        assertTrue(MaxSpeedParser.parse("XX:something") is ParsedLimit.Unknown)
        assertEquals(ParsedLimit.NoLimit, MaxSpeedParser.parse("none"))
        assertEquals(ParsedLimit.Variable, MaxSpeedParser.parse("signals"))
    }

    @Test fun geoDistanceAndBearing() {
        val london = LatLon(51.5074, -0.1278); val paris = LatLon(48.8566, 2.3522)
        assertEquals(343_500.0, Geo.distance(london, paris), 2_000.0)
        assertEquals(148.0, Geo.bearing(london, paris), 2.0)
        assertEquals(10.0, Geo.headingDiff(355.0, 5.0), 0.001)
    }

    @Test fun segmentProjection() {
        val a = LatLon(51.0, 0.0); val b = LatLon(51.0, 0.01)
        val (d, t) = Geo.distanceToSegment(LatLon(51.0001, 0.005), a, b)
        assertEquals(11.1, d, 0.5); assertEquals(0.5, t, 0.01)
    }

    @Test fun distanceFormatting() {
        assertEquals("1.0 mi", Geo.formatDistance(1609.344, true))
        assertEquals("450 m", Geo.formatDistance(455.0, false))
        assertEquals("2.5 km", Geo.formatDistance(2500.0, false))
    }
}
