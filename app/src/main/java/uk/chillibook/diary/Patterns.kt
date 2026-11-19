package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
internal fun PatternMenu(label: String, options: List<String>, choose: (Int) -> Unit, heading: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(label, Modifier.weight(1f, fill = false), fontSize = if (heading) 24.sp else 16.sp,
                fontFamily = if (heading) FontFamily.Serif else FontFamily.SansSerif,
                color = if (heading) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary)
            Text("  ⌄", color = MaterialTheme.colorScheme.primary)
        }
        if (expanded) Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(options.size) { i ->
                            Box(Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                expanded = false
                                choose(i)
                            }.heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.CenterStart) {
                                Text(options[i])
                            }
                        }
                    }
                    TextButton(onClick = { expanded = false }) { Text("Close") }
                }
            }
        }
    }
}

@Composable
internal fun PatternExplorer(diary: Diary, index: PatternIndex, start: LocalDate, end: LocalDate, today: LocalDate,
    activities: Boolean, onDate: (String) -> Unit, onToday: () -> Unit, back: () -> Unit) {
    var factor by rememberSaveable { mutableStateOf(Factor.Work) }
    var outcome by rememberSaveable { mutableStateOf(FollowUp.Next) }
    var metric by rememberSaveable { mutableStateOf<String?>("Brain fog") }
    val symptoms = remember(diary.checks) { (symptomLabels + diary.checks.flatMap { it.symptoms.keys }.distinct().sorted()).distinct() }
    val symptom = metric?.takeIf { it in symptoms }
    val levels = if (symptom == null) energyLabels else severityLabels
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var lookup by rememberSaveable { mutableStateOf<String?>(null) }
    val longRange = ChronoUnit.DAYS.between(start, end) >= 62
    val month = selectedMonth?.let(YearMonth::parse)
    val displayStart = month?.let { maxOf(start, it.atDay(1)) } ?: start
    val displayEnd = month?.let { minOf(end, it.atEndOfMonth()) } ?: end
    val result = remember(index, displayStart, displayEnd, today, factor, outcome) {
        index.compare(displayStart, displayEnd, today, factor, outcome)
    }
    val metrics = listOf("Energy") + symptoms
    val tags = remember(index, displayStart, displayEnd) {
        diary.activities.filter { it.date >= displayStart.toString() && it.date <= displayEnd.toString() }
            .flatMap { it.tags }.sorted().distinctBy(::activityTagKey)
    }
    val recordedSymptoms = remember(index, displayStart, displayEnd) {
        diary.checks.filter { it.date >= displayStart.toString() && it.date <= displayEnd.toString() }
            .flatMap { it.symptoms.keys }.distinct().sorted()
    }
    val lookupOptions = tags + recordedSymptoms
    val comparisonLevels = if (outcome == FollowUp.Wellbeing) wellbeingLabels else energyLabels
    val sparse = result.yes.usable < 5 || result.no.usable < 5
    val largeType = LocalDensity.current.fontScale > 1.35f
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (largeType) 10.dp else 8.dp)) {
        OutlinedButton(onClick = back) { Text("‹  Patterns") }
        Text("${start.format(longDate)} – ${end.format(longDate)}", style = MaterialTheme.typography.bodySmall)
        if (activities) {
            ActivityFrequencyGrid(index, displayStart, displayEnd, onDate)
        } else {
            val showDaily = !longRange || ChronoUnit.DAYS.between(start, end) == 89L || month != null
            Text("Symptom ratings", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            PatternMenu(symptom ?: "Energy", metrics, { metric = if (it == 0) null else metrics[it] })
            if (symptom != null) SymptomFrequency(index, displayStart, displayEnd, symptom, onDate)
            if (showDaily) {
                if (month != null) OutlinedButton(onClick = { selectedMonth = null }) { Text("‹ Months   ·   ${month.format(monthLabel)}") }
                Timeline(index, displayStart, displayEnd, symptom, levels, factor, onToday)
                OutlinedButton(onClick = { details = "timeline" }) { Text("Timeline as text") }
            }
            if (longRange && month == null) {
                val months = remember(index, start, end, symptom) { index.monthly(start, end, symptom, levels.size) }
                if (ChronoUnit.DAYS.between(start, end) == 89L) {
                    val options = months.keys.reversed()
                    PatternMenu("Explore a month", options.map { it.format(monthLabel) }, { selectedMonth = options[it].toString() })
                } else {
                    Text("Monthly distributions", style = MaterialTheme.typography.titleMedium)
                    if (months.values.none { slots -> slots.values.any { it.recorded > 0 } }) {
                        Text("No ${symptom?.lowercase() ?: "energy"} entries in this period.")
                        OutlinedButton(onClick = onToday) { Text("Go to Today") }
                    }
                    Text("Levels from ${levels.first().lowercase()} to ${levels.last().lowercase()}", style = MaterialTheme.typography.bodySmall)
                    months.entries.toList().asReversed().forEach { (key, slots) ->
                        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedMonth = key.toString() }
                            .padding(vertical = 8.dp)) {
                            Text("${key.format(monthLabel)}   ›", style = MaterialTheme.typography.titleMedium)
                            val peak = slots.values.maxOf { it.counts.maxOrNull() ?: 0 }.coerceAtLeast(1)
                            slots.forEach { (slot, ratings) ->
                                val am = slot == "am"
                                val color = if (am) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                Text("${if (am) "● Morning" else "◆ Evening"} · ${ratings.recorded} recorded", color = color)
                                val summary = levels.mapIndexed { i, label -> "$label ${ratings.counts[i]}" }.joinToString(" · ")
                                Canvas(Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = summary }) {
                                    val step = size.width / levels.size
                                    ratings.counts.forEachIndexed { i, count ->
                                        val height = size.height * count / peak
                                        drawRect(color, Offset(step * (i + .1f), size.height - height), Size(step * .8f, height))
                                    }
                                }
                                Text(summary, style = MaterialTheme.typography.bodySmall)
                                if (ratings.unrated > 0) Text("Severity not rated · ${ratings.unrated}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        if (activities) {
            HorizontalDivider()
            val factors = listOf("After work days", "After socialising", "After household tasks")
            PatternMenu(factors[factor.ordinal], factors, { factor = Factor.values()[it] }, true)
            PatternMenu(outcome.label, FollowUp.values().map { it.label }, { outcome = FollowUp.values()[it] })
            Text(if (outcome == FollowUp.Wellbeing) "Rough or very rough wellbeing" else "Low or very low energy")
            GroupBar(factor.label, result.yes, MaterialTheme.colorScheme.primary)
            GroupBar("No ${factor.label.lowercase()}", result.no, MaterialTheme.colorScheme.tertiary)
            if (sparse) Text("A few entries so far", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { details = "comparison" }) { Text("All levels and dates   ›") }
            if (sparse) {
                (result.yes.dates.map { factor.label to it } + result.no.dates.map { "No ${factor.label.lowercase()}" to it })
                    .sortedByDescending { it.second.anchor }.take(4).forEach { (group, pair) ->
                        PairRow(group, pair, comparisonLevels, onDate)
                    }
            }
            Text("Unknown ${factor.label.lowercase()} status on ${result.unknown} days.",
                style = MaterialTheme.typography.bodySmall)
            Text(if (outcome.lag == 0L) "These answers describe the same day. Their order is unknown."
                else "Starting energy, overlapping activities and gaps can affect these observations.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (lookupOptions.isNotEmpty()) {
            HorizontalDivider()
            PatternMenu(lookup?.takeIf { it in lookupOptions } ?: "Look up an activity or symptom", lookupOptions, { lookup = lookupOptions[it]; details = "lookup" })
        }
        Spacer(Modifier.height(18.dp))
    }
    fun open(day: LocalDate) { onDate(day.toString()) }
    if (details != null) Dialog(onDismissRequest = { details = null }) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(Modifier.padding(16.dp)) {
                Text(when (details) { "comparison" -> "All levels and dates"; "lookup" -> lookup.orEmpty(); else -> symptom ?: "Energy" },
                    style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                LazyColumn(Modifier.weight(1f)) {
                    when (details) {
                        "comparison" -> {
                            item { Text("${factor.label} · ${outcome.label}") }
                            for ((name, group) in listOf(factor.label to result.yes, "No ${factor.label.lowercase()}" to result.no)) {
                                item {
                                    Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                                    Text("${group.usable} usable · ${group.missing} missing")
                                    Text(comparisonLevels.mapIndexed { i, label -> "$label ${group.counts[i]}" }.joinToString(" · "))
                                }
                                items(group.dates.asReversed()) { PairRow(name, it, comparisonLevels, onDate) }
                            }
                            item { Text("${result.unknown} days with unknown activity status") }
                        }
                        "lookup" -> {
                            val dates = if (lookup in recordedSymptoms) {
                                diary.checks.filter { lookup in it.symptoms }.map { it.date }.distinct()
                            } else {
                                index.activities.filterValues { entries -> entries.any { it.hasActivityTag(lookup) } }.keys.toList()
                            }
                            items(dates.filter { it >= displayStart.toString() && it <= displayEnd.toString() }.sortedDescending()) { day ->
                                val date = LocalDate.parse(day)
                                val entries = index.activities[day].orEmpty().filter { it.hasActivityTag(lookup) }
                                val context = if (lookup in recordedSymptoms) {
                                    "Morning · ${symptomRatingLabel(index.metric(date, "am", lookup))}\n" +
                                        "Evening · ${symptomRatingLabel(index.metric(date, "pm", lookup))}"
                                } else {
                                    entries.joinToString("\n") { entry ->
                                        if (entry.workSetup) (listOf("Work") + listOfNotNull(entry.workLength, entry.effort) +
                                            entry.demands.sorted()).joinToString(" · ")
                                        else "Worthwhile · ${entry.worth ?: "Unanswered"}"
                                    }
                                }
                                DateRow(date, "Wellbeing · ${wellbeingLabels.value(index.days[day]?.wellbeing)}\n" +
                                    "Next morning · ${energyLabels.value(index.metric(date.plusDays(1), "am", null))}\n" +
                                    "Two mornings later · ${energyLabels.value(index.metric(date.plusDays(2), "am", null))}\n" +
                                    context) { open(date) }
                            }
                        }
                        else -> {
                            val dates = dateSequence(displayStart, displayEnd).toList().asReversed()
                            items(dates) { date ->
                                val status = index.status(date, factor)
                                DateRow(date, "Morning · ${metricLabel(index.metric(date, "am", symptom), symptom)}\n" +
                                    "Evening · ${metricLabel(index.metric(date, "pm", symptom), symptom)}\n" +
                                    "${factor.label} · ${when (status) { true -> "Yes"; false -> "No"; null -> "Unanswered" }}") { open(date) }
                            }
                        }
                    }
                }
                TextButton(onClick = { details = null }) { Text("Close") }
            }
        }
    }
}

@Composable
private fun SymptomFrequency(index: PatternIndex, start: LocalDate, end: LocalDate,
                             symptom: String, onDate: (String) -> Unit) {
    val morning = remember(index, start, end, symptom) { index.symptomDates(start, end, symptom, "am") }
    val evening = remember(index, start, end, symptom) { index.symptomDates(start, end, symptom, "pm") }
    val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
    var showDates by rememberSaveable { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    for (level in severityLabels.indices.toList() + listOf(UNRATED_SYMPTOM, null)) {
        Text(symptomRatingLabel(level), style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        FrequencyBar("Morning", morning[level].orEmpty().size, days, primary)
        FrequencyBar("Evening", evening[level].orEmpty().size, days, secondary)
    }
    OutlinedButton(onClick = { showDates = true }) { Text("Symptom rating dates   ›") }
    if (showDates) Dialog(onDismissRequest = { showDates = false }) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(Modifier.padding(16.dp)) {
                Text(symptom, style = MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.weight(1f)) {
                    fun androidx.compose.foundation.lazy.LazyListScope.group(label: String, dates: List<LocalDate>) {
                        if (dates.isEmpty()) return
                        item { Text("$label · ${dates.size}", style = MaterialTheme.typography.titleMedium) }
                        items(dates.asReversed()) { date ->
                            DateRow(date, label) { onDate(date.toString()) }
                        }
                    }
                    for ((slot, ratings) in listOf("Morning" to morning, "Evening" to evening)) {
                        for (level in severityLabels.indices.toList() + listOf(UNRATED_SYMPTOM, null)) {
                            group("$slot · ${symptomRatingLabel(level)}", ratings[level].orEmpty())
                        }
                    }
                }
                TextButton(onClick = { showDates = false }) { Text("Close") }
            }
        }
    }
}

@Composable
private fun FrequencyBar(label: String, count: Int, days: Int, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label · $count of $days days", style = MaterialTheme.typography.bodyMedium)
        val shape = RoundedCornerShape(4.dp)
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape)) {
            if (count > 0) Spacer(Modifier.fillMaxWidth(count.toFloat() / days).fillMaxHeight().background(color, shape))
        }
    }
}

