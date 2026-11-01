package ua.sprotyv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting
import ua.sprotyv.reach.Reach
import java.time.LocalDate

const val KEY_HABITS = "habits"

/**
 * "Daily hardening": small habits that hold a person and their close ones through a long war,
 * in the five supports of resilience after disasters (Hobfoll et al., 2007: safety, calm, the sense
 * of being able to act, connection, hope) and the body that has to carry it all.
 *
 * Built on what is known about habits: a habit becomes automatic in about 66 days on average, from
 * 18 to more than 200 (Lally et al., 2009), so progress counts the days it was done, never a
 * streak; a missed day did not set people back, so nothing is reset; an "if — then" plan makes doing
 * it far more likely (Gollwitzer and Sheeran, 2006); a few at a time. Kept encrypted in "Mine".
 */
private enum class Pillar(val title: Int, val hint: Int, val group: Group, val resist: Boolean = false) {
    SAFETY(R.string.gart_safety, R.string.gart_safety_hint, Group.DANGER),
    CALM(R.string.gart_calm, R.string.gart_calm_hint, Group.FIRST_AID),
    SKILL(R.string.gart_skill, R.string.gart_skill_hint, Group.PREPARE),
    BOND(R.string.gart_bond, R.string.gart_bond_hint, Group.FAMILY),
    MEANING(R.string.gart_meaning, R.string.gart_meaning_hint, Group.PREPARE, resist = true),
    BODY(R.string.gart_body, R.string.gart_body_hint, Group.LEAVE),
}

/** One habit: what to do, and an "if — then" plan that ties it to something already done every day. */
private class Habit(val id: String, val pillar: Pillar, val title: Int, val plan: Int, val route: Route? = null)

private val HABITS = listOf(
    Habit("charge", Pillar.SAFETY, R.string.habit_charge, R.string.habit_charge_plan, Route.Power),
    Habit("place", Pillar.SAFETY, R.string.habit_place, R.string.habit_place_plan, Route.Mine(MinePart.KIT)),
    Habit("water", Pillar.SAFETY, R.string.habit_water, R.string.habit_water_plan, Route.SuppliesRoute),
    Habit("night", Pillar.SAFETY, R.string.habit_night, R.string.habit_night_plan, Route.CardRoute("air-alert")),

    Habit("news", Pillar.CALM, R.string.habit_news, R.string.habit_news_plan, Route.Situation),
    Habit("breath", Pillar.CALM, R.string.habit_breath, R.string.habit_breath_plan, Route.ChapterRoute("ch1")),
    Habit("sleep", Pillar.CALM, R.string.habit_sleep, R.string.habit_sleep_plan),

    Habit("practice", Pillar.SKILL, R.string.habit_practice, R.string.habit_practice_plan, Route.PracticeRoute("")),
    Habit("skill", Pillar.SKILL, R.string.habit_skill, R.string.habit_skill_plan, Route.MozList),
    Habit("page", Pillar.SKILL, R.string.habit_page, R.string.habit_page_plan, Route.Open(Section.GUIDE)),

    Habit("call", Pillar.BOND, R.string.habit_call, R.string.habit_call_plan, Route.Mine(MinePart.PEOPLE)),
    Habit("neighbour", Pillar.BOND, R.string.habit_neighbour, R.string.habit_neighbour_plan),
    Habit("child", Pillar.BOND, R.string.habit_child, R.string.habit_child_plan),

    Habit("good", Pillar.MEANING, R.string.habit_good, R.string.habit_good_plan, Route.Resistance),
    Habit("support", Pillar.MEANING, R.string.habit_support, R.string.habit_support_plan, Route.Resistance),
    Habit("ukrainian", Pillar.MEANING, R.string.habit_ukrainian, R.string.habit_ukrainian_plan, Route.HistoryRoute),

    Habit("walk", Pillar.BODY, R.string.habit_walk, R.string.habit_walk_plan, null),
    Habit("stairs", Pillar.BODY, R.string.habit_stairs, R.string.habit_stairs_plan),
    Habit("strength", Pillar.BODY, R.string.habit_strength, R.string.habit_strength_plan),
    Habit("carry", Pillar.BODY, R.string.habit_carry, R.string.habit_carry_plan),
)

private const val MAX_ACTIVE = 5
/** The average number of days to a habit that is automatic (Lally et al., 2009). */
private const val ROOTED_DAYS = 66

/** What the owner keeps: the chosen habits, the days each was done, and this month's body check. */
private data class Gart(val active: List<String>, val done: Map<String, Set<Long>>, val body: Set<String>, val bodyMonth: String)

