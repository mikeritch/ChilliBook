package uk.chillibook.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val energyShortDate = DateTimeFormatter.ofPattern("d MMM")
private val energyFullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val energyDetailDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

internal data class EnergyPoint(val date: LocalDate, val morning: Float?, val evening: Float?)
internal data class EnergySeries(val points: List<EnergyPoint>, val paired: Int, val lower: Int)

internal fun energySeries(index: PatternIndex, start: LocalDate, end: LocalDate, weekly: Boolean): EnergySeries {
    var paired = 0
    var lower = 0
    fun median(values: List<Int>): Float? {
        if (values.isEmpty()) return null
        val ordered = values.sorted()
        return (ordered[(ordered.size - 1) / 2] + ordered[ordered.size / 2]) / 2f
    }
    val points = dateSequence(start, end).chunked(if (weekly) 7 else 1).map { dates ->
        val mornings = mutableListOf<Int>()
        val evenings = mutableListOf<Int>()
        for (date in dates) {
            val am = index.metric(date, "am", null)
            val pm = index.metric(date, "pm", null)
            if (am != null) mornings.add(am)
            if (pm != null) evenings.add(pm)
            if (am != null && pm != null) {
                paired++
                if (pm < am) lower++
            }
        }
        EnergyPoint(dates.first(), median(mornings), median(evenings))
    }.toList()
    return EnergySeries(points, paired, lower)
}

@Composable
internal fun PatternsEnergy(index: PatternIndex, start: LocalDate, end: LocalDate, weekly: Boolean,
                           onDate: (String) -> Unit, onToday: () -> Unit, onChooseEnd: () -> Unit) {
    val series = remember(index, start, end, weekly) { energySeries(index, start, end, weekly) }
    val format = if (start.year != end.year || end.year != LocalDate.now().year)
        DateTimeFormatter.ofPattern("d MMM yyyy") else energyShortDate
    var selection by rememberSaveable(start.toEpochDay(), end.toEpochDay(), weekly) { mutableStateOf<Int?>(null) }
    var showReadings by rememberSaveable(start.toEpochDay(), end.toEpochDay(), weekly) { mutableStateOf(false) }
    var readingsWeek by rememberSaveable(start.toEpochDay(), end.toEpochDay(), weekly) { mutableStateOf<Long?>(null) }
    val dates = remember(series) { series.points.map { it.date } }
    val mornings = remember(series) { series.points.map { it.morning } }
    val evenings = remember(series) { series.points.map { it.evening } }
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val range = "${start.format(format)} – ${end.format(format)}"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Choose end date",
            onClick = onChooseEnd).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(range, Modifier.weight(1f), style = type.bodyMedium, color = colors.onSurfaceVariant)
            Text("⌄", color = colors.primary)
        }
        if (weekly) Text("Weekly median ratings", style = type.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("● Morning", color = colors.primary, style = type.labelMedium)
            Text("◆ Evening", color = colors.tertiary, style = type.labelMedium)
        }
        PatternChart(
            description = "Morning and evening energy chart",
            dates = dates,
            series = listOf(
                PatternChartSeries(mornings, colors.primary),
                PatternChartSeries(evenings, colors.tertiary, diamond = true)
            ),
            scaleTop = 4f,
            yLabels = listOf("High", "Medium", "Very low"),
            daily = !weekly,
            selected = selection,
            onSelect = { selection = it }
        )
        selection?.let(series.points::getOrNull)?.let { point ->
            val weekEnd = minOf(point.date.plusDays(6), end)
            val day = point.date.format(energyDetailDate)
            val title = if (weekly && point.date != weekEnd) "$day – ${weekEnd.format(energyDetailDate)}" else day
            PatternPointDetails(title = title, onClose = { selection = null }) {
                val median = if (weekly) " median" else ""
                Text("Morning$median · ${energyPointLabel(point.morning)}", style = type.bodyMedium)
                Text("Evening$median · ${energyPointLabel(point.evening)}", style = type.bodyMedium)
                if (weekly) {
                    PatternsLink("View this week’s daily readings") {
                        readingsWeek = point.date.toEpochDay()
                        showReadings = true
                    }
                } else {
                    PatternsLink("Open diary entry") { onDate(point.date.toString()) }
                }
            }
        }
        val note = when {
            series.points.none { it.morning != null || it.evening != null } -> "No energy readings in this period."
            series.paired == 0 -> "There are no paired morning and evening readings in this period."
            series.paired == 1 -> if (series.lower == 1) "Your energy was lower by evening on the one day you recorded both."
                else "On the one day you recorded both, your evening energy was the same or higher."
            else -> "Your energy was lower by evening on ${series.lower} of the ${series.paired} days you recorded both."
        }
        Text(note, style = type.bodyMedium)
        if (series.points.none { it.morning != null || it.evening != null }) PatternsLink("Go to Today", onToday)
        PatternsLink("View daily readings") {
            readingsWeek = null
            showReadings = true
        }
    }
    if (showReadings) {
        val week = readingsWeek?.let(LocalDate::ofEpochDay)
        val readingsStart = week ?: start
        val readingsEnd = week?.let { minOf(it.plusDays(6), end) } ?: end
        AlertDialog(onDismissRequest = { showReadings = false }, title = { Text("Energy readings") },
            text = {
                LazyColumn {
                    items((readingsEnd.toEpochDay() - readingsStart.toEpochDay() + 1).toInt()) { offset ->
                        val date = readingsEnd.minusDays(offset.toLong())
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                            showReadings = false
                            onDate(date.toString())
                        }.padding(vertical = 10.dp)) {
                            Text(date.format(energyFullDate), style = type.titleSmall)
                            for ((slot, label) in listOf("am" to "Morning", "pm" to "Evening")) {
                                val value = index.metric(date, slot, null)?.let(energyLabels::getOrNull) ?: "Not recorded"
                                Text("$label · $value", style = type.bodyMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }, confirmButton = { TextButton(onClick = { showReadings = false }) { Text("Close") } })
    }
}

private fun energyPointLabel(value: Float?): String {
    if (value == null) return "Not recorded"
    val lower = value.toInt()
    val lowerLabel = energyLabels.getOrNull(lower) ?: return "Not recorded"
    if (value == lower.toFloat()) return lowerLabel
    val upperLabel = energyLabels.getOrNull(lower + 1) ?: return lowerLabel
    return "Between $lowerLabel and $upperLabel"
}
