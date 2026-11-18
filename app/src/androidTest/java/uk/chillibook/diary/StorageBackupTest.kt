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
        assertEquals(6, backup.getInt("version"))
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

    @Test fun workPinAndGroupedWorkRoundTripWithoutChangingLegacyRecords() = runBlocking {
        val pin = WorkSetup(setOf("Meetings", "Commute"), "Full day", "Moderate", setOf("Thinking"))
        val grouped = ActivityEntry(id = "group", date = morning.date, tags = pin.parts + "Work",
            workLength = pin.workLength, effort = pin.effort, demands = pin.demands, workSetup = true)
        val legacy = grouped.copy(id = "legacy", workSetup = false, workPlace = "On site", worth = "Yes")
        val custom = customActivityTag("Tea Club")
        val activityPins = mapOf("Visit" to ActivitySetup(duration = "15–60 min", demands = setOf("Thinking")),
            "Cooking" to ActivitySetup(effort = "Light"),
            activityTagKey(custom) to ActivitySetup(worth = "Yes", displayTag = custom))
        val original = sample.copy(activities = listOf(grouped, legacy), prefs = sample.prefs.copy(workPin = pin, activityPins = activityPins))
        val encoded = Backup.encode(original)
        assertEquals(original, Backup.decode(encoded))
        Storage(context, databaseName).use { it.replace(Backup.decode(encoded)) }
        Storage(context, databaseName).use {
            assertEquals(original, it.read())
            val removeVisit = original.copy(prefs = original.prefs.copy(activityPins = activityPins - "Visit"))
            it.save(original, removeVisit)
            assertEquals(removeVisit, it.read())
            assertEquals(removeVisit, Backup.decode(Backup.encode(removeVisit)))
        }

        val old = JSONObject(encoded).apply {
            put("version", 4)
            getJSONObject("prefs").remove("workPin")
            getJSONObject("prefs").remove("activityPins")
            for (i in 0..1) getJSONArray("activities").getJSONObject(i).remove("workSetup")
        }
        assertEquals(original.copy(activities = listOf(grouped.copy(workSetup = false), legacy), prefs = sample.prefs),
            Backup.decode(old.toString()))

        fun rejects(change: JSONObject.() -> Unit) {
            val bad = JSONObject(encoded).apply(change)
            assertTrue(runCatching { Backup.decode(bad.toString()) }.isFailure)
        }
        rejects { getJSONObject("prefs").remove("activityPins") }
        rejects { getJSONObject("prefs").getJSONObject("activityPins").put("Work", JSONObject()) }
        rejects { getJSONObject("prefs").getJSONObject("activityPins").getJSONObject("Visit").put("duration", "Unknown") }
        rejects { getJSONObject("prefs").getJSONObject("activityPins").getJSONObject(activityTagKey(custom)).put("displayTag", "Other · Book Club") }
        rejects { getJSONObject("prefs").getJSONObject("activityPins").run { put(custom, getJSONObject(activityTagKey(custom))) } }
        rejects { getJSONObject("prefs").remove("workPin") }
        rejects { getJSONObject("prefs").getJSONObject("workPin").put("effort", "Unknown") }
        rejects { getJSONObject("prefs").put("workPin", Backup.encodeWorkSetup(WorkSetup())) }
        rejects { getJSONArray("activities").getJSONObject(0).getJSONArray("tags").put("Visit") }
        rejects { getJSONArray("activities").getJSONObject(0).put("duration", "15–60 min") }
        rejects {
            val activities = getJSONArray("activities")
            activities.put(JSONObject(activities.getJSONObject(0).toString()).put("id", "duplicate"))
        }
    }

    @Test fun historicalQuietRestAndNewEpisodesRoundTripWithoutInventingDetails() {
        val quiet = ActivityEntry("old", morning.date, setOf("Quiet rest", "Walking"), duration = "Over an hour",
            effort = "Moderate", demands = setOf("Thinking"), worth = "Mixed", at = at, zone = zone)
        val original = sample.copy(activities = listOf(quiet, quiet.copy(id = "old2", effort = "Light")),
            prefs = Prefs(activityPins = mapOf("Quiet rest" to ActivitySetup(effort = "Light"))))
        val v5 = JSONObject(Backup.encode(original)).apply {
            put("version", 5)
            getJSONArray("days").getJSONObject(0).remove("noRests")
            for (i in 0..1) getJSONArray("activities").getJSONObject(i).apply {
                remove("restKind")
                remove("restPeriod")
                remove("restEnergy")
            }
        }
        assertEquals(original, Backup.decode(v5.toString()))
        val episodes = restKinds.mapIndexed { i, kind ->
            ActivityEntry("new$i", morning.date, setOf(kind), restKind = kind, at = at, zone = zone)
        }
        val noRest = original.days.single().copy(date = "2026-11-16", noRests = true)
        val detailed = episodes.first().copy(id = "more", duration = restDurations.first(),
            restPeriod = restPeriods.first(), restEnergy = restEnergies.first())
        val current = original.copy(activities = original.activities + episodes + detailed, days = original.days + noRest)
        assertEquals(current, Backup.decode(Backup.encode(current)))
        fun rejects(change: JSONObject.() -> Unit) {
            val invalid = JSONObject(Backup.encode(current)).apply(change)
            assertTrue(runCatching { Backup.decode(invalid.toString()) }.isFailure)
        }
        rejects { getJSONArray("days").getJSONObject(0).put("noRests", true) }
        rejects { getJSONArray("days").getJSONObject(0).put("noRests", false) }
        rejects { getJSONArray("activities").getJSONObject(0).put("restPeriod", restPeriods.first()) }
        for (key in listOf("restKind", "duration", "restPeriod", "restEnergy", "effort", "workPlace", "workLength", "worth")) {
            rejects { getJSONArray("activities").getJSONObject(2).put(key, "invalid") }
        }
        rejects { getJSONArray("activities").getJSONObject(2).getJSONArray("tags").put("Walking") }
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
            "days" to listOf("work", "social", "household", "wellbeing", "impact", "noRests"),
            "checks" to listOf("energy", "sleep"),
            "activities" to listOf("duration", "effort", "worth", "workLength", "workPlace", "restKind", "restPeriod", "restEnergy")
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
            val unsupported = JSONObject(valid).put("version", 7)
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
