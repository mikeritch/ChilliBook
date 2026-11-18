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
    background = Color(0xFF241F29), surface = Color(0xFF2E2933), onSurface = Color(0xFFF5EDF3),
    onBackground = Color(0xFFF5EDF3), primary = Color(0xFFA8D8C7), onPrimary = Color(0xFF193B34),
    onSurfaceVariant = Color(0xFFCEC1CD), surfaceVariant = Color(0xFF39313D),
    outline = Color(0xFFA498A6), outlineVariant = Color(0xFF493E4D), tertiary = Color(0xFFDAB3DA),
    secondaryContainer = Color(0xFF354B45), onSecondaryContainer = Color(0xFFA8D8C7))

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
        "Work" -> if (dark) Color(0xFFB7C7FA) else Color(0xFF475A8F)
        "Household" -> if (dark) Color(0xFFF4BE92) else Color(0xFF895029)
        "Socialising", "Symptoms" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
}

// Order runs from difficult to comfortable. Each endpoint has a separate dark-theme ink.
private val paperScale = listOf(0xFF92465A, 0xFF945339, 0xFF786021, 0xFF4C7040, 0xFF246759).map { Color(it) }
private val nightScale = listOf(0xFFF0AEC1, 0xFFF3B69B, 0xFFE6CA85, 0xFFB7D6A4, 0xFF9DDAC3).map { Color(it) }

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
        Icon(painterResource(icon), null, Modifier.size(23.dp), tint = accent)
    }
}
