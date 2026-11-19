package uk.chillibook.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val reviewTitleDate = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy")
private val reviewMorningDate = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
internal fun MedicationReviewDay(diary: Diary, index: PatternIndex, course: MedicationCourse,
    date: LocalDate, today: LocalDate, symptom: String, openDay: (String) -> Unit) {
    val dateKey = date.toString()
    fun reading(day: LocalDate, slot: String, symptomName: String? = null): String {
        if (day > today) return "Still ahead"
        val level = index.metric(day, slot, symptomName)
        return if (symptomName != null) symptomRatingLabel(level)
            else level?.let(energyLabels::getOrNull) ?: "Not recorded"
    }
    Text(date.format(reviewTitleDate), Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
    Column(Modifier.fillMaxWidth()) {
        ReviewDayHeading(course.name)
        DoseRows(diary, course, dateKey, date > today)
    }
    Column(Modifier.fillMaxWidth()) {
        ReviewDayHeading("That day")
        ReviewDayRow("Morning energy", reading(date, "am"))
        ReviewDayRow("Evening energy", reading(date, "pm"))
        ReviewDayRow("Morning · $symptom", reading(date, "am", symptom))
        ReviewDayRow("Evening · $symptom", reading(date, "pm", symptom))
        val activities = index.activities[dateKey].orEmpty()
        val details = buildList {
            for (factor in Factor.values()) {
                index.status(date, factor)?.let { add(if (it) factor.label else "No ${factor.label.lowercase()}") }
            }
            for (entry in activities) {
                add(if (entry.restKind != null) listOfNotNull(entry.restKind, entry.restPeriod, entry.duration,
                    entry.restEnergy?.let { "Energy afterwards · $it" }).joinToString(" · ") else entry.activityLabel())
            }
            if (index.days[dateKey]?.noRests == true) add("Marked no rests")
        }
        ReviewDayRow("Activities logged", details.joinToString("\n").ifBlank {
            if (date > today) "Still ahead" else "Not recorded"
        })
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.padding(14.dp)) {
            ReviewDayHeading("The next two mornings")
            for (offset in 1L..2L) {
                val morning = date.plusDays(offset)
                ReviewDayRow(morning.format(reviewMorningDate), reading(morning, "am"))
            }
        }
    }
    if (date <= today) OutlinedButton(onClick = { openDay(dateKey) }) { Text("Open check-in") }
}

@Composable
private fun DoseRows(diary: Diary, course: MedicationCourse, date: String, ahead: Boolean) {
    val regimen = diary.scheduledRegimen(course, date)
    val doses = diary.doses.filter { it.courseId == course.id && it.date == date }
    fun amount(strength: String?, quantity: String?, unit: String?) =
        medicationAmount(strength, quantity, unit).ifBlank { "Dose unknown" }
    if (regimen != null) ReviewDayRow("Regimen", listOf(regimen.schedule ?: "Schedule unknown",
        amount(regimen.strength, regimen.amount, regimen.unit)).joinToString(" · "))
    for ((slot, label) in listOf("am" to "Morning", "pm" to "Evening")) {
        val answers = doses.filter { it.slot == slot && !it.asNeeded }
        if (answers.isEmpty() && regimen?.scheduledDoseDueIn(slot) == true) {
            ReviewDayRow("$label dose", if (ahead) "Scheduled" else ASSUMED_TAKEN_LABEL)
        }
        for (dose in answers) {
            ReviewDayRow("$label dose", listOf(if (dose.status == "Taken") "Confirmed taken" else dose.status,
                amount(dose.strength, dose.amount, dose.unit), dose.schedule ?: "Schedule unknown").joinToString(" · "))
        }
    }
    val prn = doses.filter { it.asNeeded }.sortedWith(compareBy({ it.slot != "am" }, { it.time }, { it.at }))
    for (dose in prn) {
        val slot = if (dose.slot == "am") "Morning" else "Evening"
        ReviewDayRow("As-needed dose", listOf(slot, dose.time ?: "Time unrecorded", dose.status,
            amount(dose.strength, dose.amount, dose.unit), dose.reason ?: "Reason unrecorded").joinToString(" · "))
    }
    if (doses.isEmpty() && regimen?.scheduledDoseDueIn("am") != true && regimen?.scheduledDoseDueIn("pm") != true) {
        Text(when {
            !course.includesMedicationDate(date) -> "Outside the dated schedule"
            regimen == null -> "No dated schedule or dose recorded"
            ahead -> "Still ahead"
            else -> "No dose recorded"
        }, Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun ReviewDayHeading(label: String) {
    Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ReviewDayRow(label: String, value: String) {
    val stack = LocalDensity.current.fontScale >= 1.35f
    val modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 9.dp)
    if (stack) {
        Column(modifier) {
            Text(label)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, Modifier.weight(0.44f))
            Text(value, Modifier.weight(0.56f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
