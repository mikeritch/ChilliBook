package uk.chillibook.diary

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
    val context: Set<String> = emptySet(), val workOff: String? = null,
    val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "checks", primaryKeys = ["date","slot"])
data class Check(val date: String, val slot: String, val energy: Int? = null, val sleep: Int? = null,
    val symptoms: Map<String,Int> = emptyMap(), val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "activities")
data class ActivityEntry(@PrimaryKey val id: String = UUID.randomUUID().toString(), val date: String,
    val tags: Set<String> = emptySet(), val duration: String? = null, val effort: String? = null,
    val demands: Set<String> = emptySet(), val worth: String? = null, val workLength: String? = null,
    val workPlace: String? = null, val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "episodes")
data class Episode(@PrimaryKey val id: String = UUID.randomUUID().toString(), val start: String,
    val end: String? = null, val approxStart: Boolean = false, val approxEnd: Boolean = false,
    val worst: String? = null, val at: String = now(), val zone: String = zoneNow())

@Entity(tableName = "prefs")
data class Prefs(@PrimaryKey val id: Int = 1, val theme: String = "system",
    val pins: Set<String> = setOf("Brain fog","Heavy fatigue"),
    val morningReminder: Int? = null, val eveningReminder: Int? = null)

data class Diary(val days: List<Day> = emptyList(), val checks: List<Check> = emptyList(),
    val activities: List<ActivityEntry> = emptyList(), val episodes: List<Episode> = emptyList(),
    val prefs: Prefs = Prefs())

val energyLabels = listOf("Very low","Low","Medium","Fairly high","High")
val sleepLabels = listOf("Unrefreshed","A little","Mostly","Fully")
val wellbeingLabels = listOf("Very rough","Rough","Mixed","Fairly well","Well")
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
    activities.map { it.date } + episodes.flatMap { listOfNotNull(it.start,it.end) }).distinct().sorted()
