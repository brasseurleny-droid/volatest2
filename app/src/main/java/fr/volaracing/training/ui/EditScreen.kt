package fr.volaracing.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.volaracing.training.core.Gate
import fr.volaracing.training.core.LocalFrame
import fr.volaracing.training.core.Pt
import fr.volaracing.training.data.Course
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun EditScreen(vm: AppViewModel, nav: NavController, courseId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf(vm.activity) }
    var width by remember { mutableStateOf(vm.prefs.profile(vm.activity).gateWidth) }
    var aLat by remember { mutableStateOf<Double?>(null) }
    var aLon by remember { mutableStateOf<Double?>(null) }
    var bLat by remember { mutableStateOf<Double?>(null) }
    var bLon by remember { mutableStateOf<Double?>(null) }
    var mode by remember { mutableStateOf("A") }
    var loaded by remember { mutableStateOf(false) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(courseId) {
        val c = if (courseId == 0L) null else vm.course(courseId)
        if (c != null) {
            name = c.name; activity = c.activity; width = c.gateWidth
            aLat = c.aLat; aLon = c.aLon; bLat = c.bLat; bLon = c.bLon
        } else {
            name = "Nouveau parcours"
        }
        loaded = true
    }

    // Un appui sur la carte deplace le point actif (A ou B).
    val onTap by rememberUpdatedState<(Double, Double) -> Unit> { lat, lon ->
        if (mode == "A") { aLat = lat; aLon = lon; mode = "B" } else { bLat = lat; bLon = lon }
    }

    // Camera initiale : point A, sinon derniere position connue, sinon Alpes (TODO: reglable).
    LaunchedEffect(map, loaded) {
        val m = map ?: return@LaunchedEffect
        if (!loaded) return@LaunchedEffect
        val target = if (aLat != null && aLon != null) Triple(aLat!!, aLon!!, 15.0)
        else lastKnown(ctx)?.let { Triple(it.first, it.second, 14.0) } ?: Triple(45.92, 6.87, 10.0)
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), target.third))
    }

    LaunchedEffect(style, aLat, aLon, bLat, bLon, width) {
        val s = style ?: return@LaunchedEffect
        updateEditSources(s, aLat, aLon, bLat, bLon, width)
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text("Nom du parcours") }, modifier = Modifier.fillMaxWidth(),
        )
        Stepper("Largeur de porte", width, "m", 5.0, 5.0, 100.0) { width = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Placer A", Modifier.weight(1f), filled = mode == "A") { mode = "A" }
            BigButton("Placer B", Modifier.weight(1f), filled = mode == "B") { mode = "B" }
        }
        Text(
            "Touchez la carte pour placer ${if (mode == "A") "le départ A" else "l'arrivée B"}. " +
                "Retouchez pour le déplacer.",
            color = VGray, style = MaterialTheme.typography.bodyMedium,
        )
        MapPane(Modifier.fillMaxWidth().weight(1f)) { m, st ->
            ensureEditLayers(st)
            map = m
            style = st
            m.addOnMapClickListener { ll -> onTap(ll.latitude, ll.longitude); true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Annuler", Modifier.weight(1f), filled = false) { nav.popBackStack() }
            BigButton("Enregistrer", Modifier.weight(1f)) {
                scope.launch {
                    vm.saveCourse(
                        Course(
                            id = courseId, name = name.ifBlank { "Parcours" }, activity = activity,
                            aLat = aLat, aLon = aLon, bLat = bLat, bLon = bLon, gateWidth = width,
                        )
                    )
                    nav.popBackStack()
                }
            }
        }
    }
}

private const val SRC_LINES = "edit-lines"
private const val SRC_A = "edit-a"
private const val SRC_B = "edit-b"

private fun ensureEditLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_LINES))
    style.addSource(GeoJsonSource(SRC_A))
    style.addSource(GeoJsonSource(SRC_B))
    style.addLayer(LineLayer("$SRC_LINES-l", SRC_LINES).withProperties(
        PropertyFactory.lineColor(VAccent.toArgb()), PropertyFactory.lineWidth(4f)))
    fun dot(id: String, color: Int) = style.addLayer(CircleLayer("$id-c", id).withProperties(
        PropertyFactory.circleRadius(10f), PropertyFactory.circleColor(color),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(3f)))
    dot(SRC_A, VGreen.toArgb())
    dot(SRC_B, VRed.toArgb())
}

private fun updateEditSources(
    style: Style, aLat: Double?, aLon: Double?, bLat: Double?, bLon: Double?, width: Double,
) {
    fun pt(lat: Double?, lon: Double?) =
        if (lat != null && lon != null) FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))
        else FeatureCollection.fromFeatures(emptyList<Feature>())
    style.getSourceAs<GeoJsonSource>(SRC_A)?.setGeoJson(pt(aLat, aLon))
    style.getSourceAs<GeoJsonSource>(SRC_B)?.setGeoJson(pt(bLat, bLon))

    val lines = ArrayList<Feature>()
    if (aLat != null && aLon != null && bLat != null && bLon != null) {
        val fr = LocalFrame(aLat, aLon)
        val b = fr.toLocal(bLat, bLon)
        if (b.x != 0.0 || b.y != 0.0) {
            val (ga, gb) = Gate.pair(Pt(0.0, 0.0), b, width)
            lines.add(lineFeature(listOf(Pair(aLat, aLon), Pair(bLat, bLon))))
            for (g in listOf(ga, gb)) lines.add(lineFeature(listOf(fr.toLatLon(g.p1), fr.toLatLon(g.p2))))
        }
    }
    style.getSourceAs<GeoJsonSource>(SRC_LINES)?.setGeoJson(FeatureCollection.fromFeatures(lines))
}
