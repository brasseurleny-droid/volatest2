package fr.volaracing.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.volaracing.training.core.Compare
import fr.volaracing.training.core.Fmt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import java.util.Locale

@Composable
fun CompareScreen(vm: AppViewModel, nav: NavController, a: Long, b: Long) {
    var data by remember { mutableStateOf<Pair<Loaded, Loaded>?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(a, b) { data = vm.loadPair(a, b); done = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Comparaison", style = MaterialTheme.typography.displaySmall)
        val d = data
        when {
            !done -> Text("Chargement...", color = VGray)
            d == null || d.first.track.size < 2 || d.second.track.size < 2 ->
                Text("Données GPS insuffisantes pour comparer ces passages.", color = VGray)
            else -> CompareContent(d.first, d.second)
        }
        BigButton("Retour", Modifier.fillMaxWidth(), filled = false) { nav.popBackStack() }
    }
}

@Composable
private fun CompareContent(la: Loaded, lb: Loaded) {
    val delta = remember(la, lb) { Compare.deltaCurve(la.track, lb.track) }
    val diffS = (lb.passage.timeNs - la.passage.timeNs) / 1e9

    Text("A en orange, B en blanc. Écart = B par rapport à A.", color = VGray)
    Text(
        String.format(Locale.US, "Écart final : %+.2f s (%s)", diffS, if (diffS > 0) "B plus lent" else "B plus rapide"),
        style = MaterialTheme.typography.headlineMedium,
        color = if (diffS > 0) VRed else VGreen,
    )

    MapPane(Modifier.fillMaxWidth().height(260.dp)) { map, style ->
        addLine(style, "trace-a", la.track, VAccent.toArgb(), 5f)
        addLine(style, "trace-b", lb.track, Color.White.toArgb(), 5f)
        map.uiSettings.setAllGesturesEnabled(false)
        val pts = (la.track + lb.track).map { LatLng(it.lat, it.lon) }
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(pts).build(), 40))
    }

    Text("Vitesse (km/h) selon la distance (m)", color = VGray)
    Chart(listOf(
        ChartLine(la.track.map { Pair(it.dist, Fmt.kmh(it.speed)) }, VAccent),
        ChartLine(lb.track.map { Pair(it.dist, Fmt.kmh(it.speed)) }, Color.White),
    ))

    Text("Écart cumulé (s) selon la distance : rouge = B perd, vert = B gagne", color = VGray)
    if (delta.size >= 2) {
        val segColors = delta.zipWithNext().map { (p, q) -> if (q.second > p.second) VRed else VGreen }
        Chart(listOf(ChartLine(delta, VGray, segColors)), zeroLine = true)
        val worst = delta.maxBy { it.second }
        val best = delta.minBy { it.second }
        Text(String.format(Locale.US, "Écart maximal en défaveur de B : %+.2f s à %.0f m", worst.second, worst.first))
        Text(String.format(Locale.US, "Écart maximal en faveur de B : %+.2f s à %.0f m", best.second, best.first))
    }

    Row(Modifier.fillMaxWidth()) {
        Text("", Modifier.weight(1.4f))
        Text("A", Modifier.weight(1f), color = VAccent, style = MaterialTheme.typography.titleLarge)
        Text("B", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
    }
    fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    TRow("Temps", Fmt.time(la.passage.timeNs), Fmt.time(lb.passage.timeNs))
    TRow("Vitesse moy.", "${f1(Fmt.kmh(la.stats.avgMs))} km/h", "${f1(Fmt.kmh(lb.stats.avgMs))} km/h")
    TRow("Vitesse max", "${f1(Fmt.kmh(la.stats.maxMs))} km/h", "${f1(Fmt.kmh(lb.stats.maxMs))} km/h")
    TRow("G max", String.format(Locale.US, "%.2f", la.passage.gMax), String.format(Locale.US, "%.2f", lb.passage.gMax))
    TRow("Durée à vit. max", "${f1(la.stats.timeAtMaxS)} s", "${f1(lb.stats.timeAtMaxS)} s")
    TRow("Distance", "${f1(la.stats.distM)} m", "${f1(lb.stats.distM)} m")
}

@Composable
private fun TRow(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1.4f), color = VGray)
        Text(a, Modifier.weight(1f))
        Text(b, Modifier.weight(1f))
    }
}
