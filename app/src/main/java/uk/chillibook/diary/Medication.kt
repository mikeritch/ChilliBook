package uk.chillibook.diary

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Entity(tableName = "medication_courses")
data class MedicationCourse(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val start: String? = null,
    val end: String? = null,
    val ended: Boolean = false,
    val at: String = Instant.now().toString(),
    val zone: String = ZoneId.systemDefault().id,
)

@Entity(tableName = "medication_regimens")
data class MedicationRegimen(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val effective: String? = null,
    val strength: String? = null,
    val amount: String? = null,
    val unit: String? = null,
    val schedule: String? = null,
    val at: String = Instant.now().toString(),
    val zone: String = ZoneId.systemDefault().id,
)

@Entity(tableName = "medication_doses")
data class MedicationDose(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val regimenId: String,
    val date: String,
    val slot: String,
    val status: String,
    val asNeeded: Boolean = false,
    val strength: String? = null,
    val amount: String? = null,
    val unit: String? = null,
    val schedule: String? = null,
    val time: String? = null,
    val reason: String? = null,
    val at: String = Instant.now().toString(),
    val zone: String = ZoneId.systemDefault().id,
)

internal fun MedicationCourse.includesMedicationDate(date: String): Boolean =
    (start == null || date >= start) && (end == null || date <= end) && !(ended && end == null)

internal fun Diary.scheduledRegimen(course: MedicationCourse, date: String): MedicationRegimen? {
    if (!course.includesMedicationDate(date)) return null
    return regimens.filter { it.courseId == course.id && it.effective != null && it.effective <= date }
        .maxByOrNull { it.effective!! }
}

internal fun MedicationRegimen.dueIn(slot: String): Boolean = when (schedule) {
    "Morning" -> slot == "am"
    "Evening" -> slot == "pm"
    "Morning and evening", "As needed" -> true
    else -> false
}

internal fun medicationAmount(strength: String?, amount: String?, unit: String?) =
    listOfNotNull(strength, listOfNotNull(amount, unit).joinToString(" ").ifBlank { null }).joinToString(" · ")

private fun MedicationRegimen.log(date: String, slot: String, status: String) = MedicationDose(
    courseId = courseId, regimenId = id, date = date, slot = slot, status = status, asNeeded = schedule == "As needed",
    strength = strength, amount = amount, unit = unit, schedule = schedule,
)

internal fun Diary.recordMedication(course: MedicationCourse, date: String, slot: String, status: String): Diary {
    val old = doses.find { it.courseId == course.id && it.date == date && it.slot == slot && !it.asNeeded }
    if (old != null) {
        return copy(doses = if (old.status == status) doses - old else
            doses.map { if (it.id == old.id) old.copy(status = status) else it })
    }
    val regimen = scheduledRegimen(course, date) ?: return this
    if (!regimen.dueIn(slot) || regimen.schedule == "As needed") return this
    return copy(doses = doses + regimen.log(date, slot, status))
}

internal fun Diary.addAsNeeded(course: MedicationCourse, date: String, slot: String): Diary {
    val regimen = scheduledRegimen(course, date) ?: return this
    if (regimen.schedule != "As needed") return this
    return copy(doses = doses + regimen.log(date, slot, "Taken"))
}
