package fr.volaracing.training.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Theme provisoire : tout est ici, a remplacer par la charte Vola ----
val VBg = Color(0xFF0E1116)
val VText = Color(0xFFFFFFFF)
val VAccent = Color(0xFFFF5A1F)
val VGray = Color(0xFF9AA4B2)
val VSurface = Color(0xFF171C23)
val VGreen = Color(0xFF2ECC71)
val VRed = Color(0xFFE74C3C)

// TODO: remplacer par la police condensee de la charte Vola (fichier .ttf dans res/font).
// En attendant : police condensee du systeme Android.
val VCondensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
val VBody = FontFamily.SansSerif

private val typo = Typography(
    displayLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 72.sp),
    displaySmall = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineMedium = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = VBody, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = VBody, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = VCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp),
)

@Composable
fun VolaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = VAccent, onPrimary = VBg,
            secondary = VGray, onSecondary = VBg,
            background = VBg, onBackground = VText,
            surface = VSurface, onSurface = VText,
            surfaceVariant = VSurface, onSurfaceVariant = VGray,
            outline = VGray,
        ),
        typography = typo,
        content = content,
    )
}
