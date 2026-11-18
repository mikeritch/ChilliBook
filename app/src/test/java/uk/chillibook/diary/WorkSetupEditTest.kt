package uk.chillibook.diary

import org.junit.Assert.*
import org.junit.Test

class WorkSetupEditTest {
    @Test fun editsAndPinReusePreserveIndependentRowsAndYesOnlyDays() {
        val date = "2026-11-18"
        val tomorrow = "2026-11-19"
        val legacy = ActivityEntry(id = "work-setup-$date", date = date, tags = setOf("Meetings", "Call"), workPlace = "Both",
            duration = "Under 15 min", worth = "Mixed", effort = "Light")
        val initial = Diary(days = listOf(Day(date, work = true), Day(tomorrow, work = true)),
            activities = listOf(legacy))
        val setup = WorkSetup(setOf("Meetings", "Commute"), "Part day", "High", setOf("Thinking", "Upright"))
        val first = initial.editWorkSetup(date) { setup }
        val grouped = first.activities.single { it.workSetup }
        assertEquals(setOf("Work", "Meetings", "Commute"), grouped.tags)
        assertNotEquals(legacy.id, grouped.id)
        assertEquals(grouped.id, initial.editWorkSetup(date) { setup }.activities.single { it.workSetup }.id)
        val pinned = first.copy(prefs = first.prefs.copy(workPin = grouped.asWorkSetup()))
        val changed = pinned.editWorkSetup(date) { it.copy(effort = "Light") }
        assertEquals(grouped.copy(effort = "Light"), changed.activities.single { it.workSetup })
        val applied = changed.editWorkSetup(tomorrow) { changed.prefs.workPin!! }
        assertEquals(setup, applied.activities.single { it.date == tomorrow }.asWorkSetup())
        val cleared = applied.editWorkSetup(date) { WorkSetup() }
        assertEquals(listOf(legacy), cleared.activities.filter { it.date == date })
        assertEquals(initial.days, cleared.days)
        assertEquals(setup, cleared.prefs.workPin)
    }
}
