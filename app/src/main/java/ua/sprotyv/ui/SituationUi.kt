package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Link
import ua.sprotyv.reach.Reach

private const val FOCUS_PAGE = "https://play.google.com/store/apps/details?id=org.mozilla.focus"

/** Opens a source: a phone number in the dialler, an address in its own app or a Custom Tab. */
fun openLink(context: Context, link: Link, toolbar: Int? = null) {
    if (link.url.isEmpty() && link.phone.isNotEmpty()) Reach.dial(context, link.phone)
    else Reach.open(context, link.url, link.app, toolbar)
}

private val Link.groupTitle: Int get() = when (group) {
    "threats" -> R.string.situation_threats
    "front" -> R.string.situation_front
    "help" -> R.string.situation_help
    else -> R.string.situation_facts
}

private val Link.kindTitle: Int get() = when (kind) {
    Link.OFFICIAL -> R.string.kind_official
    Link.VOLUNTEER -> R.string.kind_volunteer
    else -> R.string.kind_independent
}

/**
 * "Situation": where to look, and how to read it. Sprotyv collects nothing itself: it leads to the
 * best sources, says who runs each one and how far to trust it, and opens them outside the app.
 * The rules for reading a threat map come first, offline, because the map never replaces the alert.
 */
@Composable
fun SituationScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.situation_title), nav::back) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.situation_rules_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                listOf(R.string.situation_rule_1, R.string.situation_rule_2, R.string.situation_rule_3).forEachIndexed { i, r ->
                    Text("${i + 1}. " + stringResource(r), style = MaterialTheme.typography.bodyLarge)
                }
                Text(stringResource(R.string.situation_rules_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (content == null) return@Frame Loading()
        if (content.linksBroken) return@Frame Broken()
        content.links.groupBy { it.group }.forEach { (_, links) ->
            Text(stringResource(links.first().groupTitle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
            links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
        }
        // Said plainly: whether this phone's browser keeps a trace of the pages
        // Worked out again on every return, e.g. after installing Firefox Focus
        var trace by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Reach.trace(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            trace = Reach.trace(context)
            onPauseOrDispose { }
        }
        Text(
            stringResource(
                when (trace) {
                    Reach.Trace.FOCUS -> R.string.situation_focus
                    Reach.Trace.FOCUS_NOT_FROM_PLAY -> R.string.situation_focus_other
                    Reach.Trace.CHROME_TAB -> R.string.situation_no_trace
                    Reach.Trace.BROWSER -> R.string.situation_trace
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trace != Reach.Trace.FOCUS && trace != Reach.Trace.FOCUS_NOT_FROM_PLAY && Reach.hasPlay(context)) {
            // Any phone, any main browser: Firefox Focus erases pages on closing
            androidx.compose.material3.TextButton(
                onClick = { Reach.open(context, FOCUS_PAGE, toolbarColor = toolbar) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.situation_get_focus)) }
        }
        Text(stringResource(R.string.situation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkRow(link: Link, onClick: () -> Unit) {
    val official = link.kind == Link.OFFICIAL
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(link.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // The kind in words, never by colour alone
                Surface(
                    color = if (official) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(stringResource(link.kindTitle), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Text(link.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (link.phone.isNotEmpty() && link.url.isEmpty()) stringResource(R.string.call_number, link.phone) else ua.sprotyv.content.LinksReader.host(link.url),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
