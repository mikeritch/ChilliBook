package uk.chillibook.diary

import org.junit.Assert.assertEquals
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

    @Test fun streakUsesDiaryDatesAndAllowsAnUnfinishedToday() {
        val first = "2024-02-28"
        val leapDay = "2024-02-29"
        val today = "2024-03-01"
        val diary = Diary(
            checks = listOf(Check(first, "am", energy = 1), Check(today, "am", sleep = 2)),
            days = listOf(Day(first, work = false)),
            doses = listOf(MedicationDose(courseId = "c", regimenId = "r", date = leapDay, slot = "am", status = "taken")),
            activities = listOf(ActivityEntry(date = leapDay))
        )
        assertEquals(2, diary.checkInStreak(today, today))
        assertEquals(1, diary.checkInStreak(first, today))
        assertEquals(0, diary.checkInStreak(today, "2024-03-02"))
        val completed = diary.copy(days = diary.days + Day(today, noRests = true))
        assertEquals(3, completed.checkInStreak(today, today))
        val cleared = completed.copy(activities = emptyList())
        assertEquals(1, cleared.checkInStreak(today, today))
        assertEquals(0, cleared.checkInStreak(leapDay, today))
    }
}
