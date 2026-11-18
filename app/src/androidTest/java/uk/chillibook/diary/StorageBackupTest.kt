package uk.chillibook.diary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "test-${java.util.UUID.randomUUID()}.db"
    @After fun cleanUp() { context.deleteDatabase(databaseName) }

    private val at = "2026-11-18T11:49:00Z"
    private val zone = "Europe/London"
    private val morning = Check("2026-11-15", "am", 2, null, emptyMap(), at, zone)
    private val sample = Diary(
        listOf(Day("2026-11-15", false, null, null, null, null, emptySet(), null, at, zone)),
        listOf(morning), emptyList(),
        listOf(Episode("synthetic-crash", "2026-11-10", "2026-11-14", true, false, null, at, zone)),
        Prefs()
    )

    @Test fun roundTripPreservesBackfillUnknownsAndExplicitNo() {
        assertEquals(sample, Backup.decode(Backup.encode(sample)))
    }

    @Test fun importsEarlierBackupAndValidatesNewReminderSettings() {
        val configured = sample.copy(prefs = sample.prefs.copy(morningReminder = 0, eveningReminder = 1439))
        val current = Backup.encode(configured)
        assertEquals(configured, Backup.decode(current))
        val old = JSONObject(current).apply {
            put("version", 1)
            getJSONObject("prefs").remove("morningReminder")
            getJSONObject("prefs").remove("eveningReminder")
        }
        assertEquals(sample, Backup.decode(old.toString()))
        for (badValue in listOf(-1, 1440, "08:00")) {
            val invalid = JSONObject(current)
            invalid.getJSONObject("prefs").put("morningReminder", badValue)
            assertTrue(runCatching { Backup.decode(invalid.toString()) }.isFailure)
        }
    }

    @Test fun oversizedInputIsRejectedBeforeParsingOrReplacingTheDiary() {
        val large = " ".repeat(Backup.MAX_CHARACTERS + 1)
        val result = runCatching { Backup.decode(large) }
        assertTrue(result.exceptionOrNull() is Backup.LimitExceeded)
    }

    @Test fun missingNullableFieldIsDistinctFromExplicitNull() {
        val backup = Backup.encode(sample.copy(activities = listOf(ActivityEntry(date = morning.date))))
        val fields = mapOf(
            "days" to listOf("work", "social", "household", "wellbeing", "impact", "workOff"),
            "checks" to listOf("energy", "sleep"),
            "activities" to listOf("duration", "effort", "worth", "workLength", "workPlace"),
            "episodes" to listOf("end", "worst")
        )
        for ((table, names) in fields) for (name in names) {
            val incomplete = JSONObject(backup)
            incomplete.getJSONArray(table).getJSONObject(0).remove(name)
            assertTrue("$table.$name", runCatching { Backup.decode(incomplete.toString()) }.isFailure)
        }
    }

    @Test fun malformedAndDuplicateRecordsCannotReplaceExistingDiary() = runBlocking {
        Storage(context, databaseName).use { store ->
            store.replace(sample)
            val valid = Backup.encode(sample)
            val duplicate = JSONObject(valid).apply {
                getJSONArray("checks").apply { put(getJSONObject(0)) }
            }
            val missingField = JSONObject(valid).apply { getJSONArray("days").getJSONObject(0).remove("work") }
            val wrongType = JSONObject(valid).apply { getJSONArray("checks").getJSONObject(0).put("energy", "2") }
            val badDate = JSONObject(valid).apply { getJSONArray("episodes").getJSONObject(0).put("end", "2026-11-01") }
            for (damaged in listOf(valid.dropLast(1), valid + "extra", duplicate.toString(),
                missingField.toString(), wrongType.toString(), badDate.toString())) {
                assertTrue(runCatching { store.replace(Backup.decode(damaged)) }.isFailure)
                assertEquals(sample, store.read())
            }
        }
    }

    @Test fun singleAnswerSurvivesReopenAndRetryKeepsOneRecord() = runBlocking {
        val partial = sample.copy(days = emptyList(), episodes = emptyList())
        val empty = partial.copy(checks = emptyList())
        Storage(context, databaseName).use { store ->
            store.replace(empty)
            store.save(empty, partial)
        }
        Storage(context, databaseName).use { store ->
            assertEquals(partial, store.read())
            val correction = partial.copy(checks = listOf(morning.copy(energy = 0)))
            repeat(2) { store.save(partial, correction) }
            assertEquals(correction, store.read())
            store.save(correction, empty)
            assertEquals(empty, store.read())
        }
    }
}
