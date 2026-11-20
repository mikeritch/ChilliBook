package uk.chillibook.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackCueForEditTest {
    private val date = "2026-11-18"
    private val empty = Diary()

    @Test fun explicitNoIsAnAnswerAndReplacingItSelects() {
        val no = empty.changeDay(date) { it.copy(work = false) }
        val yes = no.changeDay(date) { it.copy(work = true) }

        assertEquals(FeedbackCue.Select, diaryEditCue(empty, no, date))
        assertEquals(FeedbackCue.Select, diaryEditCue(no, yes, date))
        assertEquals(FeedbackCue.Clear, diaryEditCue(no, empty, date))
    }

    @Test fun clearingEnergyOrSeverityKeepsOtherAnswers() {
        val symptom = "Brain fog"
        val before = empty.changeCheck(date, "am") { it.copy(energy = 0, symptoms = mapOf(symptom to 0)) }
            .changeCheck(date, "pm") { it.copy(energy = 2) }
        val clearEnergy = before.changeCheck(date, "am") { it.copy(energy = null) }
        val clearSeverity = before.changeCheck(date, "am") { it.copy(symptoms = mapOf(symptom to UNRATED_SYMPTOM)) }
        val removeSymptom = clearSeverity.changeCheck(date, "am") { it.copy(symptoms = emptyMap()) }

        assertEquals(FeedbackCue.Clear, diaryEditCue(before, clearEnergy, date))
        assertEquals(FeedbackCue.Clear, diaryEditCue(before, clearSeverity, date))
        assertEquals(FeedbackCue.Clear, diaryEditCue(clearSeverity, removeSymptom, date))
        assertEquals(FeedbackCue.Select, diaryEditCue(removeSymptom, clearSeverity, date))
    }

    @Test fun removingContextOrAnActivityClearsAndAddingRestSelects() {
        val before = empty.changeDay(date) { it.copy(context = setOf("Illness"), noRests = true) }
        val rest = ActivityEntry(date = date, tags = setOf("Rest"), restKind = "Rest")
        val withRest = before.recordRest(rest)
        val editRest = withRest.updateEveningActivity(rest.id, date) { it.copy(duration = "30–59 min") }

        assertEquals(FeedbackCue.Clear, diaryEditCue(before, before.changeDay(date) { it.copy(context = emptySet()) }, date))
        assertEquals(FeedbackCue.Select, diaryEditCue(before, withRest, date))
        assertEquals(FeedbackCue.Select, diaryEditCue(withRest, editRest, date))
        assertEquals(FeedbackCue.Clear, diaryEditCue(withRest, withRest.removeActivity(rest), date))
    }

    @Test fun loggingReplacingAndClearingAScheduledDoseUseTheirOwnCues() {
        val course = MedicationCourse(name = "Example")
        val regimen = MedicationRegimen(courseId = course.id, effective = date, schedule = "Morning")
        val ready = empty.copy(courses = listOf(course), regimens = listOf(regimen))
        val taken = ready.recordMedication(course, date, "am", "Taken")
        val skipped = taken.recordMedication(course, date, "am", "Skipped")
        val cleared = skipped.recordMedication(course, date, "am", "Skipped")

        assertEquals(FeedbackCue.Select, diaryEditCue(ready, taken, date))
        assertEquals(FeedbackCue.Select, diaryEditCue(taken, skipped, date))
        assertEquals(FeedbackCue.Clear, diaryEditCue(skipped, cleared, date))
    }

    @Test fun medicationManagementSavesAndRemovalsHaveCuesWithoutACheckInDate() {
        val course = MedicationCourse(name = "Example")
        val before = empty.copy(courses = listOf(course))
        val updated = before.copy(courses = listOf(course.copy(name = "Edited")))

        assertEquals(FeedbackCue.Select, diaryEditCue(empty, before, null))
        assertEquals(FeedbackCue.Select, diaryEditCue(before, updated, null))
        assertEquals(FeedbackCue.Clear, diaryEditCue(before, empty, null))
        assertNull(diaryEditCue(before, updated, date))
    }

    @Test fun preferenceChangesListReorderingAndOtherDatesAreSilent() {
        val morning = Check(date, "am", energy = 2)
        val evening = Check(date, "pm", energy = 1)
        val otherDate = Check("2026-11-17", "am", energy = 0)
        val before = empty.copy(checks = listOf(morning, evening))
        val after = before.copy(checks = listOf(evening, otherDate, morning), prefs = Prefs(theme = "dark"))

        assertNull(diaryEditCue(before, after, date))
        assertNull(diaryEditCue(before, after, null))
    }
}
