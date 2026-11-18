package uk.chillibook.diary

import org.junit.Assert.*
import org.junit.Test

class TodayEditTest {
    private val date = "2026-11-18"

    @Test fun changingAndClearingOneSlotKeepsOtherAnswers() {
        val first = Diary().changeCheck(date, "am") { it.copy(energy = 1) }
            .changeCheck(date, "pm") { it.copy(energy = 3) }
            .changeCheck(date, "am") { it.copy(sleep = 0) }
        val cleared = first.changeCheck(date, "am") { it.copy(energy = null) }
        assertEquals(2, cleared.checks.size)
        val morning = cleared.checks.single { it.slot == "am" }
        assertNull(morning.energy)
        assertEquals(0, morning.sleep)
        assertEquals(3, cleared.checks.single { it.slot == "pm" }.energy)
        assertEquals(1, cleared.changeCheck(date, "am") { it.copy(sleep = null) }.checks.size)
    }

    @Test fun removingTaggedActivityRestoresExplicitNoAndSickDayReason() {
        val day = Day(date, work = false)
        val entry = ActivityEntry(date = date, tags = setOf("Meetings", "Visit"))
        assertEquals(true, activityFlag(day, listOf(entry), "Work"))
        assertEquals(true, activityFlag(day, listOf(entry), "Socialising"))
        val restored = Diary(days = listOf(day), activities = listOf(entry)).copy(activities = emptyList())
        assertEquals(false, activityFlag(restored.days.single(), restored.activities, "Work"))
        assertNull(activityFlag(restored.days.single(), restored.activities, "Socialising"))
        assertTrue(restored.changeDay(date) { it.setFlag("Work", null) }.days.isEmpty())
    }
}
