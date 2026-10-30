package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import ua.sprotyv.content.Block
import ua.sprotyv.home.Mode
import ua.sprotyv.R
import ua.sprotyv.content.Card
import ua.sprotyv.content.Content
import ua.sprotyv.reach.Reach

/** A tappable row for a card: large, title first. */
@Composable
fun CardRow(card: Card, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Text(
            card.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
        )
    }
}

/** "What to do now": every card, in the order of the built-in file. */
@Composable
fun NowScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.tile_now), nav::back) {
        if (content == null) return@Frame Loading()
        if (content.cardsBroken) return@Frame Broken()
        content.cards.forEach { card -> CardRow(card) { nav.go(Route.CardRoute(card.id)) } }
        CardsPdfButton(content)
    }
}

/**
 * One card, one step at a time: the step in large type, "Done" for the next, "Why" on request.
 * The call button for this situation and the source stay on the screen.
 */
@Composable
fun CardScreen(content: Content?, id: String, nav: Nav) {
    val card = content?.card(id)
    Frame(card?.title ?: "", nav::back) {
        if (content == null) return@Frame Loading()
        if (content.cardsBroken) return@Frame Broken()
        if (card == null) return@Frame Text(stringResource(R.string.not_found))
        CardBody(card, content, nav)
    }
}

/**
 * Two ways to read a card, and the person switches between them:
 * - Overview: every step on one screen, numbered, with its picture. Seeing the whole and how far
 *   it goes calms and helps planning ("overview first, details on demand").
 * - Step by step: the current step large, a strip showing where you are and the next step in
 *   small print, as in crisis checklists ("step at a glance"); fewer skipped steps under stress.
 * The full look opens in overview; the simple and child looks step by step.
 */
@Composable
private fun CardBody(card: Card, content: Content, nav: Nav) {
    val context = LocalContext.current
    val mode = HomePrefs.state.collectAsState().value?.mode
    var step by rememberSaveable(card.id) { mutableIntStateOf(0) }
    var all by rememberSaveable(card.id) { mutableStateOf(mode == Mode.FULL) }
    val done = step >= card.steps.size
    val kid = card.id.startsWith("kid-")

    card.call?.let { service ->
        Button(
            onClick = { Reach.dial(context, service.number) },
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Text("  " + stringResource(R.string.call_number, service.number), fontWeight = FontWeight.SemiBold)
        }
    }

    if (card.id == "bleeding") TourniquetTime()

    if (all) {
        card.steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.Top) {
                StepNumber(i + 1, active = false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.action, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Why(s.why, "${card.id}:$i")
                }
                Pictogram(s.icon, 44.dp)
            }
            if (s.figure.isNotEmpty()) StepFigure(content, s.figure)
        }
        Button(onClick = { all = false; step = 0 }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.step_by_step), style = MaterialTheme.typography.titleMedium)
        }
    } else if (!done) {
        val s = card.steps[step]
        Progress(card.steps.size, step)
        Text(
            stringResource(R.string.step_of, step + 1, card.steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pictogram(s.icon, 88.dp)
        // Read out by TalkBack whenever the step changes
        Text(
            s.action,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (s.figure.isNotEmpty()) StepFigure(content, s.figure)
        Why(s.why, "${card.id}:$step")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.previous)) }
            }
            Button(onClick = { step++ }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Text(stringResource(R.string.done_next), style = MaterialTheme.typography.titleMedium)
            }
        }
        // The next step in small print: what comes is never a surprise
        card.steps.getOrNull(step + 1)?.let { next ->
            Text(
                stringResource(R.string.next_step, next.action),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = { all = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.all_steps)) }
    } else if (kid) {
        // A child hears praise at the end (guide 5.5: only praise, never scolding)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sunny(96.dp, happy = true)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.kid_well_done),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                Text(stringResource(R.string.kid_well_done_hint), style = MaterialTheme.typography.titleMedium)
            }
        }
        OutlinedButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.again)) }
    } else {
        Progress(card.steps.size, card.steps.size)
        Text(
            stringResource(R.string.all_done),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.again)) }
            OutlinedButton(onClick = { all = true }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.all_steps)) }
        }
    }

    HorizontalDivider()
    // A child's card has no "in detail": the guide is written for adults
    card.guide.firstOrNull()?.takeIf { !kid }?.let { num ->
        OutlinedButton(onClick = { nav.go(Route.GuideSection(num)) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.in_detail))
        }
    }
    Source(card)
}

/**
 * The tourniquet time (guide 7.2: write it on the tourniquet or the forehead, "Т 14:25", for the
 * medics; never loosen it). One tap keeps the time and shows it large, with how long it has been.
 * Only a time is kept, on this phone, until reset or for a day; nothing about the person. Kept
 * outside the encrypted data on purpose, so it works at once on a card (a decision on record).
 */
