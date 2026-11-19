package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val axisDate = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationOverview(diary: Diary, index: PatternIndex, start: LocalDate, end: LocalDate,
    today: LocalDate, openReview: (choiceKey: String, courseId: String, measure: MedicationMeasure, symptom: String, day: String?) -> Unit) {
    val doses = remember(diary.doses, start, end, today) {
        diary.asNeededDoses(start, minOf(today, end))
    }
    val choices = remember(diary.courses, diary.regimens, diary.doses, doses, start, end) {
        diary.medicineChoices().filter { option ->
            option.event?.date?.let { it in start..end } == true || (option.asNeeded && option.course.id in doses)
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val choice = choices.find { it.key == selected } ?: choices.firstOrNull()
    if (choice == null) {
        Text("No dated medication changes or as-needed doses recorded in this period.")
        return
    }
    val names = remember(diary.checks) { (symptomLabels + diary.checks.flatMap { it.symptoms.keys }.sorted()).distinct() }
    var measure by rememberSaveable { mutableStateOf(MedicationMeasure.Evening) }
    var symptom by rememberSaveable { mutableStateOf("Brain fog") }
    fun choiceLabel(option: MedicineChoice) = option.course.name + " · " + if (option.asNeeded) "As needed" else option.caption
    val measures = listOf(MedicationMeasure.Evening.label, MedicationMeasure.Morning.label) + names.map { "Evening ${it.lowercase()}" }
    val subject = if (measure.symptom) "Evening ${symptom.lowercase()}" else measure.label
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MedicationDropdown("${choice.course.name} · ${choice.event?.label ?: "As needed"}", choices.map(::choiceLabel), { selected = choices[it].key })
        MedicationDropdown(subject, measures, { item ->
            measure = when (item) {
                0 -> MedicationMeasure.Evening
                1 -> MedicationMeasure.Morning
                else -> MedicationMeasure.EveningSymptom
            }
            if (item >= 2) symptom = names[item - 2]
        })
    }
    val event = choice.event
    val first = event?.laterWeekStart?.minusWeeks(1) ?: start
    val last = event?.laterWeekStart?.plusDays(6) ?: end
    val doseDates = remember(doses, choice.key) {
        doses[choice.course.id].orEmpty().map { LocalDate.parse(it.date) }.distinct()
    }
    val points = remember(index, measure, symptom, first, last, today) {
        val minDate = first.toString()
        val maxDate = minOf(today, last).toString()
        index.diary.checks.asSequence().filter { it.slot == measure.slot && it.date in minDate..maxDate }
            .mapNotNull { check ->
                val value = if (measure.symptom) check.symptoms[symptom] else check.energy
                value?.let { LocalDate.parse(check.date) to it }
            }.sortedBy { it.first }.toList()
    }
    MedicationPlot(first, last, event, points, if (choice.asNeeded) doseDates else emptyList(), measure, subject)
    val caption = when {
        event == null -> "Triangles mark recorded dose days. The line shows diary ratings."
        event.stop -> "The shaded week starts after the last day of the course."
        else -> "The shaded week starts on the day of the change."
    }
    Text(caption, style = MaterialTheme.typography.bodySmall)
    if (points.none { it.second >= 0 }) Text("No ${subject.lowercase()} ratings in these dates.",
        style = MaterialTheme.typography.bodySmall)
    if (points.any { it.second == UNRATED_SYMPTOM }) Text("Open circles below the plot mean severity was unrated.",
        style = MaterialTheme.typography.bodySmall)
    PatternsLink(if (event == null) "See recorded dose days" else "See the dates around this change") {
        openReview(choice.key, choice.course.id, measure, symptom, null)
    }
}

@Composable
private fun MedicationDropdown(label: String, options: List<String>, choose: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge)
            Text("  ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 320.dp)) {
            options.forEachIndexed { index, title ->
                DropdownMenuItem(text = { Text(title, modifier = Modifier.padding(vertical = 6.dp)) }, onClick = {
                    expanded = false
                    choose(index)
                })
            }
        }
    }
}

@Composable
private fun MedicationPlot(first: LocalDate, last: LocalDate, event: MedicationChange?, points: List<Pair<LocalDate, Int>>,
    doses: List<LocalDate>, measure: MedicationMeasure, subject: String) {
    val colors = MaterialTheme.colorScheme
    val labels = if (measure.symptom) severityLabels else energyLabels
    val rated = points.count { it.second in labels.indices }
    val description = "$subject, $rated rated dates. " + (event?.let { "${it.label} on ${it.date}." }
        ?: "${doses.size} recorded dose days.")
    if (event != null) Text("${event.label} · ${event.date.format(axisDate)}", color = colors.primary,
        style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Column(Modifier.height(96.dp), verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End) {
            Text(labels.last(), style = MaterialTheme.typography.labelSmall)
            Text(labels.first(), style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(118.dp).semantics { contentDescription = description }) {
                val pad = 4.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = 88.dp.toPx()
                val span = (last.toEpochDay() - first.toEpochDay()).coerceAtLeast(1)
                fun x(day: LocalDate) = pad + (size.width - 2 * pad) * ((day.toEpochDay() - first.toEpochDay()).toFloat() / span)
                fun y(level: Int) = bottom - (bottom - top) * level / labels.lastIndex
                if (event != null) {
                    val boundary = (x(event.laterWeekStart) + x(event.laterWeekStart.minusDays(1))) / 2
                    drawRect(colors.primaryContainer, Offset(boundary, top), Size(size.width - boundary, bottom - top))
                    val marker = x(event.date)
                    drawLine(colors.primary, Offset(marker, 0f), Offset(marker, bottom), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                }
                for (height in listOf(top, bottom)) drawLine(colors.outlineVariant, Offset(0f, height), Offset(size.width, height))
                val tone = if (measure.slot == "am") colors.primary else colors.tertiary
                var previous: LocalDate? = null
                var previousPoint = Offset.Zero
                for ((date, level) in points) {
                    val point = Offset(x(date), if (level in labels.indices) y(level) else bottom + 7.dp.toPx())
                    if (level in labels.indices) {
                        if (previous?.plusDays(1) == date) drawLine(tone, previousPoint, point, 2.dp.toPx())
                        drawCircle(tone, if (points.size > 32) 1.5.dp.toPx() else 3.dp.toPx(), point)
                        previous = date
                        previousPoint = point
                    } else {
                        drawCircle(tone, 2.5.dp.toPx(), point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                        previous = null
                    }
                }
                for (day in doses) {
                    val center = x(day)
                    val baseline = 114.dp.toPx()
                    drawPath(Path().apply {
                        moveTo(center, baseline - 7.dp.toPx())
                        lineTo(center - pad, baseline)
                        lineTo(center + pad, baseline)
                        close()
                    }, colors.primary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(first.format(axisDate), style = MaterialTheme.typography.labelSmall)
                if (last != first) Text(last.format(axisDate), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
