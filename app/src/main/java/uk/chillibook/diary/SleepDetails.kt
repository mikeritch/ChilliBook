package uk.chillibook.diary

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class Stage(val label: String) {
    Awake("Awake"),
    Rem("REM"),
    Light("Light"),
    Deep("Deep"),
    Asleep("Asleep, stage unspecified"),
    Unknown("Unknown stage"),
    Gap("Time without stage data");

    @Composable
    fun ink(): Color {
        val colors = MaterialTheme.colorScheme
        return when (this) {
            Awake -> colors.error
            Rem -> colors.tertiary
            Light -> colors.primary
            Deep -> colors.secondary
            Asleep -> colors.onSurfaceVariant
            Unknown -> colors.outline
            Gap -> colors.onSurfaceVariant.copy(alpha = .26f)
        }
    }
}

private data class StageSpan(val start: Long, val end: Long, val stage: Stage) {
    val seconds get() = end - start
}

private data class SleepWindow(val start: OffsetDateTime, val end: OffsetDateTime, val spans: List<StageSpan>) {
    val seconds get() = Duration.between(start, end).seconds
}

private fun RecordedSleep.window(): SleepWindow? {
    val bed: OffsetDateTime
    val wake: OffsetDateTime
    try {
        bed = OffsetDateTime.parse(start)
        wake = OffsetDateTime.parse(end)
    } catch (_: DateTimeParseException) {
        return null
    }
    val seconds = Duration.between(bed, wake).seconds
    if (seconds <= 0) return null
    val spans = buildList {
        var cursor = 0L
        for (phase in phases.sortedBy { it.startSecond }) {
            val from = phase.startSecond.toLong().coerceIn(cursor, seconds)
            val until = phase.endSecond.toLong().coerceAtMost(seconds)
            if (until <= from) continue
            if (from > cursor) add(StageSpan(cursor, from, Stage.Gap))
            val stage = when (phase.kind) {
                "awake" -> Stage.Awake
                "rem" -> Stage.Rem
                "light" -> Stage.Light
                "deep" -> Stage.Deep
                "asleep" -> Stage.Asleep
                else -> Stage.Unknown
            }
            add(StageSpan(from, until, stage))
            cursor = until
        }
        if (isNotEmpty() && cursor < seconds) add(StageSpan(cursor, seconds, Stage.Gap))
    }
    return SleepWindow(bed, wake, spans)
}

private fun sleepLength(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val remainder = seconds % 60
    return listOfNotNull(
        hours.takeIf { it > 0 }?.let { "$it h" },
        minutes.takeIf { it > 0 }?.let { "$it min" },
        remainder.takeIf { it > 0 || seconds == 0L }?.let { "$it sec" },
    ).joinToString(" ")
}

