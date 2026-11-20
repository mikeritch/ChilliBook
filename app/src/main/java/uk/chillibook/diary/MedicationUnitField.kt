package uk.chillibook.diary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

internal const val DEFAULT_MEDICATION_UNIT = "tablets"

private val commonUnits = listOf(DEFAULT_MEDICATION_UNIT, "capsules", "mL", "mg", "drops", "puffs", "patches")
private val unitOptions = commonUnits + listOf("Other", "Unknown")

@Composable
internal fun MedicationUnitField(unit: String, change: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(unit.isNotEmpty() && unit !in commonUnits) }
    val label = if (custom) "Other" else unit.ifEmpty { "Unknown" }
    MedicationDropdown("Unit · $label", unitOptions) { index ->
        val selection = unitOptions[index]
        if (selection == "Other") {
            if (!custom) change("")
            custom = true
        } else {
            custom = false
            change(if (selection == "Unknown") "" else selection)
        }
    }
    if (custom) {
        OutlinedTextField(unit, change, modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom unit") }, singleLine = true)
    }
}
