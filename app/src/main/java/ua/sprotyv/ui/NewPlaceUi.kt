package ua.sprotyv.ui

import androidx.compose.material.icons.filled.KeyboardArrowDown

import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.NewPlace
import ua.sprotyv.home.Group
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting
import ua.sprotyv.reach.Reach

const val KEY_NEW_PLACE = "new_place"

private val NewPlace.Stage.title: Int get() = when (this) {
    NewPlace.Stage.DAYS -> R.string.np_days
    NewPlace.Stage.WEEK -> R.string.np_week
    NewPlace.Stage.MONTH -> R.string.np_month
    NewPlace.Stage.LATER -> R.string.np_later
    NewPlace.Stage.ABROAD -> R.string.np_abroad
}

private fun stepRoute(r: String): Route? = when (r) {
    "safe" -> Route.Open(Section.SAFE)
    "resistance" -> Route.Resistance
    "directory" -> Route.Directory
    else -> null
}

/**
 * "New place": after leaving home, the steps by stage, each ticked by the owner (kept encrypted in
 * "Mine", in the backup). One step opens at a time; its sources open outside Sprotyv.
 */
@Composable
fun NewPlaceScreen(content: Content?, nav: Nav) {
    if (content == null) return Loading()
    val np = content.newPlace ?: return Broken()
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_NEW_PLACE).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val done = parseTicks((stored ?: return Loading()).first(), np.steps.map { it.id }.toSet())
    fun change(id: String, on: Boolean) {
        PersonalStore.save("setting:$KEY_NEW_PLACE:" + java.util.UUID.randomUUID()) { db ->
            val now = parseTicks(db.settings().get(KEY_NEW_PLACE), np.steps.map { it.id }.toSet())
            db.settings().put(Setting(KEY_NEW_PLACE, org.json.JSONArray((if (on) now + id else now - id).sorted()).toString()))
        }
    }
    val inUkraine = np.steps.filter { it.stage != NewPlace.Stage.ABROAD }
    val count = inUkraine.count { it.id in done }
    Text(stringResource(R.string.np_intro), style = MaterialTheme.typography.bodyLarge)
    Block(stringResource(R.string.np_progress_title), Group.LEAVE, summary = stringResource(R.string.path_summary, count, inUkraine.size),
        leading = { ReadyRing(count.toFloat() / inUkraine.size, "$count/${inUkraine.size}") }) {
        Text(stringResource(R.string.np_progress_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val firstStage = inUkraine.firstOrNull { it.id !in done }?.stage
    NewPlace.Stage.entries.forEach { stage ->
        val steps = np.steps.filter { it.stage == stage }
        if (steps.isEmpty()) return@forEach
        val got = steps.count { it.id in done }
        val group = if (stage == NewPlace.Stage.LATER) Group.PREPARE else Group.LEAVE
        Block(stringResource(stage.title), group, tint = if (stage == NewPlace.Stage.LATER) resist() else null, padded = false, foldable = true,
            startOpen = stage == firstStage, trailing = {
                Text((if (got == steps.size) "✓\u2002" else "") + stringResource(R.string.ready_count, got, steps.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }) {
            if (stage == NewPlace.Stage.LATER) Text(stringResource(R.string.np_live_on), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            if (stage == NewPlace.Stage.ABROAD) Text(stringResource(R.string.np_abroad_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            steps.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StepRow(s, s.id in done, open == s.id, { open = if (open == s.id) null else s.id }, { change(s.id, it) }, nav)
            }
        }
    }
    Text(stringResource(R.string.np_changing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepRow(s: NewPlace.Step, on: Boolean, expanded: Boolean, onExpand: () -> Unit, onTick: (Boolean) -> Unit, nav: Nav) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).doneRow(on).toggleable(value = on, role = Role.Checkbox, onValueChange = onTick).padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp), colors = doneCheckbox())
        Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = doneText(on), modifier = Modifier.weight(1f))
        // A small arrow, so the step's name keeps the width
        androidx.compose.material3.IconButton(onClick = onExpand, modifier = Modifier.size(48.dp)) {
            androidx.compose.material3.Icon(
                if (expanded) androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp else androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.np_less else R.string.np_more),
            )
        }
    }
    if (!expanded) return
    // The opened step's words start under its name, on the same left edge
    androidx.compose.foundation.layout.Column(Modifier.padding(start = CheckIndent, end = 16.dp, bottom = 12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(s.text, style = ReadingText)
        Text(stringResource(R.string.np_where, s.where), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        stepRoute(s.route)?.let { r -> androidx.compose.material3.OutlinedButton(onClick = { nav.go(r) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.np_open_in_app)) } }
        s.sources.forEach { src ->
            Text(
                "↗ " + src.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 4.dp).then(Modifier.toggleable(value = false, role = Role.Button) { Reach.open(context, src.url) }),
            )
        }
        if (s.sources.isNotEmpty()) Text(stringResource(R.string.history_checked, s.checked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The ticked ids; anything odd is dropped. */
private fun parseTicks(s: String?, ids: Set<String>): Set<String> {
    if (s.isNullOrBlank() || s.length > 3_000) return emptySet()
    return try {
        val a = org.json.JSONArray(s)
        (0 until minOf(a.length(), 80)).map { a.optString(it) }.filter { it in ids }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}
