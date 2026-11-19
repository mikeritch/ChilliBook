package uk.chillibook.diary

import java.time.LocalDate
import java.time.YearMonth

internal enum class Factor(val label: String) { Work("Work"), Social("Socialising"), Household("Household") }

internal enum class FollowUp(val label: String, val lag: Long) {
    Next("Next morning’s energy", 1), Two("Two mornings later", 2), Three("Three mornings later", 3),
    Evening("Same evening’s energy", 0), Wellbeing("Same day’s wellbeing", 0)
}

internal fun symptomRatingLabel(value: Int?) = when (value) {
    UNRATED_SYMPTOM -> "Severity not rated"
    else -> value?.let(severityLabels::getOrNull) ?: "Not recorded"
}

internal class MonthlyRatings(levels: Int) {
    val counts = IntArray(levels)
    var unrated = 0
    val recorded get() = counts.sum() + unrated
}

internal data class DatePair(val anchor: LocalDate, val follow: LocalDate, val level: Int?)
internal class ComparisonGroup(val dates: List<DatePair>) {
    val counts = IntArray(5).apply { dates.forEach { pair -> pair.level?.let { this[it]++ } } }
    val usable = counts.sum()
    val low = counts[0] + counts[1]
    val missing = dates.size - usable
}
internal data class Comparison(val yes: ComparisonGroup, val no: ComparisonGroup, val unknown: Int)

internal fun dateSequence(start: LocalDate, end: LocalDate): Sequence<LocalDate> =
    generateSequence(start) { if (it < end) it.plusDays(1) else null }.takeWhile { it <= end }

internal class PatternIndex(val diary: Diary) {
    val days = diary.days.associateBy { it.date }
    val checks = diary.checks.associateBy { it.date to it.slot }
    val activities = diary.activities.groupBy { it.date }
    val first = (days.keys.asSequence() + diary.checks.asSequence().map { it.date } +
        activities.keys.asSequence() + diary.courses.asSequence().flatMap { sequenceOf(it.start, it.end).filterNotNull() } +
        diary.regimens.asSequence().mapNotNull { it.effective } + diary.doses.asSequence().map { it.date }).minOrNull()?.let(LocalDate::parse)

    fun status(date: LocalDate, factor: Factor): Boolean? {
        val key = date.toString()
        return activityFlag(days[key], activities[key].orEmpty(), factor.label)

    }

    fun metric(date: LocalDate, slot: String, symptom: String?): Int? {
        val check = checks[date.toString() to slot] ?: return null
        return if (symptom == null) check.energy else check.symptoms[symptom]
    }

    fun symptomDates(start: LocalDate, end: LocalDate, symptom: String, slot: String) =
        dateSequence(start, end).groupBy { date -> metric(date, slot, symptom) }

    fun compare(start: LocalDate, end: LocalDate, today: LocalDate, factor: Factor, outcome: FollowUp): Comparison {
        val yes = mutableListOf<DatePair>()
        val no = mutableListOf<DatePair>()
        var unknown = 0
        val begin = first?.let { maxOf(it, start) }
        if (begin != null) for (date in dateSequence(begin, minOf(today, end))) {
            val group = status(date, factor)
            if (group == null) {
                unknown++
                continue
            }
            val follow = date.plusDays(outcome.lag)
            if (follow > today) continue
            val value = when (outcome) {
                FollowUp.Evening -> metric(follow, "pm", null)
                FollowUp.Wellbeing -> days[follow.toString()]?.wellbeing
                else -> metric(follow, "am", null)
            }
            if (value != null || follow < today) {
                (if (group) yes else no).add(DatePair(date, follow, value))
            }
        }
        return Comparison(ComparisonGroup(yes), ComparisonGroup(no), unknown)
    }

    fun monthly(start: LocalDate, end: LocalDate, symptom: String?, levels: Int): Map<YearMonth, Map<String, MonthlyRatings>> {
        val result = linkedMapOf<YearMonth, Map<String, MonthlyRatings>>()
        var month = YearMonth.from(start)
        while (month <= YearMonth.from(end)) {
            result[month] = mapOf("am" to MonthlyRatings(levels), "pm" to MonthlyRatings(levels))
            month = month.plusMonths(1)
        }
        for (check in diary.checks) {
            val date = LocalDate.parse(check.date)
            if (date < start || date > end) continue
            val value = if (symptom == null) check.energy else check.symptoms[symptom]
            val ratings = result.getValue(YearMonth.from(date)).getValue(check.slot)
            if (value == UNRATED_SYMPTOM && symptom != null) ratings.unrated++
            else if (value != null && value in ratings.counts.indices) ratings.counts[value]++
        }
        return result
    }
}
