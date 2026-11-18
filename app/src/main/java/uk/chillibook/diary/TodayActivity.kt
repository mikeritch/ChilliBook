package uk.chillibook.diary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EveningActivities(diary: Diary, date: String, edit: DiaryEdit, flag: (String, Boolean?) -> Unit,
                              editingId: String?, select: (String?) -> Unit) {
    val entries = diary.activities.filter { it.date == date }.sortedWith(compareBy({ it.at }, { it.id }))
    val day = diary.days.find { it.date == date }
    QuestionText("Activities")
    for ((tag, question) in listOf("Work" to "Did you work?", "Socialising" to "Did you socialise?", "Household" to "Household tasks?")) {
        val tagged = entries.any { entry -> entry.tags.any { it in categoryTags.getValue(tag) } }
        val answer = activityFlag(day, entries, tag)
        YesNoRow(question, answer, tag) { value ->
            flag(tag, if (answer == value && !tagged) null else value)
        }
        if (tagged && day?.flag(tag) == false) {
            Text("Tagged activity counts as Yes. Your earlier No returns if you remove the tag.", style = MaterialTheme.typography.bodySmall)
        }
        if (tag == "Work" && answer == false) {
            OptionalField("Reason for no work", listOf("Planned day off", "Off sick", "Other"), day?.workOff) { value ->
                edit { d -> d.changeDay(date) { it.copy(workOff = value) } }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    entries.filter { it.id != editingId }.forEach { entry ->
        DetailLink(entry.activityLabel(), "Edit") { select(entry.id) }
    }
    if (editingId == null) DetailLink("Add activity", "+") { select(java.util.UUID.randomUUID().toString()) }
    else key(editingId) {
        val editorAnchor = remember { BringIntoViewRequester() }
        Row(Modifier.fillMaxWidth().bringIntoViewRequester(editorAnchor),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { QuestionText("What did you do?") }
            TextButton(onClick = { select(null) }) { Text("Finish") }
        }
        LaunchedEffect(editingId) { editorAnchor.bringIntoView() }
        ActivityFields(diary, date, editingId, edit) { select(null) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityFields(diary: Diary, date: String, id: String, edit: DiaryEdit, done: () -> Unit) {
    val existing = diary.activities.find { it.id == id }
    val activity = existing ?: remember(date, id) { ActivityEntry(id = id, date = date) }
    val day = diary.days.find { it.date == date }
    var conflict by rememberSaveable { mutableStateOf<String?>(null) }
    var remove by rememberSaveable { mutableStateOf(false) }
    fun update(change: (ActivityEntry) -> ActivityEntry) {
        edit { d ->
            val updated = change(d.activities.find { it.id == id } ?: activity)
            val empty = updated.tags.isEmpty() && updated.duration == null && updated.effort == null &&
                updated.demands.isEmpty() && updated.worth == null && updated.workLength == null && updated.workPlace == null
            d.copy(activities = d.activities.filterNot { it.id == id } + listOfNotNull(updated.takeUnless { empty }))
        }
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
    var details by rememberSaveable { mutableStateOf(false) }
    DetailLink("Optional activity details", if (details) "⌃" else "⌄") { details = !details }
    if (details) {
        if (activity.tags.any { it in categoryTags.getValue("Work") } || activity.workLength != null || activity.workPlace != null) {
            ActivityChoice("Work day", listOf("Full day", "Part day"), activity.workLength) { value -> update { it.copy(workLength = value) } }
            ActivityChoice("Work setting", listOf("At home", "On site", "Both"), activity.workPlace) { value -> update { it.copy(workPlace = value) } }
        }
        ActivityChoice("Duration", listOf("Under 15 min", "15–60 min", "Over an hour"), activity.duration) { value -> update { it.copy(duration = value) } }
        ActivityChoice("Perceived effort", listOf("Light", "Moderate", "High"), activity.effort) { value -> update { it.copy(effort = value) } }
        ActivityChips("Demands", listOf("Physical", "Thinking", "Upright", "Sensory", "Emotional"), activity.demands) { value ->
            update { it.copy(demands = it.demands.toggle(value)) }
        }
        ActivityChoice("Was this worthwhile to you?", listOf("Yes", "Mixed", "No"), activity.worth) { value -> update { it.copy(worth = value) } }
    }
    if (existing != null) TextButton(onClick = { remove = true }) { Text("Remove activity") }
    conflict?.let { tag ->
        val category = categoryTags.entries.first { tag in it.value }.key
        AlertDialog(onDismissRequest = { conflict = null }, title = { Text("Change $category to Yes?") },
            text = { Text("You answered No for $category. Adding $tag to this entry will show Yes for the day. Removing the tag restores your earlier answer.") },
            confirmButton = { TextButton(onClick = { toggle(tag); conflict = null }) { Text("Add tag") } },
            dismissButton = { TextButton(onClick = { conflict = null }) { Text("Keep No") } })
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove this activity?") },
        text = { Text(activity.activityLabel()) }, confirmButton = { TextButton(onClick = {
            edit { d -> d.copy(activities = d.activities.filterNot { it.id == id }) }
            done()
        }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Keep") } })
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
    Text(label, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (choices + selected).distinct().forEach { choice ->
            FilterChip(selected = choice in selected, onClick = { toggle(choice) }, label = { Text(choice) })
        }
    }
}


internal fun ActivityEntry.activityLabel(): String =
    (tags.sorted() + listOfNotNull(workLength, workPlace, duration, effort) + demands.sorted() +
        listOfNotNull(worth?.let { "Worthwhile · $it" })).joinToString(" · ").ifBlank { "Activity" }
