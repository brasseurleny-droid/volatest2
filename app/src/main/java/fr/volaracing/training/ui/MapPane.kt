package fr.volaracing.training.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.volaracing.training.core.TrackPt
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Fond OpenStreetMap, sans cle d'API.
// Attention : la politique d'usage de tile.openstreetmap.org interdit un usage intensif
// dans une application diffusee (voir README).
const val OSM_STYLE = """{
 "version": 8,
 "sources": {"osm": {"type": "raster", "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
   "tileSize": 256, "maxzoom": 19, "attribution": "© OpenStreetMap contributors"}},
 "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
}"""

@Composable
fun MapPane(modifier: Modifier = Modifier, onReady: (MapLibreMap, Style) -> Unit) {
    val ctx = LocalContext.current
    val mapView = remember { MapLibre.getInstance(ctx.applicationContext); MapView(ctx) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            mapView.also { mv ->
                mv.getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(OSM_STYLE)) { style -> onReady(map, style) }
                }
            }
        },
    )
}

/** Derniere position connue (null si permission absente ou position inconnue). */
@SuppressLint("MissingPermission")
fun lastKnown(ctx: Context): Pair<Double, Double>? {
    val ok = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!ok) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) ?: return null
    return Pair(l.latitude, l.longitude)
}

fun lineFeature(pts: List<Pair<Double, Double>>): Feature =
    Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.second, it.first) }))

fun addLine(style: Style, id: String, pts: List<TrackPt>, color: Int, width: Float) {
    style.addSource(GeoJsonSource(id, lineFeature(pts.map { Pair(it.lat, it.lon) })))
    style.addLayer(LineLayer("$id-layer", id).withProperties(
        PropertyFactory.lineColor(color), PropertyFactory.lineWidth(width)))
}
