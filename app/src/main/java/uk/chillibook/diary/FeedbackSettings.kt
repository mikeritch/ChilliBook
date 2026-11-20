package uk.chillibook.diary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class FeedbackOption {
    EverydaySound, EverydayHaptics, CelebrationSound, CelebrationHaptics
}

private val hapticSettingName: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        "Media vibration"
    } else {
        "Touch feedback"
    }

@Composable
internal fun FeedbackSettings(
    enabled: Set<FeedbackOption>,
    onChanged: (FeedbackOption, Boolean) -> Unit,
    preview: (FeedbackOption) -> Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        QuestionText("Feedback")
        AndroidFeedbackSettings()
        FeedbackGroup(
            "Everyday feedback", FeedbackOption.EverydaySound, FeedbackOption.EverydayHaptics,
            enabled, onChanged, preview
        )
        FeedbackGroup(
            "End-of-day celebration", FeedbackOption.CelebrationSound, FeedbackOption.CelebrationHaptics,
            enabled, onChanged, preview
        )
    }
}

@Composable
private fun AndroidFeedbackSettings() {
    val context = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(
            onClick = {
                openFailed = try {
                    context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                    false
                } catch (_: ActivityNotFoundException) {
                    true
                } catch (_: SecurityException) {
                    true
                }
            },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Android sound & vibration settings")
        }
        Text(
            if (openFailed) {
                "Open Android Settings and look for $hapticSettingName."
            } else {
                "Haptics follow Android’s $hapticSettingName setting."
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedbackGroup(
    title: String,
    sound: FeedbackOption,
    haptics: FeedbackOption,
    enabled: Set<FeedbackOption>,
    onChanged: (FeedbackOption, Boolean) -> Unit,
    preview: (FeedbackOption) -> Boolean
) {
    var hapticPreviewRejected by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        FeedbackRow(title, "Sound effects", sound in enabled, { onChanged(sound, it) }) { preview(sound) }
        FeedbackRow(title, "Haptics", haptics in enabled, { onChanged(haptics, it) }) {
            hapticPreviewRejected = !preview(haptics)
        }
        if (hapticPreviewRejected) {
            Text(
                "Haptic preview unavailable. Check $hapticSettingName in Android Settings.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeedbackRow(
    group: String,
    label: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    preview: () -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChanged)
                .semantics { contentDescription = "$group, $label" },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
        val previewLabel = "Preview ${label.lowercase()}"
        OutlinedButton(onClick = preview, modifier = Modifier.heightIn(min = 48.dp)
            .semantics { contentDescription = "$group, $previewLabel" }) {
            Text(previewLabel)
        }
    }
}
