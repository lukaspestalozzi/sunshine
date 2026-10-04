package com.sunshine.app.about

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunshine.app.BuildConfig
import com.sunshine.app.R
import com.sunshine.app.elevation.MapterhornTiles

/** One line of the About section: [text] (formatted with [argument]), opening [url] when tapped. */
data class AboutEntry(
    @StringRes val text: Int,
    val argument: String? = null,
    val url: String? = null,
)

/** The About section's lines (map-view spec, "About and attributions"; design D1 of add-settings). */
fun aboutEntries(versionName: String): List<AboutEntry> =
    listOf(
        AboutEntry(R.string.about_version, argument = versionName),
        AboutEntry(R.string.map_attribution, url = OSM_COPYRIGHT_URL),
        AboutEntry(R.string.elevation_attribution, url = MapterhornTiles.ATTRIBUTION_URL),
        AboutEntry(R.string.about_icons),
    )

/** App version, attributions with their links, icon credit: the last section of the Settings page. */
@Composable
fun AboutSection(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (entry in aboutEntries(BuildConfig.VERSION_NAME)) {
            val text = entry.argument?.let { stringResource(entry.text, it) } ?: stringResource(entry.text)
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = entry.url?.let { url -> Modifier.clickable { uriHandler.openUri(url) } } ?: Modifier,
            )
        }
    }
}

private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
