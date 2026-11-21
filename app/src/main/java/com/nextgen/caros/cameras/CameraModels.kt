package com.nextgen.caros.cameras

import kotlinx.serialization.Serializable

/**
 * How much trust the app places in a location. Shown to the driver everywhere a camera appears.
 */
@Serializable
enum class Verification(val label: String, val explanation: String) {
    VERIFIED(
        "Verified",
        "Imported from a dataset you marked as published by a road or police authority.",
    ),
    MAPPED(
        "Community mapped",
        "Mapped by OpenStreetMap contributors. Generally reliable but not officially verified.",
    ),
    UNVERIFIED(
        "Unverified report",
        "Reported from this device. Not checked against any official source.",
    ),
}

@Serializable
enum class CameraType(val label: String) {
    FIXED("Fixed speed camera"),
    RED_LIGHT("Red-light camera"),
    AVERAGE_SPEED("Average-speed camera"),
    MOBILE("Mobile camera site"),
    OTHER("Enforcement camera"),
}

@Serializable
data class SpeedCamera(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: CameraType = CameraType.FIXED,
    /** Raw OSM-style maxspeed value attached to the camera, if the source provided one. */
    val maxspeed: String? = null,
    /** Direction the camera enforces, degrees 0..360, if known. */
    val direction: Double? = null,
    val verification: Verification,
    val sourceName: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AverageSpeedZone(
    val id: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val maxspeed: String? = null,
    val name: String? = null,
    val verification: Verification,
    val sourceName: String,
)

@Serializable
data class CameraDatabase(
    val cameras: List<SpeedCamera> = emptyList(),
    val zones: List<AverageSpeedZone> = emptyList(),
    val osmUpdatedAt: Long = 0,
    val osmCenterLat: Double? = null,
    val osmCenterLon: Double? = null,
    val osmRadiusKm: Int = 0,
    val importedSources: List<String> = emptyList(),
)
