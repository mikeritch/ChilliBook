package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
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
private val periods = listOf(7, 28, 90, 0)

@Composable
private fun PatternMenu(label: String, options: List<String>, choose: (Int) -> Unit, heading: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.clickable(role = Role.Button) { expanded = true }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically) {
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
fun PatternsScreen(diary: Diary, onDate: (String) -> Unit, onToday: () -> Unit) {
    val index = remember(diary) { PatternIndex(diary) }
    val today = LocalDate.now()
    var period by rememberSaveable { mutableIntStateOf(28) }
    var factor by rememberSaveable { mutableStateOf(Factor.Work) }
    var outcome by rememberSaveable { mutableStateOf(FollowUp.Next) }
    var metric by rememberSaveable { mutableStateOf<String?>(null) }
    val symptom = metric?.takeIf { it in diary.prefs.pins }
    val levels = if (symptom == null) energyLabels else severityLabels
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var lookup by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var selectedEnd by rememberSaveable { mutableStateOf<String?>(null) }
    val end = if (period == 0) today else selectedEnd?.let(LocalDate::parse) ?: today
    val start = if (period == 0) minOf(end, index.first ?: end) else end.minusDays(period.toLong() - 1)
    val longRange = ChronoUnit.DAYS.between(start, end) >= 62
    val month = selectedMonth?.let(YearMonth::parse)
    val displayStart = month?.let { maxOf(start, it.atDay(1)) } ?: start
    val displayEnd = month?.let { minOf(end, it.atEndOfMonth()) } ?: end
    val result = remember(index, displayStart, displayEnd, today, factor, outcome) {
        index.compare(displayStart, displayEnd, today, factor, outcome)
    }
    val metrics = listOf("Energy") + diary.prefs.pins.sorted()
    val tags = remember(index, displayStart, displayEnd) {
        diary.activities.filter { it.date >= displayStart.toString() && it.date <= displayEnd.toString() }
            .flatMap { it.tags }.distinct().sorted()
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
        verticalArrangement = Arrangement.spacedBy(if (largeType) 6.dp else 3.dp)) {
        TitleArt("Patterns", "patterns")
        val rangeFormat = if (displayStart.year == displayEnd.year) shortDate else DateTimeFormatter.ofPattern("d MMM yyyy")
        val rangeLabel = "${displayStart.format(rangeFormat)} – ${displayEnd.format(rangeFormat)}"
        if (period == 0 || month != null) {
            Text(rangeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        } else {
            Box(Modifier.clickable(role = Role.Button) {
                chooseDiaryDate(context, end.toString()) { selectedEnd = it; selectedMonth = null }
            }.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                Text("$rangeLabel   ⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val chunk = if (largeType) 2 else 4
        periods.chunked(chunk).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { value ->
                    val selected = period == value
                    Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                        .selectable(selected, role = Role.RadioButton) { period = value; selectedMonth = null }
                        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                            Text(if (value == 0) "All time" else "$value days", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        val showDaily = !longRange || month != null
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                PatternMenu(symptom ?: "Energy", metrics, { metric = if (it == 0) null else metrics[it] }, true)
            }
            if (showDaily && !largeType) TextButton(onClick = { details = "timeline" }) { Text("Timeline as text") }
        }
        if (longRange && month == null) {
            val months = remember(index, start, end, symptom) { index.monthly(start, end, symptom, levels.size) }
            if (months.values.none { slots -> slots.values.any { counts -> counts.sum() > 0 } }) {
                Text("No ${symptom?.lowercase() ?: "energy"} entries in this period.")
                TextButton(onClick = onToday) { Text("Go to Today") }
            }
            Text("Monthly distributions", style = MaterialTheme.typography.titleMedium)
            months.entries.toList().asReversed().forEach { (key, slots) ->
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { selectedMonth = key.toString() }
                    .padding(vertical = 8.dp)) {
                    Text(key.format(monthLabel), style = MaterialTheme.typography.titleMedium)
                    slots.forEach { (slot, counts) ->
                        Text("${if (slot == "am") "● Morning" else "◆ Evening"} · ${counts.sum()} recorded",
                            color = if (slot == "am") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                        Text(levels.mapIndexed { i, label -> "$label ${counts[i]}" }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        } else {
            if (month != null) TextButton(onClick = { selectedMonth = null }) { Text("‹ Months   ·   ${month.format(monthLabel)}") }
            Timeline(index, displayStart, displayEnd, symptom, levels, factor, onToday)
            if (largeType) TextButton(onClick = { details = "timeline" }) { Text("Timeline as text") }
        }
        HorizontalDivider()
        val factors = listOf("After work days", "After socialising", "After household tasks")
        PatternMenu(factors[factor.ordinal], factors, { factor = Factor.values()[it] }, true)
        PatternMenu(outcome.label, FollowUp.values().map { it.label }, { outcome = FollowUp.values()[it] })
        Text(if (outcome == FollowUp.Wellbeing) "Rough or very rough wellbeing" else "Low or very low energy")
        GroupBar(factor.label, result.yes, MaterialTheme.colorScheme.primary)
        GroupBar("No ${factor.label.lowercase()}", result.no, MaterialTheme.colorScheme.tertiary)
        if (sparse) Text("A few entries so far", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { details = "comparison" }) { Text("All levels and dates   ›") }
        if (sparse) {
            (result.yes.dates.map { factor.label to it } + result.no.dates.map { "No ${factor.label.lowercase()}" to it })
                .sortedByDescending { it.second.anchor }.take(4).forEach { (group, pair) ->
                    PairRow(group, pair, comparisonLevels, onDate)
                }
        }
        Text("Unknown ${factor.label.lowercase()} status on ${result.unknown} days.",
            style = MaterialTheme.typography.bodySmall)
        Text(if (outcome.lag == 0L) "These answers describe the same day. Their order is unknown."
            else "Starting energy, overlapping activities, crash days and gaps can affect these observations.",
            style = MaterialTheme.typography.bodySmall)
        FrequencyCharts(index, displayStart, displayEnd, tags, recordedSymptoms, onDate)
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
                                index.activities.filterValues { entries -> entries.any { lookup in it.tags } }.keys.toList()
                            }
                            items(dates.filter { it >= displayStart.toString() && it <= displayEnd.toString() }.sortedDescending()) { day ->
                                val date = LocalDate.parse(day)
                                val entries = index.activities[day].orEmpty().filter { lookup in it.tags }
                                val context = if (lookup in recordedSymptoms) {
                                    "Morning · ${severityLabels.value(index.metric(date, "am", lookup))}\n" +
                                        "Evening · ${severityLabels.value(index.metric(date, "pm", lookup))}"
                                } else {
                                    "Worthwhile · ${entries.joinToString(", ") { it.worth ?: "Unanswered" }}"
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
                                val crashes = diary.episodes.filter { it.start <= date.toString() && (it.end ?: today.toString()) >= date.toString() }
                                DateRow(date, "Morning · ${levels.value(index.metric(date, "am", symptom))}\n" +
                                    "Evening · ${levels.value(index.metric(date, "pm", symptom))}\n" +
                                    "${factor.label} · ${when (status) { true -> "Yes"; false -> "No"; null -> "Unanswered" }}" +
                                    crashes.joinToString("") { "\n${it.description()}" }) { open(date) }
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
private fun FrequencyCharts(index: PatternIndex, start: LocalDate, end: LocalDate, tags: List<String>,
                            recordedSymptoms: List<String>, onDate: (String) -> Unit) {
    val factors = Factor.values().map { it.label }
    val activities = (factors + tags).distinct()
    val symptoms = (index.diary.prefs.pins.sorted() + recordedSymptoms + symptomLabels).distinct()
    var pickedActivity by rememberSaveable { mutableStateOf("Work") }
    var pickedSymptom by rememberSaveable { mutableStateOf(symptoms.first()) }
    val activity = pickedActivity.takeIf { it in activities } ?: activities.first()
    val symptom = pickedSymptom.takeIf { it in symptoms } ?: symptoms.first()
    val activityDates = remember(index, start, end, activity) { index.activityDates(start, end, activity) }
    val morning = remember(index, start, end, symptom) { index.symptomDates(start, end, symptom, "am") }
    val evening = remember(index, start, end, symptom) { index.symptomDates(start, end, symptom, "pm") }
    val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    HorizontalDivider()
    Text("Activity frequency", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
    PatternMenu(activity, activities, { pickedActivity = activities[it] })
    val answers = if (activity in factors) listOf(true to "Yes", false to "No", null to "Unanswered")
        else listOf(true to "Marked", null to "Unmarked")
    for ((answer, label) in answers) {
        FrequencyBar(label, activityDates[answer].orEmpty().size, days, when (answer) {
            true -> primary
            false -> secondary
            null -> neutral
        })
    }
    TextButton(onClick = { detail = "activity" }) { Text("Activity dates   ›") }
    HorizontalDivider()
    Text("Symptom ratings", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
    PatternMenu(symptom, symptoms, { pickedSymptom = symptoms[it] })
    for (level in severityLabels.indices.toList() + listOf(null)) {
        Text(level?.let(severityLabels::get) ?: "Unrated", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        FrequencyBar("Morning", morning[level].orEmpty().size, days, primary)
        FrequencyBar("Evening", evening[level].orEmpty().size, days, secondary)
    }
    TextButton(onClick = { detail = "symptoms" }) { Text("Symptom rating dates   ›") }
    if (detail != null) Dialog(onDismissRequest = { detail = null }) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
            Column(Modifier.padding(16.dp)) {
                Text(if (detail == "activity") activity else symptom, style = MaterialTheme.typography.headlineSmall)
                LazyColumn(Modifier.weight(1f)) {
                    fun androidx.compose.foundation.lazy.LazyListScope.group(label: String, dates: List<LocalDate>) {
                        if (dates.isEmpty()) return
                        item { Text("$label · ${dates.size}", style = MaterialTheme.typography.titleMedium) }
                        items(dates.asReversed()) { date ->
                            DateRow(date, label) { onDate(date.toString()) }
                        }
                    }
                    if (detail == "activity") {
                        for ((answer, label) in answers) group(label, activityDates[answer].orEmpty())
                    } else {
                        for ((slot, ratings) in listOf("Morning" to morning, "Evening" to evening)) {
                            for (level in severityLabels.indices.toList() + listOf(null)) {
                                group("$slot · ${level?.let(severityLabels::get) ?: "Unrated"}", ratings[level].orEmpty())
                            }
                        }
                    }
                }
                TextButton(onClick = { detail = null }) { Text("Close") }
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

private fun List<String>.value(index: Int?) = index?.let { getOrNull(it) } ?: "No entry"

@Composable
private fun DateRow(date: LocalDate, summary: String, open: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open).heightIn(min = 48.dp).padding(vertical = 8.dp)) {
        Text(date.format(longDate), color = MaterialTheme.colorScheme.primary)
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider()
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
    val episodes = index.diary.episodes.filter { it.start <= end.toString() && (it.end ?: end.toString()) >= start.toString() }
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
                    val point = value?.let { Offset(x(i, slot == "pm"), y(it)) }
                    if (point != null) {
                        previous?.let { drawLine(color, it, point, 1.4.dp.toPx()) }
                        val radius = 2.8.dp.toPx()
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
    if (episodes.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Crash", Modifier.width(labelWidth), style = small)
            Canvas(Modifier.weight(1f).height(20.dp)) {
                val inset = 5.dp.toPx()
                fun x(date: LocalDate) = inset + (size.width - 2 * inset) * (ChronoUnit.DAYS.between(start, date) + .5f) / dates.size
                episodes.forEach { episode ->
                    val reported = LocalDate.parse(episode.start)
                    val first = maxOf(reported, start)
                    val last = minOf(episode.end?.let(LocalDate::parse) ?: end, end)
                    val middle = size.height / 2
                    drawLine(evening, Offset(x(first), middle), Offset(x(last), middle), 3.dp.toPx())
                    if (reported >= start) drawCircle(evening, 3.dp.toPx(), Offset(x(first), middle))
                    if (episode.end == null) {
                        drawLine(evening, Offset(x(last) - 4.dp.toPx(), middle - 3.dp.toPx()), Offset(x(last), middle))
                        drawLine(evening, Offset(x(last) - 4.dp.toPx(), middle + 3.dp.toPx()), Offset(x(last), middle))
                    }
                }
            }
        }
        if (episodes.any { it.end == null }) Text("Recovery date unknown", style = small)
    }
    if (!hasData) {
        Text("No ${symptom?.lowercase() ?: "energy"} entries in this period.", style = small)
        TextButton(onClick = onToday) { Text("Go to Today") }
    }
}


private fun Episode.description(): String {
    val begin = (if (approxStart) "about " else "") + LocalDate.parse(start).format(shortDate)
    val recovery = end?.let { "Recovered ${if (approxEnd) "about " else ""}${LocalDate.parse(it).format(shortDate)}" }
        ?: "Recovery date unknown"
    return "Crash from $begin · $recovery"
}
