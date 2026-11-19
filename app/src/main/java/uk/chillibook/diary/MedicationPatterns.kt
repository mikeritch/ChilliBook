package uk.chillibook.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

internal data class MedicationChange(val courseId: String, val date: LocalDate, val label: String, val stop: Boolean = false) {
    val laterWeekStart: LocalDate get() = if (stop) date.plusDays(1) else date
}

internal fun medicationComparison(before: List<MedicationReading>, after: List<MedicationReading>,
    measure: MedicationMeasure, symptom: String): String {
    fun rated(rows: List<MedicationReading>) = rows.filterNot { it.ahead }.mapNotNull { it.level }.filter { it >= 0 }
    val earlier = rated(before)
    val later = rated(after)
    if (earlier.isEmpty() || later.isEmpty()) return "There are too few rated observations to compare the two weeks."
    fun count(levels: List<Int>) = levels.count { if (measure.symptom) it > 0 else it <= 1 }
    val subject = if (measure.symptom) symptom else "Low or very low energy"
    return "$subject was recorded on ${count(earlier)} of ${earlier.size} rated dates in the earlier week and ${count(later)} of ${later.size} in the later week."
}

private fun Diary.changes(course: MedicationCourse): List<MedicationChange> {
    val plans = regimens.filter { it.courseId == course.id }
    fun details(plan: MedicationRegimen?) = listOfNotNull(
        medicationAmount(plan?.strength, plan?.amount, plan?.unit).ifBlank { null }, plan?.schedule).joinToString(" · ")
    return buildList {
        fun addEvent(date: String?, label: String, plan: MedicationRegimen? = null, stop: Boolean = false) {
            if (date != null) add(MedicationChange(course.id, LocalDate.parse(date),
                listOf(label, details(plan)).filter(String::isNotBlank).joinToString(" · "), stop))
        }
        addEvent(course.start, "Started", plans.firstOrNull { it.effective == course.start })
        plans.filter { it.effective != course.start }.forEach { addEvent(it.effective, "Regimen change", it) }
        addEvent(course.end, "Last day", stop = true)
    }.sortedBy { it.date }
}

@Composable
fun MedicationPatterns(diary: Diary, start: LocalDate, end: LocalDate, initialCourseId: String?, openDay: (String) -> Unit) {
    val courses = diary.courses.sortedWith(compareBy({ it.ended }, { it.name.lowercase() }, { it.start }))
    if (courses.isEmpty()) {
        Text("Add a medication in History to explore your diary around a change.")
        return
    }
    var selected by rememberSaveable(initialCourseId) { mutableStateOf(initialCourseId) }
    val course = courses.find { it.id == selected } ?: courses.first()
    val events = remember(diary) { diary.courses.flatMap { diary.changes(it) } }
    val options = events.filter { it.courseId == course.id && it.date in start..end }
    val index = remember(diary) { PatternIndex(diary) }
    val today = LocalDate.now()
    val prn = diary.doses.filter { it.courseId == course.id && it.asNeeded && it.date in start.toString()..end.toString() }
    val names = courses.map {
        listOf(it.name, if (it.ended) "Past" else "Current", it.start ?: "Start unknown").joinToString(" · ")
    }
    PatternMenu("Medication · ${names[courses.indexOf(course)]}", names, { selected = courses[it].id })

    var eventKey by rememberSaveable(course.id) { mutableStateOf<String?>(null) }
    val event = options.find { "${it.date} ${it.label}" == eventKey } ?: options.lastOrNull()
    val symptomOptions = (symptomLabels + diary.checks.flatMap { it.symptoms.keys }).distinct()
    var symptom by rememberSaveable { mutableStateOf("Headache") }
    var measure by rememberSaveable { mutableStateOf(MedicationMeasure.Evening) }
    if (event != null) {
        val eventNames = options.map { "${it.date.display()} · ${it.label}" }
        PatternMenu("Change · ${eventNames[options.indexOf(event)]}", eventNames, {
            eventKey = "${options[it].date} ${options[it].label}"
        })
        PatternMenu("Diary measure · ${measure.label}", MedicationMeasure.values().map { it.label }, {
            measure = MedicationMeasure.values()[it]
        })
        if (measure.symptom) PatternMenu("Symptom · $symptom", symptomOptions, { symptom = symptomOptions[it] })
        var expanded by rememberSaveable(course.id, event.date.toString(), event.label) { mutableIntStateOf(-1) }
        val weeks = listOf(event.laterWeekStart.minusWeeks(1), event.laterWeekStart).map {
            medicationWeek(index, it, today, measure, symptom)
        }
        Text(medicationComparison(weeks.first(), weeks.last(), measure, symptom))
        val titles = if (event.stop) listOf("Week ending on the last day", "Week after stopping")
            else listOf("Week before", "From this change")
        titles.forEachIndexed { n, title ->
            val rows = weeks[n]
            val first = rows.first().day
            val eligible = rows.count { !it.ahead }
            val rated = rows.mapNotNull { it.level }.filter { it >= 0 }
            val unrated = rows.count { it.level == UNRATED_SYMPTOM }
            val recorded = rated.size + unrated
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text("${first.display()} – ${rows.last().day.display()}")
                    Text("$recorded of $eligible eligible observations recorded")
                    if (measure.symptom) Text("${rated.count { it > 0 }} with $symptom · ${rated.count { it == 0 }} marked None · $unrated unrated")
                    else Text("${rated.count { it <= 1 }} with low or very low energy")
                    if (eligible < 7) Text("${7 - eligible} still ahead")
                    val contexts = rows.map { it.day }.filter { it <= today }
                    val work = contexts.count { index.status(it, Factor.Work) == true }
                    val activity = contexts.count { date -> index.activities[date.toString()].orEmpty().any { it.restKind == null && it.tags.any { tag -> tag != "Quiet rest" } } ||
                        index.status(date, Factor.Social) == true || index.status(date, Factor.Household) == true }
                    val rest = contexts.count { date -> index.activities[date.toString()].orEmpty().any { it.restKind != null || "Quiet rest" in it.tags } }
                    Text("Days with recorded work: $work · activity: $activity · rest: $rest")
                    events.filter { it != event && it.date in first..rows.last().day }.forEach { other ->
                        val name = courses.firstOrNull { it.id == other.courseId }?.name.orEmpty()
                        Text("${other.date.display()} · $name · ${other.label}")
                    }
                    OutlinedButton(onClick = { expanded = if (expanded == n) -1 else n }) {
                        Text(if (expanded == n) "Hide dates" else "See dates")
                    }
                    if (expanded == n) rows.forEach { row ->
                        var show by rememberSaveable(course.id, row.day.toString()) { mutableStateOf(false) }
                        val following = if (row.observation != row.day) " → ${row.observation.display()}" else ""
                        OutlinedButton(onClick = { show = !show }) {
                            Text("${row.day.display()}$following · ${row.label(measure.symptom)}")
                        }
                        if (show) MedicationDateDetail(diary, index, course, row.day, today, symptom, openDay)
                    }
                }
            }
        }
        Text("These dates show what you recorded around the change. They cannot establish whether the medication helped or caused a change.")
    } else Text("No dated start, change or stop in this period.")
    if (prn.isNotEmpty() || diary.regimens.any { it.courseId == course.id && it.schedule == "As needed" }) {
        Text("As-needed doses", style = MaterialTheme.typography.titleMedium)
        if (prn.isEmpty()) Text("No as-needed doses recorded in this period.")
        prn.groupBy { it.date }.toSortedMap(reverseOrder()).forEach { (date, _) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(LocalDate.parse(date).display(), style = MaterialTheme.typography.titleMedium)
                    MedicationDateDetail(diary, index, course, LocalDate.parse(date), today, symptom, openDay)
                }
            }
        }
    }
}

