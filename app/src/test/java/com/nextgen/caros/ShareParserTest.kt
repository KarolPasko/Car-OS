package com.nextgen.caros

import com.nextgen.caros.link.ShareParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class ShareParserTest {

    @Test fun geoWithLabel() {
        val p = ShareParser.parseLocal("geo:0,0?q=51.5014,-0.1419(Buckingham Palace)")
        assertEquals(51.5014, p.lat!!, 1e-6); assertEquals(-0.1419, p.lon!!, 1e-6); assertEquals("Buckingham Palace", p.name)
    }

    @Test fun geoAddressQuery() {
        val p = ShareParser.parseLocal("geo:0,0?q=10+Downing+Street,+London")
        assertNull(p.lat); assertEquals("10 Downing Street, London", p.query)
    }

    @Test fun googleMapsPlaceUrlPrefersPinCoordinates() {
        val p = ShareParser.parseLocal(
            "Buckingham Palace\nhttps://www.google.com/maps/place/Buckingham+Palace/@51.501364,-0.14189,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d51.501364!4d-0.1418899"
        )
        assertEquals(51.501364, p.lat!!, 1e-6); assertEquals(-0.1418899, p.lon!!, 1e-6); assertEquals("Buckingham Palace", p.name)
    }

    @Test fun googleMapsQueryCoords() {
        val p = ShareParser.parseLocal("https://maps.google.com/?q=48.8584,2.2945")
        assertEquals(48.8584, p.lat!!, 1e-6); assertEquals(2.2945, p.lon!!, 1e-6)
    }

    @Test fun appleMapsLink() {
        val p = ShareParser.parseLocal("https://maps.apple.com/?ll=40.748817,-73.985428&q=Empire%20State%20Building")
        assertEquals(40.748817, p.lat!!, 1e-6); assertEquals("Empire State Building", p.name)
    }

    @Test fun plainAddressBecomesQuery() {
        val p = ShareParser.parseLocal("221B Baker Street\nLondon NW1 6XE")
        assertNull(p.lat); assertEquals("221B Baker Street, London NW1 6XE", p.query)
    }

    @Test fun shortLinkWithoutCoordsKeepsName() {
        val p = ShareParser.parseLocal("Tower of London\nhttps://maps.app.goo.gl/abc123")
        assertNull(p.lat); assertEquals("Tower of London", p.name)
    }
}
