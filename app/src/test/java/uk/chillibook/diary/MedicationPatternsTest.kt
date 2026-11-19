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
        val after = listOf(MedicationReading(date, date, false, 1))
        val summary = medicationComparison(earlier, after, MedicationMeasure.EveningSymptom, "Headache")
        assertTrue(summary.contains("1 of 2 rated dates"))
        assertTrue(summary.contains("1 of 1 in the later week"))
        assertTrue(medicationComparison(earlier, emptyList(), MedicationMeasure.Evening, "").contains("too few"))
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
