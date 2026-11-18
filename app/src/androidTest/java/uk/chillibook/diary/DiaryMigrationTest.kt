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

    private fun createReleasedDatabase(): SQLiteDatabase {
        val schema = instrumentation.context.assets.open("uk.chillibook.diary.DiaryDatabase/1.json")
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

    @Test fun productionOpenMigratesPopulatedV1AndKeepsLaterWrites() = runBlocking {
        val at = "2026-11-18T16:02:12Z"
        val zone = "America/New_York"
        val date = "2026-10-30"
        val missingMorning = "2026-11-01"
        createReleasedDatabase().use { db ->
            db.execSQL("INSERT INTO days VALUES (?,0,NULL,1,2,NULL,'[]','Off sick',?,?)", arrayOf(date, at, zone))
            db.execSQL("INSERT INTO checks VALUES (?,'am',NULL,1,?, ?,?)", arrayOf(date, """{"Brain fog":0}""", at, zone))
            db.execSQL("INSERT INTO checks VALUES (?,'pm',0,NULL,'{}',?,?)", arrayOf(missingMorning, at, zone))
            db.execSQL("""INSERT INTO activities (id,date,tags,duration,effort,demands,worth,workLength,workPlace,at,zone)
                VALUES ('act',?, ?,NULL,'Light',?,'Yes',NULL,NULL,?,?)""",
                arrayOf(date, """["Cooking","Visit"]""", """["Thinking"]""", at, zone))
            db.execSQL("INSERT INTO episodes VALUES ('spell',?,NULL,1,0,NULL,?,?)", arrayOf(date, at, zone))
            db.execSQL("INSERT INTO prefs VALUES (1,'dark',?)", arrayOf("""["Headache"]"""))
        }
        val original = Diary(
            listOf(Day(date, work = false, household = true, wellbeing = 2, workOff = "Off sick", at = at, zone = zone)),
            listOf(Check(date, "am", sleep = 1, symptoms = mapOf("Brain fog" to 0), at = at, zone = zone),
                   Check(missingMorning, "pm", energy = 0, at = at, zone = zone)),
            listOf(ActivityEntry("act", date, setOf("Cooking","Visit"), effort = "Light", demands = setOf("Thinking"),
                worth = "Yes", at = at, zone = zone)),
            listOf(Episode("spell", date, approxStart = true, at = at, zone = zone)),
            Prefs(theme = "dark", pins = setOf("Headache"))
        )
        val updated = original.copy(prefs = original.prefs.copy(eveningReminder = 21 * 60))
        Storage(context, file).use {
            assertEquals(original, it.read())
            it.save(original, updated)
        }
        Storage(context, file).use { assertEquals(updated, it.read()) }
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
