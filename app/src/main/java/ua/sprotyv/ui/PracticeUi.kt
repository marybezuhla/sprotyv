package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content

/**
 * "What would you do?": one situation at a time, three answers, then why. Choosing and seeing why
 * is remembered far better than reading again (retrieval practice). No score, nothing saved, and
 * a wrong answer is never red: this is practice, red is kept for danger now.
 * [cardId] empty: all adult cards in order; otherwise only that card's situations.
 */
@Composable
fun PracticeScreen(content: Content?, cardId: String, nav: Nav) {
    Frame(stringResource(R.string.practice_title), nav::back) {
        if (content == null) return@Frame Loading()
        val list = content.practice.filter { if (cardId.isEmpty()) !it.card.startsWith("kid-") else it.card == cardId }
        if (list.isEmpty()) return@Frame Broken()
        val kid = cardId.startsWith("kid-")
        var index by rememberSaveable(cardId) { mutableIntStateOf(0) }

        if (index >= list.size) {
            if (kid) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sunny(96.dp, happy = true)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.kid_well_done), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    stringResource(R.string.practice_done),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { index = 0 }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.again)) }
                Button(onClick = nav::back, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.practice_finish)) }
            }
            return@Frame
        }

        val p = list[index]
        // A new order for each situation, kept through rotation, so the place never gives the answer
        var orderText by rememberSaveable(cardId, index) { androidx.compose.runtime.mutableStateOf(p.options.indices.shuffled().joinToString(",")) }
        val order = orderText.split(",").mapNotNull { it.toIntOrNull() }.filter { it in p.options.indices }.takeIf { it.size == p.options.size } ?: p.options.indices.toList()
        var chosen by rememberSaveable(cardId, index) { mutableIntStateOf(-1) }

        Text(
            stringResource(R.string.practice_of, index + 1, list.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            p.situation,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(stringResource(if (kid) R.string.practice_q_kid else R.string.practice_q), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

        order.forEach { i ->
            val o = p.options[i]
            val answered = chosen >= 0
            // Said in words and marks, never by colour alone
            val mark = when {
                answered && o.right -> "✓ "
                answered && i == chosen -> "✗ "
                else -> ""
            }
            OutlinedButton(
                onClick = { if (!answered) chosen = i },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (answered && o.right) ua.sprotyv.home.Group.FIRST_AID.tint().container else MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text(mark + o.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            }
        }

        if (chosen >= 0) {
            val picked = p.options[chosen]
            val right = p.options.first { it.right }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    if (kid) {
                        Sunny(64.dp, happy = picked.right)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                when {
                                    picked.right && kid -> R.string.practice_right_kid
                                    picked.right -> R.string.practice_right
                                    kid -> R.string.practice_wrong_kid
                                    else -> R.string.practice_wrong
                                },
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(picked.why, style = MaterialTheme.typography.bodyLarge)
                        if (!picked.right) {
                            Text(stringResource(R.string.practice_better, right.text), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(right.why, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Button(onClick = { index++ }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                Text(stringResource(if (index + 1 < list.size) R.string.practice_next else R.string.practice_finish), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { nav.go(Route.CardRoute(p.card)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.practice_open_card))
            }
        }

        Text(stringResource(R.string.practice_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The same source and review status as the card it comes from
        content.card(p.card)?.let { Source(it) }
    }
}
