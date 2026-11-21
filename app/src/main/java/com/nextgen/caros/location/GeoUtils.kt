package com.nextgen.caros.location

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_000.0

    fun toRad(d: Double) = d * PI / 180.0
    fun toDeg(r: Double) = r * 180.0 / PI

    /** Great-circle distance in metres. */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = toRad(b.lat - a.lat)
        val dLon = toRad(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(toRad(a.lat)) * cos(toRad(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial bearing a -> b in degrees 0..360. */
    fun bearing(a: LatLon, b: LatLon): Double {
        val y = sin(toRad(b.lon - a.lon)) * cos(toRad(b.lat))
        val x = cos(toRad(a.lat)) * sin(toRad(b.lat)) -
            sin(toRad(a.lat)) * cos(toRad(b.lat)) * cos(toRad(b.lon - a.lon))
        return (toDeg(atan2(y, x)) + 360) % 360
    }

    /** Smallest absolute difference between two headings (0..180). */
    fun headingDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    /**
     * Projects point p onto segment a-b using a local equirectangular approximation
     * (accurate to centimetres over the short segments found in road data).
     * Returns distance in metres and the fraction t along the segment.
     */
    fun distanceToSegment(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val kx = cos(toRad(p.lat)) * R * PI / 180.0
        val ky = R * PI / 180.0
        val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
        val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx; val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy) to t
    }

    fun interpolate(a: LatLon, b: LatLon, t: Double) =
        LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

    /** Bounding box (south, west, north, east) of radius metres around a point. */
    fun bbox(c: LatLon, radiusM: Double): DoubleArray {
        val dLat = toDeg(radiusM / R)
        val dLon = toDeg(radiusM / (R * cos(toRad(c.lat))))
        return doubleArrayOf(c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon)
    }

    fun formatDistance(meters: Double, imperial: Boolean): String = if (imperial) {
        val miles = meters / 1609.344
        if (miles < 0.1) "${(meters * 3.28084 / 10).toInt() * 10} ft"
        else if (miles < 10) String.format("%.1f mi", miles) else "${miles.toInt()} mi"
    } else {
        if (meters < 1000) "${(meters / 10).toInt() * 10} m"
        else if (meters < 10_000) String.format("%.1f km", meters / 1000) else "${(meters / 1000).toInt()} km"
    }
}
