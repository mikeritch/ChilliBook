package uk.chillibook.diary

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun PatternsAiSharePreview(share: PatternsAiShare, start: LocalDate, end: LocalDate, back: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var details by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    BackHandler(onBack = back)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item("header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = back) { Text("‹  Patterns") }
                Text("Share to AI App", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Serif)
                Text("${share.rows.size} days · ${start.format(dateFormat)} – ${end.format(dateFormat)}",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item("prompt") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What you’ll share", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(share.prompt, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item("actions") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("This includes personal diary data. Choose an app you trust.",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                PatternsShareActions(
                    first = { modifier ->
                        OutlinedButton(onClick = {
                            val clip = ClipData.newPlainText("ChilliBook diary", share.text).apply {
                                description.extras = PersistableBundle().apply {
                                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                                }
                            }
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                            message = "Copied to clipboard"
                        }, modifier = modifier) { Text("Copy for AI") }
                    },
                    second = { modifier ->
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, share.text)
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (_: ActivityNotFoundException) {
                                message = "Sharing is unavailable on this device"
                            }
                        }, modifier = modifier) { Text("Share") }
                    }
                )
                message?.let {
                    Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item("entries") {
            OutlinedButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth()) {
                Text(if (details) "Hide daily entries" else "View ${share.rows.size} daily entries")
            }
        }
        if (details) {
            items(share.rows) { row ->
                Text(row, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun PatternsShareActions(
    first: @Composable (Modifier) -> Unit,
    second: (@Composable (Modifier) -> Unit)?
) {
    val large = LocalDensity.current.fontScale > 1.35f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (second == null) {
            first(Modifier.heightIn(min = 48.dp))
        } else if (large || maxWidth < 290.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val button = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                first(button)
                second(button)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val button = Modifier.heightIn(min = 48.dp)
                first(button)
                second(button)
            }
        }
    }
}
