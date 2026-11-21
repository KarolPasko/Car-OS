package com.nextgen.caros.nav

import com.nextgen.caros.location.LatLon
import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
) {
    val latLon get() = LatLon(lat, lon)
}

enum class ManeuverIcon { STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN, ROUNDABOUT, MERGE, FORK_LEFT, FORK_RIGHT, RAMP_LEFT, RAMP_RIGHT, DEPART, ARRIVE }

data class RouteStep(
    val instruction: String,
    val shortInstruction: String,
    val roadName: String,
    val icon: ManeuverIcon,
    val location: LatLon,
    val distanceM: Double,
    val durationS: Double,
    /** Distance along the whole route at which this manoeuvre happens. */
    var alongM: Double = 0.0,
)

data class Route(
    val points: List<LatLon>,
    /** cumulative distance in metres at each point */
    val cumulative: DoubleArray,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
    val destination: Place,
)

data class NavProgress(
    val distanceRemainingM: Double,
    val durationRemainingS: Double,
    val etaMillis: Long,
    val nextStep: RouteStep?,
    val distanceToNextM: Double,
    val followingStep: RouteStep?,
    val offRoute: Boolean,
    val snapped: LatLon?,
    val alongM: Double,
)

sealed interface NavState {
    data object Idle : NavState
    data class Routing(val destination: Place) : NavState
    data class Preview(val route: Route) : NavState
    data class Navigating(val route: Route, val progress: NavProgress, val rerouting: Boolean = false) : NavState
    data class Arrived(val destination: Place) : NavState
    data class Error(val destination: Place?, val message: String) : NavState
}
