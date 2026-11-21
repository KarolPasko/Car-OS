package com.nextgen.caros.ui.maps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.location.Location
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nextgen.caros.cameras.AverageSpeedZone
import com.nextgen.caros.cameras.SpeedCamera
import com.nextgen.caros.location.LatLon
import com.nextgen.caros.ui.theme.LocalNg
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** False only in JVM screenshot tests, which can't load MapLibre's native renderer. */
val LocalMapRendering = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Imperative handle used by on-screen buttons (zoom, recenter, overview). */
class MapController {
    internal var map: MapLibreMap? = null
    fun zoomBy(delta: Double) { map?.animateCamera(CameraUpdateFactory.zoomBy(delta), 300) }
    fun fit(points: List<LatLon>, paddingPx: Int) {
        val m = map ?: return
        if (points.size < 2) return
        val b = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(b, paddingPx, paddingPx, paddingPx, paddingPx), 900)
    }
    fun flyTo(p: LatLon, zoom: Double = 15.0) {
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(p.lat, p.lon)).zoom(zoom).tilt(0.0).bearing(0.0).build()
        ), 900)
    }
}

private const val ROUTE_SRC = "ng-route-src"
private const val ROUTE_CASING = "ng-route-casing"
private const val ROUTE_LINE = "ng-route-line"
private const val CAM_SRC = "ng-cam-src"
private const val CAM_LAYER = "ng-cam-layer"
private const val DEST_SRC = "ng-dest-src"
private const val DEST_LAYER = "ng-dest-layer"

/**
 * MapLibre GL map embedded in Compose. Draws the route, destination and speed-camera markers
 * (colour-coded by verification level) and follows the vehicle with smooth camera animations.
 */
