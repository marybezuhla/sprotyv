package ua.sprotyv.ui

import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Link
import ua.sprotyv.home.Group
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting

const val KEY_PATH = "path"

/** A step of "My path": what it is, the level it belongs to, and where to learn it in Sprotyv. */
private class PathStep(val key: String, val level: Int, val words: Int, val learn: Route?)

/** Four levels, as in the concept: personal, family, civic, defence awareness. */
private val PATH_LEVELS = listOf(R.string.path_level_personal, R.string.path_level_family, R.string.path_level_civic, R.string.path_level_defence, R.string.path_level_medic)

private val PATH = listOf(
    PathStep("bleed", 0, R.string.path_bleed, Route.Moz(4)),
    PathStep("cpr", 0, R.string.path_cpr, Route.Moz(1)),
    PathStep("kit", 0, R.string.path_kit, Route.Mine(MinePart.KIT)),
    PathStep("move", 0, R.string.path_move, null),
    PathStep("plan", 1, R.string.path_plan, Route.Mine(MinePart.PLAN)),
    PathStep("people", 1, R.string.path_people, Route.Mine(MinePart.PEOPLE)),
    PathStep("shelter", 1, R.string.path_shelter, Route.Situation),
    PathStep("facts", 2, R.string.path_facts, Route.ChapterRoute("ch18")),
    PathStep("neighbours", 2, R.string.path_neighbours, null),
    PathStep("help", 2, R.string.path_help, Route.Resistance),
    PathStep("course", 2, R.string.path_course, Route.Resistance),
    PathStep("basics", 3, R.string.path_basics, Route.Resistance),
    PathStep("centre", 3, R.string.path_centre, Route.Resistance),
    PathStep("rights", 3, R.string.path_rights, Route.Resistance),
    // Only with "I have medical training" on
    PathStep("teach", 4, R.string.path_teach, null),
    PathStep("refresh", 4, R.string.path_refresh, Route.Resistance),
    PathStep("delegate", 4, R.string.path_delegate, Route.MozList),
)

/**
 * "Resistance": everyone has a part. What anyone can do, with one tap to where it is learnt in
 * Sprotyv; places to learn in person, official and civic (the country holds on its organisations
 * and volunteers as much as on the state); for adults who choose it, plain facts about military
 * service, with rights and risks said as they are; and resilience. No pressure, no ranking, no
 * reminders. Hidden in trip mode: on a phone that is searched it could put the owner at risk.
 */
