package uk.chillibook.diary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryContextTest {
    @Test fun filterFindsRecordedSymptomsTagsAndPositiveAnswersAcrossMonths() {
        val older = "2025-11-02"
        val recent = "2026-03-02"
        val diary = Diary(
            days = listOf(Day(older, work=true), Day("2026-02-27", work=false), Day("2026-02-28")),
            checks = listOf(Check(older, "am", symptoms=mapOf("Headache" to UNRATED_SYMPTOM)),
                Check(older, "pm", symptoms=mapOf("Heavy fatigue" to 2))),
            activities = listOf(ActivityEntry(date=older, tags=setOf("Garden club")),
                ActivityEntry(date=recent, tags=setOf("Work", "Commute"), workSetup=true))
        )
        assertEquals(listOf(HistoryMatch(recent, listOf("Work")), HistoryMatch(older, listOf("Work"))),
            historyMatches(diary, " wOrK "))
        assertEquals(listOf(HistoryMatch(older, listOf("Headache"))), historyMatches(diary, "HEAD"))
        assertEquals(listOf(HistoryMatch(older, listOf("Garden club"))), historyMatches(diary, "club"))
        assertEquals(listOf(HistoryMatch(recent, listOf("Commute"))), historyMatches(diary, "comm"))
        assertEquals(emptyList<HistoryMatch>(), historyMatches(diary, "Brain fog"))
    }

    @Test fun restSearchPreservesIndividualKindsAndLegacyNames() {
        val older = "2026-02-27"
        val recent = "2026-03-01"
        val diary = Diary(
            days = listOf(Day("2026-02-28", noRests = true)),
            activities = listOf(
                ActivityEntry(date = older, tags = setOf("Quiet rest", "Hobby")),
                ActivityEntry(date = recent, tags = setOf("Nap"), restKind = "Nap"),
            ),
        )
        assertEquals(listOf(HistoryMatch(older, listOf("Quiet rest"))), historyMatches(diary, "rest"))
        assertEquals(listOf(HistoryMatch(recent, listOf("Nap"))), historyMatches(diary, "nap"))
        assertEquals(listOf(recent, older), historyMatches(diary, REST_GROUP).map { it.date })
        val noRestsDate = LocalDate.parse("2026-02-28")
        assertEquals("No rests or naps", nearbyDayLines(diary, noRestsDate, noRestsDate, true).last())
    }

    @Test fun gapsAndExplicitNoStayDistinctAcrossMonthBoundary() {
        val today = LocalDate.of(2026, 3, 2)
        val earlier = today.minusDays(2)
        val diary = Diary(
            days = listOf(Day(earlier.toString(), work = false, context = setOf("Illness"))),
            checks = listOf(Check(earlier.toString(), "pm", energy = 0), Check(today.toString(), "am", energy = 3)),
            activities = listOf(ActivityEntry(date = earlier.toString(), tags = setOf("Call")))
        )
        assertEquals(listOf("Morning energy · No entry", "Evening energy · Very low", "No work · Call", "Illness"),
            nearbyDayLines(diary, earlier, today, true))
        assertEquals(listOf("Morning energy · No entry"), nearbyDayLines(diary, earlier.plusDays(1), today, false))
        assertEquals(listOf("Morning energy · Fairly high"), nearbyDayLines(diary, earlier.plusDays(2), today, false))
        assertEquals(listOf("Still to come"), nearbyDayLines(diary, today.plusDays(1), today, false))
    }
}
