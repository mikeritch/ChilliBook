package uk.chillibook.diary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayThanksTest {
    @Test fun eitherSlotCanCompleteABackfillWithOneAnswerEach() {
        val date = "2024-04-18"
        val morning = Diary(checks = listOf(Check(date, "am", symptoms = mapOf("Headache" to UNRATED_SYMPTOM))))
        val evening = Diary(days = listOf(Day(date, social = false)))
        val both = morning.copy(days = evening.days)
        assertTrue(newlyCompletedDay(morning, both, date))
        assertTrue(newlyCompletedDay(evening, both, date))
        assertFalse(newlyCompletedDay(Diary(), evening, date))
        assertFalse(newlyCompletedDay(both, both.copy(days = listOf(Day(date, social = true))), date))
        assertFalse(newlyCompletedDay(evening, both, "2024-04-19"))
    }
}
