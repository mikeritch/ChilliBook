package uk.chillibook.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal fun Diary.recordRest(entry: ActivityEntry): Diary =
    restoreActivity(entry).changeDay(entry.date) { it.copy(noRests = null) }

internal fun Diary.toggleNoRests(date: String): Diary {
    if (activities.any { it.date == date && (it.restKind != null || "Quiet rest" in it.tags) }) return this
    return changeDay(date) { it.copy(noRests = if (it.noRests == true) null else true) }
}

internal fun Diary.updateEveningActivity(id: String, date: String, change: (ActivityEntry) -> ActivityEntry): Diary {
    val index = activities.indexOfFirst { it.id == id && it.date == date }
    if (index < 0) return this
    val updated = change(activities[index])
    val diary = copy(activities = activities.toMutableList().apply { this[index] = updated })
    return if (updated.restKind != null || "Quiet rest" in updated.tags)
        diary.changeDay(date) { it.copy(noRests = null) } else diary
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestActivities(diary: Diary, date: String, entries: List<ActivityEntry>, edit: DiaryEdit, editLegacy: (String) -> Unit) {
    var open by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var removed by remember(date) { mutableStateOf<ActivityEntry?>(null) }
    val rests = entries.filter { it.restKind != null }
    val legacy = entries.filter { it.restKind == null && "Quiet rest" in it.tags }
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DiaryIcon(R.drawable.diary_moon, colors.primary)
        Text("Rest & naps", style = MaterialTheme.typography.titleMedium)
    }
    Text("Log each break separately. Details are optional.", style = MaterialTheme.typography.bodyMedium)
    rests.forEachIndexed { index, entry ->
        key(entry.id) {
            Spacer(Modifier.height(8.dp))
            val expanded = open == entry.id
            Surface(shape = RoundedCornerShape(18.dp), color = colors.primary.copy(alpha = .05f),
                border = BorderStroke(1.dp, colors.outlineVariant)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (expanded) null else entry.id }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                        .padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiaryIcon(if (entry.restKind == "Nap") R.drawable.diary_moon else R.drawable.diary_pillow, colors.primary)
                        Column(Modifier.weight(1f)) {
                            Text("${entry.restKind} ${index + 1}", style = MaterialTheme.typography.titleSmall)
                            val details = listOfNotNull(entry.restPeriod, entry.duration, entry.restEnergy?.let { "Energy ${it.lowercase()}" })
                            Text(details.joinToString(" · ").ifEmpty { "Details optional" },
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text(if (expanded) "⌃" else "⌄", color = colors.primary)
                    }
                    if (expanded) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                        fun update(change: (ActivityEntry) -> ActivityEntry) {
                            edit { it.updateEveningActivity(entry.id, date, change) }
                        }
                        ActivityChips("Type", restKinds, setOfNotNull(entry.restKind)) { kind ->
                            update { it.copy(restKind = kind, tags = setOf(kind)) }
                        }
                        ActivityChoice("When was it?", restPeriods, entry.restPeriod) { choice -> update { it.copy(restPeriod = choice) } }
                        ActivityChoice("About how long?", restDurations, entry.duration) { choice -> update { it.copy(duration = choice) } }
                        ActivityChoice("Energy afterwards", restEnergies, entry.restEnergy) { choice -> update { it.copy(restEnergy = choice) } }
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                edit { it.removeActivity(entry) }
                                removed = entry
                                open = null
                            }) { Text("Remove", color = colors.error) }
                            OutlinedButton(onClick = { open = null }) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
    legacy.forEach { entry -> DetailLink(entry.activityLabel(), "Edit") { editLegacy(entry.id) } }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        restKinds.forEach { kind ->
            OutlinedButton(onClick = {
                val entry = ActivityEntry(date = date, tags = setOf(kind), restKind = kind)
                edit { it.recordRest(entry) }
                open = entry.id
                removed = null
            }) {
                Text("+")
                Spacer(Modifier.width(5.dp))
                Text("Add ${kind.lowercase()}")
            }
        }
    }
    if (rests.isEmpty() && legacy.isEmpty()) {
        DiaryChip("No rests or naps today", diary.days.find { it.date == date }?.noRests == true, onClick = {
            edit { it.toggleNoRests(date) }
            removed = null
        })
    }
    removed?.let { entry ->
        TextButton(onClick = {
            edit { it.recordRest(entry) }
            removed = null
            open = entry.id
        }) { Text("Undo removal") }
    }
    if (diary.prefs.activityPins.containsKey(activityTagKey("Quiet rest")) && legacy.isEmpty()) {
        TextButton(onClick = {
            val entry = ActivityEntry(date = date, tags = setOf("Quiet rest"))
            edit { it.recordRest(entry).applyActivityPin(entry.id, "Quiet rest") }
            removed = null
            editLegacy(entry.id)
        }) { Text("Use pinned Quiet rest setup") }
    }
}
