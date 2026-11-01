package ua.sprotyv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
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
class ShieldItem(val id: String, val title: Int, val how: Int, val group: Group?, val icon: Int, val level: Int)

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
    val roles = rememberRoles() ?: return Loading()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = HomePrefs.state.collectAsStateWithLifecycle().value ?: HomePrefs.load(ctx)
    val notes by remember { PersonalStore.observe { db -> db.notes().observe() } }.collectAsStateWithLifecycle(null)
    val context = androidx.compose.ui.platform.LocalContext.current
    val h = habits ?: return Loading()
    val p = path ?: return Loading()
    val groups = listOf(
        R.string.shields_habits to habitShields(h.first()),
        R.string.shields_path to pathShields(p.first(), roles).filter { it.id.startsWith("path:") },
        R.string.shields_tests to pathShields(p.first(), roles).filter { it.id.startsWith("test:") },
        R.string.shields_ready to readinessShields(prefs.readiness),
        // The rarest one: the knowledge was needed in life, and written down
        R.string.shields_life to listOf(ShieldItem("life", R.string.shield_life, R.string.shield_how_life, Group.FIRST_AID, R.drawable.ocha_help, if (notes?.let { experienceWritten(it, context) } == true) 2 else 0)),
    )
    val earned = groups.sumOf { (_, l) -> l.count { it.level == 2 } }
    val all = groups.sumOf { (_, l) -> l.size }
    Text(stringResource(R.string.shields_intro), style = ReadingText)
    Block(stringResource(R.string.shields_title), Group.PREPARE, tint = resist(), summary = stringResource(R.string.shields_count, earned, all), leading = { ReadyRing(if (all == 0) 0f else earned.toFloat() / all, "$earned") }) {
        Text(stringResource(R.string.shields_never_lost), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    groups.forEach { (title, list) ->
        if (list.isEmpty()) return@forEach
        Block(stringResource(title), Group.PREPARE, tint = resist(), foldable = true, startOpen = true, trailing = {
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
                    2 -> { drawPath(shield, tint.container); drawPath(shield, tint.ink, style = Stroke(width = 2.dp.toPx())) }
                    1 -> {
                        // Half way: the lower half filled
                        drawPath(shield, tint.container.copy(alpha = 0.35f))
                        clipRect(top = hgt * 0.5f) { drawPath(shield, tint.container) }
                        drawPath(shield, tint.ink, style = Stroke(width = 2.dp.toPx()))
                    }
                    else -> drawPath(shield, outline, style = Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                }
            }
            Icon(ImageVector.vectorResource(s.icon), contentDescription = null, tint = if (s.level > 0) tint.ink else outline, modifier = Modifier.size(24.dp))
        }
        Text(title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = if (s.level > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.level < 2) Text(how, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
