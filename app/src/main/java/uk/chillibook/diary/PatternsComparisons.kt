package uk.chillibook.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val periods = listOf("That evening", "Next morning", "Second morning")
private val comparisonDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

internal fun PatternIndex.activityEnergy(start: LocalDate, end: LocalDate, today: LocalDate): Map<String, List<ComparisonGroup>> {
    val frequency = activityFrequency(start, minOf(end, today))
    return buildMap {
        for (row in frequency.rows) {
            val dates = frequency.cells.getValue(row.name).flatMap { it.dates }
            if (dates.isEmpty()) continue
            put(row.name, periods.indices.map { lag ->
                ComparisonGroup(dates.map { date ->
                    val follow = date.plusDays(lag.toLong())
                    val value = if (follow > today) null else metric(follow, if (lag == 0) "pm" else "am", null)
                    DatePair(date, follow, value)
                })
            })
        }
    }
}

@Composable
internal fun ActivityComparisonCard(index: PatternIndex, start: LocalDate, end: LocalDate, today: LocalDate,
                                   openDay: (String) -> Unit, explore: () -> Unit) {
    val matrix = remember(index, start, end, today) { index.activityEnergy(start, end, today) }
    val ordered = remember(matrix) {
        // Keep the familiar comparisons together, then show the most frequently logged activities.
        val first = listOf("Work", "Cooking", "Shopping", REST_GROUP)
        matrix.keys.sortedWith(compareBy<String> { name -> first.indexOf(name).takeIf { it >= 0 } ?: first.size }
            .thenByDescending { name -> matrix.getValue(name).first().dates.size })
    }
    var showAll by rememberSaveable(start, end) { mutableStateOf(false) }
    var activity by rememberSaveable(start, end) { mutableStateOf<String?>(null) }
    var lag by rememberSaveable { mutableIntStateOf(0) }
    Text("When was energy low?", style = MaterialTheme.typography.titleMedium)
    Text("Low or very low readings after each activity.", style = MaterialTheme.typography.bodyMedium)
    if (matrix.isEmpty()) Text("No activities recorded in this period.")
    else BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale > 1.25f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!stacked) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1.25f))
                for (period in periods) Text(period, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium)
            }
            for (key in if (showAll) ordered else ordered.take(4)) {
                val name = key.removePrefix(CUSTOM_ACTIVITY_PREFIX)
                val groups = matrix.getValue(key)
                val select: (Int) -> Unit = { lag = it; activity = key }
                if (stacked) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    groups.forEachIndexed { i, group ->
                        ActivityTile(name, i, group, Modifier.fillMaxWidth(), true) { select(i) }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, Modifier.weight(1.25f), style = MaterialTheme.typography.titleSmall)
                    groups.forEachIndexed { i, group ->
                        ActivityTile(name, i, group, Modifier.weight(1f), false) { select(i) }
                    }
                }
            }
            if (ordered.size > 4) TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer activities" else "Show all ${ordered.size} activities")
            }
            Text("Each tile shows low readings / recorded readings. A stronger colour means a larger share.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    PatternsLink("Explore activity frequency", explore)
    val selected = matrix[activity]?.get(lag)
    if (selected != null) {
        ComparisonDates("${activity.orEmpty().removePrefix(CUSTOM_ACTIVITY_PREFIX)} · ${periods[lag]}", selected.dates.size, { activity = null }, openDay) { i ->
            val pair = selected.dates[selected.dates.lastIndex - i]
            val value = when {
                pair.follow > today -> "Future"
                pair.level == null -> "Not recorded"
                else -> energyLabels[pair.level]
            }
            pair.anchor to "${pair.follow.format(comparisonDate)} · ${if (lag == 0) "Evening" else "Morning"} · $value"
        }
    }
}

