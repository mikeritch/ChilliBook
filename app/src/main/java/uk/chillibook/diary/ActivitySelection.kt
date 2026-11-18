package uk.chillibook.diary

internal const val CUSTOM_ACTIVITY_PREFIX = "Other · "

internal fun customActivityTag(name: String): String {
    require(name.isNotBlank())
    return CUSTOM_ACTIVITY_PREFIX + name.trim()
}

internal fun activityTagKey(tag: String): String =
    if (tag.startsWith(CUSTOM_ACTIVITY_PREFIX)) CUSTOM_ACTIVITY_PREFIX + tag.removePrefix(CUSTOM_ACTIVITY_PREFIX).lowercase()
    else tag

internal fun sameActivityTag(first: String, second: String): Boolean =
    activityTagKey(first) == activityTagKey(second)

internal fun ActivityEntry.hasActivityTag(tag: String?): Boolean =
    tag != null && tags.any { sameActivityTag(it, tag) }

internal fun Diary.addActivity(entry: ActivityEntry): Diary {
    val tag = entry.tags.singleOrNull() ?: return this
    val exists = activities.any { saved ->
        saved.id == entry.id || (saved.date == entry.date && saved.hasActivityTag(tag))
    }
    return if (exists) this else copy(activities = activities + entry)
}

internal fun Diary.removeActivity(entry: ActivityEntry): Diary =
    if (entry in activities) copy(activities = activities - entry) else this

// Undo restores the original record, including distinct legacy records that share a tag.
internal fun Diary.restoreActivity(entry: ActivityEntry): Diary =
    if (activities.any { it.id == entry.id }) this else copy(activities = activities + entry)
