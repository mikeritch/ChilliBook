package uk.chillibook.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val medicationDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private fun LocalDate.display() = format(medicationDateFormat)

internal enum class MedicationMeasure(val label: String, val slot: String, val lag: Long = 0, val symptom: Boolean = false) {
    Evening("Evening energy", "pm"),
    Morning("Morning energy", "am"),
    EveningSymptom("Evening symptom", "pm", symptom = true),
    MorningSymptom("Morning symptom", "am", symptom = true),
    Next("Next morning’s energy", "am", 1),
    Second("Second morning’s energy", "am", 2)
}

internal data class MedicationReading(val day: LocalDate, val observation: LocalDate, val ahead: Boolean, val level: Int?) {
    fun label(symptom: Boolean): String = when {
        ahead -> "Still ahead"
        symptom -> symptomRatingLabel(level)
        else -> level?.let(energyLabels::getOrNull) ?: "Not recorded"
    }
}

internal fun medicationWeek(index: PatternIndex, first: LocalDate, today: LocalDate,
    measure: MedicationMeasure, symptom: String): List<MedicationReading> =
    (0L..6L).map { offset ->
        val day = first.plusDays(offset)
        val observation = day.plusDays(measure.lag)
        val ahead = observation > today
        MedicationReading(day, observation, ahead, if (ahead) null else
            index.metric(observation, measure.slot, symptom.takeIf { measure.symptom }))
    }

internal data class MedicationChange(
    val courseId: String, val date: LocalDate, val label: String, val stop: Boolean = false,
    val detail: String = "", val entryKey: String = label,
) {
    val laterWeekStart: LocalDate get() = if (stop) date.plusDays(1) else date
}

private fun MedicationRegimen.description() =
    listOf(medicationAmount(strength, amount, unit), schedule.orEmpty()).filter(String::isNotBlank).joinToString(" · ")

internal fun Diary.changes(course: MedicationCourse): List<MedicationChange> {
    val plans = regimens.filter { it.courseId == course.id && it.effective != null }.sortedBy { it.effective }
    return buildList {
        course.start?.let { start ->
            add(MedicationChange(course.id, LocalDate.parse(start), "Started taking", entryKey = "start",
                detail = plans.lastOrNull { it.effective == start }?.description().orEmpty()))
        }
        plans.forEachIndexed { i, plan ->
            if (plan.effective == course.start) return@forEachIndexed
            val old = plans.getOrNull(i - 1)
            val before = old?.description().orEmpty()
            val after = plan.description()
            val amountBefore = old?.amount?.toBigDecimalOrNull()
            val amountAfter = plan.amount?.toBigDecimalOrNull()
            val comparable = old != null && old.unit == plan.unit && old.strength == plan.strength
            val label = when {
                old == null -> "Schedule recorded"
                old.schedule != plan.schedule -> "Schedule changed"
                comparable && amountBefore != null && amountAfter != null && amountBefore < amountAfter -> "Dose increased"
                comparable && amountBefore != null && amountAfter != null && amountBefore > amountAfter -> "Dose reduced"
                else -> "Regimen changed"
            }
            val detail = when {
                after.isBlank() -> "Dose and schedule unknown"
                before.isBlank() || before == after -> after
                else -> "$before → $after"
            }
            add(MedicationChange(course.id, LocalDate.parse(plan.effective!!), label, detail = detail, entryKey = "plan-${plan.id}"))
        }
        course.end?.let {
            add(MedicationChange(course.id, LocalDate.parse(it), "Last day of course", stop = true, entryKey = "stop"))
        }
    }.sortedBy { it.date }
}

internal data class MedicineChoice(val course: MedicationCourse, val event: MedicationChange? = null,
    val asNeeded: Boolean = false, val latest: String? = event?.date?.toString()) {
    val key = "${course.id}/${event?.entryKey ?: if (asNeeded) "prn" else "undated"}"
    val caption = when {
        event != null -> "${event.date.display()} · ${event.label}"
        asNeeded -> if (course.ended) "Past course · As needed" else "As needed"
        else -> "No dated change"
    }
}

internal fun Diary.medicineChoices(): List<MedicineChoice> = courses.flatMap { course ->
    val plans = regimens.filter { it.courseId == course.id }
    val lastDose = doses.asSequence().filter { it.courseId == course.id && it.asNeeded && it.status == "Taken" }.maxOfOrNull { it.date }
    val prn = plans.any { it.schedule == "As needed" } || lastDose != null
    val changes = if (prn && plans.none { it.schedule != null && it.schedule != "As needed" }) emptyList()
        else changes(course)
    buildList {
        for (change in changes) add(MedicineChoice(course, change))
        if (prn) add(MedicineChoice(course, asNeeded = true, latest = lastDose ?: course.start))
        if (!prn && changes.isEmpty()) add(MedicineChoice(course))
    }
}.sortedWith(compareByDescending<MedicineChoice> { it.latest }.thenByDescending { it.event?.stop == true }
    .thenBy { it.course.name.lowercase() }.thenBy { it.key })