@SuppressLint("MissingPermission")
@Composable
fun NgMap(
    modifier: Modifier = Modifier,
    styleUrl: String,
    controller: MapController = remember { MapController() },
    location: Location?,
    bearing: Float?,
    follow: Boolean,
    tilt3d: Boolean,
    followZoom: Double,
    route: List<LatLon>? = null,
    cameras: List<SpeedCamera> = emptyList(),
    zones: List<AverageSpeedZone> = emptyList(),
    destination: LatLon? = null,
    interactive: Boolean = true,
    hasLocationPermission: Boolean,
    onLongPress: ((LatLon) -> Unit)? = null,
    onUserGesture: () -> Unit = {},
    onCameraTapped: (String) -> Unit = {},
    bottomPaddingPx: Int = 0,
) {
    if (!LocalMapRendering.current) {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF1B2433), Color(0xFF111827), Color(0xFF1E293B)))))
        return
    }
    val context = LocalContext.current
    val ng = LocalNg.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current

    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    val styleHolder = remember { arrayOfNulls<Style>(1) }
    val loadedUrl = remember { arrayOf("") }

    val onLongPressState = rememberUpdatedState(onLongPress)
    val onGestureState = rememberUpdatedState(onUserGesture)
    val onCamTapState = rememberUpdatedState(onCameraTapped)

    // Colours for layers
    val accent = ng.accent.toArgb()
    val verified = ng.verified.toArgb()
    val mapped = ng.mapped.toArgb()
    val unverified = ng.unverified.toArgb()
    val zoneColor = ng.accent2.toArgb()

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            controller.map = null
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    fun addLayers(style: Style) {
        if (style.getSource(ROUTE_SRC) == null) style.addSource(GeoJsonSource(ROUTE_SRC, FeatureCollection.fromFeatures(emptyList())))
        if (style.getLayer(ROUTE_CASING) == null) style.addLayer(
            LineLayer(ROUTE_CASING, ROUTE_SRC).withProperties(
                PropertyFactory.lineColor(Color(0xCC0B1020).toArgb()), PropertyFactory.lineWidth(12f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        if (style.getLayer(ROUTE_LINE) == null) style.addLayer(
            LineLayer(ROUTE_LINE, ROUTE_SRC).withProperties(
                PropertyFactory.lineColor(accent), PropertyFactory.lineWidth(7f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )
        if (style.getSource(CAM_SRC) == null) style.addSource(GeoJsonSource(CAM_SRC, FeatureCollection.fromFeatures(emptyList())))
        if (style.getLayer(CAM_LAYER) == null) style.addLayer(
            CircleLayer(CAM_LAYER, CAM_SRC).withProperties(
                PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(),
                    Expression.stop(8, 3f), Expression.stop(14, 8f), Expression.stop(18, 12f))),
                PropertyFactory.circleColor(Color(0xFF0B1020).toArgb()),
                PropertyFactory.circleStrokeWidth(3.5f),
                PropertyFactory.circleStrokeColor(
                    Expression.match(
                        Expression.get("v"), Expression.color(mapped),
                        Expression.stop("VERIFIED", Expression.color(verified)),
                        Expression.stop("MAPPED", Expression.color(mapped)),
                        Expression.stop("UNVERIFIED", Expression.color(unverified)),
                        Expression.stop("ZONE", Expression.color(zoneColor)),
                    )
                ),
            )
        )
        if (style.getSource(DEST_SRC) == null) style.addSource(GeoJsonSource(DEST_SRC, FeatureCollection.fromFeatures(emptyList())))
        if (style.getLayer(DEST_LAYER) == null) style.addLayer(
            CircleLayer(DEST_LAYER, DEST_SRC).withProperties(
                PropertyFactory.circleRadius(10f), PropertyFactory.circleColor(Color.White.toArgb()),
                PropertyFactory.circleStrokeWidth(6f), PropertyFactory.circleStrokeColor(accent),
            )
        )
    }

    fun setData(style: Style) {
        (style.getSource(ROUTE_SRC) as? GeoJsonSource)?.setGeoJson(
            if (route != null && route.size >= 2)
                FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })))
            else FeatureCollection.fromFeatures(emptyList())
        )
        val camFeatures = cameras.map { c ->
            Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).also {
                it.addStringProperty("v", c.verification.name); it.addStringProperty("id", c.id)
            }
        } + zones.flatMap { z ->
            listOf(Point.fromLngLat(z.startLon, z.startLat), Point.fromLngLat(z.endLon, z.endLat)).map { p ->
                Feature.fromGeometry(p).also { it.addStringProperty("v", "ZONE"); it.addStringProperty("id", z.id) }
            }
        }
        (style.getSource(CAM_SRC) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(camFeatures))
        (style.getSource(DEST_SRC) as? GeoJsonSource)?.setGeoJson(
            destination?.let { FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) }
                ?: FeatureCollection.fromFeatures(emptyList())
        )
    }

    fun enableLocation(map: MapLibreMap, style: Style) {
        if (!hasLocationPermission) return
        runCatching {
            val lc = map.locationComponent
            if (!lc.isLocationComponentActivated) {
                lc.activateLocationComponent(
                    LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false).build()
                )
            }
            lc.isLocationComponentEnabled = true
            lc.cameraMode = CameraMode.NONE
            lc.renderMode = RenderMode.GPS
        }
    }

    // Style (re)load
    LaunchedEffect(styleUrl, hasLocationPermission) {
        mapView.getMapAsync { map ->
            controller.map = map
            map.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.END
                isCompassEnabled = interactive
                compassGravity = Gravity.TOP or Gravity.END
                setAllGesturesEnabled(interactive)
            }
            if (loadedUrl[0] != styleUrl) {
                loadedUrl[0] = styleUrl
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    styleHolder[0] = style
                    addLayers(style)
                    setData(style)
                    enableLocation(map, style)
                    location?.let { map.locationComponent.takeIf { _ -> hasLocationPermission }?.runCatching { forceLocationUpdate(it) } }
                }
            } else styleHolder[0]?.let { enableLocation(map, it) }
        }
    }

    // One-time listeners
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            map.addOnMapLongClickListener { ll ->
                onLongPressState.value?.invoke(LatLon(ll.latitude, ll.longitude)); onLongPressState.value != null
            }
            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onGestureState.value()
            }
            map.addOnMapClickListener { ll ->
                val pt: PointF = map.projection.toScreenLocation(ll)
                val hits = runCatching { map.queryRenderedFeatures(pt, CAM_LAYER) }.getOrDefault(emptyList())
                val id = hits.firstOrNull()?.getStringProperty("id")
                if (id != null) { onCamTapState.value(id); true } else false
            }
        }
    }

    // Overlay data
    LaunchedEffect(route, cameras, zones, destination) {
        styleHolder[0]?.let { if (it.isFullyLoaded) setData(it) }
    }

    // Location puck + follow camera
    LaunchedEffect(location, follow, tilt3d, followZoom, bottomPaddingPx) {
        val map = controller.map ?: return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        if (hasLocationPermission && styleHolder[0]?.isFullyLoaded == true) {
            runCatching { if (map.locationComponent.isLocationComponentActivated) map.locationComponent.forceLocationUpdate(loc) }
        }
        if (follow) {
            val b = bearing?.toDouble() ?: map.cameraPosition.bearing
            val pos = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(followZoom)
                .bearing(if (tilt3d) b else 0.0)
                .tilt(if (tilt3d) 50.0 else 0.0)
                .padding(0.0, if (tilt3d) with(density) { 140.dp.toPx().toDouble() } else 0.0, 0.0, bottomPaddingPx.toDouble())
                .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 900)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Suppress("unused")
private fun Context.noop() = Unit
