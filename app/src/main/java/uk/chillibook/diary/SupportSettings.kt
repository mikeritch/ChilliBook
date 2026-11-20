package uk.chillibook.diary

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val DEVELOPER_TIP_URL = "https://ko-fi.com/michanism"
private const val CHARITY_DONATION_URL = "https://www.paypal.com/GB/fundraiser/charity/3266379"

@Composable
internal fun SupportSettings() {
    val context = LocalContext.current
    var linkFailed by remember { mutableStateOf(false) }

    fun openLink(url: String) {
        linkFailed = try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            false
        } catch (_: ActivityNotFoundException) {
            true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.diary_bloom),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.secondary,
            )
            Text(
                "A little kindness",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        SupportButton(
            label = "Thank the developer",
            detail = "Support the developer on Ko-fi",
            icon = R.drawable.cups,
        ) {
            openLink(DEVELOPER_TIP_URL)
        }
        SupportButton(
            label = "Donate to Action for ME",
            detail = "Support the charity via PayPal",
            icon = R.drawable.diary_sprout,
        ) {
            openLink(CHARITY_DONATION_URL)
        }
        if (linkFailed) {
            Text(
                "No app could open the link.",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SupportButton(label: String, detail: String, icon: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
