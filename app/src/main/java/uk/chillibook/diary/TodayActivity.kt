package uk.chillibook.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

private val activityGroups = listOf("Work" to "Work", "Household" to "Household tasks",
    "Socialising" to "Socialising", "Other" to "Other activities")

private fun tagLabel(tag: String) = when (tag) {
    "Meetings" -> "Meeting"
    else -> tag.removePrefix(CUSTOM_ACTIVITY_PREFIX)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EveningActivities(diary: Diary, date: String, edit: DiaryEdit) {
    val entries = diary.activities.filter { it.date == date }.sortedWith(compareBy({ it.at }, { it.id }))
    val day = diary.days.find { it.date == date }
    var removed by remember { mutableStateOf<ActivityEntry?>(null) }
    var feedback by remember { mutableStateOf<Pair<String, String>?>(null) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewTag by rememberSaveable { mutableStateOf<String?>(null) }
    var recentCustomTags by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val customTags = (diary.activities.flatMap { it.tags } + recentCustomTags)
        .filter { it.startsWith(CUSTOM_ACTIVITY_PREFIX) }.distinctBy(::activityTagKey)
    val byTag = entries.flatMap { entry -> entry.tags.map { activityTagKey(it) to entry } }
        .groupBy({ it.first }, { it.second })
    val presetsByGroup = categoryTags.mapValues { (group, tags) -> tags.filter { it != group } } +
        ("Other" to otherTags + customTags)
    fun simpleEntry(tag: String) = byTag[activityTagKey(tag)]?.singleOrNull()?.takeIf { it.tags.size == 1 }
    fun showAction(group: String, message: String) {
        removed = null
        feedback = group to message
    }
    fun remove(group: String, entry: ActivityEntry) {
        entry.tags.filter { it.startsWith(CUSTOM_ACTIVITY_PREFIX) }.forEach { tag ->
            if (tag !in recentCustomTags) recentCustomTags = ArrayList(recentCustomTags + tag)
        }
        edit { it.removeActivity(entry) }
        showAction(group, "${entry.summary()} removed")
        removed = entry
    }
    fun add(group: String, tag: String) {
        edit { it.addActivity(ActivityEntry(date = date, tags = setOf(tag))) }
        feedback = null
        removed = null
    }
    QuestionText("What did you do?")
    activityGroups.forEach { (group, title) ->
        val other = group == "Other"
        val presets = presetsByGroup.getValue(group)
        val tagged = entries.filter { entry -> entry.tags.any { it in categoryTags[group].orEmpty() } }
        val answer = activityFlag(day, entries, group)
        Spacer(Modifier.height(22.dp))
        if (other) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiaryIcon(R.drawable.diary_sprout, categoryAccent(group))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        else {
            YesNoRow(title, answer, group) { value ->
                val next = value.takeUnless { it == answer }
                if (next != true && tagged.isNotEmpty()) {
                    showAction(group, "Remove the selected activities or edit their tags before changing this answer.")
                } else {
                    edit { d -> d.changeDay(date) { it.setFlag(group, next) } }
                    feedback = null
                    removed = null
                }
            }
            if (tagged.isNotEmpty() && day?.flag(group) == false)
                Text("Your earlier No returns when you remove the matching tags.", style = MaterialTheme.typography.bodySmall)

        }
        if (other || answer == true) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { tag ->
                    val matches = byTag[activityTagKey(tag)].orEmpty()
                    val on = matches.isNotEmpty()
                    val simple = simpleEntry(tag)
                    val label = tagLabel(tag)
                    val action = if (!on) "Select" else if (simple != null) "Remove" else "Review"
                    DiaryChip(label, on, onClick = {
                        when {
                            !on -> add(group, tag)
                            simple != null -> remove(group, simple)
                            else -> reviewTag = tag
                        }
                    }, modifier = Modifier.semantics { contentDescription = "$action $label" },
                        symbol = if (!on) "+" else if (simple != null) "−" else "›",
                        accent = categoryAccent(group), remove = simple != null)
                }
                if (other) TextButton(onClick = { custom = !custom; name = "" }) { Text("Write your own") }
            }
            if (other && custom) {
                OutlinedTextField(name, { name = it.take(64) }, label = { Text("Activity name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        val label = name.trim()
                        val tag = presets.firstOrNull { tagLabel(it).equals(label, ignoreCase = true) } ?: customActivityTag(label)
                        if (entries.any { it.hasActivityTag(tag) })
                            showAction(group, "${tagLabel(tag)} is already selected")
                        else add(group, tag)
                        name = ""; custom = false
                    }) { Text("Add activity") }
                    TextButton(onClick = { custom = false; name = "" }) { Text("Cancel") }
                }
            }
        }
        if (feedback?.first == group) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(feedback!!.second, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            removed?.let { entry -> TextButton(onClick = {
                edit { it.restoreActivity(entry) }
                feedback = null
                removed = null
            }) { Text("Undo") } }
        }
        presets.mapNotNull(::simpleEntry).forEach { entry ->
            key(entry.id) { ActivityDetails(entry, edit) }
        }
    }
    val inlineEntries = presetsByGroup.values.flatten().mapNotNull(::simpleEntry).toSet()
    val legacy = entries.filter { it !in inlineEntries }
    if (legacy.isNotEmpty()) {
        Text("Recorded activities", style = MaterialTheme.typography.titleMedium)
        legacy.forEachIndexed { i, entry -> DetailLink("${i + 1}. ${entry.activityLabel()}", "Edit") { editingId = entry.id } }
    }
    reviewTag?.let { tag ->
        AlertDialog(onDismissRequest = { reviewTag = null }, title = { Text("Review ${tagLabel(tag)}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                entries.filter { it.hasActivityTag(tag) }.forEachIndexed { i, entry ->
                    TextButton(onClick = { editingId = entry.id; reviewTag = null }) { Text("${i + 1}. ${entry.activityLabel()}") }
                }
            } }, confirmButton = { TextButton(onClick = { reviewTag = null }) { Text("Done") } })
    }
    entries.firstOrNull { it.id == editingId }?.let { entry ->
        AlertDialog(onDismissRequest = { editingId = null }, title = { Text("Edit activity") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                ActivityFields(entry, day, edit) {
                    editingId = null
                    remove("Other", entry)
                }
            } }, confirmButton = { TextButton(onClick = { editingId = null }) { Text("Done") } })
    }
}

