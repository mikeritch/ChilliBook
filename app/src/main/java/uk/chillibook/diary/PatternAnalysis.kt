package uk.chillibook.diary

import java.time.LocalDate
import java.time.YearMonth

internal enum class Factor(val label: String) { Work("Work"), Social("Socialising"), Household("Household") }

internal enum class FollowUp(val label: String, val lag: Long) {
    Next("Next morning’s energy", 1), Two("Two mornings later", 2), Three("Three mornings later", 3),
    Evening("Same evening’s energy", 0), Wellbeing("Same day’s wellbeing", 0)
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
        activities.keys.asSequence() + diary.episodes.asSequence().map { it.start }).minOrNull()?.let(LocalDate::parse)

    fun status(date: LocalDate, factor: Factor): Boolean? {
        val key = date.toString()
        return activityFlag(days[key], activities[key].orEmpty(), factor.label)

    }

    fun metric(date: LocalDate, slot: String, symptom: String?): Int? {
        val check = checks[date.toString() to slot] ?: return null
        return if (symptom == null) check.energy else check.symptoms[symptom]
    }

    fun activityDates(start: LocalDate, end: LocalDate, name: String): Map<Boolean?, List<LocalDate>> {
        val factor = Factor.values().firstOrNull { it.label == name }
        return dateSequence(start, end).groupBy { date ->
            if (factor != null) status(date, factor)
            else true.takeIf { activities[date.toString()].orEmpty().any { name in it.tags } }
        }
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

    fun monthly(start: LocalDate, end: LocalDate, symptom: String?, levels: Int): Map<YearMonth, Map<String, IntArray>> {
        val result = linkedMapOf<YearMonth, Map<String, IntArray>>()
        var month = YearMonth.from(start)
        while (month <= YearMonth.from(end)) {
            result[month] = mapOf("am" to IntArray(levels), "pm" to IntArray(levels))
            month = month.plusMonths(1)
        }
        for (check in diary.checks) {
            val date = LocalDate.parse(check.date)
            if (date < start || date > end) continue
            val value = if (symptom == null) check.energy else check.symptoms[symptom]
            if (value != null) result.getValue(YearMonth.from(date)).getValue(check.slot)[value]++
        }
        return result
    }
}
