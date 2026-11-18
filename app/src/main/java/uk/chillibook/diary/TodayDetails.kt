package uk.chillibook.diary

import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayDetails(diary: Diary, date: String, slot: String, edit: DiaryEdit) {
    val day = diary.days.find { it.date == date }
    val check = diary.checks.find { it.date == date && it.slot == slot }
    val symptoms = check?.symptoms.orEmpty()
    val pins = diary.prefs.pins
    val names = (pins.sorted() + symptomLabels + symptoms.keys.sorted()).distinct()
    var editingPins by rememberSaveable { mutableStateOf(false) }
    var correcting by rememberSaveable { mutableStateOf(false) }

    var removedName by rememberSaveable { mutableStateOf<String?>(null) }
    var removedRating by rememberSaveable { mutableIntStateOf(UNRATED_SYMPTOM) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { QuestionText("Symptoms") }
        removedName?.let { name ->
            TextButton(onClick = {
                val rating = removedRating
                edit { d -> d.changeCheck(date, slot) { check ->
                    if (name in check.symptoms) check else check.copy(symptoms = check.symptoms + (name to rating))
                } }
                removedName = null
            }, modifier = Modifier.semantics { contentDescription = "Undo removal of $name" }) { Text("Undo") }
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        names.forEach { name ->
            val selected = name in symptoms
            DiaryChip(name, selected, accent = categoryAccent("Symptoms"), symbol = if (selected) "−" else "+", remove = selected, onClick = {
                removedName = name.takeIf { selected }
                removedRating = symptoms[name] ?: UNRATED_SYMPTOM
                edit { d -> d.changeCheck(date, slot) {
                    it.copy(symptoms = if (selected) it.symptoms - name else it.symptoms + (name to UNRATED_SYMPTOM))
                } }
            }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = if (selected) "Remove $name" else "Select $name"
            })
        }
    }
    for (name in names.filter { it in symptoms }) {
        Spacer(Modifier.height(16.dp))
        Text(name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        AnswerGrid(name, severityLabels, symptoms[name]) { rating ->
            edit { d -> d.changeCheck(date, slot) { val value = if (it.symptoms[name] == rating) UNRATED_SYMPTOM else rating
                it.copy(symptoms = it.symptoms + (name to value)) } }
        }

    }

    DetailLink("Change pinned symptoms", if (editingPins) "⌃" else "⌄") { editingPins = !editingPins }
    if (editingPins) {
        Text("Pinned symptoms appear first in your check-ins.")
        for (name in (symptomLabels + symptoms.keys.sorted() + pins.sorted()).distinct()) {
            AnswerTile("Pin symptom", name, name in pins, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                edit { d -> d.copy(prefs = d.prefs.copy(pins = d.prefs.pins.toggle(name))) }
            }
        }
    }
    if (slot == "pm") {
        OptionalField("Did symptoms change your plans?", listOf("As planned", "Slowed down", "Dropped something", "Needed help"), day?.impact) { value ->
            edit { d -> d.changeDay(date) { it.copy(impact = value) } }
        }
        MultiField("Context", listOf("Fighting a bug", "Unusual stress", "Change in medication"), day?.context.orEmpty()) { value ->
            edit { d -> d.changeDay(date) { it.copy(context = it.context.toggle(value)) } }
        }
    }
    DetailLink("Edit check-in answers", if (correcting) "⌃" else "⌄") { correcting = !correcting }
    if (correcting) {
        fun clearCheck(update: (Check) -> Check) = edit { it.changeCheck(date, slot, update) }
        ClearAnswer("Energy", check?.energy?.let(energyLabels::get)) { clearCheck { it.copy(energy = null) } }
        if (slot == "am") {
            ClearAnswer("Waking refreshment", check?.sleep?.let(sleepLabels::get)) { clearCheck { it.copy(sleep = null) } }
        } else {
            ClearAnswer("Overall wellbeing", day?.wellbeing?.let(wellbeingLabels::get)) { edit { d -> d.changeDay(date) { it.copy(wellbeing = null) } } }
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
