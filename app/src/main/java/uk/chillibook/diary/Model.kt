package uk.chillibook.diary

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private fun now() = Instant.now().toString()
private fun zoneNow() = ZoneId.systemDefault().id

@Entity(tableName = "days")
data class Day(@PrimaryKey val date: String, val work: Boolean? = null, val social: Boolean? = null,
    val household: Boolean? = null, val wellbeing: Int? = null, val impact: String? = null,
    val context: Set<String> = emptySet(),
    val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "checks", primaryKeys = ["date","slot"])
data class Check(val date: String, val slot: String, val energy: Int? = null, val sleep: Int? = null,
    val symptoms: Map<String,Int> = emptyMap(), val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "activities")
data class ActivityEntry(@PrimaryKey val id: String = UUID.randomUUID().toString(), val date: String,
    val tags: Set<String> = emptySet(), val duration: String? = null, val effort: String? = null,
    val demands: Set<String> = emptySet(), val worth: String? = null, val workLength: String? = null,
    val workPlace: String? = null, val at: String = now(), val zone: String = zoneNow(),
    @ColumnInfo(defaultValue = "0") val workSetup: Boolean = false)

data class WorkSetup(val parts: Set<String> = emptySet(), val workLength: String? = null,
    val effort: String? = null, val demands: Set<String> = emptySet())

data class ActivitySetup(val duration: String? = null, val effort: String? = null,
    val demands: Set<String> = emptySet(), val worth: String? = null, val displayTag: String? = null)

internal fun supportedEffortAndDemands(effort: String?, demands: Set<String>): Boolean =
    (effort == null || effort in setOf("Light", "Moderate", "High")) &&
        demands.all { it in setOf("Physical", "Thinking", "Upright", "Sensory", "Emotional") }

internal fun ActivitySetup.hasSupportedDetails(): Boolean =
    (duration != null || effort != null || demands.isNotEmpty() || worth != null) &&
        (duration == null || duration in setOf("Under 15 min", "15–60 min", "Over an hour")) &&
        (worth == null || worth in setOf("Yes", "Mixed", "No")) && supportedEffortAndDemands(effort, demands)

@Entity(tableName = "prefs")
data class Prefs(@PrimaryKey val id: Int = 1, val theme: String = "system",
    val morningReminder: Int? = null, val eveningReminder: Int? = null, val workPin: WorkSetup? = null,
    @ColumnInfo(defaultValue = "'{}'") val activityPins: Map<String, ActivitySetup> = emptyMap())

data class Diary(val days: List<Day> = emptyList(), val checks: List<Check> = emptyList(),
    val activities: List<ActivityEntry> = emptyList(),
    val prefs: Prefs = Prefs())

val energyLabels = listOf("Very low","Low","Medium","Fairly high","High")
val sleepLabels = listOf("Unrefreshed","A little","Mostly","Fully")
val wellbeingLabels = listOf("Very rough","Rough","Mixed","Fairly well","Well")
// A selected symptom can be saved before the person chooses a severity.
internal const val UNRATED_SYMPTOM = -1
val severityLabels = listOf("None","Mild","Moderate","Strong")
val symptomLabels = listOf("Brain fog","Heavy fatigue","Muscle or joint pain","Headache","Dizzy when upright","Flu-like feeling","Light or sound sensitivity")
val categoryTags = mapOf(
    "Work" to setOf("Work","Meetings","Focused work","Commute"),
    "Socialising" to setOf("Socialising","Call","Visit","Meal out"),
    "Household" to setOf("Household","Cooking","Laundry","Cleaning","Shopping")
)
val otherTags = listOf("Personal care","Travel","Walking","Admin/screens","Quiet rest","Hobby")
fun activityFlag(day: Day?, activities: List<ActivityEntry>, category: String): Boolean? {
    if (activities.any { entry -> entry.tags.any { it in categoryTags[category].orEmpty() } }) return true
    return when(category) {
        "Work" -> day?.work
        "Socialising" -> day?.social
        "Household" -> day?.household
        else -> null
    }
}

fun Diary.dates(): List<String> = (days.map { it.date } + checks.map { it.date } +
    activities.map { it.date }).distinct().sorted()
