package uk.chillibook.diary

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class RemindersTest {
    private val london = ZoneId.of("Europe/London")
    private fun now(value: String) = java.time.LocalDateTime.parse(value).atZone(london)

    @Test fun optOutIsIndependent() {
        assertNull(Reminders.minute(Prefs(), "am"))
        assertNull(Reminders.minute(Prefs(), "pm"))
        val prefs = Prefs(eveningReminder = 1200)
        assertNull(Reminders.minute(prefs, "am"))
        assertEquals(1200, Reminders.minute(prefs, "pm"))
    }

    @Test fun scheduleAdvancesAtDeadlineAndAfterDelivery() {
        val before = now("2026-11-18T08:59")
        assertEquals(now("2026-11-18T09:00"), nextReminder(before, 540, null))
        assertEquals(now("2026-11-19T09:00"), nextReminder(before.plusMinutes(1), 540, null))
        assertEquals(now("2026-11-19T09:00"), nextReminder(before, 540, "2026-11-18"))
        assertEquals(now("2026-11-19T00:00"), nextReminder(now("2026-11-18T23:59"), 0, null))
    }

    @Test fun daylightSavingUsesLocalTimeAndSkipsRepeatedHour() {
        val gap = nextReminder(now("2026-03-28T20:00"), 90, null)
        assertEquals(now("2026-03-29T02:30"), gap)
        val overlap = now("2026-10-25T01:35").withLaterOffsetAtOverlap()
        assertEquals(now("2026-10-26T01:30"), nextReminder(overlap, 90, null))
    }

    @Test fun anyAnswerInSlotCountsIncludingNoAndSymptoms() {
        val date = "2026-11-18"
        val morning = Diary(checks = listOf(Check(date, "am", symptoms = mapOf("Headache" to 0))))
        assertTrue(morning.answeredReminder(date, "am"))
        assertFalse(morning.answeredReminder(date, "pm"))
        assertTrue(Diary(days = listOf(Day(date, work = false))).answeredReminder(date, "pm"))
        assertTrue(Diary(activities = listOf(ActivityEntry(date = date, tags = setOf("Visit")))).answeredReminder(date, "pm"))
        assertFalse(Diary(checks = listOf(Check(date, "pm")), days = listOf(Day(date))).answeredReminder(date, "pm"))
        assertFalse(morning.answeredReminder("2026-11-19", "am"))
    }
}
