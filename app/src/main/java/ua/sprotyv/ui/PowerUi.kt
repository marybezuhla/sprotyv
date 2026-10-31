package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ua.sprotyv.R
import ua.sprotyv.content.Blackout
import ua.sprotyv.content.Charge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "The power went": one tap keeps the moment, and the screen counts the hours and says what that
 * means for the fridge and the freezer. Only a time is kept, on this phone, outside the encrypted
 * data on purpose (like the tourniquet time), so it works at once; it is gone after "power is back"
 * or after three days.
 */
@Composable
fun BlackoutBlock() {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    var since by remember {
        val t = prefs.getLong("since", 0L)
        val stale = t == 0L || System.currentTimeMillis() - t > 72 * 3600_000L || t > System.currentTimeMillis()
        if (stale && t != 0L) prefs.edit().remove("since").apply()
        mutableLongStateOf(if (stale) 0L else t)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since != 0L) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Block(stringResource(R.string.block_power), ua.sprotyv.home.Group.HOME_LIFE) {
        run {
            if (since == 0L) {
                Text(stringResource(R.string.blackout_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.blackout_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    val t = System.currentTimeMillis()
                    prefs.edit().putLong("since", t).commit()
                    Notify.sync(context)
                    now = t
                    since = t
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.blackout_start), style = MaterialTheme.typography.titleMedium)
                }
                return@run
            }
            val minutes = ((now - since) / 60_000).coerceAtLeast(0)
            val at = Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(
                stringResource(R.string.blackout_for, duration(minutes), at),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Keep(R.string.blackout_fridge, Blackout.FRIDGE_HOURS, minutes, R.string.blackout_fridge_over)
            Keep(R.string.blackout_freezer_full, Blackout.FREEZER_FULL_HOURS, minutes, R.string.blackout_freezer_over)
            Keep(R.string.blackout_freezer_half, Blackout.FREEZER_HALF_HOURS, minutes, R.string.blackout_freezer_over)
            Text(stringResource(R.string.blackout_closed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {
                prefs.edit().remove("since").commit()
                Notify.sync(context)
                since = 0L
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.blackout_back)) }
            Text(
                stringResource(R.string.blackout_source, Blackout.SOURCE, Blackout.CHECKED) + " · " + stringResource(R.string.not_reviewed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One thing that keeps cold for a while: how much of that time is gone, and what is left. */
@Composable
private fun Keep(title: Int, hours: Int, minutes: Long, over: Int) {
    val left = Blackout.minutesLeft(minutes, hours)
    val name = stringResource(title, hours)
    if (left > 0) {
        Meter(name, stringResource(R.string.blackout_left, duration(left)), minutes.toFloat() / (hours * 60))
    } else {
        // Time is up: said in words, in the calm warning colour, never as an alarm
        Meter(name, stringResource(R.string.blackout_up), 1f, MaterialTheme.colorScheme.tertiary)
        Text(stringResource(over), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun duration(minutes: Long): String {
    val h = (minutes / 60).toInt()
    val m = (minutes % 60).toInt()
    return if (h == 0) stringResource(R.string.minutes_short, m) else stringResource(R.string.hours_minutes_short, h, m)
}

/**
 * "My charge": pick a power bank or a station and its size, and see roughly how long it runs a
 * phone, a lamp, a router or a laptop. The watts are shown, so the numbers can be checked.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ChargeBlock() {
    var station by rememberSaveable { mutableStateOf(false) }
    var size by rememberSaveable { mutableIntStateOf(Charge.BANKS[1]) }
    Block(stringResource(R.string.charge_title), ua.sprotyv.home.Group.HOME_LIFE) {
        run {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !station, onClick = { station = false; size = Charge.BANKS[1] }, label = { Text(stringResource(R.string.charge_bank)) }, modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = station, onClick = { station = true; size = Charge.STATIONS[1] }, label = { Text(stringResource(R.string.charge_station)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (if (station) Charge.STATIONS else Charge.BANKS).forEach { v ->
                    FilterChip(
                        selected = size == v,
                        onClick = { size = v },
                        label = { Text(stringResource(if (station) R.string.charge_wh else R.string.charge_mah, "%,d".format(v).replace(',', ' '))) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            val wh = if (station) Charge.stationWh(size) else Charge.bankWh(size)
            val phones = Charge.phoneCharges(wh)
            Line(stringResource(R.string.charge_phone), pluralStringResource(R.plurals.charge_charges, phones, phones))
            Charge.Thing.entries.filter { station || it.onBank }.forEach { t ->
                val h = Charge.hours(wh, t.watts)
                val title = stringResource(
                    when (t) {
                        Charge.Thing.ROUTER -> R.string.charge_router
                        Charge.Thing.LAMP -> R.string.charge_lamp
                        Charge.Thing.LAPTOP -> R.string.charge_laptop
                    },
                    t.watts,
                )
                Line(title, if (h == 0) stringResource(R.string.charge_under_hour) else pluralStringResource(R.plurals.charge_hours, h, h))
            }
            Text(stringResource(R.string.charge_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Line(title: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("≈ $value", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * "Wait at least 40 minutes": one tap starts the count, so no one has to watch the clock in the
 * corridor. Ends with the card's own words: a repeated strike can come even later, so the all-clear
 * decides. Only a time is kept, on this phone, like the tourniquet time; gone after three hours.
 */
@Composable
fun WaitTimer(total: Int) {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("wait", Context.MODE_PRIVATE) }
    var since by remember {
        val t = prefs.getLong("since", 0L)
        val stale = t == 0L || System.currentTimeMillis() - t > 3 * 3600_000L || t > System.currentTimeMillis()
        if (stale && t != 0L) prefs.edit().remove("since").apply()
        mutableLongStateOf(if (stale) 0L else t)
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since != 0L) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    if (since == 0L) {
        OutlinedButton(onClick = {
            val t = System.currentTimeMillis()
            prefs.edit().putLong("since", t).putInt("total", total).commit()
            Notify.sync(context)
            now = t
            since = t
        }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.wait_start, total)) }
        return
    }
    val gone = ((now - since) / 60_000).coerceAtLeast(0)
    val left = (total - gone).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (left > 0) {
            Meter(stringResource(R.string.wait_left, left), stringResource(R.string.wait_of, gone, total), gone.toFloat() / total)
        } else {
            Text(stringResource(R.string.wait_done, total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        androidx.compose.material3.TextButton(onClick = {
            prefs.edit().remove("since").commit()
            Notify.sync(context)
            since = 0L
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wait_reset)) }
    }
}
