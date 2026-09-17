package my.github.MrxSiN.modeevolved.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln

import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.trigger.GeoPoint

/**
 * A vector OpenStreetMap the person pans under a fixed pin.
 *
 * The map is drawn from vector tiles, so it stays sharp on any screen, in a
 * light or Google Maps-like dark look ([MapTheme]) whose water takes the wallpaper palette. The pin
 * marks the area's center and a circle shows its range, so what the map shows
 * is exactly what the trigger watches.
 *
 * @param focus a point to move the map to; each new value moves it once.
 */
@Composable
fun MapPicker(
    center: GeoPoint,
    radiusMeters: Double,
    focus: GeoPoint?,
    onCenterChange: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val latestOnCenterChange = rememberUpdatedState(onCenterChange)
    val latestRadius = rememberUpdatedState(radiusMeters)
    val latestTheme = rememberUpdatedState(MapTheme.of(dark))
    val latestWater = rememberUpdatedState(colors.secondaryContainer.toArgb())

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var radiusPixels by remember { mutableFloatStateOf(0f) }

    val view = remember {
        // Initialised here, so the renderer stays off the editor's cold-start path unless a map opens.
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { loaded ->
                loaded.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    setAttributionTintColor(colors.primary.toArgb())
                }
                loaded.setMinZoomPreference(MIN_ZOOM)
                loaded.cameraPosition = CameraPosition.Builder()
                    .target(center.toLatLng())
                    .zoom(zoomFor(radiusMeters, center.latitude))
                    .build()
                loaded.addOnCameraMoveListener {
                    val target = loaded.cameraPosition.target ?: return@addOnCameraMoveListener
                    radiusPixels = loaded.radiusPixels(target, latestRadius.value)
                    latestOnCenterChange.value(GeoPoint(target.latitude, target.longitude))
                }
                // Every style load is themed here, however it was started, so a retried load is themed too.
                addOnDidFinishLoadingStyleListener {
                    loaded.style?.let { latestTheme.value.applyTo(it, latestWater.value) }
                }
                // The first request of a process can fail, e.g. before the certificate transparency log list is ready.
                var failures = 0
                addOnDidFailLoadingMapListener {
                    if (++failures <= STYLE_RETRIES) postDelayed({ loaded.setStyle(MapTheme.STYLE) }, STYLE_RETRY_MILLIS)
                }
                map = loaded
            }
        }
    }

    LaunchedEffect(map, latestTheme.value, latestWater.value) {
        // Reloaded rather than repainted, so a theme change starts again from the base colors.
        map?.setStyle(MapTheme.STYLE)
    }
    LaunchedEffect(map, radiusMeters) {
        val loaded = map ?: return@LaunchedEffect
        loaded.cameraPosition.target?.let { radiusPixels = loaded.radiusPixels(it, radiusMeters) }
    }
    LaunchedEffect(map, focus) {
        val loaded = map ?: return@LaunchedEffect
        focus?.let { loaded.animateCamera(CameraUpdateFactory.newLatLng(it.toLatLng())) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }

    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        // The circle is centered on the pin, which never moves, so it is drawn over the map.
        Canvas(Modifier.fillMaxSize()) {
            if (radiusPixels <= 0f) return@Canvas
            drawCircle(colors.primary.copy(alpha = FILL_ALPHA), radiusPixels)
            drawCircle(colors.primary, radiusPixels, style = Stroke(width = 2.dp.toPx()))
        }
        // The pin's tip, not its middle, marks the center.
        Icon(
            painterResource(R.drawable.ic_pin),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.align(Alignment.Center).size(PIN_SIZE).offset(y = -PIN_SIZE / 2),
        )
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** [radiusMeters] on screen at [target]: the distance to a point that far north of it. */
private fun MapLibreMap.radiusPixels(target: LatLng, radiusMeters: Double): Float {
    val middle = projection.toScreenLocation(target)
    val edge = projection.toScreenLocation(LatLng(target.latitude + radiusMeters / METERS_PER_DEGREE, target.longitude))
    return hypot(edge.x - middle.x, edge.y - middle.y)
}

/** A zoom level at which the circle spans about half the width of a phone screen. */
private fun zoomFor(radiusMeters: Double, latitude: Double): Double {
    val metersPerDpAtZoomZero = EQUATOR_METERS_PER_DP * cos(Math.toRadians(latitude))
    val wantedMetersPerDp = radiusMeters * 4 / TARGET_WIDTH_DP
    return (ln(metersPerDpAtZoomZero / wantedMetersPerDp) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** Vector maps lay out 512-dp tiles, so one dp at zoom 0 spans the equator divided by 512. */
private const val EQUATOR_METERS_PER_DP = 78_271.517
private const val METERS_PER_DEGREE = 111_320.0
private const val TARGET_WIDTH_DP = 400.0
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0
private const val FILL_ALPHA = 0.22f
private const val STYLE_RETRIES = 3
private const val STYLE_RETRY_MILLIS = 2_000L
private val PIN_SIZE = 48.dp
