package ua.sprotyv.ui

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.foundation.layout.Box

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background

import androidx.compose.material.icons.automirrored.filled.ArrowForward

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import ua.sprotyv.home.Progress
import ua.sprotyv.home.Readiness
import ua.sprotyv.personal.PersonalStore

/**
 * "How ready are we?": one block, one question, four bars on one common scale (position along a
 * common scale is read most exactly: Cleveland & McGill 1984), each with its count in words.
 * Blue all the way, with a tick when done (green means first aid here). Never red: these are next steps, not marks.
 * The first part not yet done is named as the next step, so the block always ends in an action.
 * Only on the owner's own screens, which are kept off screenshots.
 */
@Composable
fun ReadinessBlock(content: Content?, nav: Nav) {
    val medical by remember { PersonalStore.observe { db -> db.medical().observe().map(Readiness::medical) } }.collectAsStateWithLifecycle(null)
    val people by remember { PersonalStore.observe { db -> db.people().observe().map(Readiness::people) } }.collectAsStateWithLifecycle(null)
    val peopleFlags by remember { PersonalStore.observe { db -> db.people().observe().map { l -> l.any { it.safeList } to l.any { it.ice } } } }.collectAsStateWithLifecycle(null)
    val plan by remember { PersonalStore.observe { db -> db.plan().observe().map(Readiness::plan) } }.collectAsStateWithLifecycle(null)
    val ticked by remember { PersonalStore.observe { db -> db.kit().observe().map { l -> l.filter { it.checked }.map { it.key }.toSet() } } }.collectAsStateWithLifecycle(null)
    val kit = remember(content, ticked) {
        val keys = ticked
        if (content == null || content.guideBroken || keys == null) null
        else Kit.groups(content.guide).let { g -> Readiness.kit(g.sumOf { it.items.count { i -> i.key in keys } }, g.sumOf { it.items.size }) }
    }
    // Supplies: enough water for the goal counts as one step, each food line in place as another
    val stored by remember { PersonalStore.observe { db -> db.settings().observe(KEY_SUPPLIES).map { listOf(it) } } }.collectAsStateWithLifecycle(null)
    val supplies = remember(content, stored) {
        val raw = stored
        if (content == null || content.guideBroken || raw == null) null
        else {
            val s = ua.sprotyv.content.MySupplies.fromJson(raw.first())
            val keys = ua.sprotyv.content.Supplies.food(content.guide, s.people, s.days).map { Kit.key(it.what) }
            Progress((if (s.water >= s.waterGoal) 1 else 0) + keys.count { it in s.have }, 1 + keys.size)
        }
    }
    val rows = listOf(
        Triple(R.string.medical_card, medical, Route.Mine(MinePart.MEDICAL)),
        Triple(R.string.people, people, Route.Mine(MinePart.PEOPLE)),
        Triple(R.string.family_plan, plan, Route.Mine(MinePart.PLAN)),
        Triple(R.string.kit, kit, Route.Mine(MinePart.KIT)),
        Triple(R.string.supplies, supplies, Route.SuppliesRoute),
    )
    // The shares only (no records) are kept for the strip on the full home screen
    val context = androidx.compose.ui.platform.LocalContext.current
    val shares = if (rows.all { it.second != null }) rows.joinToString(",") { "${it.second!!.done}/${it.second!!.total}" } else null
    androidx.compose.runtime.LaunchedEffect(shares) {
        if (shares != null && HomePrefs.load(context).readiness != shares) { HomePrefs.update(context) { it.copy(readiness = shares) }; Widgets.update(context) }
    }
    // Folded: one ring for the whole (each part counts the same) and the next step in words
    val known = rows.all { it.second != null }
    val whole = if (known) rows.map { it.second!!.fraction }.average().toFloat() else 0f
    val partsDone = rows.count { it.second?.complete == true }
    val nextPart = rows.firstOrNull { it.second?.complete == false }
    val summary = if (!known) null else stringResource(R.string.ready_summary, partsDone, rows.size) + " · " +
        (if (nextPart == null) stringResource(R.string.ready_all) else stringResource(R.string.ready_next, stringResource(nextPart.first)))
    Block(
        stringResource(R.string.ready_title), ua.sprotyv.home.Group.MINE,
        foldable = true, summary = summary,
        leading = { ReadyRing(whole, "$partsDone/${rows.size}") },
    ) {
        run {
            // People: two things, not two people, so they are said in words under the bar
            val yes = stringResource(R.string.ready_yes)
            val no = stringResource(R.string.ready_no)
            val peopleDetail = peopleFlags?.let { (safe, ice) ->
                stringResource(R.string.ready_people_detail, if (safe) yes else no, if (ice) yes else no)
            }
            rows.forEach { (title, p, route) -> ReadyRow(stringResource(title), p, if (title == R.string.people) peopleDetail else null) { nav.go(route) } }
            if (rows.all { it.second != null }) {
                val next = rows.firstOrNull { it.second?.complete == false }
                Text(
                    if (next == null) stringResource(R.string.ready_all) else stringResource(R.string.ready_next, stringResource(next.first)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * The whole at a glance: one ring, the share done, with the count in its middle. One ring only:
 * parts are compared on the bars below, where lengths on one scale are read exactly.
 */
@Composable
fun ReadyRing(fraction: Float, label: String) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = if (fraction >= 1f) ua.sprotyv.home.Group.PREPARE.tint().ink else ua.sprotyv.home.Group.PREPARE.tint().ink
    androidx.compose.foundation.layout.Box(Modifier.size(52.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = 3.dp.toPx()
            val box = Size(size.width - inset * 2, size.height - inset * 2)
            val at = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft = at, size = box, style = stroke)
            if (fraction > 0f) drawArc(fill, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = at, size = box, style = stroke)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReadyRow(title: String, p: Progress?, detail: String? = null, onClick: () -> Unit) {
    val done = ua.sprotyv.home.Group.PREPARE.tint().ink
    val going = ua.sprotyv.home.Group.PREPARE.tint().ink
    val track = MaterialTheme.colorScheme.outlineVariant
    val words = if (p == null) "…" else stringResource(R.string.ready_count, p.done, p.total)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$title: " + (detail ?: words) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (p?.complete == true) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = done, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(words, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val f = p?.fraction ?: 0f
            if (f > 0f) drawRoundRect(if (p?.complete == true) done else going, size = Size(size.width * f, size.height), cornerRadius = r)
        }
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The parts of readiness, in the order the shares are kept. */
private val READY_PARTS = listOf(R.string.ready_short_medical, R.string.ready_short_people, R.string.ready_short_plan, R.string.ready_short_kit, R.string.ready_short_supplies)

/**
 * Readiness at a glance on the full home screen: five small bars on one scale, from what was last
 * seen in "Mine" (the shares only). A tap opens "Mine"; it can be hidden, and shown again in settings.
 */
@Composable
fun ReadinessStrip(nav: Nav) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = HomePrefs.state.collectAsStateWithLifecycle().value ?: return
    if (prefs.readinessHidden || prefs.trip) return
    val parts = prefs.readiness.split(",").mapNotNull { p ->
        p.split("/").takeIf { it.size == 2 }?.let { (d, t) -> d.toIntOrNull()?.let { dd -> t.toIntOrNull()?.let { tt -> Progress(dd, tt) } } }
    }
    if (parts.size != READY_PARTS.size) return
    val done = parts.count { it.complete }
    val doneColour = ua.sprotyv.home.Group.PREPARE.tint().ink
    val going = ua.sprotyv.home.Group.PREPARE.tint().ink
    val track = MaterialTheme.colorScheme.outlineVariant
    Card(
        onClick = { nav.go(Route.Open(Section.MINE)) },
        colors = CardDefaults.cardColors(containerColor = ua.sprotyv.home.Group.MINE.tint().container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ready_strip, done, parts.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = { HomePrefs.update(context) { it.copy(readinessHidden = true) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ready_hide))
                }
            }
            // What is ready, in one line of words; what is not, one row each: its name, a bar, the count.
            // Names never squeezed under narrow bars, so nothing is cut off
            val names = READY_PARTS.map { stringResource(it) }
            val ready = parts.indices.filter { parts[it].complete }
            if (ready.isNotEmpty()) {
                Text(stringResource(R.string.ready_done_list, ready.joinToString(", ") { names[it] }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
            }
            parts.forEachIndexed { i, p ->
                if (p.complete) return@forEachIndexed
                Row(
                    Modifier.padding(end = 12.dp).clearAndSetSemantics { contentDescription = "${names[i]}: ${p.done} / ${p.total}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(names[i], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(112.dp))
                    Canvas(Modifier.weight(1f).height(8.dp)) {
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(track, cornerRadius = r)
                        if (p.fraction > 0f) drawRoundRect(going, size = Size(size.width * p.fraction, size.height), cornerRadius = r)
                    }
                    Text("${p.done}/${p.total}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
            // "My path" with readiness: where the owner is and a calm word for the next level
            pathLevels(prefs.path).takeIf { it.isNotEmpty() }?.let { Box(Modifier.padding(end = 12.dp)) { PathInspire(it, prefs.pathCelebrated, nav) } }
        }
    }
}

/** "My path" per level ("4/4,2/3,0/4"): done and total of each; empty when not seen yet or odd. */
fun pathLevels(s: String): List<Pair<Int, Int>> {
    val parts = s.split(",").map { p ->
        val (d, t) = p.split("/").takeIf { it.size == 2 } ?: return emptyList()
        val dd = d.toIntOrNull() ?: return emptyList()
        val tt = t.toIntOrNull()?.takeIf { it in 1..50 } ?: return emptyList()
        if (dd !in 0..tt) return emptyList()
        dd to tt
    }
    return if (parts.size in 1..6) parts else emptyList()
}

/** The whole path as two numbers; null when not seen yet or odd. */
fun pathShare(s: String): Pair<Int, Int>? = pathLevels(s).takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.first } to l.sumOf { it.second } }

/** The levels of "My path", in order, for the home screen's words. */
private val LEVEL_NAMES = listOf(R.string.path_level_personal, R.string.path_level_family, R.string.path_level_civic, R.string.path_level_defence, R.string.path_level_medic)
/** Two calm phrases for each level being worked on; one is chosen by the day, so it changes now and then. */
private val LEVEL_WORDS = listOf(
    listOf(R.string.inspire_0a, R.string.inspire_0b), listOf(R.string.inspire_1a, R.string.inspire_1b),
    listOf(R.string.inspire_2a, R.string.inspire_2b), listOf(R.string.inspire_3a, R.string.inspire_3b), listOf(R.string.inspire_4a, R.string.inspire_4b),
)

/**
 * Readiness and the path as one story on the home screen: where the owner is, a calm line for the
 * next level, and once, when a level is completed, a word that it already helps. No points, no
 * streaks, nothing that scolds. A tap opens this week's step.
 */
@Composable
private fun PathInspire(levels: List<Pair<Int, Int>>, celebrated: Int, nav: Nav) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = levels.indexOfFirst { it.first < it.second }
    val lastDone = (if (current < 0) levels.size else current) - 1
    // A level just completed: said once, then the next level's words
    val celebrate = lastDone >= 0 && lastDone > celebrated
    LaunchedEffect(lastDone) {
        if (lastDone != celebrated && !(lastDone < celebrated && lastDone >= 0)) HomePrefs.update(context) { it.copy(pathCelebrated = lastDone) }
    }
    val day = java.time.LocalDate.now().dayOfYear
    Column(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable { nav.go(Route.Mine(MinePart.PATH)) }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val ink = MaterialTheme.colorScheme.onSurface
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Shield, contentDescription = null, tint = resist().ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            // Levels done get a tick; the one in progress shows its count
            val line = levels.mapIndexedNotNull { i, (d, t) ->
                val name = context.getString(LEVEL_NAMES.getOrElse(i) { LEVEL_NAMES.last() })
                when {
                    d >= t -> "$name ✓"
                    i == current -> "$name $d/$t"
                    else -> null
                }
            }.joinToString(" · ")
            Text(line, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ink)
        }
        val words = when {
            celebrate -> stringResource(R.string.inspire_level_done, stringResource(LEVEL_NAMES.getOrElse(lastDone) { LEVEL_NAMES.last() }))
            current < 0 -> stringResource(R.string.inspire_all)
            else -> LEVEL_WORDS.getOrElse(current) { LEVEL_WORDS.last() }.let { stringResource(it[day % it.size]) }
        }
        Text(words, style = MaterialTheme.typography.bodyMedium, color = ink)
    }
}
