package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.sprotyv.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Since when": one moment kept on this phone for a running count (the power, the heating, the
 * shelter). Only a time, outside the encrypted data on purpose, so it works at once and shows on
 * the home screen; gone when stopped or after [maxHours].
 */
class Since(private val name: String, private val maxHours: Int) {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    /** The moment, or 0 when nothing is running (a stale or future time is cleared). */
    fun get(context: Context, now: Long = System.currentTimeMillis()): Long {
        val t = prefs(context).getLong("since", 0L)
        if (t == 0L) return 0L
        if (now - t > maxHours * 3600_000L || t > now + 60_000L) {
            prefs(context).edit().remove("since").apply()
            return 0L
        }
        return t
    }

    fun start(context: Context): Long = System.currentTimeMillis().also { prefs(context).edit().putLong("since", it).commit(); Notify.sync(context) }
    fun stop(context: Context) { prefs(context).edit().remove("since").commit(); Notify.sync(context) }

    companion object {
        val POWER = Since("blackout", 72)
        val HEAT = Since("heating", 30 * 24)
        val SHELTER = Since("shelter", 48)
    }
}

/** Days, hours and minutes in short words: "2 д 4 год", "3 год 10 хв", "25 хв". */
@Composable
fun span(minutes: Long): String {
    val d = (minutes / 1440).toInt()
    val h = ((minutes % 1440) / 60).toInt()
    val m = (minutes % 60).toInt()
    return when {
        d > 0 -> stringResource(R.string.days_hours_short, d, h)
        h > 0 -> stringResource(R.string.hours_minutes_short, h, m)
        else -> stringResource(R.string.minutes_short, m)
    }
}

/** A running count: the moment, a ticking "now", and start/stop that also remember. */
@Composable
private fun rememberSince(since: Since, tick: Long): Pair<Long, Long> {
    val context = LocalContext.current
    var start by remember { mutableLongStateOf(since.get(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (start != 0L) {
            now = System.currentTimeMillis()
            delay(tick)
        }
    }
    return start to now
}

private fun clock(t: Long, withDate: Boolean): String =
    Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (withDate) "dd.MM HH:mm" else "HH:mm"))

/**
 * "No heating": counts the days and says what the guide says about them (20.5): pipes can freeze
 * in 1-3 days at -5...-10° outside, so get ready to drain them; below +5° indoors for more than two
 * days is a reason for families with children, older or ill people to leave; fuel heaters only
 * with a flue. Not reviewed by a specialist yet.
 */
@Composable
fun HeatingBlock(nav: Nav) {
    val context = LocalContext.current
    var gen by remember { mutableLongStateOf(0L) }
    val (start, now) = androidx.compose.runtime.key(gen) { rememberSince(Since.HEAT, 60_000) }
    CounterCard(stringResource(R.string.block_heat)) {
        if (start == 0L) {
            Text(stringResource(R.string.heat_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.heat_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { Since.HEAT.start(context); gen++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.heat_start), style = MaterialTheme.typography.titleMedium)
            }
            return@CounterCard
        }
        val minutes = ((now - start) / 60_000).coerceAtLeast(0)
        Text(
            stringResource(R.string.heat_for, span(minutes), clock(start, true)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        // The guide's two time marks, shown as bars towards them
        Meter(stringResource(R.string.heat_pipes), stringResource(R.string.heat_pipes_when), (minutes / (24 * 60f)).coerceAtMost(1f), if (minutes >= 24 * 60) ua.sprotyv.home.Group.DANGER.tint().ink else MaterialTheme.colorScheme.primary)
        Advice(stringResource(R.string.heat_pipes_do), minutes >= 24 * 60)
        Meter(stringResource(R.string.heat_leave), stringResource(R.string.heat_leave_when), (minutes / (48 * 60f)).coerceAtMost(1f), if (minutes >= 48 * 60) ua.sprotyv.home.Group.DANGER.tint().ink else MaterialTheme.colorScheme.primary)
        Advice(stringResource(R.string.heat_leave_do), minutes >= 48 * 60)
        Advice(stringResource(R.string.heat_co), false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Since.HEAT.stop(context); gen++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.heat_back)) }
            TextButton(onClick = { nav.go(Route.GuideSection("20.5")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.in_detail)) }
        }
        SourceLine("20.5")
    }
}

/**
 * "In the shelter": how long, from one tap, with the guide's few rules for a long stay (3.7):
 * mats and blankets (concrete draws the heat out), air it in the pauses (a candle going out means
 * too little air), share the duties. Not reviewed by a specialist yet.
 */
@Composable
fun ShelterBlock(nav: Nav) {
    val context = LocalContext.current
    var gen by remember { mutableLongStateOf(0L) }
    val (start, now) = androidx.compose.runtime.key(gen) { rememberSince(Since.SHELTER, 30_000) }
    CounterCard(stringResource(R.string.block_shelter), ua.sprotyv.home.Group.DANGER) {
        if (start == 0L) {
            Text(stringResource(R.string.shelter_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Button(onClick = { Since.SHELTER.start(context); gen++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.shelter_start), style = MaterialTheme.typography.titleMedium)
            }
            return@CounterCard
        }
        val minutes = ((now - start) / 60_000).coerceAtLeast(0)
        Text(
            stringResource(R.string.shelter_for, span(minutes), clock(start, minutes >= 12 * 60)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        // A longer stay: the guide's rules for it
        if (minutes >= 60) {
            Advice(stringResource(R.string.shelter_air), false)
            Advice(stringResource(R.string.shelter_warm), false)
            Advice(stringResource(R.string.shelter_duties), false)
        } else {
            Text(stringResource(R.string.shelter_short), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Since.SHELTER.stop(context); gen++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.shelter_out)) }
            TextButton(onClick = { nav.go(Route.GuideSection("3.7")) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.in_detail)) }
        }
        SourceLine("3.7")
    }
}

@Composable
private fun CounterCard(title: String, group: ua.sprotyv.home.Group = ua.sprotyv.home.Group.HOME_LIFE, content: @Composable () -> Unit) {
    Block(title, group) { content() }
}

/** One rule: bold when its time has come, in the calm warning colour; never red. */
@Composable
private fun Advice(text: String, now: Boolean) {
    Text(
        "• $text",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
        color = if (now) ua.sprotyv.home.Group.DANGER.tint().ink else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SourceLine(section: String) {
    Text(
        stringResource(R.string.counter_source, section) + " · " + stringResource(R.string.not_reviewed),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
