package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityEnergyTest {
    @Test fun repeatedActivitiesCountOnceAndFollowCalendarDatesPastRangeEnd() {
        val monday = LocalDate.of(2026, 11, 16)
        val tuesday = monday.plusDays(1)
        val wednesday = monday.plusDays(2)
        val index = PatternIndex(Diary(
            activities = listOf(monday, monday, tuesday).map {
                ActivityEntry(date = it.toString(), tags = setOf("Cooking"))
            },
            checks = listOf(Check(monday.toString(), "pm", energy = 0),
                Check(tuesday.toString(), "pm", energy = 2),
                Check(wednesday.toString(), "am", energy = 1))
        ))
        val groups = index.activityEnergy(monday, tuesday, wednesday).getValue("Cooking")
        assertEquals(listOf(1, 1, 1), groups.map { it.low })
        assertEquals(listOf(2, 1, 1), groups.map { it.usable })
        assertEquals(listOf(DatePair(monday, tuesday, null), DatePair(tuesday, wednesday, 1)), groups[1].dates)
        assertEquals(listOf(DatePair(monday, wednesday, 1), DatePair(tuesday, wednesday.plusDays(1), null)), groups[2].dates)
    }
}
