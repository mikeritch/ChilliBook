package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PatternsEnergyTest {
    @Test fun mediansKeepMissingWeeksAndCountPairsOnlyOnMatchingDates() {
        val start = LocalDate.of(2026, 11, 1)
        fun check(day: Long, slot: String, energy: Int) = Check(start.plusDays(day).toString(), slot, energy = energy)
        val index = PatternIndex(Diary(checks = listOf(
            check(0, "am", 1), check(1, "am", 4), check(1, "pm", 0), check(14, "pm", 2)
        )))
        val end = start.plusDays(14)
        val weekly = energySeries(index, start, end, true)
        assertEquals(listOf(EnergyPoint(start, 2.5f, 0f), EnergyPoint(start.plusWeeks(1), null, null),
            EnergyPoint(end, null, 2f)), weekly.points)
        assertEquals(1, weekly.paired)
        assertEquals(1, weekly.lower)
        val daily = energySeries(index, start, end, false)
        assertEquals(15, daily.points.size)
        assertEquals(EnergyPoint(start, 1f, null), daily.points.first())
        assertEquals(EnergyPoint(start.plusDays(2), null, null), daily.points[2])
    }
}