private fun metricLabel(value: Int?, symptom: String?) = if (symptom == null) energyLabels.value(value) else symptomRatingLabel(value)

private fun List<String>.value(index: Int?) = index?.let { getOrNull(it) } ?: "No entry"

@Composable
private fun DateRow(date: LocalDate, summary: String, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open).heightIn(min = 48.dp).padding(vertical = 8.dp)) {
        Text(date.format(longDate), color = MaterialTheme.colorScheme.primary)
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PairRow(group: String, pair: DatePair, levels: List<String>, open: (String) -> Unit) {
    DateRow(pair.anchor, "$group · ${pair.follow.format(shortDate)} · ${levels.value(pair.level)}") { open(pair.anchor.toString()) }
}

@Composable
private fun GroupBar(label: String, group: ComparisonGroup, color: Color) {
    val description = "$label, ${group.low} of ${group.usable}, ${group.missing} missing"
    val modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }
        .padding(vertical = 4.dp)
    val countLabel = "${group.low} of ${group.usable}"
    if (LocalDensity.current.fontScale > 1.35f) {
        Column(modifier) {
            Text("$label · $countLabel")
            FractionTrack(Modifier.fillMaxWidth(), group, color)
        }
    } else {
        Row(modifier.heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1.1f))
            FractionTrack(Modifier.weight(1.6f), group, color)
            Box(Modifier.weight(.8f), contentAlignment = Alignment.CenterEnd) { Text(countLabel) }
        }
    }
}

