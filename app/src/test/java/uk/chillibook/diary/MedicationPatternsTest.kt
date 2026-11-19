package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MedicationPatternsTest {
    @Test fun stopIsInclusiveAndComparisonUsesOnlyRatedDates() {
        val date = LocalDate.of(2026, 11, 8)
        val stop = MedicationChange("course", date, "Last day", stop = true)
        assertEquals(date.plusDays(1), stop.laterWeekStart)
        assertEquals(date, stop.copy(stop = false).laterWeekStart)
        val earlier = listOf(0, UNRATED_SYMPTOM, 2, null).map { MedicationReading(date, date, false, it) }
        val later = listOf(MedicationReading(date, date, false, 1), MedicationReading(date, date, true, 3))
        val story = medicationWeekStory(earlier, later, MedicationMeasure.EveningSymptom, "Headache")
        assertEquals("Too few ratings to compare.", story.title)
        assertTrue(story.detail.contains("2 days in the first week and 1 day in the second"))
    }

    @Test fun typicalRatingsKeepBothMiddleCategories() {
        val start = LocalDate.of(2026, 11, 1)
        fun rows(vararg values: Int) = values.mapIndexed { i, level ->
            val day = start.plusDays(i.toLong())
            MedicationReading(day, day, false, level)
        }
        val earlier = rows(0, 0, 2, 2)
        val later = rows(1, 1, 1)
        val overlap = medicationWeekStory(earlier, later, MedicationMeasure.Evening, "")
        assertEquals("The typical ratings overlapped.", overlap.title)
        assertTrue(overlap.detail.contains("very low to medium"))
        assertEquals("Evening energy was higher.",
            medicationWeekStory(earlier, rows(3, 3, 4), MedicationMeasure.Evening, "").title)
        assertEquals("Evening brain fog was milder.",
            medicationWeekStory(rows(3, 2, 2), later, MedicationMeasure.EveningSymptom, "Brain fog").title)
        val same = medicationWeekStory(later, rows(0, 1, 2), MedicationMeasure.Morning, "")
        assertEquals("Morning energy was about the same.", same.title)
        assertEquals("Your typical rating was low in both weeks.", same.detail)
    }

    @Test fun weeksKeepGapsAndCalendarFollowUps() {
        val start = LocalDate.of(2026, 11, 8)
        val today = start.plusDays(4)
        val index = PatternIndex(Diary(checks = listOf(
            Check(start.toString(), "pm", symptoms = mapOf("Headache" to 0)),
            Check(start.plusDays(1).toString(), "pm", symptoms = mapOf("Headache" to UNRATED_SYMPTOM)),
            Check(today.toString(), "am", energy = 1))))
        val symptoms = medicationWeek(index, start, today, MedicationMeasure.EveningSymptom, "Headache")
        assertEquals(7, symptoms.size)
        assertEquals(listOf("None", "Severity not rated", "Not recorded"), symptoms.take(3).map { it.label(true) })
        assertEquals(5, symptoms.count { !it.ahead })
        val follow = medicationWeek(index, start, today, MedicationMeasure.Second, "Headache")
        assertEquals(3, follow.count { !it.ahead })
        assertEquals(1, follow[2].level)
        assertEquals(today, follow[2].observation)
        assertEquals("Still ahead", follow[3].label(false))
        assertEquals(start.minusDays(1), medicationWeek(index, start.minusWeeks(1), today,
            MedicationMeasure.Morning, "Headache").last().day)
    }
}