@Composable
private fun ActivityTile(name: String, lag: Int, group: ComparisonGroup, modifier: Modifier, stacked: Boolean, open: () -> Unit) {
    val ratio = if (group.usable == 0) 0f else group.low.toFloat() / group.usable
    val colors = MaterialTheme.colorScheme
    val count = if (group.usable == 0) "—" else "${group.low} / ${group.usable}"
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(lerp(colors.surfaceVariant, colors.primary, ratio * .45f))
        .clickable(role = Role.Button, onClick = open).semantics {
            contentDescription = "$name, ${periods[lag]}, ${group.low} low out of ${group.usable} recorded readings. Open dates"
        }.heightIn(min = 48.dp).padding(4.dp), contentAlignment = Alignment.Center) {
        Text(if (stacked) "${periods[lag]}   $count" else count, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun SymptomComparisonCard(index: PatternIndex, start: LocalDate, end: LocalDate, today: LocalDate,
                                  openDay: (String) -> Unit, explore: () -> Unit) {
    val last = minOf(end, today)
    val ratings = remember(index, start, last) {
        val counts = sortedMapOf<String, MutableMap<String, IntArray>>()
        for (check in index.checks.values) {
            if (LocalDate.parse(check.date) !in start..last) continue
            for ((name, level) in check.symptoms) {
                val levels = counts.getOrPut(name) { mutableMapOf() }.getOrPut(check.slot) { IntArray(severityLabels.size) }
                if (level in levels.indices) levels[level]++
            }
        }
        counts
    }
    var selected by rememberSaveable(start, end) { mutableStateOf<String?>(null) }
    var slot by rememberSaveable { mutableStateOf("am") }
    Text("Morning or evening?", style = MaterialTheme.typography.titleMedium)
    Text("Share rated moderate or strong, on the same 0–100% scale.", style = MaterialTheme.typography.bodyMedium)
    if (ratings.isEmpty()) Text("No symptoms recorded in this period.")
    val ordered = remember(ratings) {
        ratings.keys.sortedByDescending { name -> ratings.getValue(name).values.sumOf { it.sum() } }
    }
    var showAll by rememberSaveable(start, last) { mutableStateOf(false) }
    for (name in if (showAll) ordered else ordered.take(2)) {
        val slots = ratings.getValue(name)
        Text(name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall)
        for (s in listOf("am", "pm")) {
            val levels = slots[s]
            val hit = levels?.let { it[2] + it[3] } ?: 0
            val total = levels?.sum() ?: 0
            val label = if (s == "am") "Morning" else "Evening"
            val color = if (s == "am") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            val large = LocalDensity.current.fontScale > 1.25f
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { selected = name; slot = s }
                .semantics { contentDescription = "$name, $label, $hit moderate or strong out of $total rated. Open dates" }
                .heightIn(min = 48.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, if (large) Modifier.weight(1f) else Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                    if (!large) SeverityTrack(hit, total, color, Modifier.weight(1f))
                    Text("$hit / $total", style = MaterialTheme.typography.labelLarge)
                }
                if (large) SeverityTrack(hit, total, color, Modifier.fillMaxWidth())
            }
        }
    }
    if (ordered.size > 2) TextButton(onClick = { showAll = !showAll }) {
        Text(if (showAll) "Show fewer symptoms" else "Show all ${ordered.size} symptoms")
    }
    PatternsLink("Explore symptom ratings", explore)
    selected?.let { name ->
        val label = if (slot == "am") "Morning" else "Evening"
        ComparisonDates("$name · $label ratings", (last.toEpochDay() - start.toEpochDay() + 1).toInt(),
            { selected = null }, openDay) { i ->
            val date = last.minusDays(i.toLong())
            date to symptomRatingLabel(index.metric(date, slot, name))
        }
    }
}

@Composable
private fun SeverityTrack(hit: Int, total: Int, color: Color, modifier: Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (hit > 0) Spacer(Modifier.fillMaxHeight().fillMaxWidth(hit.toFloat() / total).background(color))
    }
}

@Composable
private fun ComparisonDates(title: String, size: Int, close: () -> Unit, openDay: (String) -> Unit,
                            observation: (Int) -> Pair<LocalDate, String>) {
    Dialog(onDismissRequest = close) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                LazyColumn(Modifier.weight(1f)) {
                    items(size) { i ->
                        val (date, reading) = observation(i)
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { openDay(date.toString()) }
                            .heightIn(min = 48.dp).padding(vertical = 8.dp)) {
                            Text(date.format(comparisonDate), color = MaterialTheme.colorScheme.primary)
                            Text(reading, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                TextButton(onClick = close) { Text("Close") }
            }
        }
    }
}
