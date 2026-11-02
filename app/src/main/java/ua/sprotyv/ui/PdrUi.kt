package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Pdr
import ua.sprotyv.home.Group

private fun Pdr.Section.name(): String = "$num. " + title.lowercase().replaceFirstChar { it.uppercase() }

private fun String.snippet(): String = if (length > 110) take(108).trimEnd() + "…" else this

/** The Rules of the Road in full: search across every point, then the 34 sections. */
@Composable
fun PdrScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.pdr_title), nav::back) {
        if (content == null) return@Frame Loading()
        val p = content.pdr ?: return@Frame Broken()
        Text(stringResource(R.string.pdr_intro), style = MaterialTheme.typography.bodyLarge)
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) }, singleLine = true,
            label = { Text(stringResource(R.string.pdr_search)) }, modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        if (q.length >= 2) {
            val found = p.sections.flatMap { s ->
                s.blocks.mapNotNull { b ->
                    val t = when (b) {
                        is Pdr.Para -> b.text
                        is Pdr.Heading -> b.text
                        is Pdr.Table -> b.rows.joinToString(" · ") { it.joinToString(" ") }
                    }
                    if (t.contains(q, ignoreCase = true)) s to t else null
                }
            }
            if (found.isEmpty()) Text(stringResource(R.string.notes_nothing_found), style = MaterialTheme.typography.bodyLarge)
            else Block(stringResource(R.string.pdr_found, found.size), Group.GUIDE, padded = false) {
                found.take(80).forEachIndexed { i, (s, t) -> BlockRow(t.snippet(), s.name(), first = i == 0) { nav.go(Route.PdrRoute(s.num)) } }
            }
            return@Frame
        }
        Block(stringResource(R.string.pdr_sections), Group.PREPARE, padded = false) {
            p.sections.forEachIndexed { i, s -> BlockRow(s.name(), first = i == 0) { nav.go(Route.PdrRoute(s.num)) } }
        }
        PdrSource(p)
    }
}

/** One section word for word, tables as rows, the neighbours one tap away. */
@Composable
fun PdrSectionScreen(content: Content?, num: String, nav: Nav) {
    Frame(stringResource(R.string.pdr_title), nav::back) {
        if (content == null) return@Frame Loading()
        val p = content.pdr ?: return@Frame Broken()
        val s = p.section(num) ?: return@Frame Broken()
        Text(s.name(), style = MaterialTheme.typography.titleLarge)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                s.blocks.forEach { b ->
                    when (b) {
                        is Pdr.Para -> Text(b.text, style = ReadingText)
                        is Pdr.Heading -> Text(b.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        is Pdr.Table -> PdrTable(b)
                    }
                }
            }
        }
        val at = p.sections.indexOf(s)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            p.sections.getOrNull(at - 1)?.let { prev ->
                OutlinedButton(onClick = { nav.replace(Route.PdrRoute(prev.num)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("← " + stringResource(R.string.pdr_section, prev.num)) }
            }
            p.sections.getOrNull(at + 1)?.let { next ->
                OutlinedButton(onClick = { nav.replace(Route.PdrRoute(next.num)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.pdr_section, next.num) + " →") }
            }
        }
        OutlinedButton(onClick = { nav.go(Route.PdrRoute("")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.pdr_all)) }
        PdrSource(p)
    }
}

/** A table as rows; the first row is the header. Each value on its own line under its row on a narrow screen. */
@Composable
private fun PdrTable(t: Pdr.Table) {
    Column(Modifier.fillMaxWidth()) {
        t.rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { c, cell ->
                    Text(
                        cell, style = if (i == 0) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(if (c == 0) 2f else 1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdrSource(p: Pdr) {
    SourceBox(p.act, p.license, note = stringResource(R.string.pdr_official))
    Text(
        listOf(p.edition, stringResource(R.string.const_retrieved, p.retrieved), stringResource(R.string.pdr_no_pictures)).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
