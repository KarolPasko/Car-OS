package com.nextgen.caros.ui.maps

import com.nextgen.caros.data.AppSettings
import com.nextgen.caros.data.MapStyle

fun AppSettings.mapStyleUrl(dark: Boolean): String = when (mapStyle) {
    MapStyle.CUSTOM -> customMapStyleUrl.ifBlank { MapStyle.OPENFREEMAP.darkUrl }
    else -> if (dark) mapStyle.darkUrl else mapStyle.lightUrl
}
