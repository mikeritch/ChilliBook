package uk.chillibook.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
    val icon = when (text) {
        "How much energy do you have now?" -> R.drawable.diary_spark
        "How refreshed did you feel on waking?" -> R.drawable.diary_pillow
        "How did today feel overall?" -> R.drawable.diary_bloom
        "What did you do?" -> R.drawable.diary_sprout
        "Symptoms" -> R.drawable.symbook
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon?.let { DiaryIcon(it, when (it) {
            R.drawable.diary_spark -> categoryAccent("Household")
            R.drawable.diary_pillow -> categoryAccent("Work")
            R.drawable.symbook, R.drawable.diary_bloom -> categoryAccent("Symptoms")
            else -> MaterialTheme.colorScheme.primary
        }) }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    }
}

// A quick tap inside a scrolling column can finish before the delayed press indication.
@Composable
private fun selectionScale(selected: Boolean, pressed: Boolean): Float {
    val press by animateFloatAsState(if (pressed) .94f else 1f, tween(85), label = "press")
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (previous != selected) {
            previous = selected
            pulse.snapTo(.90f)
            pulse.animateTo(1.025f, tween(125))
            pulse.animateTo(1f, tween(135))
        }
    }
    return press * pulse.value
}

@Composable
internal fun AnswerTile(question: String, label: String, active: Boolean, modifier: Modifier = Modifier,
                        rank: Int? = null, compact: Boolean = false, accent: Color? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tone = accent ?: colors.primary
    val shape = RoundedCornerShape(12.dp)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shrink = selectionScale(active, pressed)
    val pastel = accent != null && colors.background.luminance() > .5f && tone.luminance() > .36f
    val fill = when {
        active && accent == null && colors.background.luminance() > .5f -> colors.primaryContainer
        active -> tone
        else -> tone.copy(alpha = if (pastel) .40f else if (accent != null) .13f else .06f).compositeOver(colors.surface)
    }
    val ink = if (active && fill.luminance() <= .36f) Color.White else if (active) Color(0xFF192421) else colors.onSurface
    val edge = if (pastel && active) ink.copy(alpha = .45f) else if (active) tone else tone.copy(alpha = .22f)
    val background by animateColorAsState(if (pressed) ink.copy(alpha = .18f).compositeOver(fill) else fill,
        tween(120), label = "answer fill")
    Box(modifier.clip(shape)
        .selectable(active, interactionSource = interactions, indication = LocalIndication.current,
            role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = "$question, $label" }
        .graphicsLayer { scaleX = shrink; scaleY = shrink }.background(background, shape)
        .border(if (pastel && active) 2.dp else 1.dp, edge, shape)
        .heightIn(min = if (rank == null) 48.dp else 68.dp),
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = if (compact) 3.dp else 12.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (rank != null) {
                Canvas(Modifier.size(28.dp, 12.dp)) {
                    for (i in 0..4) {
                        val h = size.height * (0.22f + i * 0.17f)
                        drawRoundRect(if (i <= rank) (if (active || pastel) ink else tone) else tone.copy(alpha = .25f),
                            Offset(i * size.width / 5, size.height - h), Size(size.width / 7, h), CornerRadius(1.dp.toPx()))
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            Text(label, textAlign = TextAlign.Center, color = ink,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 14.sp else 15.sp, lineHeight = 17.sp))
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.indices.chunked(cols).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (cols == 5) 6.dp else 8.dp),
                    modifier = Modifier.height(IntrinsicSize.Min)) {
                    indices.forEach { i ->
                        AnswerTile(question, labels[i], selected == i, Modifier.weight(1f).fillMaxHeight(),
                            if (bars) i else null, compact = columns == 5, accent = scaleAccent(labels, i)) { onChoice(i) }
                    }
                    repeat(cols - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun DetailLink(label: String, trailing: String = "›", icon: Int? = null, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(trailing, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
    }
}

@Composable
internal fun DiaryChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
                       symbol: String? = null, accent: Color = MaterialTheme.colorScheme.primary,
                       remove: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale = selectionScale(selected, pressed)
    val shape = RoundedCornerShape(15.dp)
    val colors = MaterialTheme.colorScheme
    val alpha = when { pressed -> .40f; selected -> .24f; else -> .06f }
    val fill by animateColorAsState(accent.copy(alpha = alpha).compositeOver(colors.surface),
        tween(145), label = "chip fill")
    val symbolAngle by animateFloatAsState(if (remove) 180f else 0f, tween(210), label = "chip turn")
    Row(modifier.heightIn(min = 48.dp).clip(shape)
        .toggleable(value = selected, interactionSource = interactions, indication = LocalIndication.current,
            role = Role.Checkbox, onValueChange = { onClick() })
        .graphicsLayer { scaleX = scale; scaleY = scale }.background(fill, shape)
        .border(1.dp, accent.copy(alpha = if (selected) .75f else .15f), shape).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (symbol != null) {
            Box(Modifier.width(18.dp).graphicsLayer { rotationZ = symbolAngle }, contentAlignment = Alignment.Center) {
                Text(symbol, color = if (remove) colors.error else accent, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) accent else colors.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}
