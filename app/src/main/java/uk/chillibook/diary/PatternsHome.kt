package uk.chillibook.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.LocalDate

private enum class PatternsPage { Overview, Activities, Symptoms, Medication }
private val ranges = listOf(7, 28, 90, 0)
private fun rangeLabel(days: Int) = if (days == 0) "All time" else "$days days"

@Composable
fun PatternsScreen(diary: Diary, onDate: (String) -> Unit, onToday: () -> Unit, initialCourseId: String? = null) {
    val index = remember(diary) { PatternIndex(diary) }
    val today = LocalDate.now()
    val context = LocalContext.current
    var period by rememberSaveable { mutableIntStateOf(7) }
    var endDate by rememberSaveable { mutableStateOf<String?>(null) }
    val end = endDate?.let(LocalDate::parse)?.coerceAtMost(today) ?: today
    val start = if (period == 0) minOf(index.first ?: end, end) else end.minusDays(period - 1L)
    var page by rememberSaveable(initialCourseId) {
        mutableStateOf(if (initialCourseId == null) PatternsPage.Overview else PatternsPage.Medication)
    }
    var choiceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var courseId by rememberSaveable { mutableStateOf(initialCourseId) }
    var measure by rememberSaveable { mutableStateOf<MedicationMeasure?>(null) }
    var symptom by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewDate by rememberSaveable { mutableStateOf<String?>(null) }
    val states = rememberSaveableStateHolder()
    val overview = { page = PatternsPage.Overview }
    BackHandler(page != PatternsPage.Overview, onBack = overview)
    when (page) {
        PatternsPage.Activities, PatternsPage.Symptoms -> PatternExplorer(
            diary, index, start, end, today, page == PatternsPage.Activities, onDate, onToday, overview)
        PatternsPage.Medication -> if (diary.courses.isNotEmpty()) {
            MedicationPatterns(diary, index, today, courseId, onDate, initialChoiceKey = choiceKey,
                initialMeasure = measure, initialSymptom = symptom, initialDay = reviewDate,
                initialStart = start.takeIf { choiceKey != null }, initialEnd = end.takeIf { choiceKey != null },
                exit = overview)
        } else LaunchedEffect(Unit) { overview() }
        PatternsPage.Overview -> states.SaveableStateProvider("overview") {
            val colors = MaterialTheme.colorScheme
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PatternsHeader(period) { period = it }
                PatternsCard("Your energy", hero = true) {
                    PatternsEnergy(index, start, end, weekly = period == 90 || period == 0, onDate = onDate,
                        onToday = onToday, onChooseEnd = { chooseDiaryDate(context, end.toString()) { endDate = it } })
                }
                PatternsCard("Activities", R.drawable.diary_sprout, colors.primaryContainer) {
                    ActivityComparisonCard(index, start, end, today, onDate) { page = PatternsPage.Activities }
                }
                PatternsCard("Symptoms", R.drawable.cloud, colors.tertiaryContainer) {
                    SymptomComparisonCard(index, start, end, today, onDate) { page = PatternsPage.Symptoms }
                }
                PatternsCard("Medications", R.drawable.diary_pill, colors.secondaryContainer) {
                    MedicationOverview(diary, index, start, end, today) { key, course, selected, name, date ->
                        choiceKey = key
                        courseId = course
                        measure = selected
                        symptom = name
                        reviewDate = date
                        page = PatternsPage.Medication
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternsHeader(period: Int, choose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val large = LocalDensity.current.fontScale > 1.35f
    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Patterns", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Serif)
            if (!large) Image(painterResource(R.drawable.diary_plot), null, Modifier.padding(start = 5.dp).size(36.dp))
        }
    }
    val picker: @Composable () -> Unit = {
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(rangeLabel(period))
                Text("  ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ranges.forEach { days ->
                    DropdownMenuItem(text = { Text(rangeLabel(days)) }, onClick = {
                        expanded = false
                        choose(days)
                    }, trailingIcon = if (days == period) ({ Text("●", color = MaterialTheme.colorScheme.primary) }) else null)
                }
            }
        }
    }
    if (large) Column { title(); picker() }
    else Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) { title(); picker() }
}

@Composable
private fun PatternsCard(title: String, icon: Int? = null, accent: Color = Color.Transparent, hero: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val top = if (hero) lerp(surface, colors.primaryContainer, .38f) else surface
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, colors.outlineVariant), color = surface) {
        Column(Modifier.background(Brush.linearGradient(listOf(top, surface)))
            .padding(horizontal = 15.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) Box(Modifier.size(32.dp).background(accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(icon), null, Modifier.size(24.dp))
                }
                Text(title, Modifier.semantics { heading() }, fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.headlineSmall)
            }
            content()
        }
    }
}

@Composable
internal fun PatternsLink(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainerLow, contentColor = colors.primary) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
