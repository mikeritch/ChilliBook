package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityFrequencyTest {
    private val start = LocalDate.of(2024, 2, 28)
    private fun day(n: Int) = start.plusDays(n.toLong())

    @Test fun bucketsClipToTheSelectedRange() {
        val index = PatternIndex(Diary())
        fun buckets(days: Int) = index.activityFrequency(start, day(days - 1)).buckets
        assertEquals((0..6).map { FrequencyBucket(day(it), day(it)) }, buckets(7))
        assertEquals(listOf(FrequencyBucket(start, day(6)), FrequencyBucket(day(7), day(7))), buckets(8))
        assertEquals(FrequencyBucket(day(28), day(30)), buckets(31).last())
        assertEquals(listOf(FrequencyBucket(start, day(1)), FrequencyBucket(day(2), day(31))), buckets(32))
    }

    @Test fun restCountsDatesAcrossEpisodesAndPreservesLegacyTags() {
        val diary = Diary(
            days = listOf(Day(day(2).toString(), noRests = true), Day(day(3).toString())),
            activities = listOf(
                ActivityEntry(date = start.toString(), tags = setOf("Quiet rest", "Walking")),
                ActivityEntry(date = start.toString(), tags = setOf("Rest"), restKind = "Rest"),
                ActivityEntry(date = start.toString(), tags = setOf("Nap"), restKind = "Nap"),
                ActivityEntry(date = day(1).toString(), tags = setOf("Quiet rest")),
            ),
        )
        val grid = PatternIndex(diary).activityFrequency(start, day(7))
        assertEquals(FrequencyCell(listOf(start, day(1)), 4, 1), grid.cells.getValue(REST_GROUP).first())
        for (tag in listOf("Rest", "Nap", "Walking")) {
            assertEquals(listOf(start), grid.cells.getValue(tag).first().dates)
        }
        assertEquals(listOf(start, day(1)), grid.cells.getValue("Quiet rest").first().dates)
    }

    @Test fun mixedTagsExplicitNoAndPartialDaysKeepDistinctMeanings() {
        val custom = customActivityTag("Garden")
        val index = PatternIndex(Diary(
            days = listOf(Day(day(1).toString(), work = false)),
            checks = listOf(Check(day(2).toString(), "pm", energy = 1)),
            activities = listOf(
                ActivityEntry(date = start.toString(), tags = setOf("Meetings", "Call", custom)),
                ActivityEntry(date = start.toString(), tags = setOf("Work", customActivityTag("GARDEN"))),
                ActivityEntry(date = day(3).toString(), tags = setOf(customActivityTag("garden"))),
                ActivityEntry(date = day(8).toString(), tags = setOf("Hobby")),
            )
        ))
        val grid = index.activityFrequency(start, day(7))
        assertEquals(listOf("Work", "Meetings", "Socialising", "Call", "Household", REST_GROUP, custom), grid.rows.map { it.name })
        assertEquals(listOf("Meetings", "Call"), grid.rows.filter { it.child }.map { it.name })
        assertEquals(FrequencyCell(listOf(start), 4, 1), grid.cells.getValue("Work").first())
        for (name in listOf("Meetings", "Socialising", "Call")) {
            assertEquals(FrequencyCell(listOf(start), 4, 0), grid.cells.getValue(name).first())
        }
        assertEquals(FrequencyCell(listOf(start, day(3)), 4, 0), grid.cells.getValue(custom).first())
        assertEquals(FrequencyCell(emptyList(), 4, 0), grid.cells.getValue("Household").first())
        assertEquals(FrequencyCell(emptyList(), 0, 0), grid.cells.getValue("Work").last())
    }
}
