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
private class Habit(val id: String, val pillar: Pillar, val title: String, val plan: String, val route: Route? = null)

private val HABITS = listOf(
    Habit("charge", Pillar.SAFETY, "Телефон і павербанк — на зарядку щовечора", "Коли чищу зуби ввечері — ставлю телефон і павербанк на зарядку.", Route.Power),
    Habit("place", Pillar.SAFETY, "Документи й ключі — завжди на одному місці", "Коли приходжу додому — кладу документи й ключі на їхнє місце.", Route.Mine(MinePart.KIT)),
    Habit("water", Pillar.SAFETY, "Пляшка води біля ліжка", "Коли вечеряю — наповнюю пляшку води й ставлю біля ліжка.", Route.SuppliesRoute),
    Habit("night", Pillar.SAFETY, "Одяг і взуття поруч на ніч", "Коли лягаю спати — кладу поруч одяг, взуття й ліхтарик.", Route.CardRoute("air-alert")),

    Habit("news", Pillar.CALM, "Новини — двічі на день, не весь день", "Коли п'ю ранкову каву — 10 хвилин новин, далі до вечора ні. Сповіщення про тривогу лишаю ввімкненими.", Route.Situation),
    Habit("breath", Pillar.CALM, "Дві хвилини повільного дихання", "Коли під час тривоги я вже в укритті — дихаю повільно дві хвилини.", Route.ChapterRoute("ch1")),
    Habit("sleep", Pillar.CALM, "Лягати щодня в один час", "Коли настає мій час сну — вимикаю екран і кладу телефон на зарядку."),

    Habit("practice", Pillar.SKILL, "Одна ситуація «Що б ви зробили?»", "Коли їду в транспорті чи чекаю — проходжу одну ситуацію.", Route.PracticeRoute("")),
    Habit("skill", Pillar.SKILL, "Повторити одну дію: турнікет, вузол, номер служби", "Коли дивлюся вечірні новини — повторюю одну дію з карток.", Route.MozList),
    Habit("page", Pillar.SKILL, "Одна сторінка довідника", "Коли сідаю пити чай — читаю одну сторінку довідника.", Route.Open(Section.GUIDE)),

    Habit("call", Pillar.BOND, "Подзвонити або написати рідним", "Коли обідаю — пишу комусь із рідних чи друзів.", Route.Mine(MinePart.PEOPLE)),
    Habit("neighbour", Pillar.BOND, "Перемовитися з сусідами", "Коли виходжу з дому — вітаюся з сусідами й питаю, як справи."),
    Habit("child", Pillar.BOND, "10 хвилин з дитиною без телефону", "Коли дитина вкладається спати — 10 хвилин розмови без екранів."),

    Habit("good", Pillar.MEANING, "Одна добра справа для когось", "Коли бачу, що комусь важко, — допомагаю хоч трохи.", Route.Resistance),
    Habit("support", Pillar.MEANING, "Підтримати країну: донат, кров, правда", "Коли отримую зарплату — частину відкладаю на підтримку.", Route.Resistance),
    Habit("ukrainian", Pillar.MEANING, "Своє: книжка, пісня, історія", "Коли лягаю спати — читаю кілька сторінок української книжки чи історії.", Route.HistoryRoute),

    Habit("walk", Pillar.BODY, "Прогулянка 20–30 хвилин", "Коли повертаюся з роботи чи магазину — іду довшою дорогою.", null),
    Habit("stairs", Pillar.BODY, "Сходи замість ліфта", "Коли заходжу в будинок — іду сходами, хоча б частину."),
    Habit("strength", Pillar.BODY, "10 хвилин на силу й рівновагу", "Після ранкового вмивання — присідання, віджимання від стіни, стояння на одній нозі біля опори."),
    Habit("carry", Pillar.BODY, "Пройтися з рюкзаком", "Коли йду в магазин — беру рюкзак, а не пакет, і несу покупки в ньому."),
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
    "walk3" to "Пройду 3 км з рюкзаком",
    "floors" to "Піднімуся на 5 поверхів без зупинки",
    "drag" to "Протягну дорослу людину на ковдрі кілька метрів (за її згодою, спина рівна)",
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
                        Text(h.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(h.plan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Checkbox) { v ->
                            change { s -> val cur = if (s.bodyMonth == month) s.body else emptySet(); s.copy(body = if (v) cur + key else cur - key, bodyMonth = month) }
                        }.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                        Text(words, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
    "Lally P. та ін. (2010, онлайн 2009). Як формуються звички. European Journal of Social Psychology" to "https://doi.org/10.1002/ejsp.674",
    "Gollwitzer P., Sheeran P. (2006). Плани «якщо — то» і досягнення цілей" to "https://doi.org/10.1016/S0065-2601(06)38002-1",
    "Hobfoll S. та ін. (2007). П'ять основ допомоги після катастроф. Psychiatry" to "https://doi.org/10.1521/psyc.2007.70.4.283",
    "ВООЗ (2020). Рекомендації щодо фізичної активності" to "https://www.who.int/publications/i/item/9789240015128",
)

@Composable
private fun ActiveHabit(h: Habit, done: Set<Long>, today: Long, nav: Nav, onTick: (Boolean) -> Unit, onDrop: () -> Unit) {
    val on = today in done
    val tint = if (h.pillar.resist) resist() else h.pillar.group.tint()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = onTick).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(h.plan, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The last two weeks as dots: done is filled, missed is only empty, never red
        Row(Modifier.padding(start = 56.dp, end = 16.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (13 downTo 0).forEach { back ->
                val d = today - back
                Box(Modifier.size(12.dp).background(if (d in done) tint.ink else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        val count = done.size
        Column(Modifier.padding(start = 56.dp, end = 16.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LinearProgressIndicator(progress = { (count / ROOTED_DAYS.toFloat()).coerceAtMost(1f) }, color = tint.ink, trackColor = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxWidth())
            Text(stringResource(stage(count)) + " · " + stringResource(R.string.gart_days, count, ROOTED_DAYS), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(start = 44.dp)) {
            h.route?.let { r -> TextButton(onClick = { nav.go(r) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.gart_open)) } }
            TextButton(onClick = onDrop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.gart_drop)) }
        }
    }
}
