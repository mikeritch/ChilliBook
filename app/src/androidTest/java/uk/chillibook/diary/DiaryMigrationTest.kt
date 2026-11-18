package uk.chillibook.diary

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DiaryMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val file = "migration-${UUID.randomUUID()}.db"

    private fun createReleasedDatabase(version: Int = 1): SQLiteDatabase {
        val schema = instrumentation.context.assets.open("uk.chillibook.diary.DiaryDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val db = context.openOrCreateDatabase(file, 0, null)
        val tables = schema.getJSONArray("entities")
        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            db.execSQL(table.getString("createSql").replace("${'$'}{TABLE_NAME}", table.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = schema.getInt("version")
        return db
    }

    @After fun cleanUp() { context.deleteDatabase(file) }

    @Test fun productionOpenMigratesPopulatedV1AndKeepsLaterWrites() = checkUpgrade(1)

    @Test fun productionOpenMigratesPopulatedV2AndKeepsLaterWrites() = checkUpgrade(2)

    @Test fun productionOpenMigratesPopulatedV3AndKeepsLaterWrites() = checkUpgrade(3)

    @Test fun productionOpenMigratesPopulatedV4AndKeepsLaterWrites() = checkUpgrade(4)

    @Test fun populatedV5KeepsRepeatedQuietRestsAndPinsAcrossEditAndReopen() = runBlocking {
        val date = "2026-11-01"
        val at = "2026-11-02T01:00:00Z"
        val zone = "America/New_York"
        val quiet = ActivityEntry("a", date, setOf("Quiet rest"), duration = "Under 15 min",
            effort = "Light", worth = "Yes", at = at, zone = zone)
        val mixed = quiet.copy(id = "b", tags = setOf("Quiet rest", "Walking"), duration = "Over an hour",
            effort = "High", demands = setOf("Physical"), worth = "Mixed", workLength = "Part day", workPlace = "Both")
        val pin = ActivitySetup(effort = "Light")
        val original = Diary(days = listOf(Day(date, work = false, at = at, zone = zone)),
            activities = listOf(quiet, mixed), prefs = Prefs(activityPins = mapOf("Quiet rest" to pin)))
        createReleasedDatabase(5).use { db ->
            db.execSQL("INSERT INTO days (date,work,context,at,zone) VALUES (?,0,'[]',?,?)", arrayOf(date, at, zone))
            db.execSQL("""INSERT INTO prefs (id,theme,activityPins) VALUES (1,'system',
                '{"Quiet rest":{"duration":null,"effort":"Light","demands":[],"worth":null,"displayTag":null}}')""")
            db.execSQL("""INSERT INTO activities (id,date,tags,duration,effort,demands,worth,at,zone)
                VALUES ('a',?,'["Quiet rest"]','Under 15 min','Light','[]','Yes',?,?)""", arrayOf(date, at, zone))
            db.execSQL("""INSERT INTO activities (id,date,tags,duration,effort,demands,worth,workLength,workPlace,at,zone)
                VALUES ('b',?,'["Walking","Quiet rest"]','Over an hour','High','["Physical"]','Mixed','Part day','Both',?,?)""",
                arrayOf(date, at, zone))
        }
        val kind = restKinds.first()
        val updated = original.copy(activities = original.activities + ActivityEntry("c", date, setOf(kind),
            restKind = kind, restPeriod = restPeriods.first(), restEnergy = restEnergies.first(), at = at, zone = zone))
        Storage(context, file).use {
            assertEquals(original, it.read())
            it.save(original, updated)
        }
        Storage(context, file).use { assertEquals(updated, it.read()) }
    }

    private fun checkUpgrade(version: Int) = runBlocking {
        val at = "2026-11-18T16:02:12Z"
        val zone = "America/New_York"
        val date = "2026-10-30"
        val missingMorning = "2026-11-01"
        createReleasedDatabase(version).use { db ->
            db.execSQL("""INSERT INTO days (date,work,social,household,wellbeing,impact,context,at,zone)
                VALUES (?,0,NULL,1,2,'Slowed down','["Fighting a bug"]',?,?)""", arrayOf(date, at, zone))
            if (version < 3) db.execSQL("UPDATE days SET workOff = 'Off sick'")
            db.execSQL("INSERT INTO checks VALUES (?,'am',NULL,1,?, ?,?)", arrayOf(date, """{"Brain fog":0}""", at, zone))
            db.execSQL("INSERT INTO checks VALUES (?,'pm',0,NULL,'{}',?,?)", arrayOf(missingMorning, at, zone))
            db.execSQL("""INSERT INTO activities (id,date,tags,duration,effort,demands,worth,workLength,workPlace,at,zone)
                VALUES ('act',?, ?,'Under 15 min','Light',?,'Yes','Part day','Both',?,?)""",
                arrayOf(date, """["Cooking","Visit","Meetings"]""", """["Thinking"]""", at, zone))
            if (version < 3) db.execSQL("INSERT INTO episodes VALUES ('spell',?,NULL,1,0,NULL,?,?)", arrayOf(date, at, zone))
            if (version == 4) db.execSQL("INSERT INTO prefs (id,theme) VALUES (1,'dark')")
            else db.execSQL("INSERT INTO prefs (id,theme,pins) VALUES (1,'dark',?)", arrayOf("""["Headache"]"""))
            if (version >= 2) db.execSQL("UPDATE prefs SET morningReminder = 480")
        }
        val original = Diary(
            listOf(Day(date, work = false, household = true, wellbeing = 2, impact = "Slowed down", context = setOf("Fighting a bug"), at = at, zone = zone)),
            listOf(Check(date, "am", sleep = 1, symptoms = mapOf("Brain fog" to 0), at = at, zone = zone),
                   Check(missingMorning, "pm", energy = 0, at = at, zone = zone)),
            listOf(ActivityEntry("act", date, setOf("Cooking","Visit","Meetings"), duration = "Under 15 min", effort = "Light", demands = setOf("Thinking"),
                worth = "Yes", workLength = "Part day", workPlace = "Both", at = at, zone = zone)),
            Prefs(theme = "dark", morningReminder = if (version >= 2) 480 else null)
        )
        val updated = original.copy(prefs = original.prefs.copy(eveningReminder = 21 * 60, workPin = WorkSetup(effort = "High")))
        Storage(context, file).use {
            assertEquals(original, it.read())
            it.save(original, updated)
        }
        Storage(context, file).use { assertEquals(updated, it.read()) }
    }

    @Test fun failedV2UpgradeRestoresRetiredFieldsAndCanRetry() = runBlocking {
        val date = "2026-11-01"
        val at = "2026-11-01T07:00:00Z"
        val zone = "Europe/London"
        createReleasedDatabase(2).use { db ->
            db.execSQL("INSERT INTO days VALUES (?,0,NULL,NULL,NULL,NULL,'[]','Off sick',?,?)", arrayOf(date, at, zone))
            db.execSQL("INSERT INTO episodes VALUES ('spell',?,NULL,1,0,NULL,?,?)", arrayOf(date, at, zone))
            // Extra index causes Room's final schema check to fail after the table changes.
            db.execSQL("CREATE INDEX unexpected_idx ON checks(energy)")
        }
        Storage(context, file).use { assertTrue(runCatching { it.read() }.isFailure) }
        context.openOrCreateDatabase(file, 0, null).use { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT workOff FROM days", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Off sick", it.getString(0))
            }
            db.rawQuery("SELECT id FROM episodes", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("spell", it.getString(0))
            }
            db.execSQL("DROP INDEX unexpected_idx")
        }
        Storage(context, file).use {
            assertEquals(listOf(Day(date, work = false, at = at, zone = zone)), it.read().days)
        }
    }

    @Test fun unsuccessfulSchemaUpgradeRollsBackAndKeepsOriginalData() = runBlocking {
        // Make the second ALTER fail after the first ALTER would have succeeded.
        createReleasedDatabase().use {
            it.execSQL("INSERT INTO prefs VALUES (1,'dark','[]')")
            it.execSQL("ALTER TABLE prefs ADD COLUMN eveningReminder INTEGER")
        }
        Storage(context, file).use { assertTrue(runCatching { it.read() }.isFailure) }
        SQLiteDatabase.openDatabase(context.getDatabasePath(file).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version)
            db.rawQuery("SELECT * FROM prefs", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("dark", it.getString(it.getColumnIndexOrThrow("theme")))
                assertEquals(-1, it.getColumnIndex("morningReminder"))
            }
        }
    }
}
