package uk.chillibook.diary

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayMedication(diary: Diary, date: String, slot: String, edit: ((Diary) -> Diary) -> Unit, manage: () -> Unit) {
    val records = diary.doses.filter { it.date == date && it.slot == slot }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center) {
            Text("Medications", Modifier.padding(end = 12.dp, top = 8.dp), style = MaterialTheme.typography.titleLarge)
            FilledTonalButton(onClick = manage) { Text(if (diary.courses.isEmpty()) "Add medication" else "Manage medications") }
        }
        val courses = diary.courses.filter { c ->
            diary.scheduledRegimen(c, date)?.dueIn(slot) == true || records.any { it.courseId == c.id }
        }.sortedBy { it.name.lowercase() }
        if (courses.isEmpty() && diary.courses.isNotEmpty())
            Text("No doses scheduled for this check-in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (course in courses) key(course.id) {
            val regimen = diary.scheduledRegimen(course, date)
            val logs = records.filter { it.courseId == course.id }
            val scheduled = logs.firstOrNull { !it.asNeeded }
            val prn = regimen?.schedule == "As needed"
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(course.name, fontWeight = FontWeight.SemiBold)
                    if (scheduled != null || (!prn && regimen?.dueIn(slot) == true)) {
                        val detail = if (scheduled == null) medicationAmount(regimen?.strength, regimen?.amount, regimen?.unit)
                            else medicationAmount(scheduled.strength, scheduled.amount, scheduled.unit)
                        val schedule = if (scheduled != null) scheduled.schedule else regimen?.schedule
                        Text(listOfNotNull(detail.ifBlank { null }, schedule, scheduled?.status ?: ASSUMED_TAKEN_LABEL).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Taken", "Skipped").forEach { status ->
                                AnswerTile(course.name, status, (scheduled?.status ?: "Taken") == status) {
                                    edit { it.recordMedication(course, date, slot, status) }
                                }
                            }
                        }
                    }
                    if (prn) OutlinedButton(onClick = { edit { it.addAsNeeded(course, date, slot) } }) { Text("+  Add as-needed dose") }
                    logs.filter { it.asNeeded }.sortedWith(compareBy({ it.time ?: "24:00" }, { it.at }, { it.id }))
                        .forEachIndexed { i, dose -> key(dose.id) { AsNeededDose(i + 1, dose, edit) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AsNeededDose(number: Int, dose: MedicationDose, edit: ((Diary) -> Diary) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    fun update(fn: (MedicationDose) -> MedicationDose) {
        edit { d -> d.copy(doses = d.doses.map { if (it.id == dose.id) fn(it) else it }) }
    }
    OutlinedButton(onClick = { expanded = !expanded }) {
        Text(listOfNotNull("Dose $number", dose.time, medicationAmount(dose.strength, dose.amount, dose.unit).ifBlank { null },
            dose.reason, if (expanded) "Hide details" else "Edit").joinToString(" · "))
    }
    if (expanded) {
        var amount by rememberSaveable(dose.id) { mutableStateOf(dose.amount.orEmpty()) }
        var unit by rememberSaveable(dose.id) { mutableStateOf(dose.unit.orEmpty()) }
        var reason by rememberSaveable(dose.id) { mutableStateOf(dose.reason.orEmpty()) }
        val picker = {
            val initial = dose.time?.let(LocalTime::parse) ?: LocalTime.now()
            TimePickerDialog(context, { _, h, m -> update { it.copy(time = String.format(Locale.ROOT, "%02d:%02d", h, m)) } },
                initial.hour, initial.minute, true).show()
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = picker) { Text(dose.time?.let { "Time · $it" } ?: "Add time") }
            if (dose.time != null) OutlinedButton(onClick = { update { it.copy(time = null) } }) { Text("Clear time") }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            MedicationUnitField(unit) { unit = it }
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason or note (optional)") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { edit { d -> d.copy(doses = d.doses.filterNot { it.id == dose.id }) } }) { Text("Remove dose", color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    update { it.copy(amount = amount.trim().ifBlank { null }, unit = if (unit == dose.unit.orEmpty()) it.unit else unit.trim().ifBlank { null }, reason = reason.trim().ifBlank { null }) }
                    expanded = false
                }) { Text("Save details") }
            }
        }
    }
}
