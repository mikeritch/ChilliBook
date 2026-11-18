package uk.chillibook.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*

private val paper = lightColorScheme(
    background = Color(0xFFFAF7F1), surface = Color(0xFFFFFCF7), onSurface = Color(0xFF322A37),
    onBackground = Color(0xFF322A37), primary = Color(0xFF28665B), onPrimary = Color.White,
    onSurfaceVariant = Color(0xFF665D66), surfaceVariant = Color(0xFFEEE8DF),
    outline = Color(0xFF837B74), outlineVariant = Color(0xFFE3DBD1), tertiary = Color(0xFF795576),
    secondaryContainer = Color(0xFFDCEBE4), onSecondaryContainer = Color(0xFF205B50))
private val night = darkColorScheme(
    background = Color(0xFF141526), surface = Color(0xFF20213A), onSurface = Color(0xFFFBF4FF),
    onBackground = Color(0xFFFBF4FF), primary = Color(0xFF65E5CD), onPrimary = Color(0xFF003D35),
    primaryContainer = Color(0xFF155349), onPrimaryContainer = Color(0xFFB4FFEC),
    secondary = Color(0xFFACBCFF), onSecondary = Color(0xFF172451),
    secondaryContainer = Color(0xFF303C65), onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFFEBA6FF), onTertiary = Color(0xFF481356),
    onSurfaceVariant = Color(0xFFD2CCE5), surfaceVariant = Color(0xFF32314D),
    surfaceContainerLow = Color(0xFF1C1C32), surfaceContainer = Color(0xFF27263E),
    surfaceContainerHigh = Color(0xFF31304B), surfaceContainerHighest = Color(0xFF3A3855),
    outline = Color(0xFFAAA2BF), outlineVariant = Color(0xFF4A4562), error = Color(0xFFFF95A0))

@Composable
fun DiaryTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) night else paper, content = content)
}

@Composable
fun TitleArt(title: String, art: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 43.sp, lineHeight = 50.sp,
            fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onBackground)
        if (LocalDensity.current.fontScale < 1.5f) {
            val icon = when (art) {
                "am" -> R.drawable.diary_sun
                "pm" -> R.drawable.diary_moon
                "history" -> R.drawable.book_icon
                else -> R.drawable.diary_plot
            }
            Image(painterResource(icon), null, Modifier.padding(end = 8.dp).size(64.dp))
        }
    }
}

@Composable
internal fun categoryAccent(category: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    return when (category) {
        "Work" -> if (dark) Color(0xFF9DB5FF) else Color(0xFF475A8F)
        "Household" -> if (dark) Color(0xFFFFB47E) else Color(0xFF895029)
        "Socialising", "Symptoms" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

// Order runs from difficult to comfortable. Each endpoint has a separate dark-theme ink.
private val paperScale = listOf(0xFF92465A, 0xFF945339, 0xFF786021, 0xFF4C7040, 0xFF246759).map { Color(it) }
private val nightScale = listOf(0xFFFF94AE, 0xFFFFAE86, 0xFFF7D477, 0xFFB1E68D, 0xFF64E1C4).map { Color(it) }

@Composable
internal fun scaleAccent(labels: List<String>, index: Int): Color? {
    val reverse = labels == severityLabels
    if (!reverse && labels != energyLabels && labels != sleepLabels && labels != wellbeingLabels) return null
    val step = ((if (reverse) labels.lastIndex - index else index) * 4f / labels.lastIndex).roundToInt()
    return (if (MaterialTheme.colorScheme.background.luminance() < .5f) nightScale else paperScale)[step]
}

@Composable
internal fun DiaryIcon(icon: Int, accent: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp).background(accent.copy(alpha = .12f), CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(if (icon == R.drawable.diary_spark) 29.dp else 23.dp), tint = accent)
    }
}
