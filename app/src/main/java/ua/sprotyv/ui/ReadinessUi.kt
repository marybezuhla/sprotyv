package ua.sprotyv.ui

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
 * Blue while on the way, green with a tick when done. Never red: these are next steps, not marks.
 * The first part not yet done is named as the next step, so the block always ends in an action.
 * Only on the owner's own screens, which are kept off screenshots.
 */
@Composable
fun ReadinessBlock(content: Content?, nav: Nav) {
    val medical by remember { PersonalStore.observe { db -> db.medical().observe().map(Readiness::medical) } }.collectAsStateWithLifecycle(null)
    val people by remember { PersonalStore.observe { db -> db.people().observe().map(Readiness::people) } }.collectAsStateWithLifecycle(null)
    val plan by remember { PersonalStore.observe { db -> db.plan().observe().map(Readiness::plan) } }.collectAsStateWithLifecycle(null)
    val ticked by remember { PersonalStore.observe { db -> db.kit().observe().map { l -> l.filter { it.checked }.map { it.key }.toSet() } } }.collectAsStateWithLifecycle(null)
    val kit = remember(content, ticked) {
        val keys = ticked
        if (content == null || content.guideBroken || keys == null) null
        else Kit.groups(content.guide).let { g -> Readiness.kit(g.sumOf { it.items.count { i -> i.key in keys } }, g.sumOf { it.items.size }) }
    }
    val rows = listOf(
        Triple(R.string.medical_card, medical, Route.Mine(MinePart.MEDICAL)),
        Triple(R.string.people, people, Route.Mine(MinePart.PEOPLE)),
        Triple(R.string.family_plan, plan, Route.Mine(MinePart.PLAN)),
        Triple(R.string.kit, kit, Route.Mine(MinePart.KIT)),
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            rows.forEach { (title, p, route) -> ReadyRow(stringResource(title), p) { nav.go(route) } }
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

@Composable
private fun ReadyRow(title: String, p: Progress?, onClick: () -> Unit) {
    val done = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val going = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val words = if (p == null) "…" else stringResource(R.string.ready_count, p.done, p.total)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$title: $words" },
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
    }
}
