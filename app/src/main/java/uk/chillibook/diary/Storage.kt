package uk.chillibook.diary

import android.content.Context
import androidx.room.*
import androidx.room.migration.AutoMigrationSpec
import org.json.JSONArray
import org.json.JSONObject

class ValueConverters {
    @TypeConverter fun writeActivityPins(value: Map<String, ActivitySetup>): String = Backup.encodeActivityPins(value).toString()
    @TypeConverter fun readActivityPins(raw: String): Map<String, ActivitySetup> = Backup.decodeActivityPins(JSONObject(raw))

    @TypeConverter fun writeWorkSetup(value: WorkSetup?): String? = value?.let { Backup.encodeWorkSetup(it).toString() }
    @TypeConverter fun readWorkSetup(raw: String?): WorkSetup? = raw?.let { Backup.decodeWorkSetup(JSONObject(it)) }

    @TypeConverter fun writeSet(value: Set<String>): String = JSONArray(value.sorted()).toString()
    @TypeConverter fun readSet(raw: String): Set<String> = JSONArray(raw).let { array ->
        (0 until array.length()).mapTo(linkedSetOf()) { array.getString(it) }
    }
    @TypeConverter fun writeSymptoms(value: Map<String, Int>): String = JSONObject(value).toString()
    @TypeConverter fun readSymptoms(raw: String): Map<String, Int> = JSONObject(raw).let { obj ->
        obj.keys().asSequence().associateWith { obj.getInt(it) }
    }
}

@Dao
interface DiaryDao {
    @Query("SELECT * FROM days ORDER BY date") suspend fun days(): List<Day>
    @Query("SELECT * FROM checks ORDER BY date, slot") suspend fun checks(): List<Check>
    @Query("SELECT * FROM activities ORDER BY date, id") suspend fun activities(): List<ActivityEntry>
    @Query("SELECT * FROM prefs WHERE id = 1") suspend fun prefs(): Prefs?
    @Upsert suspend fun putDay(value: Day)
    @Upsert suspend fun putCheck(value: Check)
    @Upsert suspend fun putActivity(value: ActivityEntry)
    @Upsert suspend fun putPrefs(value: Prefs)
    @Delete suspend fun deleteDay(value: Day)
    @Delete suspend fun deleteCheck(value: Check)
    @Delete suspend fun deleteActivity(value: ActivityEntry)
}

@Database(entities = [Day::class, Check::class, ActivityEntry::class, Prefs::class], version = 5, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = RemoveRetiredFields::class),
        AutoMigration(from = 3, to = 4, spec = RemoveSymptomPins::class), AutoMigration(from = 4, to = 5)])
@TypeConverters(ValueConverters::class)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diary(): DiaryDao
}

@DeleteTable(tableName = "episodes")
@DeleteColumn(tableName = "days", columnName = "workOff")
class RemoveRetiredFields : AutoMigrationSpec

@DeleteColumn(tableName = "prefs", columnName = "pins")
class RemoveSymptomPins : AutoMigrationSpec

class Storage(context: Context, databaseName: String = "diary.db") : AutoCloseable {
    private val db = Room.databaseBuilder(context.applicationContext, DiaryDatabase::class.java, databaseName).build()
    private val dao = db.diary()

    private suspend fun snapshot() = Diary(dao.days(), dao.checks(), dao.activities(), dao.prefs() ?: Prefs())
    suspend fun read(): Diary = db.withTransaction { snapshot() }

    suspend fun save(before: Diary, after: Diary) = db.withTransaction { applyChanges(before, after) }
    suspend fun replace(diary: Diary) = db.withTransaction { applyChanges(snapshot(), diary) }
    override fun close() = db.close()

    private suspend fun applyChanges(before: Diary, after: Diary) {
        sync(before.days, after.days, Day::date, dao::putDay, dao::deleteDay)
        sync(before.checks, after.checks, { it.date to it.slot }, dao::putCheck, dao::deleteCheck)
        sync(before.activities, after.activities, ActivityEntry::id, dao::putActivity, dao::deleteActivity)
        if (before.prefs != after.prefs) dao.putPrefs(after.prefs)
    }

    private suspend fun <T, K> sync(
        before: List<T>, after: List<T>, key: (T) -> K,
        put: suspend (T) -> Unit, delete: suspend (T) -> Unit
    ) {
        if (before == after) return
        val old = before.associateBy(key)
        val new = after.associateBy(key)
        require(new.size == after.size)
        for ((id, value) in old) if (id !in new) delete(value)
        for ((id, value) in new) if (old[id] != value) put(value)
    }
}
