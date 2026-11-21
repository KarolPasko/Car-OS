package com.nextgen.caros.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nextgen.caros.appContainer
import com.nextgen.caros.cameras.CameraAlert
import com.nextgen.caros.cameras.Verification
import com.nextgen.caros.data.SpeedUnit
import com.nextgen.caros.location.Geo
import com.nextgen.caros.speedlimit.MaxSpeedParser
import com.nextgen.caros.speedlimit.ParsedLimit
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

/**
 * Compact, informational camera notice. Slides in at the screen edge, never covers the map
 * centre, and shows how trustworthy the location is.
 */
@Composable
fun CameraAlertCard(modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    val alert by appContainer.cameras.alert.collectAsState()
    val s by appContainer.settings.settings.collectAsState()
    val limitState by appContainer.speedLimits.state.collectAsState()
    val imperial = s.speedUnit == SpeedUnit.MPH
    // Keep the last alert to animate it out.
    val last = remember { arrayOfNulls<CameraAlert>(1) }
    if (alert != null) last[0] = alert
    val a = alert ?: last[0]

    AnimatedVisibility(
        visible = alert != null && s.camerasEnabled,
        enter = slideInHorizontally(tween(350)) { it } + fadeIn(),
        exit = slideOutHorizontally(tween(250)) { it } + fadeOut(),
        modifier = modifier,
    ) {
        if (a == null) return@AnimatedVisibility
        val (verification, title, subtitle, limitRaw) = when (a) {
            is CameraAlert.Approaching -> Quad(a.camera.verification, a.camera.type.label, "in ${Geo.formatDistance(a.distanceM, imperial)}", a.camera.maxspeed)
            is CameraAlert.ZoneAhead -> Quad(a.zone.verification, "Average-speed zone ahead", "starts in ${Geo.formatDistance(a.distanceM, imperial)}", a.zone.maxspeed)
            is CameraAlert.InZone -> Quad(
                a.zone.verification, "In average-speed zone",
                (a.averageMps?.let { "Your average ${s.speedUnit.fromMps(it).toInt()} ${s.speedUnit.label} · " } ?: "") +
                    "${Geo.formatDistance(a.remainingM, imperial)} to end",
                a.zone.maxspeed,
            )
        }
        val color = when (verification) {
            Verification.VERIFIED -> ng.verified
            Verification.MAPPED -> ng.mapped
            Verification.UNVERIFIED -> ng.unverified
        }
        Row(
            Modifier.clip(RoundedCornerShape(22.dp))
                .background(if (ng.isDark) Color(0xF20E1320) else Color(0xF7FFFFFF))
                .border(1.5.dp, color.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (a is CameraAlert.Approaching) Icons.Filled.CameraAlt else Icons.Filled.Timer, null, tint = color, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = NgType.label.copy(color = ng.textPrimary))
                Text(subtitle, style = NgType.caption.copy(color = ng.textSecondary))
                Text(verification.label, style = NgType.caption.copy(color = color))
            }
            Spacer(Modifier.width(12.dp))
            // Show the limit recorded with the camera, else the current road's limit, else nothing.
            val camLimit = MaxSpeedParser.parse(limitRaw)
            val sign = if (camLimit is ParsedLimit.Value) com.nextgen.caros.speedlimit.SpeedLimitState.Known(camLimit, null, "camera") else limitState
            SpeedLimitSign(sign, s.speedUnit, size = 52.dp)
        }
    }
}

private data class Quad(val a: Verification, val b: String, val c: String, val d: String?)