@Composable
private fun MedicationDateDetail(diary: Diary, index: PatternIndex, course: MedicationCourse, date: LocalDate,
    today: LocalDate, symptom: String, openDay: (String) -> Unit) {
    val dateKey = date.toString()
    val doses = diary.doses.filter { it.courseId == course.id && it.date == dateKey }
    val regimen = diary.scheduledRegimen(course, dateKey)
    if (regimen != null) Text(listOfNotNull(course.name, regimen.schedule,
        medicationAmount(regimen.strength, regimen.amount, regimen.unit).ifBlank { null }).joinToString(" · "))
    val scheduledSlots = listOf("am" to "Morning", "pm" to "Evening").filter { (slot, _) ->
        regimen?.scheduledDoseDueIn(slot) == true && doses.none { !it.asNeeded && it.slot == slot }
    }
    for ((_, label) in scheduledSlots) {
        val status = if (date > today) "Scheduled" else ASSUMED_TAKEN_LABEL
        Text("$label · $status")
    }
    if (doses.isEmpty() && scheduledSlots.isEmpty()) Text(when {
        !course.includesMedicationDate(dateKey) -> "Outside the dated schedule"
        regimen == null -> "No dated schedule or dose recorded"
        else -> "No dose answer recorded"
    })
    doses.sortedBy { it.time ?: "" }.forEach { dose ->
        val whenTaken = if (dose.asNeeded) dose.time ?: "Time unrecorded" else if (dose.slot == "am") "Morning" else "Evening"
        val reason = if (dose.asNeeded) dose.reason ?: "Reason unrecorded" else dose.reason
        Text(listOfNotNull(course.name, whenTaken, dose.status,
            medicationAmount(dose.strength, dose.amount, dose.unit).ifBlank { null }, reason).joinToString(" · "))
    }
    val activities = index.activities[dateKey].orEmpty()
    index.status(date, Factor.Work)?.let { Text(if (it) "Work recorded" else "Marked no work") }
    activities.forEach { activity ->
        val description = listOfNotNull(activity.tags.sorted().joinToString(", ").ifBlank { activity.restKind },
            activity.restPeriod, activity.workLength ?: activity.duration, activity.effort).joinToString(" · ")
        if (description.isNotBlank()) Text(description)
    }
    if (index.days[dateKey]?.noRests == true) Text("Marked no rests")
    listOf(MedicationMeasure.Morning, MedicationMeasure.Evening, MedicationMeasure.MorningSymptom,
        MedicationMeasure.EveningSymptom, MedicationMeasure.Next, MedicationMeasure.Second).forEach { measure ->
        val day = date.plusDays(measure.lag)
        val label = if (measure.symptom) "${if (measure.slot == "am") "Morning" else "Evening"} · $symptom" else measure.label
        val value = MedicationReading(date, day, day > today, index.metric(day, measure.slot, symptom.takeIf { measure.symptom }))
        Text("$label · ${day.display()} · ${value.label(measure.symptom)}")
    }
    if (date <= today) OutlinedButton(onClick = { openDay(dateKey) }) { Text("Open check-in") }
}