@Composable
private fun ActivityDetails(activity: ActivityEntry, edit: DiaryEdit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)
        .background(MaterialTheme.colorScheme.primary.copy(alpha = .07f), RoundedCornerShape(18.dp)).padding(16.dp)) {
        Text(tagLabel(activity.tags.single()), style = MaterialTheme.typography.titleMedium)
        Text("Optional details", style = MaterialTheme.typography.bodySmall)
        DetailChoices(activity) { change ->
            edit { diary -> diary.copy(activities = diary.activities.map { if (it.id == activity.id) change(it) else it }) }
        }
    }
}

@Composable
private fun ActivityFields(activity: ActivityEntry, day: Day?, edit: DiaryEdit, removeEntry: () -> Unit) {
    var conflict by rememberSaveable { mutableStateOf<String?>(null) }
    var remove by rememberSaveable { mutableStateOf(false) }
    fun update(change: (ActivityEntry) -> ActivityEntry) {
        edit { d -> d.copy(activities = d.activities.map { if (it.id == activity.id) change(it) else it }) }
    }
    fun toggle(tag: String) = update { it.copy(tags = it.tags.toggle(tag)) }
    Text("Choose one or more tags for this activity.", style = MaterialTheme.typography.bodySmall)
    val groups = categoryTags + mapOf("Everyday" to otherTags.toSet())
    val other = activity.tags - groups.values.flatten().toSet()
    for ((category, tags) in groups + (if (other.isEmpty()) emptyMap() else mapOf("Other" to other))) {
        ActivityChips(category, tags.toList(), activity.tags.intersect(tags)) { tag ->
            if (tag !in activity.tags && category in categoryTags && day?.flag(category) == false) conflict = tag
            else toggle(tag)
        }
    }
    DetailChoices(activity, ::update)
    TextButton(onClick = { remove = true }) { Text("Remove activity") }
    conflict?.let { tag ->
        val category = categoryTags.entries.first { tag in it.value }.key
        AlertDialog(onDismissRequest = { conflict = null }, title = { Text("Change $category to Yes?") },
            text = { Text("You answered No for $category. Adding $tag to this entry will show Yes for the day. Removing the tag restores your earlier answer.") },
            confirmButton = { TextButton(onClick = { toggle(tag); conflict = null }) { Text("Add tag") } },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("Keep No") } })
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove this activity?") },
        text = { Text(activity.activityLabel()) }, confirmButton = { TextButton(onClick = {
            removeEntry()
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } })
}

@Composable
private fun DetailChoices(activity: ActivityEntry, update: ((ActivityEntry) -> ActivityEntry) -> Unit) {
    if (activity.tags.any { it in categoryTags.getValue("Work") } || activity.workLength != null || activity.workPlace != null) {
        ActivityChoice("Work day", listOf("Full day", "Part day"), activity.workLength) { value -> update { it.copy(workLength = value) } }
        ActivityChoice("Work setting", listOf("At home", "On site", "Both"), activity.workPlace) { value -> update { it.copy(workPlace = value) } }
    }
    ActivityChoice("Duration", listOf("Under 15 min", "15–60 min", "Over an hour"), activity.duration) { value -> update { it.copy(duration = value) } }
    ActivityChoice("Effort", listOf("Light", "Moderate", "High"), activity.effort) { value -> update { it.copy(effort = value) } }
    ActivityChips("Demands", listOf("Physical", "Thinking", "Upright", "Sensory", "Emotional"), activity.demands) { value ->
        update { it.copy(demands = it.demands.toggle(value)) }
    }
    ActivityChoice("Was this worthwhile to you?", listOf("Yes", "Mixed", "No"), activity.worth) { value -> update { it.copy(worth = value) } }
}

@Composable
private fun ActivityChoice(label: String, choices: List<String>, selected: String?, change: (String?) -> Unit) {
    ActivityChips(label, choices, setOfNotNull(selected)) { choice ->
        change(choice.takeUnless { it == selected })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityChips(label: String, choices: List<String>, selected: Set<String>, toggle: (String) -> Unit) {
    Spacer(Modifier.height(14.dp))
    Text(label, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (choices + selected).distinct().forEach { choice ->
            DiaryChip(choice, choice in selected, onClick = { toggle(choice) })
        }
    }
}


internal fun ActivityEntry.activityLabel(): String =
    (tags.sorted() + listOfNotNull(workLength, workPlace, duration, effort) + demands.sorted() +
        listOfNotNull(worth?.let { "Worthwhile · $it" })).joinToString(" · ").ifBlank { "Activity" }
