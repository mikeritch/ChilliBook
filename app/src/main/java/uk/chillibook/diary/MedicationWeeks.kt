package uk.chillibook.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val weekDate = DateTimeFormatter.ofPattern("d MMM")
private val spokenDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

internal data class MedicationWeekStory(val title: String, val detail: String)

private fun MedicationMeasure.subject(symptom: String) = when {
    this == MedicationMeasure.MorningSymptom -> "Morning ${symptom.lowercase()}"
    this == MedicationMeasure.EveningSymptom -> "Evening ${symptom.lowercase()}"
    else -> label
}

internal fun medicationWeekStory(before: List<MedicationReading>, after: List<MedicationReading>,
    measure: MedicationMeasure, symptom: String): MedicationWeekStory {
    val labels = if (measure.symptom) severityLabels else energyLabels
    fun ratings(rows: List<MedicationReading>) = rows.filterNot { it.ahead }
        .mapNotNull { it.level }.filter { it in labels.indices }.sorted()
    val earlier = ratings(before)
    val later = ratings(after)
    val subject = measure.subject(symptom)
    if (earlier.size < 3 || later.size < 3) {
        fun dayCount(size: Int) = if (size == 1) "1 day" else "$size days"
        return MedicationWeekStory("Too few ratings to compare.",
            "You rated ${subject.lowercase()} on ${dayCount(earlier.size)} in the first week and ${dayCount(later.size)} in the second.")
    }
    // Keep both middle categories when an ordinal sample has two different middle ratings.
    fun middle(sorted: List<Int>) = sorted[(sorted.size - 1) / 2]..sorted[sorted.size / 2]
    fun describe(range: IntRange): String {
        val first = labels[range.first].lowercase()
        return if (range.first == range.last) first else "$first to ${labels[range.last].lowercase()}"
    }
    val from = middle(earlier)
    val to = middle(later)
    val direction = when {
        to.first > from.last -> if (measure.symptom) "stronger" else "higher"
        to.last < from.first -> if (measure.symptom) "milder" else "lower"
        to == from -> "about the same"
        else -> null
    }
    val title = direction?.let { "$subject was $it." } ?: "The typical ratings overlapped."
    val detail = if (from == to) "Your typical rating was ${describe(from)} in both weeks."
        else "Your typical rating was ${describe(from)} in the first week and ${describe(to)} in the second."
    return MedicationWeekStory(title, detail)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicationWeeks(diary: Diary, index: PatternIndex, event: MedicationChange, today: LocalDate,
    symptom: String, onSymptom: (String) -> Unit, openDate: (LocalDate) -> Unit, initialMeasure: MedicationMeasure? = null) {
    var measure by rememberSaveable(initialMeasure) { mutableStateOf(initialMeasure ?: MedicationMeasure.Evening) }
    val names = remember(diary.checks) { (symptomLabels + diary.checks.flatMap { it.symptoms.keys }.sorted()).distinct() }
    val subject = measure.subject(symptom)
    val start = event.laterWeekStart
    val weeks = remember(index, start, today, measure, symptom) {
        listOf(start.minusWeeks(1), start).map { medicationWeek(index, it, today, measure, symptom) }
    }
    val story = medicationWeekStory(weeks.first(), weeks.last(), measure, symptom)
    val colors = MaterialTheme.colorScheme
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((option, name) in listOf(MedicationMeasure.Evening to "Evenings", MedicationMeasure.Morning to "Mornings",
            MedicationMeasure.EveningSymptom to symptom)) {
            val selected = option == measure
            val shape = RoundedCornerShape(12.dp)
            Box(Modifier.weight(1f).widthIn(min = 80.dp).clip(shape)
                .background(if (selected) colors.primary else colors.surface)
                .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                .selectable(selected, role = Role.Tab) { measure = option }
                .heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                Text(name, color = if (selected) colors.onPrimary else colors.onSurface)
            }
        }
    }
    if (measure.symptom) PatternMenu("Choose symptom", names, { onSymptom(names[it]) })
    Surface(shape = RoundedCornerShape(18.dp), color = colors.primaryContainer.copy(alpha = .65f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(story.title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineSmall,
                color = colors.primary)
            Text(story.detail, style = MaterialTheme.typography.bodyMedium)
            val work = weeks.map { rows -> rows.count { it.day <= today && index.status(it.day, Factor.Work) == true } }
            if (work.first() != work.last() && weeks.last().none { it.ahead }) {
                val frequency = if (work.first() > work.last()) "fewer" else "more"
                Text("You also logged work on $frequency days in the second week.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$subject by day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("Tap a date to see the entry and the next two mornings.", style = MaterialTheme.typography.bodySmall)
    }
    weeks.forEachIndexed { i, rows ->
        val title = if (i == 0) {
            if (event.stop) "Week ending on the last day" else "Week before"
        } else if (event.stop) "Week after stopping" else "Week from ${start.format(weekDate)}"
        MedicationWeekRow(rows, title, measure, subject, today, openDate)
    }
    val legend = if (measure.symptom) "Taller bars mean stronger ${symptom.lowercase()}. ○ Severity unrated."
        else "Taller bars mean higher energy."
    Text("$legend  – Not recorded." + if (weeks.flatten().any { it.ahead }) "  · Still ahead." else "",
        style = MaterialTheme.typography.bodySmall)
    val overlaps = remember(diary, event, today) {
        diary.courses.flatMap { course -> diary.changes(course).filter {
            it != event && it.date in start.minusWeeks(1)..minOf(today, start.plusDays(6))
        }.map { it to course.name } }.sortedBy { it.first.date }
    }
    for ((change, name) in overlaps) {
        Text("${change.date.format(weekDate)} · $name · ${change.label}", style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationWeekRow(rows: List<MedicationReading>, title: String, measure: MedicationMeasure, subject: String,
    today: LocalDate, openDate: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
            Text("${rows.first().day.format(weekDate)} – ${rows.last().day.format(weekDate)}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        BoxWithConstraints {
            val cellWidth = maxOf(48.dp, (28 * LocalDensity.current.fontScale).dp)
            val gap = ((maxWidth - cellWidth * 7) / 6).coerceIn(0.dp, 3.dp)
            val capacity = ((maxWidth + gap) / (cellWidth + gap)).toInt().coerceIn(1, 7)
            val columns = if (capacity < 7) minOf(capacity, 4) else 7
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (line in rows.chunked(columns)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in line) {
                            val shape = RoundedCornerShape(9.dp)
                            val description = "${row.day.format(spokenDate)}, $subject, ${row.label(measure.symptom)}"
                            Column(Modifier.weight(1f).testTag("medication-day-${row.day}").clip(shape)
                                .background(colors.surface).border(1.dp, colors.outlineVariant, shape)
                                .semantics { contentDescription = description }
                                .clickable(enabled = row.day <= today, role = Role.Button, onClickLabel = "Open diary day") { openDate(row.day) }
                                .heightIn(min = 61.dp).padding(vertical = 5.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                val value = row.level
                                Box(Modifier.height(25.dp), contentAlignment = Alignment.Center) {
                                    if (value != null && value >= 0 && !row.ahead) {
                                        val levels = if (measure.symptom) severityLabels.size else energyLabels.size
                                        Box(Modifier.fillMaxHeight().width(13.dp).clip(RoundedCornerShape(4.dp))
                                            .background(colors.surfaceContainerHighest), contentAlignment = Alignment.BottomCenter) {
                                            Box(Modifier.fillMaxWidth().fillMaxHeight((value + 1f) / levels)
                                                .background(colors.primary, RoundedCornerShape(3.dp)))
                                        }
                                    } else Text(when {
                                        row.ahead -> "·"
                                        value == UNRATED_SYMPTOM -> "○"
                                        else -> "–"
                                    }, Modifier.clearAndSetSemantics {}, color = colors.onSurfaceVariant)
                                }
                                Text(row.day.dayOfMonth.toString(), Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        val eligible = rows.count { !it.ahead }
        val recorded = rows.count { !it.ahead && it.level != null }
        val slot = if (measure.slot == "am") "mornings" else "evenings"
        Text("$recorded of $eligible $slot recorded" + if (eligible < rows.size) " · ${rows.size - eligible} still ahead" else "",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
