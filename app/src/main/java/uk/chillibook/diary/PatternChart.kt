package uk.chillibook.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

internal data class PatternChartSeries(val values: List<Float?>, val color: Color, val diamond: Boolean = false)

@Composable
internal fun PatternChart(description: String, dates: List<LocalDate>, series: List<PatternChartSeries>,
                          scaleTop: Float, yLabels: List<String>, daily: Boolean, selected: Int?,
                          onSelect: (Int) -> Unit, height: Dp = 113.dp) {
    if (dates.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val fontScale = LocalDensity.current.fontScale
    val plotHeight = height + if (fontScale > 1.35f) 34.dp else 0.dp
    val select by rememberUpdatedState(onSelect)
    val lastRecorded = dates.indices.lastOrNull { i -> series.any { it.values[i] != null } }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.height(plotHeight).clearAndSetSemantics {},
            verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            yLabels.forEach { Text(it, style = type.labelSmall) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Canvas(Modifier.fillMaxWidth().height(plotHeight)
                .pointerInput(dates) {
                    detectTapGestures { tap ->
                        val index = (tap.x * dates.size / size.width).toInt().coerceIn(dates.indices)
                        select(index)
                    }
                }
                .semantics {
                    contentDescription = "$description. Gaps indicate missing readings."
                    selected?.let {
                        val date = dates[it].format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
                        stateDescription = if (daily) date else "Week starting $date"
                    }
                    onClick(label = "Show a reading") {
                        select(selected ?: lastRecorded ?: dates.lastIndex)
                        true
                    }
                    customActions = buildList {
                        val active = selected ?: lastRecorded ?: dates.lastIndex
                        if (active > 0) add(CustomAccessibilityAction("Previous chart point") { select(active - 1); true })
                        if (active < dates.lastIndex) add(CustomAccessibilityAction("Next chart point") { select(active + 1); true })
                    }
                }) {
                val pad = (if (fontScale > 1.35f) 15.dp else 9.dp).toPx()
                val plotSpan = size.height - pad * 2
                val slotWidth = size.width / dates.size
                fun x(i: Int) = slotWidth * (i + .5f)
                fun y(value: Float) = size.height - pad - value / scaleTop * plotSpan
                for (fraction in listOf(0f, .5f, 1f)) {
                    val gridY = pad + fraction * plotSpan
                    drawLine(colors.outlineVariant, Offset(0f, gridY), Offset(size.width, gridY), 1.dp.toPx())
                }
                selected?.let {
                    val lineX = x(it)
                    drawLine(colors.primary.copy(alpha = .65f), Offset(lineX, pad),
                        Offset(lineX, size.height - pad), 1.dp.toPx())
                }
                val radius = (if (dates.size > 14) 2.5.dp else 3.5.dp).toPx()
                for (line in series) {
                    var previous: Offset? = null
                    line.values.forEachIndexed { i, value ->
                        val point = value?.let { Offset(x(i), y(it)) }
                        if (point != null) {
                            previous?.let { drawLine(line.color, it, point, 1.8.dp.toPx()) }
                            if (line.diamond) drawPath(Path().apply {
                                moveTo(point.x, point.y - radius)
                                lineTo(point.x + radius, point.y)
                                lineTo(point.x, point.y + radius)
                                lineTo(point.x - radius, point.y)
                                close()
                            }, line.color) else drawCircle(line.color, radius, point)
                        }
                        previous = point
                    }
                    selected?.let { i -> line.values[i]?.let { value ->
                        drawCircle(line.color, radius + 3.dp.toPx(), Offset(x(i), y(value)), style = Stroke(1.5.dp.toPx()))
                    } }
                }
            }
            PatternDateAxis(dates, daily, selected, fontScale)
        }
    }
}

@Composable
internal fun PatternDateAxis(dates: List<LocalDate>, daily: Boolean, selected: Int?, fontScale: Float) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        val everyDay = daily && dates.size <= 7
        val capacity = if (everyDay) dates.size else minOf(dates.size, (maxWidth / (58.dp * fontScale)).toInt().coerceIn(1, 5))
        val indexes = if (capacity == 1) listOf(dates.lastIndex) else List(capacity) {
            (it.toFloat() * dates.lastIndex / (capacity - 1)).roundToInt()
        }
        val labelWidth = maxWidth / capacity
        for (i in indexes) {
            val date = dates[i]
            val label = if (everyDay) {
                val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                if (labelWidth < 37.dp * fontScale) day.take(2) else day
            } else date.format(DateTimeFormatter.ofPattern("d MMM"))
            val center = maxWidth * ((i + .5f) / dates.size)
            val x = (center - labelWidth / 2).coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
            Text(label, Modifier.absoluteOffset(x = x).width(labelWidth), textAlign = TextAlign.Center,
                style = type.labelSmall, maxLines = 1,
                color = if (i == selected) colors.primary else colors.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PatternPointDetails(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 12.dp, end = 6.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).padding(vertical = 6.dp), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close chart details" }) {
                    Text("×", Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge)
                }
            }
            content()
        }
    }
}
