package uk.chillibook.diary

import org.junit.Assert.*
import org.junit.Test

class ActivitySelectionTest {
    private val date = "2026-11-18"
    private val legacy = ActivityEntry(date = date, tags = setOf("Call", "Cooking"),
        effort = "High", demands = setOf("Physical"), worth = "Mixed", workPlace = "On site")
    private val duplicate = legacy.copy(id = "duplicate")
    private val detailsOnly = ActivityEntry(date = date, duration = "15–60 min")
    private val diary = Diary(activities = listOf(legacy, duplicate, detailsOnly))

    @Test fun addKeepsLegacyRowsAndChecksEachDateSeparately() {
        val newCall = ActivityEntry(date = date, tags = setOf("Call"))
        assertSame(diary, diary.addActivity(newCall))
        val tomorrow = newCall.copy(date = "2026-11-19")
        val updated = diary.addActivity(tomorrow)
        assertEquals(diary.activities + tomorrow, updated.activities)
        assertSame(updated, updated.addActivity(tomorrow))
    }

    @Test fun removingAndUndoingOneDuplicatePreservesEveryRecord() {
        val removed = diary.removeActivity(legacy)
        assertEquals(listOf(duplicate, detailsOnly), removed.activities)
        assertEquals(diary.activities.toSet(), removed.restoreActivity(legacy).activities.toSet())
        assertSame(diary, diary.removeActivity(legacy.copy(worth = "No")))
        assertSame(diary, diary.restoreActivity(legacy.copy(worth = "No")))
    }

    @Test fun frequencyCountsCustomSpellingVariantsOncePerDate() {
        val first = java.time.LocalDate.parse(date)
        val custom = ActivityEntry(date = date, tags = setOf(customActivityTag("Walk")))
        val index = PatternIndex(Diary(activities = listOf(custom, custom.copy(id = "duplicate"),
            custom.copy(id = "next", date = first.plusDays(1).toString(), tags = setOf(customActivityTag("walk"))))))
        val dates = index.activityDates(first, first.plusDays(2), customActivityTag("Walk"))
        assertEquals(listOf(first, first.plusDays(1)), dates[true])
        assertEquals(listOf(first.plusDays(2)), dates[null])
    }

    @Test fun customNamesCannotImplyACategoryOrDuplicateAnotherCustomChip() {
        val entry = ActivityEntry(date = date, tags = setOf(customActivityTag("  Work  ")))
        val added = diary.addActivity(entry)
        assertEquals(false, activityFlag(Day(date, work = false), added.activities, "Work"))
        assertSame(added, added.addActivity(ActivityEntry(date = date, tags = setOf(customActivityTag("work")))))
        assertEquals(diary.activities + entry, added.activities)
    }
}
