package ua.sprotyv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach

/** One messenger for when the network is down: what it is, its limits said plainly, and a way to it. */
private class Messenger(val pkg: String, val site: String, val title: Int, val facts: List<Int>, val source: Int)

private val MESSENGERS = listOf(
    Messenger("org.briarproject.briar.android", "https://briarproject.org/", R.string.comms_briar,
        listOf(R.string.comms_briar_1, R.string.comms_briar_2, R.string.comms_briar_3, R.string.comms_briar_4), R.string.comms_briar_source),
    Messenger("com.geeksville.mesh", "https://meshtastic.org/", R.string.comms_mesh,
        listOf(R.string.comms_mesh_1, R.string.comms_mesh_2, R.string.comms_mesh_3, R.string.comms_mesh_4), R.string.comms_mesh_source),
)

/**
 * "When the network is down": two ways to send words without towers — phone to phone (Briar) and
 * small radios (Meshtastic). Sprotyv only explains and opens them: no network, Bluetooth or radio
 * of its own, so its data stays out of reach. Set up in advance, while the network still works.
 */
@Composable
fun CommsScreen(nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.comms_title), nav::back) {
        Text(stringResource(R.string.comms_intro), style = ReadingText)
        // In advance: the one thing that cannot be done later
        Block(stringResource(R.string.comms_before), Group.SAFETY) {
            listOf(R.string.comms_before_1, R.string.comms_before_2, R.string.comms_before_3).forEach {
                Row { Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(it), style = ReadingText, modifier = Modifier.weight(1f)) }
            }
            OutlinedButton(onClick = { nav.go(Route.Mine(MinePart.PLAN)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.comms_to_plan)) }
        }
        MESSENGERS.forEach { m ->
            Block(stringResource(m.title), Group.SAFETY, foldable = true) {
                m.facts.forEach {
                    Row { Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(it), style = ReadingText, modifier = Modifier.weight(1f)) }
                }
                val there = androidx.compose.runtime.remember(m.pkg) { Reach.installed(context, m.pkg) }
                OutlinedButton(
                    onClick = { if (!there || !Reach.launch(context, m.pkg)) Reach.open(context, m.site, toolbarColor = toolbar) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(if (there) R.string.comms_open else R.string.comms_where)) }
                Text(stringResource(m.source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.comms_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
