package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsAiShareTest {
    @Test fun preservesGapsAndPartialAnswersWithoutLeakingOtherDatesOrMetadata() {
        val start = LocalDate.of(2026, 11, 1)
        val end = start.plusDays(6)
        val first = start.toString()
        val last = end.toString()
        val before = start.minusDays(1).toString()
        val after = end.plusDays(1).toString()
        val diary = Diary(
            days = listOf(Day(first, work = false, caffeineTimes = emptySet(), zone = "Private zone")),
            checks = listOf(
                Check(first, "am", symptoms = mapOf("Heavy fatigue" to UNRATED_SYMPTOM, "Headache" to 0)),
                Check(after, "pm", symptoms = mapOf("Outside selected dates" to 3)),
            ),
            activities = listOf(
                ActivityEntry("private activity ID", first, tags = setOf("Nap"), restKind = "Nap",
                    restPeriod = "Afternoon", duration = "15–29 min", restEnergy = "Higher"),
                ActivityEntry(date = before, tags = setOf("Private activity before range")),
            ),
            courses = listOf(MedicationCourse("private course ID", "Example\nmedicine", start = before)),
            doses = listOf(
                MedicationDose("private dose ID", "private course ID", "private regimen ID", first,
                    "pm", "Skipped", reason = "Private reason"),
                MedicationDose(courseId = "private course ID", regimenId = "private regimen ID",
                    date = after, slot = "pm", status = "Private dose after range"),
            ),
            wearable = listOf(WearableReading(last, steps = 0, sleepMinutes = 420,
                stepsSource = "Private source", stepHours = listOf(StepHour(6, 1234)))),
        )

        val share = patternsAiShare(diary, start, end)

        assertEquals(7, share.rows.size)
        assertEquals(start.plusDays(1).toString() + " | No entries", share.rows[1])
        assertEquals("$last | Sleep ending this day: 420 min | Steps: 0", share.rows.last())
        assertTrue(share.rows.first().startsWith("$first | "))
        assertTrue(share.rows.first().contains("Headache (None), Heavy fatigue (Severity not rated)"))
        assertTrue(share.rows.first().contains("Work: No"))
        assertTrue(share.rows.first().contains("Caffeine: Recorded, time unspecified"))
        assertTrue(share.rows.first().contains("Nap, Afternoon, 15–29 min, Energy after: Higher"))
        assertTrue(share.rows.first().contains("Example medicine PM: Skipped"))
        assertEquals(share.prompt + "\n\n" + share.rows.joinToString("\n"), share.text)
        assertEquals(9, share.text.lines().size)
        assertFalse(share.text.contains("private", ignoreCase = true))
        assertFalse(share.text.contains("Outside selected dates"))
        assertFalse(share.text.contains("Course start"))
        assertFalse(share.text.contains("1234"))
    }
}
