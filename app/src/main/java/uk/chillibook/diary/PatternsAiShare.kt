package uk.chillibook.diary

import java.time.LocalDate

internal data class PatternsAiShare(val prompt: String, val rows: List<String>) {
    val text: String get() = prompt + "\n\n" + rows.joinToString("\n")
}

private const val SHARE_PROMPT =
    "Read this diary period and tell me what stands out. Start with the most useful observation in plain language. " +
        "Look at what changed over time and whether anything tended to happen together or a day or two apart. " +
        "If there’s a plausible explanation, say what else could explain it and what would help check. " +
        "Keep it brief when the entries are sparse. Treat missing answers as unknown and associations as possible clues."

private val lineBreaks = Regex("\\R+")

internal fun patternsAiShare(diary: Diary, start: LocalDate, end: LocalDate): PatternsAiShare {
    val days = diary.days.associateBy { it.date }
    val checks = diary.checks.associateBy { it.date to it.slot }
    val activities = diary.activities.groupBy { it.date }
    val wearable = diary.wearable.associateBy { it.date }
    val courses = diary.courses.associateBy { it.id }
    val coursesStarting = diary.courses.groupBy { it.start }
    val coursesEnding = diary.courses.groupBy { it.end }
    val regimens = diary.regimens.groupBy { it.effective }
    val doses = diary.doses.groupBy { it.date }

    val rows = dateSequence(start, end).map { date ->
        val key = date.toString()
        val day = days[key]
        val fields = buildList {
            checks[key to "am"]?.let { addCheck("AM", it) }
            checks[key to "pm"]?.let { addCheck("PM", it) }
            day?.let {
                it.wellbeing?.let(wellbeingLabels::getOrNull)?.let { add("Wellbeing: $it") }
                it.stress?.let(stressLabels::getOrNull)?.let { add("Stress: $it") }
                it.work?.let { add("Work: ${yesNo(it)}") }
                it.social?.let { add("Socialising: ${yesNo(it)}") }
                it.household?.let { add("Household: ${yesNo(it)}") }
                if (it.noRests == true) add("Rest and naps: No")
                it.impact?.let { add("Recorded change to plans: $it") }
                it.caffeineTimes?.let { add("Caffeine: ${drinkDescription(it)}") }
                it.alcoholTimes?.let { add("Alcohol: ${drinkDescription(it)}") }
                if (it.context.isNotEmpty()) add("Context: ${it.context.sorted().joinToString()}")
            }
            for (activity in activities[key].orEmpty()) {
                activityDescription(activity)?.let(::add)
            }
            wearable[key]?.let {
                it.sleepMinutes?.let { add("Sleep ending this day: $it min") }
                it.steps?.let { add("Steps: $it") }
            }
            for (course in coursesStarting[key].orEmpty()) add("${course.name}: Course start")
            for (regimen in regimens[key].orEmpty()) {
                val name = courses[regimen.courseId]?.name ?: continue
                val details = listOfNotNull(
                    medicationAmount(regimen.strength, regimen.amount, regimen.unit).ifBlank { null },
                    regimen.schedule,
                )
                add((listOf("$name: Regimen effective") + details).joinToString(", "))
            }
            for (dose in doses[key].orEmpty()) {
                val name = courses[dose.courseId]?.name ?: continue
                val slot = when (dose.slot) {
                    "am" -> "AM"
                    "pm" -> "PM"
                    else -> dose.slot
                }
                val details = listOfNotNull(
                    "as needed".takeIf { dose.asNeeded },
                    medicationAmount(dose.strength, dose.amount, dose.unit).ifBlank { null },
                    dose.time,
                )
                add((listOf("$name $slot: ${dose.status}") + details).joinToString(", "))
            }
            for (course in coursesEnding[key].orEmpty()) add("${course.name}: Last day of course")
        }
        "$key | ${fields.joinToString(" | ").ifEmpty { "No entries" }.replace(lineBreaks, " ")}"
    }.toList()
    return PatternsAiShare(SHARE_PROMPT, rows)
}

private fun MutableList<String>.addCheck(label: String, check: Check) {
    check.energy?.let(energyLabels::getOrNull)?.let { add("$label energy: $it") }
    check.sleep?.let(sleepLabels::getOrNull)?.let { add("$label waking refreshment: $it") }
    if (check.symptoms.isNotEmpty()) {
        val symptoms = check.symptoms.toSortedMap().entries.joinToString(", ") { (name, value) ->
            "$name (${symptomRatingLabel(value)})"
        }
        add("$label symptoms: $symptoms")
    }
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun drinkDescription(times: Set<String>): String =
    (drinkTimes.filter { it in times } + (times - drinkTimes.toSet()).sorted()).joinToString()
        .ifEmpty { "Recorded, time unspecified" }

private fun activityDescription(activity: ActivityEntry): String? {
    val tags = activity.tags.sorted().filter { it != activity.restKind }
    val details = listOfNotNull(
        activity.restKind,
        activity.restPeriod,
        activity.workLength,
        activity.workPlace,
        activity.duration,
        activity.effort?.let { "$it effort" },
        activity.restEnergy?.let { "Energy after: $it" },
        activity.worth?.let { "Worthwhile: $it" },
    )
    val fields = tags + details + activity.demands.sorted()
    return fields.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Activity: ")
}