@Composable
private fun FractionTrack(modifier: Modifier, group: ComparisonGroup, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape)) {
        if (group.low > 0) Spacer(Modifier.fillMaxWidth(group.low.toFloat() / group.usable)
            .fillMaxHeight().background(color, shape))
    }
}

@Composable
private fun Timeline(index: PatternIndex, start: LocalDate, end: LocalDate, symptom: String?, levels: List<String>,
                     factor: Factor, onToday: () -> Unit) {
    val dates = remember(start, end) { dateSequence(start, end).toList() }
    val morning = MaterialTheme.colorScheme.primary
    val evening = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val small = MaterialTheme.typography.bodySmall
    val fontScale = LocalDensity.current.fontScale
    val chartHeight = (126 * fontScale).dp
    val labelWidth = (66 * fontScale.coerceAtMost(2f)).dp
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("● Morning", color = morning, style = small)
        Text("◆ Evening", color = evening, style = small)
    }
    val hasData = dates.any { index.metric(it, "am", symptom) != null || index.metric(it, "pm", symptom) != null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(labelWidth).height(chartHeight + 24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            levels.asReversed().forEach { Text(it, style = small, modifier = Modifier.height(24.dp * fontScale).wrapContentHeight()) }
        }
        Canvas(Modifier.weight(1f).height(chartHeight).semantics {
            contentDescription = "${symptom ?: "Energy"} timeline. Missing observations break each line. Open Timeline as text for each date."
        }) {
            val margin = 5.dp.toPx()
            fun x(i: Int, eveningSlot: Boolean) = margin + (size.width - margin * 2) *
                (i + if (eveningSlot) .60f else .40f) / dates.size
            fun y(level: Int) = (size.height - margin * 2) * (1f - level.toFloat() / levels.lastIndex) + margin
            levels.indices.forEach { i -> drawLine(grid, Offset(0f, y(i)), Offset(size.width, y(i)), 1.dp.toPx()) }
            for ((slot, color) in listOf("pm" to evening, "am" to morning)) {
                var previous: Offset? = null
                dates.forEachIndexed { i, date ->
                    val value = index.metric(date, slot, symptom)
                    val point = value?.takeIf { it in levels.indices }?.let { Offset(x(i, slot == "pm"), y(it)) }
                    if (point != null) {
                        previous?.let { drawLine(color, it, point, 1.4.dp.toPx()) }
                        val radius = if (dates.size > 31) 1.7.dp.toPx() else 2.8.dp.toPx()
                        if (slot == "am") drawCircle(color, radius, point)
                        else drawPath(Path().apply {
                            moveTo(point.x, point.y - radius); lineTo(point.x + radius, point.y)
                            lineTo(point.x, point.y + radius); lineTo(point.x - radius, point.y); close()
                        }, color)
                    }
                    previous = point
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = labelWidth), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(start.format(shortDate), style = small)
        if (dates.size >= 14 && fontScale <= 1.35f) Text(dates[(dates.size - 1) / 2].format(shortDate), style = small)
        if (end != start) Text(end.format(shortDate), style = small)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(factor.label, Modifier.width(labelWidth), style = small)
        Canvas(Modifier.weight(1f).height(20.dp)) {
            val inset = 5.dp.toPx()
            dates.forEachIndexed { i, date ->
                if (index.status(date, factor) == true) {
                    drawCircle(morning, 2.2.dp.toPx(), Offset(inset + (size.width - 2 * inset) * (i + .5f) / dates.size, size.height / 2))
                }
            }
        }
    }
    if (symptom != null) {
        val unrated = dates.sumOf { date ->
            listOf("am", "pm").count { slot -> index.metric(date, slot, symptom) == UNRATED_SYMPTOM }
        }
        if (unrated > 0) Text("Severity not rated · $unrated " +
            if (unrated == 1) "check-in" else "check-ins", style = small)
    }
    if (!hasData) {
        Text("No ${symptom?.lowercase() ?: "energy"} entries in this period.", style = small)
        OutlinedButton(onClick = onToday) { Text("Go to Today") }
    }
}
