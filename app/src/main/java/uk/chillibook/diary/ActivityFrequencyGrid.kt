package uk.chillibook.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val brief = DateTimeFormatter.ofPattern("d MMM")
private val full = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
private val monthYear = DateTimeFormatter.ofPattern("MMM\nyyyy")
private val dayMonth = DateTimeFormatter.ofPattern("d\nMMM")
private fun dayCount(n: Int) = if (n == 1) "1 day" else "$n days"

@Composable
internal fun ActivityFrequencyGrid(index: PatternIndex, start: LocalDate, end: LocalDate, onDate: (String) -> Unit) {
    val grid = remember(index, start, end) { index.activityFrequency(start, end) }
    val span = ChronoUnit.DAYS.between(start, end) + 1
    val unit = when (span) {
        in 1..7 -> "day"
        in 8..31 -> "week"
        else -> "month"
    }
    var selectedRow by rememberSaveable(start, end) { mutableStateOf<String?>(null) }
    var selectedBucket by rememberSaveable(start, end) { mutableIntStateOf(-1) }
    Text("Activity frequency", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.Serif)
    Text("Days recorded each $unit · Tap a count for dates", style = MaterialTheme.typography.bodyMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelWidth = maxWidth * .34f
        val minCell = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
        val capacity = ((maxWidth - labelWidth) / (minCell + 4.dp)).toInt().coerceAtLeast(1)
        val pages = (grid.buckets.size + capacity - 1) / capacity
        var page by rememberSaveable(start, end, capacity) { mutableIntStateOf(if (span > 31) (pages - 1).coerceAtLeast(0) else 0) }
        val first = page.coerceIn(0, (pages - 1).coerceAtLeast(0)) * capacity
        val visible = first until minOf(first + capacity, grid.buckets.size)
        val cellWidth = (maxWidth - labelWidth) / minOf(capacity, grid.buckets.size).coerceAtLeast(1) - 4.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (pages > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { page--; selectedRow = null }, enabled = page > 0) { Text("Earlier") }
                    TextButton(onClick = { page++; selectedRow = null }, enabled = page + 1 < pages) { Text("Later") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when (unit) {
                    "week" -> "Week of"
                    "month" -> "Month"
                    else -> "Date"
                },
                    Modifier.width(labelWidth), style = MaterialTheme.typography.labelMedium)
                visible.forEach { b ->
                    Text(grid.buckets[b].start.format(if (unit == "month") monthYear else dayMonth),
                        Modifier.width(cellWidth), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
            grid.rows.forEach { row ->
                val name = row.name.removePrefix(CUSTOM_ACTIVITY_PREFIX)
                val cells = grid.cells.getValue(row.name)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, Modifier.width(labelWidth).padding(start = if (row.child) 10.dp else 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (row.child) FontWeight.Normal else FontWeight.SemiBold)
                    visible.forEach { b ->
                        val cell = cells[b]
                        val count = cell.dates.size
                        val selected = selectedRow == row.name && selectedBucket == b
                        val shape = RoundedCornerShape(9.dp)
                        val colors = MaterialTheme.colorScheme
                        val color = if (count == 0) colors.surfaceContainer else
                            lerp(colors.primaryContainer, colors.primary,
                                .32f * count / (ChronoUnit.DAYS.between(grid.buckets[b].start, grid.buckets[b].end) + 1))
                        Box(Modifier.width(cellWidth).heightIn(min = 48.dp).clip(shape).background(color)
                            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
                            .clickable(role = Role.Button) {
                                selectedRow = if (selected) null else row.name
                                selectedBucket = b
                            }.semantics {
                                val observed = if (cell.recordedDays == 0) "No diary entries"
                                    else if (count == 1) "1 recorded day" else "$count recorded days"
                                contentDescription = "${row.name}, ${grid.buckets[b].start}, $observed"
                                stateDescription = if (selected) "Expanded" else "Collapsed"
                            }, contentAlignment = Alignment.Center) {
                            Text(if (cell.recordedDays == 0) "—" else count.toString(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (selectedRow == row.name && selectedBucket in visible) {
                    val bucket = grid.buckets[selectedBucket]
                    val cell = cells[selectedBucket]
                    FrequencyDetails(index, name, bucket, cell, onDate) { selectedRow = null }
                }
            }
        }
    }
}

@Composable
private fun FrequencyDetails(index: PatternIndex, name: String, bucket: FrequencyBucket, cell: FrequencyCell,
                             onDate: (String) -> Unit, close: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f).semantics { heading() }, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = close) { Text("Close") }
        }
        val period = if (bucket.start == bucket.end) bucket.start.format(brief)
            else "${bucket.start.format(brief)} – ${bucket.end.format(brief)}"
        Text("$period · ${dayCount(cell.dates.size)} recorded", style = MaterialTheme.typography.bodyMedium)
        if (cell.recordedDays == 0) Text("No diary entries in this period.")
        if (cell.noDays > 0) Text("Answered No on ${dayCount(cell.noDays)}.")
        val unknown = cell.recordedDays - cell.noDays - cell.dates.size
        if (unknown > 0) Text("Activity unrecorded on ${dayCount(unknown)} with diary entries.")
        val today = LocalDate.now()
        cell.dates.forEach { date ->
            Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(role = Role.Button, onClickLabel = "Open diary entry for $date") { onDate(date.toString()) }
                .semantics { contentDescription = "Open diary entry for $date" }
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(date.format(full), fontWeight = FontWeight.SemiBold)
                val wellbeing = index.days[date.toString()]?.wellbeing?.let(wellbeingLabels::getOrNull)
                FrequencyReading("Evening wellbeing", wellbeing ?: "No entry")
                for (lag in 1L..2L) {
                    val follow = date.plusDays(lag)
                    val label = if (lag == 1L) "Next morning’s energy" else "Two mornings later"
                    val energy = if (follow > today) "Still to come" else
                        index.metric(follow, "am", null)?.let(energyLabels::getOrNull) ?: "No entry"
                    FrequencyReading("$label · ${follow.format(brief)}", energy)
                }
            }
        }
    }
}

@Composable
private fun FrequencyReading(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}
