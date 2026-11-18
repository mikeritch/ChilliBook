package uk.chillibook.diary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

internal fun newlyCompletedDay(before: Diary, after: Diary, date: String): Boolean {
    fun Diary.complete() = answeredReminder(date, "am") && answeredReminder(date, "pm")
    return after.complete() && !before.complete()
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
internal fun DayThanksHeading(title: String, earned: Boolean, animation: Animatable<Float, AnimationVector1D>?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (earned) {
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
                Text("Thanks for checking in!", color = colors.onSecondaryContainer, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(title, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}