@Composable
private fun sleepClock(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

@Composable
private fun SupportingSleepText(label: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SleepStrip(spans: List<StageSpan>, duration: Long, scale: Long, detailed: Boolean) {
    if (scale <= 0) return
    val colors = Stage.entries.associateWith { it.ink() }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val summary = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(if (detailed) 42.dp else 12.dp).clip(MaterialTheme.shapes.extraSmall)) {
        drawRect(track)
        val perSecond = size.width / scale
        if (spans.isEmpty()) {
            drawRect(summary, topLeft = Offset(0f, size.height / 3), size = Size(duration * perSecond, size.height / 3))
        }
        for (span in spans) {
            val fraction = if (detailed) when (span.stage) {
                Stage.Awake -> 1f
                Stage.Rem -> .8f
                Stage.Light -> .6f
                Stage.Deep -> .4f
                else -> .25f
            } else 1f
            val height = size.height * fraction
            drawRect(
                colors.getValue(span.stage),
                topLeft = Offset(span.start * perSecond, size.height - height),
                size = Size(span.seconds * perSecond, height),
            )
        }
    }
}

@Composable
private fun StageLabel(stage: Stage, duration: Long? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(stage.ink(), CircleShape))
        Text(
            if (duration == null) stage.label else "${stage.label} · ${sleepLength(duration)}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private data class SleepPeriodWindow(val window: SleepWindow, val source: String?)

private fun WearableReading.sleepWindows(): List<SleepPeriodWindow> =
    recordedSleepPeriods().mapNotNull { period ->
        period.detail.window()?.let { SleepPeriodWindow(it, period.source) }
    }.sortedBy { it.window.start.toInstant() }

private fun WearableReading.sleepDuration(periods: List<SleepPeriodWindow>): Long? =
    periods.takeIf { it.isNotEmpty() }?.sumOf { it.window.seconds } ?: sleepMinutes?.times(60L)

@Composable
internal fun SleepDetails(reading: WearableReading) {
    val periods = remember(reading) { reading.sleepWindows() }
    val duration = reading.sleepDuration(periods) ?: return
    val type = MaterialTheme.typography
    val clock = sleepClock()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val durationLabel = sleepLength(duration)
            Text(
                if (periods.size > 1) "$durationLabel total across sleep periods"
                else "$durationLabel recorded session",
                style = type.titleMedium,
            )
            if (periods.isEmpty()) {
                SupportingSleepText("Session times unavailable.")
                SupportingSleepText("No sleep stages recorded.")
                reading.sleepSource?.takeIf(String::isNotBlank)?.let { SupportingSleepText("Source · $it") }
            }
            periods.forEachIndexed { index, period ->
                if (periods.size > 1) {
                    if (index > 0) HorizontalDivider()
                    Text(
                        "Sleep period ${index + 1} · ${sleepLength(period.window.seconds)}",
                        modifier = Modifier.semantics { heading() },
                        style = type.titleSmall,
                    )
                }
                SleepPeriodDetails(period, clock)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepPeriodDetails(period: SleepPeriodWindow, clock: DateTimeFormatter) {
    val window = period.window
    val totals = remember(window) { window.spans.groupBy { it.stage }.mapValues { (_, spans) -> spans.sumOf { it.seconds } } }
    val day = DateTimeFormatter.ofPattern("d MMM")
    val type = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column {
                SupportingSleepText("Session start · ${window.start.format(day)}")
                Text(window.start.format(clock), style = type.bodyLarge)
            }
            Column {
                SupportingSleepText("Wake · ${window.end.format(day)}")
                Text(window.end.format(clock), style = type.bodyLarge)
            }
        }
        if (totals.isEmpty()) {
            SupportingSleepText("No sleep stages recorded.")
        } else {
            SleepStrip(window.spans, window.seconds, window.seconds, detailed = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (stage in Stage.entries) {
                    val seconds = totals[stage] ?: continue
                    StageLabel(stage, seconds)
                }
            }
        }
        period.source?.takeIf(String::isNotBlank)?.let { SupportingSleepText("Source · $it") }
    }
}

private data class SleepDay(
    val date: LocalDate,
    val reading: WearableReading,
    val periods: List<SleepPeriodWindow>,
    val seconds: Long,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepPatterns(
    readings: List<WearableReading>,
    start: LocalDate,
    end: LocalDate,
    onDate: (String) -> Unit,
    onBack: () -> Unit,
) {
    val days = remember(readings, start, end) {
        readings.mapNotNull { reading ->
            val date = LocalDate.parse(reading.date)
            if (date !in start..end) return@mapNotNull null
            val periods = reading.sleepWindows()
            val duration = reading.sleepDuration(periods) ?: return@mapNotNull null
            SleepDay(date, reading, periods, duration)
        }.sortedByDescending { it.date }
    }
    val scale = remember(days) { maxOf(days.maxOfOrNull { it.seconds } ?: 0, 3_600) }
    val stages = remember(days) {
        days.flatMap { it.periods.singleOrNull()?.window?.spans.orEmpty() }.map { it.stage }.toSet()
    }
    val type = MaterialTheme.typography
    val clock = sleepClock()
    val day = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
    val rangeDay = DateTimeFormatter.ofPattern("d MMM yyyy")
    var expanded by rememberSaveable(start.toString(), end.toString()) { mutableStateOf<String?>(null) }
    BackHandler {
        if (expanded != null) expanded = null else onBack()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack) { Text("‹  Diary patterns") }
                Text("Sleep patterns", Modifier.semantics { heading() }, style = type.headlineLarge, fontFamily = FontFamily.Serif)
                SupportingSleepText("${start.format(rangeDay)} – ${end.format(rangeDay)}")
                if (days.isEmpty()) {
                    Text("No sleep readings in this period.", style = type.bodyMedium)
                } else {
                    val totalDays = end.toEpochDay() - start.toEpochDay() + 1
                    Text("${days.size} of $totalDays wake dates recorded", style = type.bodyMedium)
                    SupportingSleepText("Bars show recorded duration. Full width represents ${sleepLength(scale)}.")
                    if (stages.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (stage in Stage.entries) if (stage in stages) StageLabel(stage)
                        }
                    }
                }
            }
        }
        items(days, key = { it.reading.date }) { sleepDay ->
            val open = expanded == sleepDay.reading.date
            Column {
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) {
                        expanded = if (open) null else sleepDay.reading.date
                    }.semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                        .heightIn(min = 48.dp).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${sleepDay.date.format(day)} · ${sleepLength(sleepDay.seconds)}", Modifier.weight(1f), style = type.titleSmall)
                        Text(if (open) "⌃" else "⌄", Modifier.clearAndSetSemantics {},
                            style = type.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    val window = sleepDay.periods.singleOrNull()?.window
                    SupportingSleepText(
                        when {
                            window != null -> "Start ${window.start.format(clock)} · Wake ${window.end.format(clock)}"
                            sleepDay.periods.isNotEmpty() -> "${sleepDay.periods.size} sleep periods"
                            else -> "Session duration only"
                        },
                    )
                    SleepStrip(window?.spans.orEmpty(), sleepDay.seconds, scale, detailed = false)
                }
                if (open) {
                    SleepDetails(sleepDay.reading)
                    PatternsLink("Open diary entry") { onDate(sleepDay.reading.date) }
                }
                HorizontalDivider()
            }
        }
    }
}
