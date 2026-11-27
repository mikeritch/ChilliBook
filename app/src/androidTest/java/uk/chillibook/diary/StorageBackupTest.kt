package uk.chillibook.diary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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

    @Test fun wearableBackupPreservesZeroMissingMetricsAndSources() {
        val readings = listOf(
            WearableReading("2026-11-15", steps = 0, stepsSource = "Example steps"),
            WearableReading("2026-11-16", sleepMinutes = 385, sleepSource = "Example sleep")
        )
        val original = sample.copy(wearable = readings)
        val encoded = Backup.encode(original.copy(wearable = readings.reversed()))
        assertEquals(original, Backup.decode(encoded))
        val previous = JSONObject(Backup.encode(sample)).apply {
            put("version", 8)
            remove("wearable")
        }
        assertEquals(sample, Backup.decode(previous.toString()))

        val duplicate = JSONObject(encoded).apply {
            getJSONArray("wearable").apply { put(getJSONObject(0)) }
        }
        val negative = JSONObject(encoded).apply {
            getJSONArray("wearable").getJSONObject(0).put("steps", -1)
        }
        assertTrue(runCatching { Backup.decode(duplicate.toString()) }.isFailure)
        assertTrue(runCatching { Backup.decode(negative.toString()) }.isFailure)
    }

    @Test fun sleepDetailBackupPreservesOffsetChangesAndRejectsBadPhases() {
        val detail = RecordedSleep("2026-10-24T23:00:00+01:00", "2026-10-25T05:00:00Z",
            listOf(SleepPhase(600, 900, "unknown"), SleepPhase(24000, 25200, "rem")))
        val reading = WearableReading("2026-10-25", sleepMinutes = 420, sleepDetail = detail)
        val original = sample.copy(wearable = listOf(reading))
        val encoded = Backup.encode(original)
        assertEquals(original, Backup.decode(encoded))

        val previous = JSONObject(encoded).put("version", 9)
        previous.getJSONArray("wearable").getJSONObject(0).remove("sleepDetail")
        assertEquals(sample.copy(wearable = listOf(reading.copy(sleepDetail = null))),
            Backup.decode(previous.toString()))

        val invalid = listOf(
            detail.copy(end = "2026-10-25T05:00:00"),
            detail.copy(end = detail.start),
            detail.copy(phases = listOf(SleepPhase(-1, 20, "awake"))),
            detail.copy(phases = listOf(SleepPhase(0, 0, "awake"))),
            detail.copy(phases = listOf(SleepPhase(0, 25201, "deep"))),
            detail.copy(phases = listOf(SleepPhase(0, 100, "nap"))),
            detail.copy(phases = listOf(SleepPhase(0, 100, "awake"), SleepPhase(99, 200, "light"))),
            detail.copy(phases = detail.phases.reversed()),
            detail.copy(phases = List(MAX_SLEEP_PHASES + 1) { SleepPhase(it, it + 1, "light") })
        )
        for (sleep in invalid) {
            val damaged = JSONObject(encoded)
            damaged.getJSONArray("wearable").getJSONObject(0).put("sleepDetail", Backup.encodeSleepDetail(sleep))
            assertTrue(runCatching { Backup.decode(damaged.toString()) }.isFailure)
        }
    }

    @Test fun wearableDetailBackupPreservesPeriodsHoursAndLegacyFallback() {
        val night = RecordedSleep("2026-10-24T23:00:00+01:00", "2026-10-25T05:00:00Z",
            listOf(SleepPhase(24000, 25200, "rem")))
        val nap = RecordedSleep("2026-10-25T12:00:00Z", "2026-10-25T12:30:00Z")
        val periods = listOf(WearableSleepPeriod(night, "Watch"), WearableSleepPeriod(nap))
        val reading = WearableReading("2026-10-25", steps = 158, sleepMinutes = 450,
            sleepDetail = night, sleepSource = "Watch", sleepPeriods = periods,
            stepHours = listOf(StepHour(9, 0), StepHour(21, 158)))
        val original = sample.copy(wearable = listOf(reading))
        val encoded = Backup.encode(original)
        assertEquals(original, Backup.decode(encoded))

        val legacy = JSONObject(encoded).put("version", 10).apply {
            getJSONArray("wearable").getJSONObject(0).apply {
                remove("sleepPeriods")
                remove("stepHours")
            }
        }
        val restored = Backup.decode(legacy.toString()).wearable.single()
        assertEquals(reading.copy(sleepPeriods = null, stepHours = null), restored)
        assertEquals(periods.take(1), restored.recordedSleepPeriods())
    }

    @Test fun wearableDetailBackupRejectsOverlappingPeriodsWrongWakeDatesAndInvalidHours() {
        val night = WearableSleepPeriod(RecordedSleep("2026-10-24T23:00:00+01:00", "2026-10-25T05:00:00Z"))
        val overlap = WearableSleepPeriod(RecordedSleep("2026-10-25T05:30:00+01:00", "2026-10-25T07:00:00+01:00"))
        val nextDay = WearableSleepPeriod(RecordedSleep("2026-10-25T23:00:00Z", "2026-10-26T00:01:00Z"))
        val reading = WearableReading("2026-10-25")
        val invalid = listOf(
            reading.copy(sleepPeriods = listOf(night, overlap)),
            reading.copy(sleepPeriods = listOf(nextDay)),
            reading.copy(stepHours = listOf(StepHour(9, 1), StepHour(9, 0))),
            reading.copy(stepHours = listOf(StepHour(9, 1), StepHour(8, 0))),
            reading.copy(stepHours = listOf(StepHour(24, 1))),
            reading.copy(stepHours = listOf(StepHour(9, -1)))
        )
        for (entry in invalid) {
            val encoded = Backup.encode(sample.copy(wearable = listOf(entry)))
            assertTrue(runCatching { Backup.decode(encoded) }.isFailure)
        }
    }

    @Test fun contextBackupPreservesUnknownsAndRejectsInvalidAnswers() {
        val day = sample.days.single()
        val original = sample.copy(days = listOf(day.copy(
            stress = 0, caffeineTimes = emptySet(), alcoholTimes = setOf("Morning", "Evening")
        ), day.copy(date = "2026-11-16")))
        val encoded = Backup.encode(original)
        assertEquals(original, Backup.decode(encoded))

        val previous = JSONObject(encoded).put("version", 7)
        val days = previous.getJSONArray("days")
        for (index in 0 until days.length()) {
            days.getJSONObject(index).apply {
                remove("stress")
                remove("caffeineTimes")
                remove("alcoholTimes")
            }
        }
        assertEquals(original.copy(days = listOf(day, original.days.last())), Backup.decode(previous.toString()))

        val invalidValues = listOf(
            "stress" to -1,
            "stress" to 5,
            "caffeineTimes" to "Morning",
            "caffeineTimes" to JSONArray(listOf("Morning", "Morning")),
            "alcoholTimes" to JSONArray(listOf("Night"))
        )
        for ((field, value) in invalidValues) {
            val damaged = JSONObject(encoded)
            damaged.getJSONArray("days").getJSONObject(0).put(field, value)
            assertTrue(field, runCatching { Backup.decode(damaged.toString()) }.isFailure)
        }
    }

    @Test fun unratedIsDistinctFromNoneAndUnknown() {
        val check = morning.copy(symptoms = mapOf("Brain fog" to UNRATED_SYMPTOM, "Headache" to 0))
        val original = sample.copy(checks = listOf(check))
        val backup = JSONObject(Backup.encode(original))
        assertEquals(10, backup.getInt("version"))
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

    @Test fun regimenSnapshotsAndSeparateAsNeededDosesSurviveCorrectionAndRestore() = runBlocking {
        val date = morning.date
        val course = MedicationCourse("c", "Example", date, null, false, at, zone)
        val past = MedicationCourse("p", "Past example", null, null, true, at, zone)
        val old = MedicationRegimen("a", course.id, date, "10 mg", "1", "tablet", "Morning and evening", at, zone)
        val changed = old.copy(id = "b", effective = "2026-11-16", amount = "2", schedule = "As needed")
        val unknown = MedicationRegimen("u", past.id, null, null, null, null, null, at, zone)
        val first = MedicationDose("1", course.id, old.id, date, "am", "Taken", false,
            old.strength, old.amount, old.unit, old.schedule, null, null, at, zone)
        val evening = first.copy(id = "2", slot = "pm", status = "Skipped")
        val extra = first.copy(id = "3", regimenId = changed.id, date = "2026-11-16", asNeeded = true,
            amount = "2", schedule = changed.schedule, time = "09:15", reason = "Recorded reason")
        val original = sample.copy(courses = listOf(course, past), regimens = listOf(old, changed, unknown),
            doses = listOf(first, evening, extra, extra.copy(id = "4", time = null, amount = null, reason = null)))
        val corrected = original.copy(doses = listOf(first.copy(status = "Skipped")) + original.doses.drop(1))
        Storage(context, databaseName).use { store ->
            store.replace(original)
            store.save(original, corrected)
        }
        Storage(context, databaseName).use { store ->
            assertEquals(corrected, store.read())
            val encoded = Backup.encode(store.read())
            store.replace(Diary())
            store.replace(Backup.decode(encoded))
            assertEquals(corrected, store.read())
            fun damaged(edit: JSONObject.() -> Unit) = JSONObject(encoded).apply(edit).toString()
            val badExports = listOf(
                damaged { getJSONArray("regimens").getJSONObject(0).put("courseId", "missing") },
                damaged { getJSONArray("doses").getJSONObject(0).put("regimenId", unknown.id) },
                damaged { getJSONArray("doses").getJSONObject(0).put("slot", "other") },
                damaged { getJSONArray("doses").getJSONObject(0).put("status", "unknown") },
                damaged { getJSONArray("doses").getJSONObject(0).put("regimenId", "missing") },
                damaged { getJSONArray("regimens").getJSONObject(0).put("effective", "2026-02-30") },
                damaged { getJSONArray("doses").apply { put(getJSONObject(0)) } },
                damaged { getJSONArray("doses").getJSONObject(2).put("time", "25:90") },
                damaged { remove("doses") }
            )
            for (bad in badExports) {
                assertTrue(runCatching { store.replace(Backup.decode(bad)) }.isFailure)
                assertEquals(corrected, store.read())
            }
            val previous = JSONObject(encoded).apply {
                put("version", 6)
                remove("courses")
                remove("regimens")
                remove("doses")
            }
            assertEquals(sample, Backup.decode(previous.toString()))
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
            "days" to listOf("work", "social", "household", "wellbeing", "impact", "noRests",
                "stress", "caffeineTimes", "alcoholTimes"),
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
            val unsupported = JSONObject(valid).put("version", 11)
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
