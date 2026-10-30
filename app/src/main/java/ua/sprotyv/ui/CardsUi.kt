package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
        CardBody(card, nav)
    }
}

@Composable
private fun CardBody(card: Card, nav: Nav) {
    val context = LocalContext.current
    var step by rememberSaveable(card.id) { mutableIntStateOf(0) }
    var showWhy by rememberSaveable(card.id, step) { mutableStateOf(false) }
    var all by rememberSaveable(card.id) { mutableStateOf(false) }
    val done = step >= card.steps.size

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

    if (all) {
        card.steps.forEachIndexed { i, s ->
            Text("${i + 1}. ${s.action}", style = MaterialTheme.typography.titleMedium)
            if (s.why.isNotBlank()) Text(s.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { all = false }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.one_at_a_time)) }
    } else if (!done) {
        val s = card.steps[step]
        Text(
            stringResource(R.string.step_of, step + 1, card.steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Read out by TalkBack whenever the step changes
        Text(
            s.action,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (showWhy) {
            Text(s.why, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (s.why.isNotBlank()) {
            TextButton(onClick = { showWhy = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.why)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.heightIn(min = 64.dp)) { Text(stringResource(R.string.previous)) }
            }
            Button(onClick = { step++ }, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                Text(stringResource(R.string.done_next), style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(onClick = { all = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.all_steps)) }
    } else {
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
    card.guide.firstOrNull()?.let { num ->
        OutlinedButton(onClick = { nav.go(Route.GuideSection(num)) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.in_detail))
        }
    }
    Source(card)
}

/** Where a card comes from and whether a specialist has checked it — always visible, never hidden. */
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
