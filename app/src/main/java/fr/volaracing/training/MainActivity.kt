package fr.volaracing.training

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.volaracing.training.ui.AppViewModel
import fr.volaracing.training.ui.BigButton
import fr.volaracing.training.ui.CompareScreen
import fr.volaracing.training.ui.EditScreen
import fr.volaracing.training.ui.HomeScreen
import fr.volaracing.training.ui.PassagesScreen
import fr.volaracing.training.ui.RunScreen
import fr.volaracing.training.ui.SettingsScreen
import fr.volaracing.training.ui.VBg
import fr.volaracing.training.ui.VolaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VolaTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val nav = rememberNavController()
    var warn by remember { mutableStateOf(!vm.prefs.warningAccepted) }

    if (warn) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Avertissement") },
            text = {
                Text(
                    "Vola Racing Training est un outil d'entraînement. Ne manipulez pas le " +
                        "téléphone en mouvement. Cette application n'est pas un équipement de sécurité " +
                        "et ne remplace ni casque, ni protections, ni jugement. " +
                        "Toutes les données restent sur votre téléphone."
                )
            },
            confirmButton = { BigButton("J'ai compris") { vm.prefs.warningAccepted = true; warn = false } },
        )
    }

    Surface(Modifier.fillMaxSize(), color = VBg) {
        NavHost(nav, startDestination = "home") {
            composable("home") { HomeScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                EditScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("run/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                RunScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable("passages/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                PassagesScreen(vm, nav, it.arguments!!.getLong("id"))
            }
            composable(
                "compare/{a}/{b}",
                listOf(navArgument("a") { type = NavType.LongType }, navArgument("b") { type = NavType.LongType }),
            ) { CompareScreen(vm, nav, it.arguments!!.getLong("a"), it.arguments!!.getLong("b")) }
        }
    }
}
