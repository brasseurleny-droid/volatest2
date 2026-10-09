package fr.volaracing.training.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import fr.volaracing.training.core.Fmt
import fr.volaracing.training.data.Course
import fr.volaracing.training.recording.RecordingService
import kotlinx.coroutines.delay
import java.util.Locale

/** Ecran d'effort : chiffres tres grands, un seul gros bouton, interface minimale. */
@Composable
fun RunScreen(vm: AppViewModel, nav: NavController, courseId: Long) {
    val ctx = LocalContext.current
    val st by RecordingService.state.collectAsState()
    var course by remember { mutableStateOf<Course?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    val mine = st.active && st.courseId == courseId

    LaunchedEffect(courseId) { course = vm.course(courseId) }
    LaunchedEffect(mine) { while (mine) { now = SystemClock.elapsedRealtimeNanos(); delay(30) } }

    val view = LocalView.current
    DisposableEffect(mine) { view.keepScreenOn = mine; onDispose { view.keepScreenOn = false } }

    fun startRec() {
        val i = Intent(ctx, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START).putExtra(RecordingService.EXTRA_COURSE, courseId)
        ContextCompat.startForegroundService(ctx, i)
    }
    fun send(action: String) { ctx.startService(Intent(ctx, RecordingService::class.java).setAction(action)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) startRec()
        else Toast.makeText(ctx, "La localisation précise est nécessaire pour chronométrer.", Toast.LENGTH_LONG).show()
    }
    fun onStart() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startRec() else launcher.launch(missing.toTypedArray())
    }

    val running = mine && st.runStartNs > 0
    val shown = if (running) now - st.runStartNs else if (mine) st.lastTimeNs else 0L
    val ready = course?.let { it.aLat != null && it.bLat != null } == true

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(course?.name ?: "", color = VGray, style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                !ready -> "Parcours incomplet"
                !mine -> "Prêt"
                st.fixes == 0 -> "Recherche du signal GPS..."
                running -> "COURSE EN COURS"
                else -> "Franchissez la porte A"
            },
            color = if (running) VAccent else VGray,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            Fmt.time(shown), style = MaterialTheme.typography.displayLarge,
            color = if (running) VAccent else VText, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Big("Vitesse", String.format(Locale.US, "%.0f", st.speedKmh), "km/h")
            Big("Passages", st.passages.toString(), "")
        }
        if (mine) {
            Text(
                "GPS ± ${if (st.hAcc >= 0) String.format(Locale.US, "%.0f", st.hAcc) else "?"} m  -  ${st.sats} satellites",
                color = VGray,
            )
        }
        Column(Modifier.weight(1f)) {}
        if (mine) BigButton("Marquer", Modifier.fillMaxWidth(), filled = false, height = 72.dp) { send(RecordingService.ACTION_MARK) }
        BigButton(
            if (mine) "ARRÊTER" else "DÉMARRER", Modifier.fillMaxWidth(), height = 120.dp, enabled = ready || mine,
        ) { if (mine) send(RecordingService.ACTION_STOP) else onStart() }
        if (!mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Passages", Modifier.weight(1f), filled = false) { nav.navigate("passages/$courseId") }
                BigButton("Retour", Modifier.weight(1f), filled = false) { nav.popBackStack() }
            }
        }
    }
}

@Composable
private fun Big(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = VGray)
        Text(value, style = MaterialTheme.typography.displaySmall)
        if (unit.isNotEmpty()) Text(unit, color = VGray)
    }
}
