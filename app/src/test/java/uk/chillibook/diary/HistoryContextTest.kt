package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryContextTest {
    @Test fun gapsAndExplicitNoStayDistinctAcrossMonthBoundary() {
        val today = LocalDate.of(2026, 3, 2)
        val earlier = today.minusDays(2)
        val diary = Diary(
            days = listOf(Day(earlier.toString(), work = false, context = setOf("Illness"))),
            checks = listOf(Check(earlier.toString(), "pm", energy = 0), Check(today.toString(), "am", energy = 3)),
            activities = listOf(ActivityEntry(date = earlier.toString(), tags = setOf("Call")))
        )
        assertEquals(listOf("Morning energy · No entry", "Evening energy · Very low", "No work · Call", "Illness"),
            nearbyDayLines(diary, earlier, today, true))
        assertEquals(listOf("Morning energy · No entry"), nearbyDayLines(diary, earlier.plusDays(1), today, false))
        assertEquals(listOf("Morning energy · Fairly high"), nearbyDayLines(diary, earlier.plusDays(2), today, false))
        assertEquals(listOf("Still to come"), nearbyDayLines(diary, today.plusDays(1), today, false))
    }
}
