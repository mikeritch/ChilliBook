package uk.chillibook.diary

internal typealias DiaryEdit = ((Diary) -> Diary) -> Unit

internal fun Diary.changeDay(date: String, update: (Day) -> Day): Diary {
    val day = update(days.find { it.date == date } ?: Day(date))
    val empty = day.work == null && day.social == null && day.household == null && day.wellbeing == null &&
        day.impact == null && day.workOff == null && day.context.isEmpty()
    return copy(days = days.filterNot { it.date == date } + listOfNotNull(day.takeUnless { empty }))
}

internal fun Diary.changeCheck(date: String, slot: String, update: (Check) -> Check): Diary {
    val matches: (Check) -> Boolean = { it.date == date && it.slot == slot }
    val check = update(checks.find(matches) ?: Check(date, slot))
    val empty = check.energy == null && check.sleep == null && check.symptoms.isEmpty()
    return copy(checks = checks.filterNot(matches) + listOfNotNull(check.takeUnless { empty }))
}

internal fun Day.flag(tag: String) = when(tag) {
    "Work" -> work
    "Socialising" -> social
    "Household" -> household
    else -> error("Unsupported activity category")
}

internal fun Day.setFlag(tag: String, value: Boolean?) = when(tag) {
    "Work" -> copy(work = value, workOff = workOff.takeIf { value == false })
    "Socialising" -> copy(social = value)
    "Household" -> copy(household = value)
    else -> error("Unsupported activity category")
}

internal fun <T> Set<T>.toggle(item: T) = if (contains(item)) this - item else this + item
internal fun ActivityEntry.summary() = tags.sorted().joinToString(" · ").ifBlank { "Activity details" }
