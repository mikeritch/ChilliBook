package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val energyShortDate = DateTimeFormatter.ofPattern("d MMM")
private val energyFullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

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
    var showReadings by rememberSaveable(start.toString(), end.toString()) { mutableStateOf(false) }
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
        EnergyPlot(series.points)
        val note = when {
            series.points.none { it.morning != null || it.evening != null } -> "No energy readings in this period."
            series.paired == 0 -> "There are no paired morning and evening readings in this period."
            series.paired == 1 -> if (series.lower == 1) "Your energy was lower by evening on the one day you recorded both."
                else "On the one day you recorded both, your evening energy was the same or higher."
            else -> "Your energy was lower by evening on ${series.lower} of the ${series.paired} days you recorded both."
        }
        Text(note, style = type.bodyMedium)
        if (series.points.none { it.morning != null || it.evening != null }) PatternsLink("Go to Today", onToday)
        PatternsLink("View daily readings") { showReadings = true }
    }
    if (showReadings) {
        AlertDialog(onDismissRequest = { showReadings = false }, title = { Text("Energy readings") },
            text = {
                LazyColumn {
                    items((end.toEpochDay() - start.toEpochDay() + 1).toInt()) { offset ->
                        val date = end.minusDays(offset.toLong())
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) {
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

@Composable
private fun EnergyPlot(points: List<EnergyPoint>) {
    if (points.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val large = LocalDensity.current.fontScale > 1.35f
    val height = if (large) 158.dp else 113.dp
    val pad = if (large) 16.dp else 9.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.height(height), verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End) {
            listOf("High", "Medium", "Very low").forEach { Text(it, style = style) }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(height).semantics {
                contentDescription = "Morning and evening energy. Gaps indicate missing readings."
            }) {
                val margin = 5.dp.toPx()
                val top = pad.toPx()
                fun y(value: Float) = size.height - top - value / 4f * (size.height - 2 * top)
                fun x(i: Int) = if (points.size == 1) size.width / 2
                    else margin + (size.width - 2 * margin) * i / (points.size - 1)
                for (level in listOf(0f, 2f, 4f)) {
                    drawLine(colors.outlineVariant, Offset(margin, y(level)), Offset(size.width - margin, y(level)), 1.dp.toPx())
                }
                val r = if (points.size > 10) 2.dp.toPx() else 3.4.dp.toPx()
                for (morning in listOf(true, false)) {
                    val color = if (morning) colors.primary else colors.tertiary
                    var previous: Offset? = null
                    points.forEachIndexed { i, point ->
                        val value = if (morning) point.morning else point.evening
                        val position = value?.let { Offset(x(i), y(it)) }
                        if (position != null) {
                            previous?.let { drawLine(color, it, position, 1.8.dp.toPx()) }
                            if (morning) drawCircle(color, r, position) else drawPath(Path().apply {
                                moveTo(position.x, position.y - r)
                                lineTo(position.x + r, position.y)
                                lineTo(position.x, position.y + r)
                                lineTo(position.x - r, position.y)
                                close()
                            }, color)
                        }
                        previous = position
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (points.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
                val ticks = if (large) listOf(0, points.lastIndex) else listOf(0, points.lastIndex / 2, points.lastIndex)
                ticks.distinct().forEach { i -> Text(points[i].date.format(energyShortDate), style = style) }
            }
        }
    }
}
