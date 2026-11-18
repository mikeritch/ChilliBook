package uk.chillibook.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

internal fun ActivityEntry.activitySetup() = ActivitySetup(duration = duration, effort = effort, demands = demands, worth = worth)

internal fun Diary.saveActivityPin(id: String, tag: String): Diary {
    val entry = activities.firstOrNull { it.id == id && !it.workSetup && it.tags.singleOrNull() == tag } ?: return this
    val details = entry.activitySetup()
    if (!details.hasSupportedDetails()) return this
    val pin = details.copy(displayTag = tag.takeIf { it.startsWith(CUSTOM_ACTIVITY_PREFIX) })
    return copy(prefs = prefs.copy(activityPins = prefs.activityPins + (activityTagKey(tag) to pin)))
}

internal fun Diary.applyActivityPin(id: String, tag: String): Diary {
    val pin = prefs.activityPins[activityTagKey(tag)] ?: return this
    return copy(activities = activities.map { entry ->
        if (entry.id == id && !entry.workSetup && entry.tags.singleOrNull() == tag)
            entry.copy(duration = pin.duration, effort = pin.effort, demands = pin.demands, worth = pin.worth)
        else entry
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivityPinHeader(title: String, label: String, accent: Color, hasDetails: Boolean,
    matches: Boolean, summary: String?, save: () -> Unit, apply: () -> Unit, unpin: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        FilledTonalIconButton(shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (summary == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (summary == null) accent else MaterialTheme.colorScheme.onSecondaryContainer
            ), enabled = summary != null || hasDetails, onClick = {
                if (summary == null) save() else open = !open
            }) {
            Icon(painterResource(if (summary == null) R.drawable.work_pin else R.drawable.work_pin_filled),
                if (summary == null) "Pin these $label details" else "Open pinned $label setup")
        }
    }
    if (open && summary != null) {
        Text("Pinned $label setup", style = MaterialTheme.typography.titleSmall)
        Text(summary)
        FlowRow {
            if (!matches) {
                TextButton(onClick = { apply(); open = false }) { Text("Use for this day") }
                if (hasDetails) TextButton(onClick = { save(); open = false }) { Text("Replace pin with these details") }
            }
            TextButton(onClick = { unpin(); open = false }) { Text("Unpin") }
        }
    }
}
