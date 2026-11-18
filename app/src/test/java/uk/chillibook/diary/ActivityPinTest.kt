package uk.chillibook.diary

import org.junit.Assert.*
import org.junit.Test

class ActivityPinTest {
    @Test fun independentPinsApplyOnlyToTheSelectedEntryAndKeepCustomSpelling() {
        val date = "2026-11-18"
        val tomorrow = "2026-11-19"
        val tag = customActivityTag("Garden Walk")
        val custom = ActivityEntry(date = date, tags = setOf(tag), worth = "Yes")
        val cooking = ActivityEntry(date = date, tags = setOf("Cooking"), duration = "Under 15 min",
            demands = setOf("Physical", "Upright"), effort = "Light", worth = "Mixed")
        val mixed = cooking.copy(id = "mixed", tags = setOf("Cooking", "Call"))
        val work = WorkSetup(parts = setOf("Commute"))
        val initial = Diary(activities = listOf(custom, cooking, mixed), prefs = Prefs(workPin = work))
        val pinned = initial.saveActivityPin(custom.id, tag).saveActivityPin(cooking.id, "Cooking")
        assertEquals(work, pinned.prefs.workPin)
        assertEquals(tag, pinned.prefs.activityPins[activityTagKey(tag)]?.displayTag)
        assertSame(pinned, pinned.saveActivityPin(mixed.id, "Cooking"))
        assertEquals(pinned, pinned.applyActivityPin(mixed.id, "Cooking"))
        val target = ActivityEntry(date = tomorrow, tags = setOf("Cooking"), workPlace = "Both")
        val other = target.copy(id = "duplicate", effort = "High")
        val replayed = pinned.copy(activities = listOf(mixed, target, other)).applyActivityPin(target.id, "Cooking")
        assertEquals(listOf(mixed, target.copy(duration = cooking.duration, effort = cooking.effort,
            demands = cooking.demands, worth = cooking.worth), other), replayed.activities)
        val spelling = customActivityTag("GARDEN WALK")
        val blank = ActivityEntry(date = tomorrow, tags = setOf(spelling))
        val reused = replayed.copy(activities = listOf(blank)).applyActivityPin(blank.id, spelling)
        assertEquals(listOf(blank.copy(worth = "Yes")), reused.activities)
        assertEquals(pinned.prefs, reused.prefs)
    }
}
