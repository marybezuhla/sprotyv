package ua.sprotyv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Moz441
import ua.sprotyv.home.Group

/** The procedures in four groups a person looks for, in the order's own numbers. */
private val MOZ_GROUPS = listOf(
    R.string.moz_group_heart to listOf(1, 2, 3, 25),
    R.string.moz_group_injury to listOf(4, 14, 28, 29, 7, 8, 9, 10, 11, 12, 13, 15, 16),
    R.string.moz_group_sudden to listOf(5, 6, 21, 26, 27, 17, 18),
    R.string.moz_group_environment to listOf(19, 20, 23, 24, 22),
)

/** Which procedure stands behind an action card, so the official text is one tap away. */
val CARD_MOZ = mapOf("bleeding" to 4, "unconscious" to 1, "burn" to 19, "rubble" to 15, "chemical" to 18)

/** All 29 procedures of order No. 441, grouped, each opening as steps or as the full text. */
@Composable
fun MozListScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.moz_title), nav::back) {
        if (content == null) return@Frame Loading()
        val moz = content.moz441 ?: return@Frame Broken()
        val medic = HomePrefs.state.collectAsState().value?.medic == true
        Text(stringResource(if (medic) R.string.moz_intro_medic else R.string.moz_intro), style = MaterialTheme.typography.bodyLarge)
        if (medic) {
            Block(stringResource(R.string.medic_tools), Group.FIRST_AID, padded = false) {
                BlockRow(stringResource(R.string.handover_button), stringResource(R.string.handover_hint), first = true) { nav.go(Route.Handover) }
            }
        }
        // Search across all 29 procedures, word for word
        var query by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) }, singleLine = true,
            label = { Text(stringResource(R.string.moz_search)) }, modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        if (q.length >= 2) {
            val found = moz.procedures.filter { it.allText.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(stringResource(R.string.notes_nothing_found), style = MaterialTheme.typography.bodyLarge)
            else Block(stringResource(R.string.moz_found, found.size), Group.FIRST_AID, padded = false) {
                found.forEachIndexed { i, p -> BlockRow("${p.n}. ${p.short}", first = i == 0) { nav.go(Route.Moz(p.n)) } }
            }
            return@Frame
        }
        MOZ_GROUPS.forEach { (title, numbers) ->
            val list = numbers.mapNotNull(moz::procedure)
            Block(stringResource(title), Group.FIRST_AID, padded = false, foldable = true, summary = stringResource(R.string.shelf_count, list.size)) {
                list.forEachIndexed { i, p -> BlockRow("${p.n}. ${p.short}", first = i == 0) { nav.go(Route.Moz(p.n)) } }
            }
        }
        // The order itself, so the whole document is here in one place
        moz.order?.let { o ->
            Block(stringResource(R.string.moz_order_text), Group.GUIDE, foldable = true, summary = moz.act) {
                Text(o.preamble, style = MaterialTheme.typography.bodyMedium)
                Text("НАКАЗУЮ:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(o.approve, style = MaterialTheme.typography.bodyMedium)
                moz.procedures.forEach { p ->
                    Text(
                        "${p.n}) Порядок ${p.title}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp).heightIn(min = 32.dp).clickable { nav.go(Route.Moz(p.n)) },
                    )
                }
                o.points.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(o.signed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        SourceBox(moz.act, moz.license, reviewedBy = "")
        Text(listOf(moz.status, stringResource(R.string.moz_retrieved, moz.retrieved)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One procedure. Two ways to show the same official words, never changed: "Steps" (the sequence
 * of actions as numbered steps, signs and warnings in their own blocks, the scope and terms
 * folded away) for everyone, and "Full text" (every point in the order's numbering, as a medic
 * reads a document). Medical training in settings makes the full text the first view.
 */
@Composable
fun MozScreen(content: Content?, n: Int, nav: Nav) {
    val p = content?.moz441?.procedure(n)
    Frame(p?.short ?: stringResource(R.string.moz_title), nav::back) {
        if (content == null) return@Frame Loading()
        val moz = content.moz441
        if (moz == null || p == null) return@Frame Broken()
        val medic = HomePrefs.state.collectAsState().value?.medic == true
        var full by rememberSaveable(n) { mutableStateOf(medic) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !full, onClick = { full = false }, label = { Text(stringResource(R.string.moz_steps)) }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(selected = full, onClick = { full = true }, label = { Text(stringResource(R.string.moz_full)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
        if (full) FullText(p, medic) else Steps(p)
        SourceBox(stringResource(R.string.moz_source_line, moz.act, p.n), moz.license, reviewedBy = "")
        Text(
            stringResource(if (full) R.string.moz_as_is else R.string.moz_layout_only) + " " + moz.status.let { if (it.isBlank()) "" else "$it. " } + stringResource(R.string.moz_retrieved, moz.retrieved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // For someone who knows the procedure: hand its steps to people nearby; notes for the crew
        if (p.actions != null) {
            OutlinedButton(onClick = { nav.go(Route.Delegate(p.n)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.delegate_button)) }
        }
        OutlinedButton(onClick = { nav.go(Route.Handover) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.handover_button)) }
        OutlinedButton(onClick = { nav.go(Route.MozList) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.moz_all)) }
    }
}

@Composable
private fun Steps(p: Moz441.Procedure) {
    val actions = p.actions
    // Action first (the app's rule): the sequence of actions leads, signs and notes follow
    (listOfNotNull(actions) + p.points.drop(2).filter { it != actions }).forEach { point ->
        when {
            point == actions -> Block(stringResource(R.string.moz_what_to_do), Group.FIRST_AID) {
                point.items.forEach { step -> StepLine(step) }
            }
            point.items.isNotEmpty() -> Block(point.text.trimEnd(':'), Group.GUIDE) {
                point.items.forEach { item -> Bullet(item) }
            }
            else -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(point.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp)) }
        }
    }
    // The scope and the meaning of the words: there for anyone who wants them, out of the way
    Block(stringResource(R.string.moz_about), Group.GUIDE, foldable = true) {
        p.points.take(2).forEach { Text("${it.n}. ${it.text}", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** A step of the sequence: its number in a circle, the words large, the order's own sub-items below. */
@Composable
private fun StepLine(step: Moz441.Item) {
    val tint = Group.FIRST_AID.tint()
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).background(tint.container, CircleShape), contentAlignment = Alignment.Center) {
            Text(step.n, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = tint.ink)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(step.text, style = MaterialTheme.typography.bodyLarge)
            step.items.forEach { Text("${it.n}) ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun Bullet(item: Moz441.Item) {
    Row {
        Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge)
            item.items.forEach { Text("${it.n}) ${it.text}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Every point as the order numbers it, compact, as a document is read. */
@Composable
private fun FullText(p: Moz441.Procedure, medic: Boolean) {
    if (medic) {
        Card(colors = CardDefaults.cardColors(containerColor = Group.FIRST_AID.tint().container), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.moz_medic_note), style = MaterialTheme.typography.bodyMedium, color = Group.FIRST_AID.tint().ink, modifier = Modifier.padding(16.dp))
        }
    }
    Text(stringResource(R.string.moz_procedure_title, p.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    p.points.forEach { point ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${point.n}. ${point.text}", style = MaterialTheme.typography.bodyMedium)
            point.items.forEach { item ->
                Text("${item.n}) ${item.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
                item.items.forEach { sub ->
                    Text("${sub.n}) ${sub.text}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 32.dp))
                }
            }
        }
    }
}
