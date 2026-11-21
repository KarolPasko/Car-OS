package com.nextgen.caros.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.ui.graphics.vector.ImageVector
import com.nextgen.caros.nav.ManeuverIcon

fun ManeuverIcon.vector(): ImageVector = when (this) {
    ManeuverIcon.STRAIGHT -> Icons.Filled.Straight
    ManeuverIcon.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    ManeuverIcon.LEFT -> Icons.Filled.TurnLeft
    ManeuverIcon.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    ManeuverIcon.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    ManeuverIcon.RIGHT -> Icons.Filled.TurnRight
    ManeuverIcon.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    ManeuverIcon.UTURN -> Icons.Filled.UTurnLeft
    ManeuverIcon.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    ManeuverIcon.MERGE -> Icons.Filled.Merge
    ManeuverIcon.FORK_LEFT -> Icons.Filled.ForkLeft
    ManeuverIcon.FORK_RIGHT -> Icons.Filled.ForkRight
    ManeuverIcon.RAMP_LEFT -> Icons.Filled.RampLeft
    ManeuverIcon.RAMP_RIGHT -> Icons.Filled.RampRight
    ManeuverIcon.DEPART -> Icons.Filled.Navigation
    ManeuverIcon.ARRIVE -> Icons.Filled.Flag
}
