package uk.chillibook.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class MedicineForm { Add, Restart, Course, Stop, Change, Regimen }

private fun displayDate(date: String?) = date?.let {
    LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
} ?: "Date unknown"

private fun MedicationRegimen.description() = listOfNotNull(
    medicationAmount(strength, amount, unit).ifBlank { null }, schedule
).joinToString(" · ").ifBlank { "Dose and schedule unknown" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationManagement(diary: Diary, edit: ((Diary) -> Diary) -> Unit, onPatterns: (String) -> Unit) {
    var past by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var form by rememberSaveable { mutableStateOf<MedicineForm?>(null) }
    var regimenId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = diary.courses.find { it.id == expanded }
    BackHandler(enabled = form != null) { form = null }
    key(form) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val mode = form
            if (mode != null) {
                MedicineEditor(mode, selected, diary.regimens.filter { it.courseId == selected?.id },
                    regimenId, edit, { form = null }) { course ->
                    expanded = course.id
                    past = course.ended
                    form = null
                }
            } else {
                TitleArt("Medication history", "history")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Current", true to "Past").forEach { (ended, label) ->
                        FilterChip(past == ended, { past = ended; expanded = null }, { Text(label) })
                    }
                }
                FilledTonalButton(onClick = { expanded = null; form = MedicineForm.Add }) { Text("Add medication") }
                val visible = diary.courses.filter { it.ended == past }.sortedWith(
                    compareBy<MedicationCourse> { it.name.lowercase() }.thenByDescending { it.start })
                if (visible.isEmpty()) Text(if (past) "No past medications." else "No current medications.")
                visible.forEach { course ->
                    val history = diary.regimens.filter { it.courseId == course.id }.sortedByDescending { it.effective }
                    HorizontalDivider()
                    Text(course.name, style = MaterialTheme.typography.titleLarge)
                    Text("${displayDate(course.start)} – " + if (course.ended) displayDate(course.end) else "present")
                    history.firstOrNull()?.let { Text(it.description()) }
                    OutlinedButton(onClick = { expanded = if (expanded == course.id) null else course.id }) {
                        Text(if (expanded == course.id) "Hide details" else "Show details")
                    }
                    if (expanded == course.id) {
                        if (course.ended) Text("Stopped · ${displayDate(course.end)}")
                        history.forEach { regimen ->
                            Text("${displayDate(regimen.effective)} · ${regimen.description()}")
                            OutlinedButton(onClick = { regimenId = regimen.id; form = MedicineForm.Regimen }) {
                                Text("Correct regimen")
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { form = MedicineForm.Change }) { Text("Record change") }
                            if (course.ended) {
                                OutlinedButton(onClick = { form = MedicineForm.Restart }) { Text("Restart medication") }
                            } else {
                                OutlinedButton(onClick = { form = MedicineForm.Stop }) { Text("Record stop date") }
                            }
                            OutlinedButton(onClick = { form = MedicineForm.Course }) { Text("Correct course details") }
                            OutlinedButton(onClick = { onPatterns(course.id) }) { Text("View in Patterns") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineEditor(
    form: MedicineForm, course: MedicationCourse?, history: List<MedicationRegimen>, regimenId: String?,
    edit: ((Diary) -> Diary) -> Unit, cancel: () -> Unit, saved: (MedicationCourse) -> Unit
) {
    val today = LocalDate.now().toString()
    val creating = form == MedicineForm.Add || form == MedicineForm.Restart
    val courseFields = creating || form == MedicineForm.Course
    val regimenFields = creating || form == MedicineForm.Change || form == MedicineForm.Regimen
    val initial = when (form) {
        MedicineForm.Change -> history.maxByOrNull { it.effective.orEmpty() }
        MedicineForm.Regimen -> history.find { it.id == regimenId }
        else -> null
    }
    var name by rememberSaveable { mutableStateOf(course?.name.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(if (form == MedicineForm.Course) course?.start else null) }
    var ended by rememberSaveable { mutableStateOf(form == MedicineForm.Stop || form == MedicineForm.Course && course?.ended == true) }
    var end by rememberSaveable { mutableStateOf(if (form == MedicineForm.Stop) today else if (form == MedicineForm.Course) course?.end else null) }
    var effective by rememberSaveable { mutableStateOf(if (form == MedicineForm.Regimen) initial?.effective else course?.end ?: today) }
    var strength by rememberSaveable { mutableStateOf(initial?.strength.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(initial?.amount.orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(initial?.unit.orEmpty()) }
    var schedule by rememberSaveable { mutableStateOf(initial?.schedule) }
    val title = when (form) {
        MedicineForm.Add -> "Add medication"
        MedicineForm.Restart -> "Restart medication"
        MedicineForm.Course -> "Correct course details"
        MedicineForm.Stop -> "Record a stop date"
        MedicineForm.Change -> "Record a change"
        MedicineForm.Regimen -> "Correct regimen"
    }
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (!courseFields) Text(course?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
    if (courseFields) {
        MedicineTextField("Medication name", name) { name = it }
        MedicineDate("Start date", start) { start = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!ended, { ended = false }, { Text("Ongoing") })
            FilterChip(ended, { ended = true }, { Text("Already stopped") })
        }
    }
    if (form == MedicineForm.Stop || courseFields && ended) {
        MedicineDate("Last day of course", end) { end = it }
    }
    if (regimenFields) {
        if (!creating) MedicineDate("Effective date", effective, optional = form == MedicineForm.Regimen) { date ->
            effective = date
            if (form == MedicineForm.Change && date != null) {
                val previous = history.filter { it.effective != null && it.effective <= date }
                    .maxByOrNull { it.effective!! }
                strength = previous?.strength.orEmpty()
                amount = previous?.amount.orEmpty()
                unit = previous?.unit.orEmpty()
                schedule = previous?.schedule
            }
        }
        MedicineTextField("Strength (optional)", strength) { strength = it }
        MedicineTextField("Amount (optional)", amount) { amount = it }
        MedicineTextField("Unit (optional)", unit) { unit = it }
        Text("Schedule", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, "Morning", "Evening", "Morning and evening", "As needed").forEach { option ->
                FilterChip(schedule == option, { schedule = option }, { Text(option ?: "Unknown") })
            }
        }
    }
    if (creating && start == null && !ended && schedule != null) {
        Text("This schedule begins today. The course start date stays unknown.")
    }
    val firstDay = if (courseFields) start else course?.start
    val lastDay = if (courseFields || form == MedicineForm.Stop) end.takeIf { ended } else course?.end
    fun outside(date: String?) = date != null &&
        (firstDay != null && date < firstDay || lastDay != null && date > lastDay)
    val error = when {
        firstDay != null && lastDay != null && lastDay < firstDay -> "The last day must be on or after the start date."
        !creating && (courseFields || form == MedicineForm.Stop) && history.any { outside(it.effective) } ->
            "These dates would put a recorded regimen outside this course. Check its effective date."
        !creating && regimenFields && outside(effective) -> "Choose a date within this course."
        !creating && regimenFields && history.any { it.id != (if (form == MedicineForm.Regimen) regimenId else null) && it.effective == effective } ->
            "A regimen already has this effective date. Open that regimen to correct it."
        else -> null
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = error == null && (!courseFields || name.isNotBlank()) &&
            (form != MedicineForm.Change || effective != null), onClick = {
            val updated = when {
                creating -> MedicationCourse(name = name.trim(), start = start, end = lastDay, ended = ended)
                course == null -> return@Button
                courseFields -> course.copy(name = name.trim(), start = start, end = lastDay, ended = ended)
                form == MedicineForm.Stop -> course.copy(end = end, ended = true)
                else -> course
            }
            val regimen = if (regimenFields) {
                val base = if (form == MedicineForm.Regimen) initial else null
                (base ?: MedicationRegimen(courseId = updated.id)).copy(effective = if (creating) start ?: today.takeIf { !ended && schedule != null } else effective,
                    strength = strength.trim().ifBlank { null }, amount = amount.trim().ifBlank { null },
                    unit = unit.trim().ifBlank { null }, schedule = schedule)
            } else null
            edit { current ->
                val courses = when {
                    creating -> current.courses + updated
                    courseFields || form == MedicineForm.Stop -> current.courses.map { if (it.id == updated.id) updated else it }
                    else -> current.courses
                }
                val regimens = when {
                    regimen == null -> current.regimens
                    form == MedicineForm.Regimen -> current.regimens.map { if (it.id == regimen.id) regimen else it }
                    creating && regimen.strength == null && regimen.amount == null && regimen.unit == null && regimen.schedule == null -> current.regimens
                    else -> current.regimens + regimen
                }
                current.copy(courses = courses, regimens = regimens)
            }
            saved(updated)
        }) { Text("Save") }
        OutlinedButton(onClick = cancel) { Text("Cancel") }
    }
}

@Composable
private fun MedicineTextField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicineDate(label: String, date: String?, optional: Boolean = true, change: (String?) -> Unit) {
    val context = LocalContext.current
    Text(label, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { chooseDiaryDate(context, date ?: LocalDate.now().toString()) { change(it) } }) {
            Text(displayDate(date))
        }
        if (optional && date != null) OutlinedButton(onClick = { change(null) }) { Text("Date unknown") }
    }
}
