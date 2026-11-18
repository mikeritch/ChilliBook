package uk.chillibook.diary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayDetails(diary: Diary, date: String, slot: String, edit: DiaryEdit, status: String,
                          retry: () -> Unit, close: () -> Unit, episodes: () -> Unit) {
    val day = diary.days.find { it.date == date }
    val check = diary.checks.find { it.date == date && it.slot == slot }
    var page by rememberSaveable { mutableStateOf("main") }
    var symptom by rememberSaveable { mutableStateOf("") }
    val mainScroll = rememberScrollState()
    fun back() { if (page == "main") close() else page = "main" }
    DiaryDetailSheet(onDismiss = ::back) {
        BackHandler(page != "main") { back() }
        val title = when(page) {
            "symptom" -> symptom
            "pins" -> "Pinned symptoms"
            "corrections" -> "Edit check-in answers"
            else -> if (slot == "am") "Symptoms" else "Symptoms & context"
        }
        Column(Modifier.fillMaxWidth().weight(1f)
            .verticalScroll(if (page == "main") mainScroll else key(page, symptom) { rememberScrollState() }, overscrollEffect = null)
            .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            when (page) {
                "pins" -> {
                    Text("Pinned symptoms appear first in your check-ins.")
                    symptomLabels.forEach { name ->
                        AnswerTile("Pin symptom", name, name in diary.prefs.pins, Modifier.fillMaxWidth()) {
                            edit { d -> d.copy(prefs = d.prefs.copy(pins = d.prefs.pins.toggle(name))) }
                        }
                    }
                }
                "symptom" -> {
                    val selected = check?.symptoms?.get(symptom)
                    AnswerGrid(symptom, severityLabels, selected) { value ->
                        edit { d -> d.changeCheck(date, slot) { it.copy(symptoms = it.symptoms + (symptom to value)) } }
                    }
                    Text("Mild means noticeable, I could carry on. Moderate means I had to adjust what I was doing.")
                    if (selected != null) TextButton(onClick = {
                        edit { d -> d.changeCheck(date, slot) { it.copy(symptoms = it.symptoms - symptom) } }
                    }) { Text("Clear symptom rating") }
                }
                "corrections" -> {
                    fun clearCheck(update: (Check) -> Check) = edit { it.changeCheck(date, slot, update) }
                    fun clearDay(update: (Day) -> Day) = edit { it.changeDay(date, update) }
                    ClearAnswer("Energy", check?.energy?.let(energyLabels::get)) { clearCheck { it.copy(energy = null) } }
                    if (slot == "am") {
                        ClearAnswer("Waking refreshment", check?.sleep?.let(sleepLabels::get)) { clearCheck { it.copy(sleep = null) } }
                    } else {
                        ClearAnswer("Overall wellbeing", day?.wellbeing?.let(wellbeingLabels::get)) { clearDay { it.copy(wellbeing = null) } }
                    }
                }
                else -> {
                    if (slot == "pm") {
                        OptionalField("Did symptoms change your plans?", listOf("As planned", "Slowed down", "Dropped something", "Needed help"), day?.impact) { value ->
                            edit { d -> d.changeDay(date) { it.copy(impact = value) } }
                        }
                        MultiField("Context", listOf("Fighting a bug", "Unusual stress", "Change in medication"), day?.context.orEmpty()) { value ->
                            edit { d -> d.changeDay(date) { it.copy(context = it.context.toggle(value)) } }
                        }
                    }
                    QuestionText("Symptoms")
                    val names = (diary.prefs.pins.sorted() + symptomLabels + check?.symptoms.orEmpty().keys).distinct()
                    for (name in names) {
                        val rating = check?.symptoms?.get(name)?.let(severityLabels::get)
                        DetailLink(listOfNotNull(name, rating).joinToString(" · ")) { symptom = name; page = "symptom" }
                    }
                    DetailLink("Change pinned symptoms") { page = "pins" }
                    DetailLink("Edit check-in answers") { page = "corrections" }
                    DetailLink("Crash episodes", onClick = episodes)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            SaveNotice(status, retry, Modifier.weight(1f))
            TextButton(onClick = ::back) { Text(if (page == "main") "Done" else "Back") }
        }
    }
}

@Composable
private fun ClearAnswer(name: String, value: String?, clear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("$name · ${value ?: "Unanswered"}", Modifier.weight(1f))
        TextButton(onClick = clear, enabled = value != null) { Text("Clear") }
    }
}

@Composable
internal fun OptionalField(label: String, choices: List<String>, value: String?, change: (String?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    DetailLink(listOfNotNull(label, value).joinToString(" · "), if (expanded) "⌃" else "⌄") { expanded = !expanded }
    if (expanded) {
        AnswerGrid(label, choices, choices.indexOf(value)) { change(choices[it]) }
        if (value != null) TextButton(onClick = { change(null) }) { Text("Clear $label") }
    }
}

@Composable
internal fun MultiField(label: String, choices: List<String>, value: Set<String>, toggle: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    DetailLink(if (value.isEmpty()) label else "$label · ${value.sorted().joinToString()}", if (expanded) "⌃" else "⌄") { expanded = !expanded }
    if (expanded) choices.forEach { name ->
        AnswerTile(label, name, name in value, Modifier.fillMaxWidth()) { toggle(name) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiaryDetailSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(top = 16.dp), content = content)
    }
}