@Composable
private fun TourniquetTime() {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("tourniquet", android.content.Context.MODE_PRIVATE) }
    var at by remember {
        val kept = prefs.getLong("at", 0L)
        // Older than a day: removed from the phone, not just hidden
        if (kept > 0 && System.currentTimeMillis() - kept >= 24 * 3600_000L) prefs.edit().remove("at").apply()
        mutableStateOf(kept.takeIf { it > 0 && System.currentTimeMillis() - it < 24 * 3600_000L })
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(at) {
        while (at != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(15_000)
        }
    }
    val time = at
    if (time == null) {
        OutlinedButton(onClick = {
            val t = System.currentTimeMillis()
            prefs.edit().putLong("at", t).apply()
            at = t
            now = t
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.tourniquet_on), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val clock = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val label = stringResource(R.string.tourniquet_mark, String.format(java.util.Locale.ROOT, "%02d:%02d", clock.hour, clock.minute))
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tourniquet_since, minutes / 60, minutes % 60), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.tourniquet_write), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { prefs.edit().remove("at").apply(); at = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.tourniquet_reset))
            }
        }
    }
}

/**
 * The step's pictogram: one action in the same simple line style, always next to the words that
 * say it (a picture alone is often misread). Decorative for TalkBack, which reads the words.
 */
@Composable
private fun Pictogram(name: String, size: androidx.compose.ui.unit.Dp) {
    val res = PICTOGRAMS[name] ?: return
    Icon(
        androidx.compose.ui.res.painterResource(res),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(size),
    )
}

private val PICTOGRAMS = mapOf(
    "call" to R.drawable.pict_call,
    "close_door" to R.drawable.pict_close_door,
    "gas_off" to R.drawable.pict_gas_off,
    "lie_down" to R.drawable.pict_lie_down,
    "no_touch" to R.drawable.pict_no_touch,
    "no_window" to R.drawable.pict_no_window,
    "press" to R.drawable.pict_press,
    "shelter" to R.drawable.pict_shelter,
    "shoes" to R.drawable.pict_shoes,
    "stay_low" to R.drawable.pict_stay_low,
    "step_back" to R.drawable.pict_step_back,
    "two_walls" to R.drawable.pict_two_walls,
)

/** "Why" folded under a step, opened on request: the second level never crowds the first. */
@Composable
private fun Why(why: String, key: String) {
    if (why.isBlank()) return
    var open by rememberSaveable(key) { mutableStateOf(false) }
    if (open) {
        Text(why, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.why)) }
    }
}

/** A step number in a circle; filled for the current step. */
@Composable
private fun StepNumber(n: Int, active: Boolean) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            n.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** Where you are: one segment per step, done ones filled. The words "Step 2 of 5" say the same. */
@Composable
private fun Progress(total: Int, current: Int) {
    val done = MaterialTheme.colorScheme.primary
    val todo = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        repeat(total) { i ->
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i <= current) done else todo))
        }
    }
}

/** The guide's own picture for a step, if the section has one. */
@Composable
private fun StepFigure(content: Content, num: String) {
    val figure = content.guide.section(num)?.second?.blocks?.filterIsInstance<Block.Figure>()?.firstOrNull() ?: return
    FigureView(figure)
}

@Composable
fun Source(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.source_line, card.source), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.checked_on, card.checkedOn), style = MaterialTheme.typography.bodySmall)
        Text(
            if (card.reviewedBy.isBlank()) stringResource(R.string.not_reviewed) else stringResource(R.string.reviewed_by, card.reviewedBy),
            style = MaterialTheme.typography.bodySmall,
            color = if (card.reviewedBy.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Air alert section: alerts come in 1.1; until then, the two alert cards and the official app. */
@Composable
fun AlarmScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.tile_alarm), nav::back) {
        Text(stringResource(R.string.alarm_not_yet), style = MaterialTheme.typography.bodyLarge)
        if (content == null) return@Frame Loading()
        if (content.cardsBroken) return@Frame Broken()
        listOf("air-alert", "ballistic", "drone").mapNotNull(content::card).forEach { card ->
            CardRow(card) { nav.go(Route.CardRoute(card.id)) }
        }
    }
}

/** Life in a crisis: the blackout card and the guide chapters for life without power, heat or water. */
@Composable
fun HomeLifeScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.tile_home_life), nav::back) {
        if (content == null) return@Frame Loading()
        if (content.cardsBroken || content.guideBroken) Broken()
        listOf("blackout", "leave-home").mapNotNull(content::card).forEach { card ->
            CardRow(card) { nav.go(Route.CardRoute(card.id)) }
        }
        NavRow(stringResource(R.string.supplies), stringResource(R.string.supplies_hint)) { nav.go(Route.SuppliesRoute) }
        Text(stringResource(R.string.in_the_guide), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        // Blackout, water, food, warmth, hygiene, health
        listOf("ch20", "ch8", "ch9", "ch12", "ch16", "ch21").mapNotNull { id -> content.guide.chapters.firstOrNull { it.id == id } }
            .forEach { ch -> ChapterRow(ch) { nav.go(Route.ChapterRoute(ch.id)) } }
    }
}
