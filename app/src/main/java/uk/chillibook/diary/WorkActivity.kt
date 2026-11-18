package uk.chillibook.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

private val workParts = listOf("Commute", "Meetings", "Focused work")
private val workDemands = listOf("Physical", "Thinking", "Upright", "Sensory", "Emotional")

internal fun ActivityEntry.asWorkSetup() = WorkSetup(
    parts = tags - "Work", workLength = workLength, effort = effort, demands = demands
)

private fun Diary.groupedWork(date: String) = activities.firstOrNull { it.date == date && it.workSetup }

private fun Diary.newGroupedWork(date: String): ActivityEntry {
    val base = "work-setup-$date"
    var id = base
    var suffix = 0
    while (activities.any { it.id == id }) id = "$base-${++suffix}"
    return ActivityEntry(id = id, date = date, workSetup = true)
}

internal fun Diary.editWorkSetup(date: String, update: (WorkSetup) -> WorkSetup): Diary {
    val entry = groupedWork(date)
    val setup = update(entry?.asWorkSetup() ?: WorkSetup())
    if (setup == WorkSetup()) return if (entry == null) this else removeActivity(entry)
    val saved = (entry ?: newGroupedWork(date)).copy(
        tags = setOf("Work") + setup.parts, workLength = setup.workLength,
        effort = setup.effort, demands = setup.demands
    )
    return copy(activities = if (entry == null) activities + saved else activities.map { if (it.id == entry.id) saved else it })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkActivity(diary: Diary, date: String, tagged: List<ActivityEntry>, edit: DiaryEdit, review: (String) -> Unit) {
    val day = diary.days.find { it.date == date }
    val answer = activityFlag(day, tagged, "Work")
    val setup = diary.groupedWork(date)?.asWorkSetup() ?: WorkSetup()
    val legacy = tagged.filterNot { it.workSetup }
    val pin = diary.prefs.workPin
    // The pending answer can be either No or unanswered.
    var pending by rememberSaveable(date) { mutableStateOf<String?>(null) }
    var reviewNeeded by rememberSaveable(date) { mutableStateOf(false) }
    fun change(update: (WorkSetup) -> WorkSetup) {
        pending = null
        edit { it.editWorkSetup(date, update) }
    }
    YesNoRow("Did you work?", answer, "Work") { value ->
        val next = value.takeUnless { it == answer }
        reviewNeeded = next != true && legacy.isNotEmpty()
        pending = null
        if (!reviewNeeded) {
            if (next != true && setup != WorkSetup()) pending = if (next == false) "No" else "Clear"
            else edit { d -> d.changeDay(date) { it.copy(work = next) } }
        }
    }
    if (pending != null) {
        Text("Clear the work details for this day?", style = MaterialTheme.typography.bodyMedium)
        FlowRow {
            TextButton(onClick = { pending = null }) { Text("Keep details") }
            TextButton(onClick = {
                val next = false.takeIf { pending == "No" }
                edit { d -> d.editWorkSetup(date) { WorkSetup() }.changeDay(date) { it.copy(work = next) } }
                pending = null
            }) { Text("Clear details") }
        }
    }
    if (answer != true) return
    if (legacy.isNotEmpty()) {
        if (reviewNeeded) Text("Edit or remove the recorded work activities before changing this answer.",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (day?.work == false) Text("Your earlier No returns when you remove the matching tags.", style = MaterialTheme.typography.bodySmall)
        legacy.forEach { entry -> DetailLink(entry.activityLabel(), "Edit") { review(entry.id) } }
    }
    Spacer(Modifier.height(8.dp))
    Column(Modifier.fillMaxWidth().background(categoryAccent("Work").copy(alpha = .09f),
        RoundedCornerShape(18.dp)).padding(14.dp)) {
        key(date, setup, pending) {
            ActivityPinHeader("What did work involve?", "work", categoryAccent("Work"),
                hasDetails = setup != WorkSetup(), matches = setup == pin,
                summary = pin?.let { saved -> (workParts.filter { it in saved.parts } +
                    listOfNotNull(saved.workLength, saved.effort) + workDemands.filter { it in saved.demands }).joinToString(" · ") },
                save = { edit { d -> d.copy(prefs = d.prefs.copy(workPin = d.groupedWork(date)?.asWorkSetup())) } },
                apply = { edit { d -> d.prefs.workPin?.let { saved -> d.editWorkSetup(date) { saved } } ?: d } },
                unpin = { edit { d -> d.copy(prefs = d.prefs.copy(workPin = null)) } })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            workParts.forEach { part ->
                val selected = part in setup.parts
                DiaryChip(part, selected, { change { it.copy(parts = it.parts.toggle(part)) } },
                    symbol = if (selected) "−" else "+", accent = categoryAccent("Work"), remove = selected)
            }
        }
        ActivityChips("Work day", listOf("Full day", "Part day"), setOfNotNull(setup.workLength)) { value ->
            change { setup -> setup.copy(workLength = value.takeUnless { it == setup.workLength }) }
        }
        ActivityChips("Overall work effort", listOf("Light", "Moderate", "High"), setOfNotNull(setup.effort)) { value ->
            change { setup -> setup.copy(effort = value.takeUnless { it == setup.effort }) }
        }
        ActivityChips("Demands across the work day", workDemands, setup.demands) { value ->
            change { it.copy(demands = it.demands.toggle(value)) }
        }
    }
}
