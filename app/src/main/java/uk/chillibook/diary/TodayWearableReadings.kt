package uk.chillibook.diary

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate

private enum class WearableMetric { Steps, Sleep }

private val stepCheers = listOf(
    "Well done for checking in", "Be kind to yourself today", "Some days ask more of you",
    "Thank you for making time for yourself", "Go gently, whatever today brings", "What you managed today matters",
    "It’s okay to take today slowly", "I’m glad you took a moment for yourself", "Whatever you managed was enough",
    "There’s room for a gentler day", "Well done for showing up today", "Hard days deserve kindness, too",
    "Gentle days deserve credit, too", "Here’s a moment just for you", "However today went, you deserve care"
)

private val sleepCheers = listOf(
    "I hope today is gentle with you", "If it was a rough night, go gently", "You deserve a little care today",
    "Rest counts, too", "Ease into today, if you can", "However you slept, be kind to yourself",
    "A quiet moment for yourself", "It’s okay if you still feel tired", "Thanks for taking a moment to check in",
    "Here’s to a softer start", "I hope you find a moment of ease", "Take today as it comes",
    "Well done for making space for yourself", "You’re allowed to take your time", "Your body deserves some kindness"
)

private const val STEP_BREAKDOWN_START = 6

@Composable
internal fun TodayWearableReadings(reading: WearableReading) {
    val steps = reading.steps
    val sleep = reading.sleepMinutes
    if (steps == null && sleep == null) return
    val date = remember(reading.date) { LocalDate.parse(reading.date) }
    var expanded by rememberSaveable(reading.date) { mutableStateOf<WearableMetric?>(null) }
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    val hasBoth = steps != null && sleep != null
    val day = date.toEpochDay()
    val moonHeading = steps == null || (hasBoth && Math.floorMod(day, 2L) == 1L)
    val messages = if (moonHeading) sleepCheers else stepCheers
    val rotation = if (hasBoth) Math.floorDiv(day, 2L) else day
    val message = messages[Math.floorMod(rotation, messages.size.toLong()).toInt()]
    fun toggle(metric: WearableMetric) { expanded = if (expanded == metric) null else metric }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paired = hasBoth && maxWidth >= 330.dp && fontScale <= 1.25f
        val stepTile: @Composable () -> Unit = {
            if (steps != null) WearableButton(
                "Steps", stepCount(steps), colors.primaryContainer,
                colors.onPrimaryContainer, colors.primary, expanded == WearableMetric.Steps,
                onClick = { toggle(WearableMetric.Steps) }
            )
        }
        val sleepTile: @Composable () -> Unit = {
            if (sleep != null) WearableButton(
                wearableSleepLabel(reading, today = date == LocalDate.now()), sleepDuration(sleep),
                colors.tertiaryContainer, colors.onTertiaryContainer,
                colors.tertiary, expanded == WearableMetric.Sleep, onClick = { toggle(WearableMetric.Sleep) }
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestionText(message, illustration = if (moonHeading) R.drawable.diary_moon else R.drawable.diary_trainer)
            if (paired) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { stepTile() }
                    Box(Modifier.weight(1f)) { sleepTile() }
                }
            } else {
                stepTile()
                sleepTile()
            }
            when (expanded) {
                WearableMetric.Steps -> StepsDetails(reading)
                WearableMetric.Sleep -> SleepDetails(reading)
                null -> Unit
            }
        }
    }
}

@Composable
private fun WearableButton(label: String, value: String, fill: Color, ink: Color, accent: Color, expanded: Boolean,
                           onClick: () -> Unit) {
    val type = MaterialTheme.typography
    val shape = MaterialTheme.shapes.large
    val input = remember { MutableInteractionSource() }
    val pressed by input.collectIsPressedAsState()
    val scale = selectionScale(expanded, pressed)
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(shape)
            .clickable(interactionSource = input, indication = LocalIndication.current, role = Role.Button,
                onClickLabel = if (expanded) "Hide details" else "Show details", onClick = onClick)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = shape, color = fill, contentColor = ink,
        border = BorderStroke(if (expanded) 2.dp else 1.dp, accent.copy(alpha = if (expanded) .85f else .35f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label, style = type.labelLarge)
            Text(value, style = type.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepsDetails(reading: WearableReading) {
    val steps = reading.steps ?: return
    val hours = reading.stepHours.orEmpty().filter { it.hour >= STEP_BREAKDOWN_START }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stepCount(steps)} steps recorded", style = type.titleMedium)
            if (hours.isEmpty()) {
                Text("No hourly readings from 6 am onward for this date.", style = type.bodyMedium)
            } else {
                Text("Steps by hour", style = type.titleSmall)
                val peak = hours.maxOf { it.steps }.coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(60.dp).clearAndSetSemantics {}) {
                    val cell = size.width / (24 - STEP_BREAKDOWN_START)
                    drawLine(colors.outlineVariant, Offset(0f, size.height), Offset(size.width, size.height))
                    for (hour in hours) {
                        val bar = (size.height * hour.steps / peak).coerceAtLeast(2.dp.toPx())
                        val left = cell * (hour.hour - STEP_BREAKDOWN_START + .14f)
                        drawRoundRect(colors.primary, Offset(left, size.height - bar),
                            Size(cell * .72f, bar), CornerRadius(2.dp.toPx()))
                    }
                }
                val labels = if (is24Hour) listOf("06:00", "12:00", "18:00")
                    else listOf("6 am", "12 pm", "6 pm")
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    labels.forEach { Text(it, Modifier.weight(1f), style = type.labelSmall) }
                }
                val dayParts = listOf("Morning", "Afternoon", "Evening")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayParts.forEachIndexed { part, label ->
                        val recorded = hours.filter { (it.hour - STEP_BREAKDOWN_START) / 6 == part }
                        val total = if (recorded.isEmpty()) "No reading" else
                            NumberFormat.getIntegerInstance().format(recorded.sumOf { it.steps.toLong() })
                        Text("$label · $total", style = type.bodySmall)
                    }
                }
            }
            reading.stepsSource?.let { Text("Source · $it", style = type.bodySmall, color = colors.onSurfaceVariant) }
        }
    }
}