private fun parseGart(s: String?): Gart {
    val empty = Gart(emptyList(), emptyMap(), emptySet(), "")
    if (s.isNullOrBlank() || s.length > 60_000) return empty
    return try {
        val o = JSONObject(s)
        val ids = HABITS.map { it.id }.toSet()
        val a = o.optJSONArray("active") ?: JSONArray()
        val active = (0 until minOf(a.length(), MAX_ACTIVE)).map { a.optString(it) }.filter { it in ids }.distinct()
        val d = o.optJSONObject("done") ?: JSONObject()
        val done = ids.associateWith { id ->
            val days = d.optJSONArray(id) ?: JSONArray()
            (0 until minOf(days.length(), 400)).map { days.optLong(it) }.filter { it > 0 }.toSet()
        }.filterValues { it.isNotEmpty() }
        val b = o.optJSONArray("body") ?: JSONArray()
        Gart(active, done, (0 until minOf(b.length(), 10)).map { b.optString(it) }.toSet(), o.optString("bodyMonth").take(7))
    } catch (e: Exception) {
        empty
    }
}

private fun Gart.json(): String = JSONObject()
    .put("active", JSONArray(active))
    // A year of days at most per habit: enough to see a habit take root
    .put("done", JSONObject().apply { done.forEach { (id, days) -> put(id, JSONArray(days.sorted().takeLast(366))) } })
    .put("body", JSONArray(body.toList()))
    .put("bodyMonth", bodyMonth)
    .toString()

/** How rooted a habit is, by the days it was done (never by an unbroken run). */
private fun stage(days: Int): Int = when {
    days >= ROOTED_DAYS -> R.string.gart_stage_own
    days >= 21 -> R.string.gart_stage_almost
    else -> R.string.gart_stage_rooting
}

private val BODY_CHECKS = listOf(
    "walk3" to R.string.body_walk3,
    "floors" to R.string.body_floors,
    "drag" to R.string.body_drag,
)

@Composable
fun GartScreen(nav: Nav) {
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_HABITS).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val g = parseGart((stored ?: return Loading()).first())
    val today = LocalDate.now().toEpochDay()
    val month = LocalDate.now().toString().take(7)
    fun change(f: (Gart) -> Gart) {
        PersonalStore.save("setting:$KEY_HABITS:" + java.util.UUID.randomUUID()) { db ->
            db.settings().put(Setting(KEY_HABITS, f(parseGart(db.settings().get(KEY_HABITS))).json()))
        }
    }
    Text(stringResource(R.string.gart_intro), style = MaterialTheme.typography.bodyLarge)

    // Today: the chosen habits, one tick each
    val mine = g.active.mapNotNull { id -> HABITS.firstOrNull { it.id == id } }
    // Only today's counts leave "Mine", for the home screen, "My way" and the widget
    val context = androidx.compose.ui.platform.LocalContext.current
    val share = "$today:${mine.count { today in g.done[it.id].orEmpty() }}/${mine.size}"
    androidx.compose.runtime.LaunchedEffect(share) {
        if (HomePrefs.load(context).gart != share) { HomePrefs.update(context) { it.copy(gart = share) }; Widgets.update(context) }
    }
    Block(stringResource(R.string.gart_today), Group.PREPARE, padded = mine.isEmpty(),
        summary = if (mine.isEmpty()) null else stringResource(R.string.gart_today_count, mine.count { today in g.done[it.id].orEmpty() }, mine.size)) {
        if (mine.isEmpty()) Text(stringResource(R.string.gart_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        mine.forEachIndexed { i, h ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActiveHabit(h, g.done[h.id].orEmpty(), today, nav,
                onTick = { on -> change { s -> s.copy(done = s.done + (h.id to (s.done[h.id].orEmpty().let { if (on) it + today else it - today }))) } },
                onDrop = { change { s -> s.copy(active = s.active - h.id) } })
        }
    }

    // Choosing: the habits by support, folded; a few at a time
    val full = g.active.size >= MAX_ACTIVE
    if (full) Text(stringResource(R.string.gart_full, MAX_ACTIVE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Pillar.entries.forEach { p ->
        val list = HABITS.filter { it.pillar == p }
        Block(stringResource(p.title), p.group, tint = if (p.resist) resist() else null, padded = false, foldable = true, summary = stringResource(p.hint)) {
            list.forEachIndexed { i, h ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val on = h.id in g.active
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(h.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(h.plan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        onClick = { change { s -> s.copy(active = if (on) s.active - h.id else (s.active + h.id).take(MAX_ACTIVE)) } },
                        enabled = on || !full, modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(if (on) R.string.gart_drop else R.string.gart_take)) }
                }
            }
            // The body: a check once a month, never a score
            if (p == Pillar.BODY) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.gart_body_check), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.gart_body_check_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val got = if (g.bodyMonth == month) g.body else emptySet()
                BODY_CHECKS.forEach { (key, words) ->
                    val on = key in got
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).doneRow(on).toggleable(value = on, role = Role.Checkbox) { v ->
                            change { s -> val cur = if (s.bodyMonth == month) s.body else emptySet(); s.copy(body = if (v) cur + key else cur - key, bodyMonth = month) }
                        }.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp), colors = doneCheckbox())
                        Text(stringResource(words), style = MaterialTheme.typography.bodyLarge, color = doneText(on), modifier = Modifier.weight(1f))
                    }
                }
                Text(stringResource(R.string.gart_body_doctor), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }

    // Why it works: the science, with its sources
    val context = LocalContext.current
    Block(stringResource(R.string.gart_why), Group.GUIDE, foldable = true, summary = stringResource(R.string.gart_why_hint)) {
        listOf(R.string.gart_why_1, R.string.gart_why_2, R.string.gart_why_3, R.string.gart_why_4).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
        }
        SCIENCE.forEach { (title, url) ->
            Text("↗ $title", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).toggleable(value = false, role = Role.Button) { Reach.open(context, url) }.padding(vertical = 6.dp))
        }
    }
}

