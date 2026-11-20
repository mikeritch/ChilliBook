package uk.chillibook.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

internal fun newlyCompletedDay(before: Diary, after: Diary, date: String): Boolean {
    fun Diary.complete() = answeredReminder(date, "am") && answeredReminder(date, "pm")
    return after.complete() && !before.complete()
}

internal fun Diary.checkInStreak(date: String, today: String): Int {
    val morning = mutableSetOf<String>()
    val evening = mutableSetOf<String>()
    fun addSlot(day: String, slot: String) {
        when (slot) {
            "am" -> morning.add(day)
            "pm" -> evening.add(day)
        }
    }
    checks.forEach { if (it.hasSlotAnswer()) addSlot(it.date, it.slot) }
    doses.forEach { addSlot(it.date, it.slot) }
    activities.mapTo(evening) { it.date }
    days.forEach { if (it.hasEveningAnswer()) evening.add(it.date) }

    fun complete(day: String) = day in morning && day in evening
    var cursor = LocalDate.parse(date)
    if (date == today && !complete(date)) cursor = cursor.minusDays(1)
    var count = 0
    while (complete(cursor.toString())) {
        count++
        cursor = cursor.minusDays(1)
    }
    return count
}

@Composable
internal fun rememberDayCelebration(date: String?, completions: Flow<String>): Animatable<Float, AnimationVector1D>? {
    var animation by remember(date) { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }
    LaunchedEffect(date, completions) {
        if (date != null) completions.filter { it == date }.collectLatest {
            val progress = Animatable(0f)
            animation = progress
            try {
                progress.animateTo(1f, tween(4400, easing = LinearEasing))
            } finally {
                animation = null
            }
        }
    }
    return animation
}

@Composable
internal fun DayThanksHeading(title: String, earned: Boolean, streak: Int, animation: Animatable<Float, AnimationVector1D>?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (earned || streak > 0) {
            Row(
                Modifier.background(colors.secondaryContainer, RoundedCornerShape(24.dp))
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BloomingFlower(
                    progress = { FastOutSlowInEasing.transform(((animation?.value ?: 1f) / .29f).coerceAtMost(1f)) },
                    modifier = Modifier.size(44.dp)
                )
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(if (earned) "Thanks for checking in!" else title, color = colors.onSecondaryContainer,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.size(12.dp).graphicsLayer {
                                val progress = ((animation?.value ?: 1f) / .35f).coerceIn(0f, 1f)
                                scaleX = .55f + .45f * progress
                                scaleY = scaleX
                                rotationZ = -30f * (1f - progress)
                            }) {
                                val radius = size.minDimension / 2f
                                val middle = radius * .3f
                                val sparkle = Path().apply {
                                    moveTo(radius, 0f)
                                    quadraticTo(radius + middle, radius - middle, size.width, radius)
                                    quadraticTo(radius + middle, radius + middle, radius, size.height)
                                    quadraticTo(radius - middle, radius + middle, 0f, radius)
                                    quadraticTo(radius - middle, radius - middle, radius, 0f)
                                    close()
                                }
                                drawPath(sparkle, colors.primary)
                            }
                            Text("$streak-day streak", color = colors.onSecondaryContainer,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            Text(title, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}