@Composable
fun ResistanceScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    val prefs = HomePrefs.state.collectAsState().value
    Frame(stringResource(R.string.resist_title), nav::back) {
        // The opening: why it matters, in plain words
        Block(stringResource(R.string.resist_hero_title), Group.PREPARE) {
            Text(stringResource(R.string.resist_hero_1), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.resist_hero_2), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        // What anyone can do: each with the place in Sprotyv where it starts
        Block(stringResource(R.string.resist_roles_title), Group.PREPARE, padded = false) {
            BlockRow(stringResource(R.string.role_save), stringResource(R.string.role_save_hint), first = true) { nav.go(Route.MozList) }
            BlockRow(stringResource(R.string.role_ready), stringResource(R.string.role_ready_hint)) { nav.go(Route.Mine(MinePart.KIT)) }
            BlockRow(stringResource(R.string.role_family), stringResource(R.string.role_family_hint)) { nav.go(Route.Mine(MinePart.PLAN)) }
            BlockRow(stringResource(R.string.role_community), stringResource(R.string.role_community_hint)) { nav.go(Route.Directory) }
            BlockRow(stringResource(R.string.role_truth), stringResource(R.string.role_truth_hint)) { nav.go(Route.ChapterRoute("ch18")) }
            BlockRow(stringResource(R.string.role_learn), stringResource(R.string.role_learn_hint)) { nav.go(Route.Mine(MinePart.PATH)) }
        }
        // My path: the owner's own ticks, private, in "Mine"; here only the count
        val share = prefs?.path?.let(::pathShare)
        Block(
            stringResource(R.string.path_title), Group.PREPARE, padded = false,
            summary = share?.let { (d, t) -> stringResource(R.string.path_summary, d, t) },
            leading = share?.let { (d, t) -> { ReadyRing(d.toFloat() / t, "$d/$t") } },
        ) {
            BlockRow(stringResource(R.string.path_week_open), stringResource(R.string.path_open_hint), first = true) { nav.go(Route.Mine(MinePart.PATH)) }
        }
        if (content == null) return@Frame Loading()
        val links = content.links
        // Where to learn: official, civic and volunteer side by side, each marked
        LinkGroup(stringResource(R.string.resist_learn_title), stringResource(R.string.resist_learn_intro), links.filter { it.group == "learn" }, toolbar, context)
        // Resilience: its own entries, then fact-checkers from "Situation" (one list, never two copies)
        LinkGroup(stringResource(R.string.resist_resilience_title), stringResource(R.string.resist_resilience_intro), links.filter { it.group == "resilience" || it.group == "facts" }, toolbar, context)
        // Support the country: donations to the largest funds, bonds, blood, reporting
        LinkGroup(stringResource(R.string.resist_support_title), stringResource(R.string.resist_support_intro), links.filter { it.group == "support" }, toolbar, context)
        // Military service: only for adults who choose to see it
        Block(stringResource(R.string.service_title), Group.PREPARE) {
            Toggle(stringResource(R.string.service_toggle), prefs?.service == true) { on -> HomePrefs.update(context) { it.copy(service = on) } }
            Text(stringResource(R.string.service_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (prefs?.service == true) {
            Block(stringResource(R.string.service_facts_title), Group.GUIDE) {
                listOf(R.string.service_fact_1, R.string.service_fact_2, R.string.service_fact_3, R.string.service_fact_4, R.string.service_fact_5).forEach {
                    Row { Text("•", modifier = Modifier.padding(end = 8.dp)); Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)) }
                }
                Text(stringResource(R.string.service_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            links.filter { it.group == "service" }.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
        }
        Text(stringResource(R.string.resist_principles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LinkGroup(title: String, intro: String, links: List<Link>, toolbar: Int, context: android.content.Context) {
    if (links.isEmpty()) return
    Text(title, style = BlockTitle, modifier = Modifier.padding(top = 8.dp))
    Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
}

/**
 * "My path": concrete things a person can learn or do, in four levels, ticked by the owner. Kept
 * encrypted in "Mine", in the backup; seen by no one else. No score for courage or patriotism: only
 * what is done.
 */
@Composable
fun PathScreen(nav: Nav) {
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_PATH).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val done = stored?.first()?.let(::parsePath) ?: return Loading()
    val medic = HomePrefs.state.collectAsState().value?.medic == true
    val path = if (medic) PATH else PATH.filter { it.level < 4 }
    fun change(key: String, on: Boolean) {
        PersonalStore.save("setting:$KEY_PATH:" + java.util.UUID.randomUUID()) { db ->
            val now = parsePath(db.settings().get(KEY_PATH))
            db.settings().put(Setting(KEY_PATH, org.json.JSONArray((if (on) now + key else now - key).sorted()).toString()))
        }
    }
    val count = path.count { it.key in done }
    // Only the count leaves "Mine", for the home screen and "Resistance"
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(count, path.size) {
        val share = "$count/${path.size}"
        if (HomePrefs.load(context).path != share) HomePrefs.update(context) { it.copy(path = share) }
    }
    // This week's step: the first one not done yet, in the order of the path. One at a time, calmly
    var justDone by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val next = path.firstOrNull { it.key !in done }
    Block(stringResource(R.string.path_week_title), Group.PREPARE) {
        justDone?.let { Text(stringResource(R.string.path_week_well_done), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }
        if (next == null) {
            Text(stringResource(R.string.path_week_all), style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(stringResource(next.words), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = { change(next.key, true); justDone = next.key }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.path_week_done))
                }
                next.learn?.let { r -> androidx.compose.material3.OutlinedButton(onClick = { nav.go(r) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.path_learn)) } }
            }
            Text(stringResource(R.string.path_week_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Block(
        stringResource(R.string.path_title), Group.PREPARE,
        summary = stringResource(R.string.path_summary, count, path.size),
        leading = { ReadyRing(count.toFloat() / path.size, "$count/${path.size}") },
    ) {
        Text(stringResource(R.string.path_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    PATH_LEVELS.forEachIndexed { level, title ->
        val steps = path.filter { it.level == level }
        if (steps.isEmpty()) return@forEachIndexed
        val got = steps.count { it.key in done }
        Block(stringResource(title), Group.PREPARE, padded = false, trailing = {
            Text((if (got == steps.size) "✓ " else "") + stringResource(R.string.ready_count, got, steps.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }) {
            steps.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val on = s.key in done
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, role = Role.Checkbox) { change(s.key, it) }
                        .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                    Text(stringResource(s.words), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    s.learn?.let { r -> TextButton(onClick = { nav.go(r) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.path_learn)) } }
                }
            }
        }
    }
}

/** The ticked keys; anything odd is dropped. */
private fun parsePath(s: String?): Set<String> {
    if (s.isNullOrBlank() || s.length > 2_000) return emptySet()
    return try {
        val a = org.json.JSONArray(s)
        (0 until minOf(a.length(), 50)).map { a.optString(it) }.filter { k -> PATH.any { it.key == k } }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}
