package fr.volaracing.training.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.volaracing.training.core.Fmt
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PassagesScreen(vm: AppViewModel, nav: NavController, courseId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by vm.passages(courseId).collectAsState(emptyList())
    var selected by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Passages", style = MaterialTheme.typography.displaySmall)
        Text(
            if (selected == null) "Touchez deux passages pour les comparer."
            else "Touchez un deuxième passage pour comparer.",
            color = VGray,
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item { Text("Aucun passage enregistré.", color = VGray) }
            items(list, key = { it.id }) { p ->
                val isSel = selected == p.id
                Card(
                    colors = CardDefaults.cardColors(containerColor = VSurface),
                    border = if (isSel) BorderStroke(3.dp, VAccent) else null,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val first = selected
                        when {
                            first == null -> selected = p.id
                            first == p.id -> selected = null
                            else -> { selected = null; nav.navigate("compare/$first/${p.id}") }
                        }
                    },
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Fmt.time(p.timeNs), style = MaterialTheme.typography.displaySmall)
                            Text(
                                if (p.lowQuality) "QUALITÉ FAIBLE" else "QUALITÉ OK",
                                color = if (p.lowQuality) VAccent else VGreen,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            String.format(Locale.US, "Vmax %.1f km/h   G max %.2f   session %d", Fmt.kmh(p.vMax), p.gMax, p.sessionId),
                            color = VGray,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BigButton("Exporter", Modifier.weight(1f), filled = false) {
                                scope.launch {
                                    val msg = try {
                                        "${vm.exportSession(ctx, p)} fichiers dans Téléchargements/VolaRacing"
                                    } catch (e: Exception) { "Export impossible : ${e.message}" }
                                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                            BigButton("Partager", Modifier.weight(1f), filled = false) {
                                scope.launch {
                                    try {
                                        val i = vm.shareIntent(ctx, p)
                                        ctx.startActivity(Intent.createChooser(i, "Partager la session").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "Partage impossible : ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        BigButton("Retour", Modifier.fillMaxWidth(), filled = false) { nav.popBackStack() }
    }
}
