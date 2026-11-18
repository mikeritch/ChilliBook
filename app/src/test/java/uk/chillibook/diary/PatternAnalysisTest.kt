package uk.chillibook.diary

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class PatternAnalysisTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val wednesday = monday.plusDays(2)

    @Test fun calendarLagKeepsTheTuesdayGapAndReadsBeyondAnchorRange() {
        val index = PatternIndex(Diary(
            days = listOf(Day(monday.toString(), work = true, social = false)),
            checks = listOf(Check(monday.toString(), "pm", energy = 2),
                Check(wednesday.toString(), "am", energy = 1)),
            activities = listOf(ActivityEntry(date = monday.toString(), tags = setOf("Call"), worth = "Yes"),
                ActivityEntry(date = monday.toString(), tags = setOf("Visit"))),
        ))
        for (factor in listOf(Factor.Work, Factor.Social)) {
            val missing = index.compare(monday.minusWeeks(1), monday, wednesday, factor, FollowUp.Next)
            assertEquals(1, missing.yes.missing)
            assertEquals(0, missing.yes.usable)
            assertEquals(0, missing.unknown)
            val usable = index.compare(monday, monday, wednesday, factor, FollowUp.Two)
            assertEquals(listOf(DatePair(monday, wednesday, 1)), usable.yes.dates)
            assertEquals(1, usable.yes.low)
        }
        assertNull(index.metric(monday.plusDays(1), "am", null))
    }

    @Test fun frequenciesCountCalendarDatesAndPreserveMissingRatings() {
        val mid = monday.plusDays(1)
        val index = PatternIndex(Diary(
            days = listOf(Day(mid.toString(), social = false)),
            activities = listOf(monday, monday, wednesday.plusDays(1)).map {
                ActivityEntry(date = it.toString(), tags = setOf("Call"))
            },
            checks = listOf(Check(monday.toString(), "am", symptoms = mapOf("Headache" to 0)),
                Check(mid.toString(), "am", energy = 1, symptoms = mapOf("Headache" to UNRATED_SYMPTOM)),
                Check(wednesday.toString(), "pm", symptoms = mapOf("Headache" to 3)))
        ))
        val social = index.activityDates(monday, wednesday, "Socialising")
        assertEquals(mapOf(true to listOf(monday), false to listOf(mid), null to listOf(wednesday)), social)
        assertEquals(mapOf(true to listOf(monday), null to listOf(mid, wednesday)),
            index.activityDates(monday, wednesday, "Call"))
        val ratings = index.monthly(monday, wednesday, "Headache", 4).getValue(YearMonth.from(monday)).getValue("am")
        assertEquals(2, ratings.recorded)
        assertEquals(1, ratings.unrated)
        assertEquals(1, ratings.counts[0])
        assertEquals(mapOf(0 to listOf(monday), UNRATED_SYMPTOM to listOf(mid), null to listOf(wednesday)),
            index.symptomDates(monday, wednesday, "Headache", "am"))
        assertEquals(mapOf(3 to listOf(wednesday), null to listOf(monday, mid)),
            index.symptomDates(monday, wednesday, "Headache", "pm"))
    }

    @Test fun incompleteAndFutureFollowUpsAreExcludedUntilEligible() {
        val index = PatternIndex(Diary(days = listOf(
            Day(monday.toString(), work = false, wellbeing = 0),
            Day(monday.plusDays(1).toString(), work = true))))
        val pending = index.compare(monday, wednesday, wednesday, Factor.Work, FollowUp.Next)
        assertEquals(1, pending.no.missing)
        assertEquals(0, pending.yes.missing)
        assertEquals(1, pending.unknown)
        val future = index.compare(monday, wednesday, wednesday, Factor.Work, FollowUp.Three)
        assertTrue(future.no.dates.isEmpty())
        assertTrue(future.yes.dates.isEmpty())
        assertEquals(1, index.compare(monday, monday, monday, Factor.Work, FollowUp.Wellbeing).no.low)
    }

    @Test fun yearlyMonthlyTotalsAndGroupDistributionsMatchTheirDates() {
        val start = LocalDate.of(2025, 1, 1)
        val dates = (0L until 365L).map(start::plusDays)
        val checks = dates.flatMapIndexed { i, date ->
            buildList {
                if (i % 3 != 0) add(Check(date.toString(), "am", energy = i % 5,
                    symptoms = if (i % 5 == 0) mapOf("Brain fog" to 0) else emptyMap()))
                if (i % 2 == 0) add(Check(date.toString(), "pm", energy = 4))
            }
        }
        val index = PatternIndex(Diary(checks = checks,
            days = dates.mapIndexed { i, d -> Day(d.toString(), work = i % 2 == 0) }))
        val months = index.monthly(start, dates.last(), null, 5)
        assertEquals(12, months.size)
        assertEquals(16, months.getValue(YearMonth.of(2025, 1)).getValue("pm").counts[4])
        for (slot in listOf("am", "pm")) {
            assertEquals(checks.count { it.slot == slot }, months.values.sumOf { it.getValue(slot).recorded })
        }
        val severity = index.monthly(start, dates.last(), "Brain fog", 4)
        assertEquals(checks.count { "Brain fog" in it.symptoms }, severity.values.sumOf { it.getValue("am").counts[0] })
        val result = index.compare(start, dates.last(), dates.last(), Factor.Work, FollowUp.Evening)
        assertEquals(183, result.yes.usable)
        assertEquals(182, result.no.missing)
        for (group in listOf(result.yes, result.no)) {
            for (level in 0..4) assertEquals(group.dates.count { it.level == level }, group.counts[level])
        }
    }
}
