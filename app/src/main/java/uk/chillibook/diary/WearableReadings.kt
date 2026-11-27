package uk.chillibook.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

internal fun stepCount(steps: Int) = NumberFormat.getIntegerInstance().format(steps)

internal fun sleepDuration(minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours == 0 -> "$remainder min"
        remainder == 0 -> "$hours h"
        else -> "$hours h $remainder min"
    }
}

internal fun wearableSleepLabel(reading: WearableReading?, today: Boolean = false): String = when {
    (reading?.sleepPeriods?.size ?: 0) > 1 -> "Sleep periods"
    today -> "Last night"
    else -> "Previous night"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WearableSummary(reading: WearableReading) {
    val colors = MaterialTheme.colorScheme
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reading.steps?.let { steps ->
            WearableStat("Steps", stepCount(steps), R.drawable.diary_trainer,
                colors.primaryContainer, colors.onPrimaryContainer)
        }
        reading.sleepMinutes?.let { minutes ->
            WearableStat(wearableSleepLabel(reading), sleepDuration(minutes), R.drawable.diary_moon,
                colors.tertiaryContainer, colors.onTertiaryContainer)
        }
    }
}

@Composable
private fun WearableStat(label: String, value: String, icon: Int, background: Color, ink: Color) {
    val type = MaterialTheme.typography
    Surface(shape = MaterialTheme.shapes.medium, color = background, contentColor = ink,
        modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(30.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(label, style = type.labelMedium)
                Text(value, style = type.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun WearableReadingValues(
    reading: WearableReading?,
    showSources: Boolean = true,
    missingLabel: String = "Not recorded",
) {
    Text("Steps · " + (reading?.steps?.let(::stepCount) ?: missingLabel),
        style = MaterialTheme.typography.bodyMedium)
    if (showSources && reading?.steps != null) reading.stepsSource?.let { WearableSource(it) }
    Text("${wearableSleepLabel(reading)} · " + (reading?.sleepMinutes?.let(::sleepDuration) ?: missingLabel),
        style = MaterialTheme.typography.bodyMedium)
    if (showSources && reading?.sleepMinutes != null) reading.sleepSource?.let { WearableSource(it) }
}

@Composable
private fun WearableSource(source: String) {
    Text("Source · $source", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private data class WearablePoint(
    val start: LocalDate,
    val end: LocalDate,
    val steps: Double?,
    val sleepMinutes: Double?,
)

private fun median(values: List<Int>): Double? {
    if (values.isEmpty()) return null
    val ordered = values.sorted()
    return (ordered[(ordered.size - 1) / 2].toDouble() + ordered[ordered.size / 2]) / 2
}

private fun medianSleepDuration(minutes: Double): String = when {
    minutes % 1 == 0.0 -> sleepDuration(minutes.toInt())
    minutes < 1 -> "30 sec"
    else -> "${sleepDuration(minutes.toInt())} 30 sec"
}

@Composable
internal fun WearablePatterns(
    readings: List<WearableReading>,
    start: LocalDate,
    end: LocalDate,
    weekly: Boolean,
    onDate: (String) -> Unit,
) {
    val byDate = remember(readings) { readings.associateBy { LocalDate.parse(it.date) } }
    val points = remember(byDate, start, end, weekly) {
        dateSequence(start, end).chunked(if (weekly) 7 else 1).map { dates ->
            val recorded = dates.mapNotNull(byDate::get)
            WearablePoint(
                dates.first(),
                dates.last(),
                median(recorded.mapNotNull { it.steps }),
                median(recorded.mapNotNull { it.sleepMinutes }),
            )
        }.toList()
    }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var selectedIndex by rememberSaveable(start.toString(), end.toString(), weekly) {
        mutableStateOf<Int?>(null)
    }
    var selectedPlot by rememberSaveable(start.toString(), end.toString(), weekly) {
        mutableStateOf<String?>(null)
    }
    var showReadings by rememberSaveable(start.toString(), end.toString(), weekly) {
        mutableStateOf(false)
    }
    var readingsWeek by rememberSaveable(start.toString(), end.toString(), weekly) {
        mutableStateOf<Int?>(null)
    }
    val shortDate = DateTimeFormatter.ofPattern(
        if (start.year != end.year || end.year != LocalDate.now().year) "d MMM yyyy" else "d MMM"
    )
    Text("${start.format(shortDate)} – ${end.format(shortDate)}", style = type.bodyMedium,
        color = colors.onSurfaceVariant)
    if (points.none { it.steps != null || it.sleepMinutes != null }) {
        Text("No wearable readings in this period.", style = type.bodyMedium)
        return
    }
    if (weekly) Text("Weekly medians of recorded days", style = type.bodySmall,
        color = colors.onSurfaceVariant)

    val dates = points.map { it.start }
    val steps = points.map { it.steps?.toFloat() }
    val sleep = points.map { it.sleepMinutes?.toFloat() }
    val fullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    val point = selectedIndex?.let(points::getOrNull)
    val details: @Composable () -> Unit = {
        if (point != null) {
            val title = if (point.start == point.end) point.start.format(fullDate)
                else "${point.start.format(shortDate)} – ${point.end.format(shortDate)}"
            PatternPointDetails(title, onClose = {
                selectedIndex = null
                selectedPlot = null
            }) {
                if (weekly) {
                    val medianSteps = point.steps?.let { NumberFormat.getNumberInstance().format(it) }
                    val medianSleep = point.sleepMinutes?.let(::medianSleepDuration)
                    Text("Median steps · ${medianSteps ?: "No entry"}", style = type.bodyMedium)
                    Text("Median sleep · ${medianSleep ?: "No entry"}", style = type.bodyMedium)
                    PatternsLink("View days in this week") {
                        readingsWeek = selectedIndex
                        showReadings = true
                    }
                } else {
                    WearableReadingValues(byDate[point.start], missingLabel = "No entry")
                    PatternsLink("Open diary entry") { onDate(point.start.toString()) }
                }
            }
        }
    }
    val stepsTitle = "Steps"
    val sleepTitle = "Recorded sleep duration"
    WearablePlot(stepsTitle, steps, dates, colors.primary, 1_000, weekly, selectedIndex, {
        selectedIndex = it
        selectedPlot = stepsTitle
    }, ::stepCount)
    if (selectedPlot == stepsTitle) details()
    WearablePlot(sleepTitle, sleep, dates, colors.tertiary, 60, weekly, selectedIndex, {
        selectedIndex = it
        selectedPlot = sleepTitle
    }) { "${it / 60} h" }
    if (selectedPlot == sleepTitle) details()
    if (sleep.any { it != null }) Text("Sleep periods are totalled by wake date.",
        style = type.bodySmall, color = colors.onSurfaceVariant)
    PatternsLink("View daily wearable readings") {
        readingsWeek = null
        showReadings = true
    }
    if (showReadings) {
        val week = readingsWeek?.let(points::getOrNull)
        val firstDay = (week?.start ?: start).toEpochDay()
        val lastDay = (week?.end ?: end).toEpochDay()
        val closeReadings: () -> Unit = { showReadings = false }
        AlertDialog(
            onDismissRequest = closeReadings,
            title = { Text("Daily wearable readings") },
            text = {
                LazyColumn {
                    items((lastDay - firstDay + 1).toInt()) { offset ->
                        val date = LocalDate.ofEpochDay(lastDay - offset)
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            closeReadings()
                            onDate(date.toString())
                        }.heightIn(min = 48.dp).padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(date.format(fullDate), style = type.titleSmall)
                            WearableReadingValues(byDate[date], showSources = false)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = closeReadings) { Text("Close") } }
        )
    }
}

@Composable
private fun WearablePlot(
    title: String,
    values: List<Float?>,
    dates: List<LocalDate>,
    color: Color,
    scaleUnit: Int,
    weekly: Boolean,
    selected: Int?,
    onSelect: (Int) -> Unit,
    scaleLabel: (Int) -> String,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Text(title, style = type.titleMedium)
    val highest = values.filterNotNull().maxOrNull()
    if (highest == null) {
        Text("No readings in this period.", style = type.bodyMedium, color = colors.onSurfaceVariant)
        return
    }
    val scaleTop = maxOf(scaleUnit, (ceil(highest / scaleUnit) * scaleUnit).toInt())
    PatternChart(
        description = "$title chart",
        dates = dates,
        series = listOf(PatternChartSeries(values, color)),
        scaleTop = scaleTop.toFloat(),
        yLabels = listOf(scaleLabel(scaleTop), scaleLabel(0)),
        daily = !weekly,
        selected = selected,
        onSelect = onSelect,
    )
}
