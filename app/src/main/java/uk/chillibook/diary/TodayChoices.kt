package uk.chillibook.diary

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.ZoneId

internal fun chooseDiaryDate(context: Context, date: String, min: String? = null, max: String = LocalDate.now().toString(), chosen: (String) -> Unit) {
    val initial = LocalDate.parse(date)
    fun millis(value: String) = LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    DatePickerDialog(context, { _, y, m, d -> chosen(LocalDate.of(y, m + 1, d).toString()) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
        datePicker.maxDate = millis(max)
        if (min != null) datePicker.minDate = millis(min)
    }.show()
}

@Composable
internal fun QuestionText(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun AnswerTile(question: String, label: String, active: Boolean, modifier: Modifier = Modifier,
                        rank: Int? = null, compact: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = if (active) colors.onPrimary else colors.onSurface
    val shape = RoundedCornerShape(12.dp)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val fill = if (active) colors.primary else colors.surface
    val background = if (pressed) ink.copy(alpha = .10f).compositeOver(fill) else fill
    Box(modifier.clip(shape).background(background)
        .border(1.dp, if (active) colors.primary else colors.outline, shape)
        .selectable(active, interactionSource = interactions, indication = LocalIndication.current,
            role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = "$question, $label" }
        .heightIn(min = if (rank == null) 48.dp else 68.dp),
        contentAlignment = Alignment.Center) {
        // Reserve the tick space in both states so selecting a tile never moves its label.
        val contentPadding = if (compact || rank != null) PaddingValues(start = 2.dp, end = 2.dp, top = 19.dp, bottom = 6.dp)
            else PaddingValues(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        Column(Modifier.padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (rank != null) {
                Canvas(Modifier.size(28.dp, 12.dp)) {
                    for (i in 0..4) {
                        val h = size.height * (0.22f + i * 0.17f)
                        drawRoundRect(if (i <= rank) ink else ink.copy(alpha = .25f),
                            Offset(i * size.width / 5, size.height - h), Size(size.width / 7, h), CornerRadius(1.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(label, textAlign = TextAlign.Center, color = ink,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 14.sp else 15.sp, lineHeight = 17.sp))
        }
        val tickPosition = if (compact || rank != null) Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp)
            else Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
        if (active) Canvas(tickPosition.size(11.dp, 8.dp)) {
            val mid = Offset(size.width * .32f, size.height * .86f)
            drawLine(ink, Offset(0f, size.height * .45f), mid, 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, mid, Offset(size.width, 0f), 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun AnswerGrid(question: String, labels: List<String>, selected: Int?, columns: Int = 2,
                       bars: Boolean = false, onChoice: (Int) -> Unit) {
    BoxWithConstraints {
        val scale = LocalDensity.current.fontScale
        val minimum = (if (columns == 5) 60 else 118) * scale
        val cols = (maxWidth.value / minimum).toInt().coerceIn(1, columns)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.indices.chunked(cols).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (cols == 5) 4.dp else 6.dp),
                    modifier = Modifier.height(IntrinsicSize.Min)) {
                    indices.forEach { i ->
                        AnswerTile(question, labels[i], selected == i, Modifier.weight(1f).fillMaxHeight(),
                            if (bars) i else null, compact = columns == 5) { onChoice(i) }
                    }
                    repeat(cols - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun DetailLink(label: String, trailing: String = "›", icon: Int? = null, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(colors.primary.copy(alpha = if (pressed) .10f else 0f))
        .clickable(interactionSource = interactions, indication = LocalIndication.current,
            role = Role.Button, onClick = onClick).heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(trailing, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
