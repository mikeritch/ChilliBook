package uk.chillibook.diary

internal fun diaryEditCue(before: Diary, after: Diary, date: String?): FeedbackCue? {
    if (date == null) return listOf(
        recordCue(before.courses, after.courses, MedicationCourse::id, ::answerCue),
        recordCue(before.regimens, after.regimens, MedicationRegimen::id, ::answerCue),
    ).combinedCue()

    val oldDay = before.days.find { it.date == date }
    val newDay = after.days.find { it.date == date }
    return buildList {
        add(answerCue(oldDay?.work, newDay?.work))
        add(answerCue(oldDay?.social, newDay?.social))
        add(answerCue(oldDay?.household, newDay?.household))
        add(answerCue(oldDay?.wellbeing, newDay?.wellbeing))
        add(answerCue(oldDay?.stress, newDay?.stress))
        add(drinkCue(oldDay?.caffeineTimes, newDay?.caffeineTimes))
        add(drinkCue(oldDay?.alcoholTimes, newDay?.alcoholTimes))
        add(answerCue(oldDay?.impact, newDay?.impact))
        add(answerCue(oldDay?.noRests?.takeIf { it }, newDay?.noRests?.takeIf { it }))
        add(choiceCue(oldDay?.context.orEmpty(), newDay?.context.orEmpty()))

        val oldChecks = before.checks.filter { it.date == date }.associateBy(Check::slot)
        val newChecks = after.checks.filter { it.date == date }.associateBy(Check::slot)
        for (slot in oldChecks.keys + newChecks.keys) {
            val oldCheck = oldChecks[slot]
            val newCheck = newChecks[slot]
            add(answerCue(oldCheck?.energy, newCheck?.energy))
            add(answerCue(oldCheck?.sleep, newCheck?.sleep))
            val oldSymptoms = oldCheck?.symptoms.orEmpty()
            val newSymptoms = newCheck?.symptoms.orEmpty()
            add(choiceCue(oldSymptoms.keys, newSymptoms.keys))
            for (name in oldSymptoms.keys.intersect(newSymptoms.keys)) {
                add(answerCue(
                    oldSymptoms[name]?.takeUnless { it == UNRATED_SYMPTOM },
                    newSymptoms[name]?.takeUnless { it == UNRATED_SYMPTOM },
                ))
            }
        }
        add(recordCue(before.activities.filter { it.date == date }, after.activities.filter { it.date == date },
            ActivityEntry::id, ::activityCue))
        add(recordCue(before.doses.filter { it.date == date }, after.doses.filter { it.date == date },
            MedicationDose::id, ::doseCue))
    }.combinedCue()
}

private fun answerCue(before: Any?, after: Any?): FeedbackCue? = when {
    before == after -> null
    after == null -> FeedbackCue.Clear
    else -> FeedbackCue.Select
}

private fun drinkCue(before: Set<String>?, after: Set<String>?): FeedbackCue? =
    if (before == null || after == null) answerCue(before, after) else choiceCue(before, after)

private fun choiceCue(before: Set<String>, after: Set<String>): FeedbackCue? = when {
    before == after -> null
    !before.containsAll(after) -> FeedbackCue.Select
    else -> FeedbackCue.Clear
}

private fun activityCue(before: ActivityEntry, after: ActivityEntry): FeedbackCue? = listOf(
    choiceCue(before.tags, after.tags),
    answerCue(before.duration, after.duration),
    answerCue(before.effort, after.effort),
    choiceCue(before.demands, after.demands),
    answerCue(before.worth, after.worth),
    answerCue(before.workLength, after.workLength),
    answerCue(before.workPlace, after.workPlace),
    answerCue(before.restKind, after.restKind),
    answerCue(before.restPeriod, after.restPeriod),
    answerCue(before.restEnergy, after.restEnergy),
).combinedCue()

private fun doseCue(before: MedicationDose, after: MedicationDose): FeedbackCue? =
    if (before.copy(time = after.time) != after) FeedbackCue.Select else answerCue(before.time, after.time)

private fun <T> recordCue(
    before: List<T>,
    after: List<T>,
    id: (T) -> String,
    editCue: (T, T) -> FeedbackCue?,
): FeedbackCue? {
    val oldRecords = before.associateBy(id)
    val newRecords = after.associateBy(id)
    return (oldRecords.keys + newRecords.keys).map { key ->
        val old = oldRecords[key]
        val new = newRecords[key]
        when {
            new == null -> FeedbackCue.Clear
            old == null -> FeedbackCue.Select
            else -> editCue(old, new)
        }
    }.combinedCue()
}

// A replacement can clear an old value as part of saving a new answer.
private fun List<FeedbackCue?>.combinedCue(): FeedbackCue? = when {
    FeedbackCue.Select in this -> FeedbackCue.Select
    FeedbackCue.Clear in this -> FeedbackCue.Clear
    else -> null
}