internal fun Diary.asNeededDoses(start: LocalDate?, end: LocalDate?): Map<String, List<MedicationDose>> {
    val first = start?.toString()
    val last = end?.toString()
    return doses.filter { it.asNeeded && it.status == "Taken" && (first == null || it.date >= first) && (last == null || it.date <= last) }
        .groupBy { it.courseId }
}

@Composable
internal fun MedicationPatterns(diary: Diary, index: PatternIndex, today: LocalDate, initialCourseId: String?,
    openDay: (String) -> Unit, initialChoiceKey: String? = null, initialMeasure: MedicationMeasure? = null,
    initialSymptom: String? = null, initialDay: String? = null, initialStart: LocalDate? = null,
    initialEnd: LocalDate? = null, exit: () -> Unit) {
    val options = remember(diary.courses, diary.regimens, diary.doses) { diary.medicineChoices() }
    var selected by rememberSaveable(initialCourseId, initialChoiceKey) { mutableStateOf(initialChoiceKey) }
    val choice = options.find { it.key == selected } ?: options.find { it.course.id == initialCourseId } ?: options.firstOrNull() ?: return
    var picking by rememberSaveable { mutableStateOf(false) }
    var day by rememberSaveable(choice.key, initialDay) { mutableStateOf(initialDay.takeIf { choice.key == initialChoiceKey }) }
    var symptom by rememberSaveable(initialSymptom) { mutableStateOf(initialSymptom ?: "Brain fog") }
    val states = rememberSaveableStateHolder()
    val back = {
        if (day != null) day = null else if (picking) picking = false else exit()
    }
    BackHandler(onBack = back)
    val frame = Modifier.fillMaxSize().padding(horizontal = 20.dp)
    val gap = Arrangement.spacedBy(12.dp)
    when {
        day != null -> key(day) {
            Column(frame.verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = gap) {
                OutlinedButton(onClick = back) { Text(if (choice.asNeeded) "Dose days" else "Comparison") }
                MedicationReviewDay(diary, index, choice.course, LocalDate.parse(day), today, symptom, openDay)
            }
        }
        picking -> {
            Column(frame.verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = gap) {
                OutlinedButton(onClick = back) { Text("Back") }
                Text("Choose a change", style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif)
                for ((prn, title) in listOf(false to "Scheduled medicines", true to "As needed")) {
                    val group = options.filter { it.asNeeded == prn }
                    if (group.isEmpty()) continue
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    for (option in group) {
                        OutlinedButton(onClick = { selected = option.key; picking = false }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(option.course.name)
                                Text(if (prn) listOfNotNull(option.course.start, option.course.end).joinToString(" – ").ifEmpty { "Dates unknown" }
                                    else option.caption, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        else -> states.SaveableStateProvider(choice.key) {
            val event = choice.event
            val top: @Composable () -> Unit = {
                OutlinedButton(onClick = exit) { Text("‹  Diary patterns") }
                Text("Medication patterns", style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(choice.course.name, style = MaterialTheme.typography.titleMedium)
                            Text(choice.caption, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (options.size > 1) FilledTonalButton(onClick = { picking = true }) { Text("Switch") }
                    }
                }
                if (event != null && event.detail.isNotBlank()) Text(event.detail, style = MaterialTheme.typography.bodyMedium)
            }
            if (choice.asNeeded) {
                val days = remember(diary.doses, choice.course.id, initialStart, initialEnd) {
                    diary.asNeededDoses(initialStart, initialEnd)[choice.course.id].orEmpty()
                        .groupBy { it.date }.toSortedMap(reverseOrder())
                }
                LazyColumn(frame, contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = gap) {
                    item { Column(verticalArrangement = gap) { top() } }
                    item {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("On the days you took it", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                                val withReason = days.values.count { doses -> doses.any { !it.reason.isNullOrBlank() } }
                                val coverage = when {
                                    days.isEmpty() -> if (initialStart == null) "No as-needed doses recorded yet." else "No as-needed doses recorded in this period."
                                    withReason == 0 -> "You haven’t added a reason for these doses."
                                    withReason == 1 && days.size == 1 -> "You recorded why you took it that day."
                                    else -> "You recorded why you took it on $withReason of ${days.size} dose days."
                                }
                                Text(coverage)
                            }
                        }
                    }
                    items(days.keys.toList(), key = { it }) { date ->
                        val doses = days.getValue(date)
                        val reasons = doses.mapNotNull { it.reason }.distinct().joinToString(" · ").ifBlank { "Reason unrecorded" }
                        OutlinedButton(onClick = { day = date }, modifier = Modifier.fillMaxWidth().testTag("medication-dose-day-$date")) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(LocalDate.parse(date).display())
                                Text("${doses.size} ${if (doses.size == 1) "dose" else "doses"} · $reasons", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
                Column(frame.verticalScroll(rememberScrollState()).padding(bottom = 20.dp), verticalArrangement = gap) {
                    top()
                    if (event == null) Text("There’s no dated start, change or stop to compare for this course.")
                    else MedicationWeeks(diary, index, event, today, symptom, { symptom = it }, { day = it.toString() }, initialMeasure)
                }
            }
        }
    }
}
