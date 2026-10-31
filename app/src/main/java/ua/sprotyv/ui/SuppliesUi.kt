package ua.sprotyv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Kit
import ua.sprotyv.content.MySupplies
import ua.sprotyv.content.Supplies
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting
import java.time.YearMonth

const val KEY_SUPPLIES = "supplies"

/** A value read from the store, possibly null; the wrapper itself is null only before the first read. */
private class Read(val value: String?)

/**
 * Home supplies: the guide's norms (2.5) for the household and goal, and what is already at home.
 * On top, one block answers one question, "how long will it last?": water in days, food as lines
 * in place, and what to replace soon. Bars, never red: this is preparation, not danger.
 * What is at home is the owner's data, encrypted with the rest of "Mine".
 */
@Composable
fun SuppliesScreen(content: Content?, nav: Nav) {
    if (content == null) return Loading()
    if (content.guideBroken) return Broken()
    // Wrapped, so "not read yet" (null) is told apart from "nothing saved yet": no tap before the first read
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_SUPPLIES).map { Read(it) } } }.collectAsStateWithLifecycle(null)
    val s = MySupplies.fromJson((stored ?: return Loading()).value)
    // Each change is applied to what is stored at the moment of writing, in order, under its own key:
    // two quick taps both count, and none is written over by a copy that missed the other
    fun change(edit: (MySupplies) -> MySupplies) {
        PersonalStore.save("setting:$KEY_SUPPLIES:" + java.util.UUID.randomUUID()) { db ->
            val now = MySupplies.fromJson(db.settings().get(KEY_SUPPLIES))
            db.settings().put(Setting(KEY_SUPPLIES, edit(now).toJson()))
        }
    }
    val lines = Supplies.food(content.guide, s.people, s.days)
    val keyed = lines.map { Kit.key(it.what) to it }
    val now = YearMonth.now()
    // Only when to remind is kept for notifications, never what runs out
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(s) { Notify.supplies(context, s.expiring(YearMonth.of(9998, 1)).firstOrNull()?.second) }
    var picking by remember { mutableStateOf<String?>(null) }

    // How long will it last
    Block(stringResource(R.string.supplies_dash_title), ua.sprotyv.home.Group.PREPARE) {
        run {
            val waterWords = if (s.waterDays == 0) stringResource(R.string.supplies_water_none) else pluralStringResource(R.plurals.supplies_water_days, s.waterDays, s.waterDays)
            Meter(waterWords, stringResource(R.string.supplies_of_litres, s.water, s.waterGoal), s.water.toFloat() / s.waterGoal)
            val have = keyed.count { it.first in s.have }
            Meter(stringResource(R.string.supplies_food_have), stringResource(R.string.supplies_of, have, keyed.size), if (keyed.isEmpty()) 0f else have.toFloat() / keyed.size)
            val soon = s.expiring(now)
            val waterWord = stringResource(R.string.supplies_water_word)
            val medsWord = stringResource(R.string.supplies_meds)
            val batteriesWord = stringResource(R.string.supplies_batteries)
            if (soon.isNotEmpty()) {
                val names = soon.mapNotNull { (k, m) ->
                    val what = when (k) {
                        MySupplies.WATER -> waterWord
                        MySupplies.MEDS -> medsWord
                        MySupplies.BATTERIES -> batteriesWord
                        else -> keyed.firstOrNull { it.first == k }?.second?.what
                    }
                    what?.let { "$it (${monthText(m)})" }
                }.joinToString(", ")
                if (names.isNotEmpty()) {
                    Text(stringResource(R.string.supplies_expiring, names), style = MaterialTheme.typography.bodyLarge, color = ua.sprotyv.home.Group.DANGER.tint().ink, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // For whom and for how long
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { if (s.people > 1) change { it.copy(people = (it.people - 1).coerceAtLeast(1)) } }, modifier = Modifier.heightIn(min = 56.dp)) { Text("−") }
        Text(stringResource(R.string.supplies_people, s.people), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { if (s.people < 20) change { it.copy(people = (it.people + 1).coerceAtMost(20)) } }, modifier = Modifier.heightIn(min = 56.dp)) { Text("+") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Supplies.LEVELS.forEach { d ->
            FilterChip(selected = s.days == d, onClick = { change { it.copy(days = d) } }, label = { Text(stringResource(R.string.supplies_days, d)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text(stringResource(R.string.supplies_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Water: how many litres there are
    Block(stringResource(R.string.supplies_water_word), ua.sprotyv.home.Group.PREPARE, foldable = true) {
    Text(stringResource(R.string.supplies_water_need, s.waterGoal), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(R.string.supplies_water_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.supplies_water_have), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { change { it.copy(water = (it.water - 1).coerceAtLeast(0)) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("−1") }
        Text(stringResource(R.string.supplies_litres, s.water), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { change { it.copy(water = (it.water + 1).coerceAtMost(2000)) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+1") }
        OutlinedButton(onClick = { change { it.copy(water = (it.water + 5).coerceAtMost(2000)) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+5") }
    }
    UntilButton(MySupplies.month(s.waterUntil)) { picking = MySupplies.WATER }
    }

    // Food: tick what is at home in the needed amount
    val haveCount = keyed.count { it.first in s.have }
    Block(stringResource(R.string.supplies_food), ua.sprotyv.home.Group.PREPARE, foldable = true, trailing = {
        Text(stringResource(R.string.supplies_of, haveCount, keyed.size), style = MaterialTheme.typography.labelLarge)
    }) {
    Text(stringResource(R.string.supplies_have_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    keyed.forEachIndexed { i, (key, line) ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val have = key in s.have
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = have, onCheckedChange = { on -> change { it.copy(have = if (on) it.have + key else it.have - key) } })
            Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text(line.what, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(line.amount, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                if (line.note.isNotBlank()) Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (have) UntilButton(s.until[key]?.let(MySupplies::month)) { picking = key }
            }
        }
    }
    }
    // Other things that run out: one date each, the soonest
    Block(stringResource(R.string.supplies_other), ua.sprotyv.home.Group.PREPARE, foldable = true) {
    listOf(MySupplies.MEDS to R.string.supplies_meds, MySupplies.BATTERIES to R.string.supplies_batteries).forEach { (key, name) ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            UntilButton(s.until[key]?.let(MySupplies::month)) { picking = key }
        }
    }
    }
    Text(stringResource(R.string.supplies_rotation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { nav.go(Route.GuideSection(Supplies.SECTION)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.in_detail)) }

    picking?.let { key ->
        val current = if (key == MySupplies.WATER) MySupplies.month(s.waterUntil) else s.until[key]?.let(MySupplies::month)
        MonthDialog(current ?: now.plusMonths(6), onDone = { m ->
            val v = m?.toString().orEmpty()
            change {
                if (key == MySupplies.WATER) it.copy(waterUntil = v)
                else it.copy(until = if (m == null) it.until - key else it.until + (key to v))
            }
            picking = null
        }, onCancel = { picking = null })
    }
}

/** A labelled bar: the words and the numbers say it, the bar only shows it. */
@Composable
fun Meter(title: String, numbers: String, fraction: Float, fill: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$title, $numbers" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(numbers, style = MaterialTheme.typography.bodyLarge)
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val f = fraction.coerceIn(0f, 1f)
            if (f > 0f) drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = r)
        }
    }
}

@Composable
private fun UntilButton(month: YearMonth?, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (month == null) stringResource(R.string.supplies_add_until) else stringResource(R.string.supplies_until, monthText(month)))
    }
}

private fun monthText(m: YearMonth) = "%02d.%d".format(m.monthValue, m.year)

/** Month and year with plain buttons: no calendar to fight with, works with TalkBack. */
@Composable
private fun MonthDialog(start: YearMonth, onDone: (YearMonth?) -> Unit, onCancel: () -> Unit) {
    var m by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.supplies_pick_month)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { m = m.minusMonths(1) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("−") }
                Text(monthText(m), style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = { m = m.plusMonths(1) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("+") }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(m) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { onDone(null) }) { Text(stringResource(R.string.supplies_clear_until)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
