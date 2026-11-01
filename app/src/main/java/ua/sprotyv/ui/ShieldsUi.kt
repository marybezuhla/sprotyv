package ua.sprotyv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.personal.PersonalStore

/**
 * One shield: what it is for, how it is earned, its colour (a group, or resistance's khaki when
 * null) and emblem, and how far: 0 not yet (a pale outline), 1 half, 2 earned.
 */
class ShieldItem(
    val id: String, val title: Int, val how: Int, val group: Group?, val icon: Int, val level: Int,
    /** A number on the shield (the last mark reached), for counts. */
    val count: Int? = null,
    /** A big step: a double rim. */
    val big: Boolean = false,
    /** Ticked by the owner: its id in [Steps.MARKS]; or counted with "+1": its id in [Steps.COUNTS]. */
    val mark: String? = null,
    val counter: String? = null,
    /** For counts: how many marks (10, 25, 50, 100, then every 50) are passed — small notches on the rim, nothing more. */
    val notches: Int = 0,
)

/** The readiness parts, from the counts already kept for the home screen (no records). */
private fun readinessShields(shares: String): List<ShieldItem> {
    val parts = shares.split(",").mapNotNull { p -> p.split("/").takeIf { it.size == 2 }?.let { (d, t) -> d.toIntOrNull()?.let { dd -> t.toIntOrNull()?.let { tt -> dd to tt } } } }
    val names = listOf(R.string.medical_card to R.drawable.ocha_health, R.string.people to R.drawable.ocha_users, R.string.family_plan to R.drawable.ocha_assembly_point,
        R.string.kit to R.drawable.ocha_non_food_items, R.string.supplies to R.drawable.ocha_food)
    if (parts.size != names.size) return emptyList()
    return names.mapIndexed { i, (title, icon) ->
        val (d, t) = parts[i]
        ShieldItem("ready:$i", title, R.string.shield_how_ready, Group.PREPARE, icon, if (t > 0 && d >= t) 2 else 0)
    }
}

