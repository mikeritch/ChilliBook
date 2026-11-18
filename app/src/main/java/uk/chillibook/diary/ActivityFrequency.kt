package uk.chillibook.diary

import java.time.LocalDate

internal const val REST_GROUP = "Rest & naps"

internal fun restRecorded(day: Day?, entries: List<ActivityEntry>): Boolean? = when {
    entries.any { it.restKind != null || "Quiet rest" in it.tags } -> true
    day?.noRests == true -> false
    else -> null
}

internal data class FrequencyBucket(val start: LocalDate, val end: LocalDate)
internal data class FrequencyActivity(val name: String, val child: Boolean)
internal data class FrequencyCell(val dates: List<LocalDate>, val recordedDays: Int, val noDays: Int)
internal data class ActivityFrequency(
    val buckets: List<FrequencyBucket>,
    val rows: List<FrequencyActivity>,
    val cells: Map<String, List<FrequencyCell>>,
)

internal fun PatternIndex.activityFrequency(start: LocalDate, end: LocalDate): ActivityFrequency {
    val span = end.toEpochDay() - start.toEpochDay() + 1
    val buckets = buildList {
        var from = start
        while (from <= end) {
            val through = when {
                span <= 7 -> from
                span <= 31 -> from.plusDays(minOf(6L, end.toEpochDay() - from.toEpochDay()))
                else -> minOf(end, from.withDayOfMonth(from.lengthOfMonth()))
            }
            add(FrequencyBucket(from, through))
            if (through == end) break
            from = through.plusDays(1)
        }
    }

    val displayNames = mutableMapOf<String, String>()
    val recorded = IntArray(buckets.size)
    val matches = buckets.map { mutableMapOf<String, MutableList<LocalDate>>() }
    val negatives = buckets.map { mutableMapOf<String, Int>() }
    buckets.forEachIndexed { i, bucket ->
        for (date in dateSequence(bucket.start, bucket.end)) {
            val key = date.toString()
            val day = days[key]
            val entries = activities[key].orEmpty()
            if (day == null && entries.isEmpty() && (key to "am") !in checks && (key to "pm") !in checks) continue
            recorded[i]++
            val marked = mutableSetOf<String>()
            for (entry in entries) for (tag in entry.tags) {
                val tagKey = activityTagKey(tag)
                displayNames.putIfAbsent(tagKey, tag)
                marked.add(tagKey)
            }
            for (category in categoryTags.keys) {
                when (activityFlag(day, entries, category)) {
                    true -> marked.add(category)
                    false -> negatives[i][category] = negatives[i].getOrDefault(category, 0) + 1
                    null -> Unit
                }
            }
            when (restRecorded(day, entries)) {
                true -> marked.add(REST_GROUP)
                false -> negatives[i][REST_GROUP] = negatives[i].getOrDefault(REST_GROUP, 0) + 1
                null -> Unit
            }
            for (tag in marked) matches[i].getOrPut(tag) { mutableListOf() }.add(date)
        }
    }
    val rows = buildList {
        val remaining = displayNames.toMutableMap()
        for ((category, tags) in categoryTags) {
            add(FrequencyActivity(category, false))
            remaining.remove(category)
            for (tag in tags) remaining.remove(tag)?.let { add(FrequencyActivity(it, true)) }
        }
        add(FrequencyActivity(REST_GROUP, false))
        remaining.remove(REST_GROUP)
        for (tag in listOf("Rest", "Nap", "Quiet rest")) {
            remaining.remove(tag)?.let { add(FrequencyActivity(it, true)) }
        }
        for (tag in otherTags) remaining.remove(tag)?.let { add(FrequencyActivity(it, false)) }
        for (tag in remaining.values.sortedWith(String.CASE_INSENSITIVE_ORDER)) add(FrequencyActivity(tag, false))
    }
    val cells = rows.associate { row ->
        val key = activityTagKey(row.name)
        row.name to buckets.indices.map { i ->
            FrequencyCell(matches[i][key].orEmpty(), recorded[i], negatives[i].getOrDefault(key, 0))
        }
    }
    return ActivityFrequency(buckets, rows, cells)
}
