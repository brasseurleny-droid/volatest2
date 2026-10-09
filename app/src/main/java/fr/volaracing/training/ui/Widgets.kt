package fr.volaracing.training.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Bouton a zone tactile large (56 dp minimum, utilisable avec des gants). */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    if (filled) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = height), enabled = enabled, shape = shape) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = modifier.heightIn(min = height), enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, VGray),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VText),
        ) { Text(text, style = MaterialTheme.typography.labelLarge) }
    }
}

fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

@Composable
fun Stepper(label: String, value: Double, unit: String, step: Double, min: Double, max: Double, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = VGray, style = MaterialTheme.typography.bodyMedium)
            Text("${fmtNum(value)} $unit", style = MaterialTheme.typography.titleLarge)
        }
        BigButton("-", Modifier.width(64.dp), filled = false) { onChange((value - step).coerceAtLeast(min)) }
        Spacer(Modifier.width(8.dp))
        BigButton("+", Modifier.width(64.dp), filled = false) { onChange((value + step).coerceAtMost(max)) }
    }
}

data class ChartLine(
    val pts: List<Pair<Double, Double>>,
    val color: Color,
    val segColors: List<Color>? = null,
)

/** Courbe simple dessinee sur Canvas (x = distance en m). */
@Composable
fun Chart(lines: List<ChartLine>, modifier: Modifier = Modifier, zeroLine: Boolean = false) {
    Canvas(modifier.fillMaxWidth().height(170.dp).background(VSurface)) {
        val all = lines.flatMap { it.pts }
        if (all.size < 2) return@Canvas
        val xMax = all.maxOf { it.first }.coerceAtLeast(1.0)
        var yMin = if (zeroLine) minOf(all.minOf { it.second }, 0.0) else 0.0
        var yMax = if (zeroLine) maxOf(all.maxOf { it.second }, 0.0) else all.maxOf { it.second }
        if (yMax - yMin < 1e-6) yMax = yMin + 1.0
        val pad = 8.dp.toPx()
        fun px(x: Double) = pad + (x / xMax).toFloat() * (size.width - 2 * pad)
        fun py(y: Double) = size.height - pad - ((y - yMin) / (yMax - yMin)).toFloat() * (size.height - 2 * pad)
        if (zeroLine) drawLine(VGray, Offset(pad, py(0.0)), Offset(size.width - pad, py(0.0)), 1.dp.toPx())
        for (l in lines) {
            for (i in 1 until l.pts.size) {
                drawLine(
                    l.segColors?.getOrNull(i - 1) ?: l.color,
                    Offset(px(l.pts[i - 1].first), py(l.pts[i - 1].second)),
                    Offset(px(l.pts[i].first), py(l.pts[i].second)),
                    strokeWidth = 3.dp.toPx(),
                )
            }
        }
    }
}
