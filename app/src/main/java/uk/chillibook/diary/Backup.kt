package uk.chillibook.diary

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Backup {
    // Bound both the text buffer and the number of small objects an untrusted document can allocate.
    // Export applies the same two limits so every backup we create remains importable.
    internal const val MAX_CHARACTERS = 8 * 1024 * 1024
    private const val MAX_VALUES = 250_000
    class LimitExceeded : IllegalArgumentException("Backup exceeds the supported size")

    private class InputLimit(source: Reader) : FilterReader(source) {
        private var total = 0
        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) total += count
            if (total > MAX_CHARACTERS) throw LimitExceeded()
            return count
        }
    }

    private class Values {
        private var count = 0
        fun take() { if (++count > MAX_VALUES) throw LimitExceeded() }
        fun checkTree(node: Any) {
            take()
            when (node) {
                is JSONObject -> node.keys().forEach { checkTree(node.get(it)) }
                is JSONArray -> for (index in 0 until node.length()) checkTree(node.get(index))
            }
        }
    }

    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
        for ((key, value) in pairs) put(key, value ?: JSONObject.NULL)
    }

    private fun set(values: Set<String>) = JSONArray(values.sorted())
    private fun <T> rows(values: List<T>, encode: (T) -> JSONObject) = JSONArray(values.map(encode))

    fun encode(diary: Diary): String = obj(
        "format" to "ChilliBook", "version" to 3,
        "days" to rows(diary.days) { d -> obj(
            "date" to d.date, "work" to d.work, "social" to d.social, "household" to d.household,
            "wellbeing" to d.wellbeing, "impact" to d.impact, "context" to set(d.context),
            "at" to d.at, "zone" to d.zone
        ) },
        "checks" to rows(diary.checks) { c -> obj(
            "date" to c.date, "slot" to c.slot, "energy" to c.energy, "sleep" to c.sleep,
            "symptoms" to JSONObject(c.symptoms), "at" to c.at, "zone" to c.zone
        ) },
        "activities" to rows(diary.activities) { a -> obj(
            "id" to a.id, "date" to a.date, "tags" to set(a.tags), "duration" to a.duration,
            "effort" to a.effort, "demands" to set(a.demands), "worth" to a.worth,
            "workLength" to a.workLength, "workPlace" to a.workPlace, "at" to a.at, "zone" to a.zone
        ) },
        "prefs" to obj("id" to diary.prefs.id, "theme" to diary.prefs.theme, "pins" to set(diary.prefs.pins),
            "morningReminder" to diary.prefs.morningReminder, "eveningReminder" to diary.prefs.eveningReminder)
    ).let { root ->
        Values().checkTree(root)
        root.toString(2).also { if (it.length > MAX_CHARACTERS) throw LimitExceeded() }
    }

    // Android's JSONObject(String) accepts extensions such as duplicate keys and trailing content.
    private fun value(reader: JsonReader, depth: Int, values: Values): Any {
        values.take()
        require(depth < 10)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val key = reader.nextName()
                    require(!has(key))
                    put(key, value(reader, depth + 1, values))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) put(value(reader, depth + 1, values))
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().toInt()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("Invalid backup")
        }
    }

    private fun JSONObject.optional(key: String): Any? = get(key).takeUnless { it === JSONObject.NULL }
    private fun JSONObject.string(key: String) = (get(key) as String).also { require(it.isNotBlank()) }
    private fun JSONObject.text(key: String) = optional(key)?.let { it as String }
    private fun JSONObject.bool(key: String) = optional(key)?.let { it as Boolean }
    private fun JSONObject.level(key: String, range: IntRange) =
        optional(key)?.let { (it as Int).also { n -> require(n in range) } }
    private fun JSONObject.strings(key: String): Set<String> = getJSONArray(key).let { array ->
        buildSet { for (i in 0 until array.length()) {
            val item = array.get(i) as String
            require(item.isNotBlank() && add(item))
        } }
    }
    private fun checkedDate(raw: String) = raw.also { require(it.length == 10 && LocalDate.parse(it).year in 1..9999 && LocalDate.parse(it).toString() == it) }
    private fun JSONObject.date(key: String) = checkedDate(string(key))
    private fun JSONObject.instant() = string("at").also { Instant.parse(it) }
    private fun JSONObject.zone() = string("zone").also { ZoneId.of(it) }
    private fun <T, K> JSONObject.records(name: String, key: (T) -> K, parse: JSONObject.() -> T): List<T> {
        val array = getJSONArray(name)
        val seen = mutableSetOf<K>()
        return List(array.length()) { i -> array.getJSONObject(i).parse().also { require(seen.add(key(it))) } }
    }

    fun decode(text: String): Diary = decode(StringReader(text))

    fun decode(source: Reader): Diary {
        val root = JsonReader(InputLimit(source)).use { reader ->
            val result = value(reader, 0, Values()) as JSONObject
            require(reader.peek() == JsonToken.END_DOCUMENT)
            result
        }
        val version = root.get("version") as Int
        require(root.string("format") == "ChilliBook" && version in 1..3)
        val days = root.records("days", Day::date) {
            Day(date("date"), bool("work"), bool("social"), bool("household"), level("wellbeing", 0..4),
                text("impact"), strings("context"), instant(), zone())
        }
        val severityRange = (if (version >= 3) UNRATED_SYMPTOM else 0)..3
        val checks = root.records("checks", { c: Check -> c.date to c.slot }) {
            val slot = string("slot").also { require(it in setOf("am", "pm")) }
            val symptoms = getJSONObject("symptoms").let { values ->
                values.keys().asSequence().associateWith { values.level(it, severityRange) ?: error("Missing severity") }
            }
            val sleep = level("sleep", 0..3)
            require(slot == "am" || sleep == null)
            Check(date("date"), slot, level("energy", 0..4), sleep, symptoms, instant(), zone())
        }
        val activities = root.records("activities", ActivityEntry::id) {
            ActivityEntry(string("id"), date("date"), strings("tags"), text("duration"), text("effort"),
                strings("demands"), text("worth"), text("workLength"), text("workPlace"), instant(), zone())
        }
        val prefs = root.getJSONObject("prefs").run {
            require(get("id") == 1)
            val theme = string("theme").also { require(it in setOf("system", "light", "dark")) }
            Prefs(1, theme, strings("pins"),
                morningReminder = if (version >= 2) level("morningReminder", 0..1439) else null,
                eveningReminder = if (version >= 2) level("eveningReminder", 0..1439) else null)
        }
        return Diary(days, checks, activities, prefs)
    }
}