/**
 * "My shields": what has really been done — a habit made one's own, a level of the path, a test
 * without a mistake, a part of readiness. Earned by the thing itself, never by a tap; never taken
 * away; no ranking, no reminders. Competence acknowledged, not points (self-determination theory:
 * informational feedback supports motivation, controlling rewards undermine it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShieldsScreen(nav: Nav) {
    val habits by remember { PersonalStore.observe { db -> db.settings().observe(KEY_HABITS).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val path by remember { PersonalStore.observe { db -> db.settings().observe(KEY_PATH).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val stepsStored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_STEPS).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val roles = rememberRoles() ?: return Loading()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = HomePrefs.state.collectAsStateWithLifecycle().value ?: HomePrefs.load(ctx)
    val h = habits ?: return Loading()
    val p = path ?: return Loading()
    val st = Steps.parse((stepsStored ?: return Loading()).first())
    val pathDone = pathShields(p.first(), roles)
    val (gartDays, bodyMonths) = gartTotals(h.first())
    fun counted(id: String, title: Int, icon: Int, n: Int, rare: Boolean, group: Group?, counter: String? = null): ShieldItem {
        // Every single time counts: the exact number is on the shield; the marks passed are only notches
        return ShieldItem(id, title, R.string.shield_how_count, group, icon, if (n > 0) 2 else 0, count = n.takeIf { it > 0 }, counter = counter, notches = marksPassed(n, rare))
    }
    val held = listOf(
        counted("held:blackout", R.string.held_blackout, R.drawable.ocha_power_outage, Held.get(ctx, "blackout"), false, Group.HOME_LIFE),
        counted("held:heating", R.string.held_heating, R.drawable.ocha_stove, Held.get(ctx, "heating"), false, Group.HOME_LIFE),
        counted("held:shelter", R.string.held_shelter, R.drawable.ocha_shelter, Held.get(ctx, "shelter"), false, Group.DANGER),
        counted("held:gart", R.string.held_gart, R.drawable.ocha_training, gartDays, false, Group.PREPARE),
        counted("held:body", R.string.held_body, R.drawable.ocha_health, bodyMonths, true, Group.FIRST_AID),
    )
    val tests = pathDone.filter { it.id.startsWith("test:") } +
        PRACTICE_TOPICS.map { (id, title) -> ShieldItem("topic:$id", title, R.string.shield_how_topic, Group.PREPARE, R.drawable.tab_know, if (id in st.topics) 2 else 0) } +
        counted("solved", R.string.shield_solved, R.drawable.tab_know, st.solved.size, false, Group.PREPARE)
    // Big steps: some seen from what is already ticked, the rest on the owner's own word
    val pathKeys = p.first()
    fun auto(key: String) = pathKeys?.contains("\"$key\"") == true
    fun marked(m: String, title: Int, icon: Int, group: Group?, big: Boolean, auto: Boolean = false) =
        ShieldItem("step:$m", title, if (auto) R.string.shield_how_auto else R.string.shield_how_mark, group, icon, if (auto || m in st.marked) 2 else 0, big = big, mark = m.takeIf { !auto })
    val big = listOf(
        marked("rescue", R.string.step_rescue, R.drawable.ocha_help, Group.FIRST_AID, true),
        marked("left", R.string.step_left, R.drawable.ocha_internally_displaced, Group.LEAVE, true),
        marked("newlife", R.string.step_newlife, R.drawable.ocha_house, Group.LEAVE, true),
        marked("course", R.string.step_course, R.drawable.ocha_learning, Group.FIRST_AID, true, auto = auto("course")),
        marked("teach", R.string.step_teach, R.drawable.ocha_users, Group.FIRST_AID, true, auto = auto("teach")),
        marked("volunteer", R.string.step_volunteer, R.drawable.ocha_group, null, true),
        marked("defence", R.string.step_defence, R.drawable.ocha_security, null, true),
    )
    val small = listOf(
        marked("donate", R.string.step_donate, R.drawable.ocha_help, null, false),
        marked("twosources", R.string.step_twosources, R.drawable.ocha_public_information, Group.GUIDE, false),
    )
    val counters = listOf(
        counted("count:blood", R.string.count_blood, R.drawable.ocha_health, st.counts["blood"] ?: 0, true, Group.FIRST_AID, "blood"),
        counted("count:shifts", R.string.count_shifts, R.drawable.ocha_group, st.counts["shifts"] ?: 0, true, null, "shifts"),
        counted("count:taught", R.string.count_taught, R.drawable.ocha_users, st.counts["taught"] ?: 0, true, Group.FIRST_AID, "taught"),
    )
    val groups = listOf(
        R.string.shields_held to held,
        R.string.shields_big to big,
        R.string.shields_habits to habitShields(h.first()),
        R.string.shields_path to pathDone.filter { it.id.startsWith("path:") },
        R.string.shields_tests to tests,
        R.string.shields_ready to readinessShields(prefs.readiness),
        R.string.shields_small to small,
        R.string.shields_counts to counters,
    )
    val earned = groups.sumOf { (_, l) -> l.count { it.level == 2 } }
    val all = groups.sumOf { (_, l) -> l.size }
    Text(stringResource(R.string.shields_intro), style = ReadingText)
    Block(stringResource(R.string.shields_title), Group.PREPARE, tint = resist(), summary = stringResource(R.string.shields_count, earned, all), leading = { ReadyRing(if (all == 0) 0f else earned.toFloat() / all, "$earned") }) {
        Text(stringResource(R.string.shields_never_lost), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    groups.forEachIndexed { gi, (title, list) ->
        if (list.isEmpty()) return@forEachIndexed
        Block(stringResource(title), Group.PREPARE, tint = resist(), foldable = true, startOpen = gi < 2, trailing = {
            Text(stringResource(R.string.ready_count, list.count { it.level == 2 }, list.size), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                list.forEach { ShieldBadge(it) }
            }
        }
    }
}

/** A shield of the app's own shape: filled when earned, half filled half way, a pale outline before. */
@Composable
private fun ShieldBadge(s: ShieldItem) {
    val tint = s.group?.tint() ?: resist()
    val outline = MaterialTheme.colorScheme.outlineVariant
    val title = stringResource(s.title)
    val how = stringResource(s.how)
    val state = stringResource(when (s.level) { 2 -> R.string.shield_earned; 1 -> R.string.shield_half; else -> R.string.shield_not_yet })
    Column(
        Modifier.width(96.dp).clearAndSetSemantics { contentDescription = "$title: $state. $how" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val w = size.width; val hgt = size.height
                fun sx(x: Float) = x / 24f * w
                fun sy(y: Float) = y / 24f * hgt
                val shield = Path().apply {
                    moveTo(sx(12f), sy(2.5f)); lineTo(sx(19.5f), sy(5.2f)); lineTo(sx(19.5f), sy(10.6f))
                    cubicTo(sx(19.5f), sy(15.4f), sx(16.4f), sy(18.9f), sx(12f), sy(20.6f))
                    cubicTo(sx(7.6f), sy(18.9f), sx(4.5f), sy(15.4f), sx(4.5f), sy(10.6f))
                    lineTo(sx(4.5f), sy(5.2f)); close()
                }
                when (s.level) {
                    2 -> {
                        drawPath(shield, tint.container); drawPath(shield, tint.ink, style = Stroke(width = 2.dp.toPx()))
                        // A big step: a second, inner rim
                        if (s.big) scale(0.8f) { drawPath(shield, tint.ink, style = Stroke(width = 1.5.dp.toPx())) }
                        // Marks passed: short notches along the top edge, up to seven shown
                        val shown = s.notches.coerceAtMost(7)
                        for (k in 0 until shown) {
                            val x = sx(12f + (k - (shown - 1) / 2f) * 1.6f)
                            drawLine(tint.ink, androidx.compose.ui.geometry.Offset(x, sy(4.2f)), androidx.compose.ui.geometry.Offset(x, sy(5.6f)), strokeWidth = 1.5.dp.toPx())
                        }
                    }
                    1 -> {
                        // Half way: the lower half filled
                        drawPath(shield, tint.container.copy(alpha = 0.35f))
                        clipRect(top = hgt * 0.5f) { drawPath(shield, tint.container) }
                        drawPath(shield, tint.ink, style = Stroke(width = 2.dp.toPx()))
                    }
                    else -> drawPath(shield, outline, style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                }
            }
            if (s.count != null) {
                // Counts: the last mark reached, on the shield itself
                Text(s.count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint.ink)
            } else {
                Icon(ImageVector.vectorResource(s.icon), contentDescription = null, tint = if (s.level > 0) tint.ink else outline, modifier = Modifier.size(24.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = if (s.level > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        // Counts say nothing more than their name: no "still to go"
        if (s.level < 2 && s.mark == null && s.count == null && s.counter == null && !s.id.startsWith("held:") && s.id != "solved") {
            Text(how, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // On the owner's word: a tick; counts: "+1" (and "−1" for a slip of the finger)
        s.mark?.let { m ->
            val on = s.level == 2
            androidx.compose.material3.TextButton(onClick = { Steps.change { st -> st.copy(marked = if (on) st.marked - m else st.marked + m) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (on) R.string.shield_unmark else R.string.shield_mark), style = MaterialTheme.typography.labelLarge)
            }
        }
        s.counter?.let { c ->
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(onClick = { Steps.change { st -> st.copy(counts = st.counts + (c to ((st.counts[c] ?: 0) - 1).coerceAtLeast(0))) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("−1") }
                androidx.compose.material3.TextButton(onClick = { Steps.change { st -> st.copy(counts = st.counts + (c to ((st.counts[c] ?: 0) + 1).coerceAtMost(10_000))) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+1") }
            }
        }
    }
}
