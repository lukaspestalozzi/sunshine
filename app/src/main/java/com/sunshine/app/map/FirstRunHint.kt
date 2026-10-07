package com.sunshine.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.sunshine.app.R

/**
 * The hint shown until the user taps `Got it` ([onDismiss]): what the crosshair is, tap to centre,
 * and the overlay modes with the toggle's own icons (map-view spec, "First-run hint"; design D9 of
 * polish-ui). It sits above the panel, so it covers neither the crosshair, the toggle nor the tape.
 */
@Composable
fun FirstRunHint(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes =
        buildAnnotatedString {
            appendInlineContent(SHADE_ICON, "◐")
            append(" ")
            append(stringResource(R.string.hint_modes_shade))
            append(" ")
            appendInlineContent(HOURS_ICON, "◔")
            append(" ")
            append(stringResource(R.string.hint_modes_hours))
        }
    val icons =
        mapOf(
            SHADE_ICON to inlineIcon(R.drawable.ic_contrast),
            HOURS_ICON to inlineIcon(R.drawable.ic_timelapse),
        )
    Surface(modifier, color = floatingSurfaceColor(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.hint_crosshair), style = MaterialTheme.typography.bodyMedium)
            Text(modes, inlineContent = icons, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.hint_got_it)) }
            }
        }
    }
}

// An icon the size of a line of text.
private fun inlineIcon(icon: Int) =
    InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
        Icon(painterResource(icon), contentDescription = null)
    }

private const val SHADE_ICON = "shade"
private const val HOURS_ICON = "hours"