private val SCIENCE = listOf(
    "Lally P. et al. (2010, online 2009). How are habits formed. European Journal of Social Psychology" to "https://doi.org/10.1002/ejsp.674",
    "Gollwitzer P., Sheeran P. (2006). Implementation intentions and goal achievement" to "https://doi.org/10.1016/S0065-2601(06)38002-1",
    "Hobfoll S. et al. (2007). Five essential elements of immediate and mid-term mass trauma intervention. Psychiatry" to "https://doi.org/10.1521/psyc.2007.70.4.283",
    "WHO (2020). Guidelines on physical activity and sedentary behaviour" to "https://www.who.int/publications/i/item/9789240015128",
)

@Composable
private fun ActiveHabit(h: Habit, done: Set<Long>, today: Long, nav: Nav, onTick: (Boolean) -> Unit, onDrop: () -> Unit) {
    val on = today in done
    val tint = if (h.pillar.resist) resist() else h.pillar.group.tint()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).doneRow(on).toggleable(value = on, role = Role.Checkbox, onValueChange = onTick).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp), colors = doneCheckbox())
            Column(Modifier.weight(1f)) {
                Text(stringResource(h.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = doneText(on))
                Text(stringResource(h.plan), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The last two weeks as dots: done is filled, missed is only empty, never red
        Row(Modifier.padding(start = CheckIndent, end = 16.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (13 downTo 0).forEach { back ->
                val d = today - back
                Box(Modifier.size(12.dp).background(if (d in done) tint.ink else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        val count = done.size
        Column(Modifier.padding(start = CheckIndent, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(progress = { (count / ROOTED_DAYS.toFloat()).coerceAtMost(1f) }, color = tint.ink, trackColor = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth())
            Text(stringResource(stage(count)) + " · " + stringResource(R.string.gart_days, count, ROOTED_DAYS), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(start = CheckIndent - ButtonInset)) {
            h.route?.let { r -> TextButton(onClick = { nav.go(r) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.gart_open)) } }
            TextButton(onClick = onDrop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.gart_drop)) }
        }
    }
}


/** Today's "Daily hardening" from the counts kept outside: (done, chosen), or null when none chosen. */
fun gartToday(share: String, today: Long = LocalDate.now().toEpochDay()): Pair<Int, Int>? {
    val m = Regex("^(\\d{1,9}):(\\d{1,2})/(\\d{1,2})$").matchEntire(share) ?: return null
    val total = m.groupValues[3].toInt().takeIf { it in 1..20 } ?: return null
    // Another day's count: today nothing is ticked yet
    val done = if (m.groupValues[1].toLong() == today) m.groupValues[2].toInt().coerceIn(0, total) else 0
    return done to total
}

/** "Daily hardening today: 1 of 3", on the home screen and in "My way": counts only, a tap to tick. */
@Composable
fun GartStrip(nav: Nav) {
    val prefs = androidx.compose.runtime.collectAsState(HomePrefs.state).value ?: return
    if (prefs.trip) return
    val (d, t) = gartToday(prefs.gart) ?: return
    HubCard(
        stringResource(R.string.gart_strip_title),
        if (d >= t) stringResource(R.string.gart_strip_all, d, t) else stringResource(R.string.gart_today_count, d, t),
        Group.PREPARE.tint(),
        leading = { ReadyRing(d.toFloat() / t, "$d/$t") },
    ) { nav.go(Route.Mine(MinePart.GART)) }
}
