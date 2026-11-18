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
        listOf(Day("2026-11-15", false, null, null, null, null, emptySet(), at, zone)),
        listOf(morning), emptyList(),
        Prefs()
    )

    @Test fun roundTripPreservesBackfillUnknownsAndExplicitNo() {
        assertEquals(sample, Backup.decode(Backup.encode(sample)))
    }

    @Test fun unratedIsDistinctFromNoneAndUnknown() {
        val check = morning.copy(symptoms = mapOf("Brain fog" to UNRATED_SYMPTOM, "Headache" to 0))
        val original = sample.copy(checks = listOf(check))
        val backup = JSONObject(Backup.encode(original))
        assertEquals(4, backup.getInt("version"))
        assertFalse(backup.getJSONObject("prefs").has("pins"))
        assertFalse(backup.has("episodes"))
        assertFalse(backup.getJSONArray("days").getJSONObject(0).has("workOff"))
        assertEquals(original, Backup.decode(backup.toString()))
        backup.put("version", 3)
        backup.getJSONObject("prefs").put("pins", org.json.JSONArray().put("Heavy fatigue"))
        assertEquals(original, Backup.decode(backup.toString()))
        for (version in 1..2) {
            backup.put("version", version)
            assertTrue(runCatching { Backup.decode(backup.toString()) }.isFailure)
        }
    }

    @Test fun activityRoundTripPreservesLegacyAndCustomRecords() {
        val legacy = ActivityEntry(id = "one", date = morning.date, tags = setOf("Call", "Cooking"),
            workPlace = "On site", effort = "High", worth = "Mixed", at = at, zone = zone)
        val activities = listOf(legacy, legacy.copy(id = "two"),
            legacy.copy(id = "details-only", tags = emptySet()),
            legacy.copy(id = "custom", tags = setOf(customActivityTag("Work · Café"))))
        val original = sample.copy(activities = activities)
        assertEquals(original, Backup.decode(Backup.encode(original)))
    }

    @Test fun importsEarlierBackupAndValidatesNewReminderSettings() {
        val configured = sample.copy(prefs = sample.prefs.copy(morningReminder = 0, eveningReminder = 1439))
        val current = Backup.encode(configured)
        assertEquals(configured, Backup.decode(current))
        val old = JSONObject(current).apply {
            put("version", 2)
            getJSONObject("prefs").put("pins", org.json.JSONArray().put("Headache"))
            put("episodes", org.json.JSONArray().put(JSONObject().apply {
                put("id", "spell")
                put("start", "2026-11-10")
                put("end", JSONObject.NULL)
                put("approxStart", true)
                put("approxEnd", false)
                put("worst", JSONObject.NULL)
                put("at", at)
                put("zone", zone)
            }))
            getJSONArray("days").getJSONObject(0).put("workOff", "Off sick")
            assertEquals(configured, Backup.decode(toString()))
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
            "days" to listOf("work", "social", "household", "wellbeing", "impact"),
            "checks" to listOf("energy", "sleep"),
            "activities" to listOf("duration", "effort", "worth", "workLength", "workPlace")
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
            val unsupported = JSONObject(valid).put("version", 5)
            val badDate = JSONObject(valid).apply { getJSONArray("days").getJSONObject(0).put("date", "2026-02-30") }
            for (damaged in listOf(valid.dropLast(1), valid + "extra", duplicate.toString(), unsupported.toString(),
                missingField.toString(), wrongType.toString(), badDate.toString())) {
                assertTrue(runCatching { store.replace(Backup.decode(damaged)) }.isFailure)
                assertEquals(sample, store.read())
            }
        }
    }

    @Test fun singleAnswerSurvivesReopenAndRetryKeepsOneRecord() = runBlocking {
        val partial = sample.copy(days = emptyList())
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
