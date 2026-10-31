package ua.sprotyv.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.sprotyv.R
import ua.sprotyv.home.Group

/** One count running now: what it is, for how long, its colour, where it opens. */
private class Running(val words: String, val tone: Group, val route: Route)

/**
 * "Running now", at the top of the home screen by itself: every count started anywhere (the
 * tourniquet time, the wait after a strike, the shelter, no power, no heating), live, one tap to
 * its screen. Nothing to set up: it is there while something runs and gone when it stops.
 */
@Composable
fun ActiveStrip(nav: Nav) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val list = running(context, now)
    if (list.isEmpty()) return
    val u = urgent()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, u.edge.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(R.string.active_title), style = BlockTitle, color = u.ink,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        )
        // Each count on its own plate, so several running at once never read as one
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { r ->
                // Plain plates with the dot of their colour; only danger now has its shade and its words in colour
                val t = r.tone.tint()
                val now = r.tone == Group.DANGER
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (now) t.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { nav.go(r.route) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(t.ink, CircleShape))
                    Text(r.words, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (now) t.ink else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (now) t.ink else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun running(context: Context, now: Long): List<Running> {
    val out = ArrayList<Running>()
    fun prefs(name: String) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    // Tourniquet: a day at most, as on the card
    Tourniquets.all(context, now).let { list ->
        val first = list.firstOrNull() ?: return@let
        val words = if (list.size == 1) stringResource(R.string.active_tourniquet, span((now - first.at) / 60_000))
            else stringResource(R.string.active_tourniquets, list.size, span((now - first.at) / 60_000))
        out += Running(words, Group.DANGER, Route.CardRoute("bleeding"))
    }
    // The wait after a strike: three hours at most, as on the card
    prefs("wait").let { p ->
        val t = p.getLong("since", 0L)
        if (t > 0 && now - t in 0 until 3 * 3600_000L) {
            val total = p.getInt("total", 40)
            val left = (total - (now - t) / 60_000).coerceAtLeast(0)
            out += Running(
                if (left > 0) stringResource(R.string.active_wait, left) else stringResource(R.string.active_wait_done, total),
                Group.DANGER, Route.Open(Section.NOW),
            )
        }
    }
    Since.SHELTER.get(context, now).takeIf { it != 0L }?.let { out += Running(stringResource(R.string.tile_shelter, span((now - it) / 60_000)), Group.DANGER, Route.Open(Section.ALARM)) }
    Since.POWER.get(context, now).takeIf { it != 0L }?.let { out += Running(stringResource(R.string.tile_power_out, span((now - it) / 60_000)), Group.GUIDE, Route.Power) }
    Since.HEAT.get(context, now).takeIf { it != 0L }?.let { out += Running(stringResource(R.string.active_heat, span((now - it) / 60_000)), Group.GUIDE, Route.Power) }
    return out
}
