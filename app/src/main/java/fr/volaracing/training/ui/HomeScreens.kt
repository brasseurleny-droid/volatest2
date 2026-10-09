package fr.volaracing.training.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.volaracing.training.data.Act
import fr.volaracing.training.data.Profile

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val courses by vm.courses.collectAsState()
    val list = courses.filter { it.activity == vm.activity }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VOLA RACING", style = MaterialTheme.typography.displaySmall, color = VAccent)
        Text("Training", style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Act.all.forEach { a ->
                BigButton(Act.label(a), Modifier.weight(1f), filled = vm.activity == a, height = 64.dp) {
                    vm.selectActivity(a)
                }
            }
        }

        Text("Parcours", color = VGray)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item {
                Text("Aucun parcours pour cette activité. Créez-en un.", color = VGray)
            }
            items(list, key = { it.id }) { c ->
                Card(colors = CardDefaults.cardColors(containerColor = VSurface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge)
                        if (c.aLat == null || c.bLat == null) {
                            Text("Parcours incomplet : placez A et B", color = VAccent)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BigButton("Passages", Modifier.weight(1f), filled = false) { nav.navigate("passages/${c.id}") }
                            BigButton("Démarrer", Modifier.weight(1f)) { nav.navigate("run/${c.id}") }
                            BigButton("Parcours", Modifier.weight(1f), filled = false) { nav.navigate("edit/${c.id}") }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton("Nouveau parcours", Modifier.weight(1f), height = 64.dp) { nav.navigate("edit/0") }
            BigButton("Réglages", Modifier.weight(1f), filled = false, height = 64.dp) { nav.navigate("settings") }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.displaySmall)
        Act.all.forEach { act ->
            Text("Profil ${Act.label(act)}", style = MaterialTheme.typography.titleLarge, color = VAccent)
            ProfileEditor(vm, act)
        }
        BigButton("Télécharger une zone hors ligne (bientôt)", Modifier.fillMaxWidth(), filled = false, enabled = false) {}
        Text("Vola Racing Training 0.1.0 - mode solo. Toutes les données restent sur ce téléphone.", color = VGray)
        BigButton("Retour", Modifier.fillMaxWidth()) { nav.popBackStack() }
    }
}

@Composable
private fun ProfileEditor(vm: AppViewModel, act: String) {
    var p by remember { mutableStateOf(vm.prefs.profile(act)) }
    fun upd(n: Profile) { p = n; vm.prefs.saveProfile(act, n) }
    Stepper("Largeur de porte (nouveaux parcours)", p.gateWidth, "m", 5.0, 5.0, 100.0) { upd(p.copy(gateWidth = it)) }
    Stepper("Vitesse minimale de détection", p.minSpeedKmh, "km/h", 1.0, 0.0, 60.0) { upd(p.copy(minSpeedKmh = it)) }
    Stepper("Fenêtre de filtrage de la vitesse", p.windowS, "s", 0.5, 0.5, 5.0) { upd(p.copy(windowS = it)) }
    Stepper("Seuil de précision GPS (qualité faible au-delà)", p.accThreshold, "m", 5.0, 5.0, 100.0) { upd(p.copy(accThreshold = it)) }
}
