package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
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
import ua.sprotyv.content.Constitution
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group

/**
 * Articles most asked about in danger, with a plain topic each. The topic only says where to look;
 * the words are always the article's own, one tap away.
 */
private val KEY_ARTICLES = listOf(
    "1" to R.string.const_key_1,
    "5" to R.string.const_key_5,
    "17" to R.string.const_key_17,
    "27" to R.string.const_key_27,
    "28" to R.string.const_key_28,
    "29" to R.string.const_key_29,
    "55" to R.string.const_key_55,
    "59" to R.string.const_key_59,
    "64" to R.string.const_key_64,
    "65" to R.string.const_key_65,
    "157" to R.string.const_key_157,
)

private fun Constitution.Article.snippet(): String = parts.first().let { if (it.length > 90) it.take(88).trimEnd() + "…" else it }

/** The Constitution of Ukraine in full: the main articles first, search, then every section. */
@Composable
fun ConstitutionScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.const_title), nav::back) {
        if (content == null) return@Frame Loading()
        val c = content.constitution ?: return@Frame Broken()
        Text(stringResource(R.string.const_intro), style = MaterialTheme.typography.bodyLarge)
        var query by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) }, singleLine = true,
            label = { Text(stringResource(R.string.const_search)) }, modifier = Modifier.fillMaxWidth(),
        )
        val q = query.trim()
        if (q.length >= 2) {
            val found = c.sections.flatMap { it.articles }.filter { it.allText.contains(q, ignoreCase = true) }
            if (found.isEmpty()) Text(stringResource(R.string.notes_nothing_found), style = MaterialTheme.typography.bodyLarge)
            else Block(stringResource(R.string.const_found, found.size), Group.GUIDE, tint = resist(), padded = false) {
                found.take(60).forEachIndexed { i, a -> BlockRow(stringResource(R.string.const_article, a.num), a.snippet(), first = i == 0) { nav.go(Route.ConstitutionRoute(a.num)) } }
            }
            return@Frame
        }
        Block(stringResource(R.string.const_key_title), Group.GUIDE, tint = resist(), padded = false) {
            KEY_ARTICLES.forEachIndexed { i, (num, topic) ->
                BlockRow(stringResource(R.string.const_article, num), stringResource(topic), first = i == 0) { nav.go(Route.ConstitutionRoute(num)) }
            }
        }
        Block(stringResource(R.string.const_preamble), Group.PREPARE, tint = resist(), foldable = true, summary = stringResource(R.string.const_preamble_hint)) {
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { c.preamble.forEach { Text(it, style = ReadingText) } } }
        }
        c.sections.forEach { s ->
            Block(stringResource(R.string.const_section, s.num, s.title.lowercase().replaceFirstChar { it.uppercase() }), Group.PREPARE, tint = resist(), padded = s.articles.isEmpty(), foldable = true,
                summary = if (s.articles.isEmpty()) null else stringResource(R.string.const_articles_count, s.articles.first().num, s.articles.last().num)) {
                s.articles.forEachIndexed { i, a -> BlockRow(stringResource(R.string.const_article, a.num), a.snippet(), first = i == 0) { nav.go(Route.ConstitutionRoute(a.num)) } }
                if (s.articles.isEmpty()) SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { s.text.forEach { Text(it, style = ReadingText) } } }
            }
        }
        Text(c.closing.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ConstitutionSource(c)
    }
}

/** One article, word for word, with the edition's notes below and the neighbours one tap away. */
@Composable
fun ConstitutionArticleScreen(content: Content?, num: String, nav: Nav) {
    Frame(stringResource(R.string.const_article, num), nav::back) {
        if (content == null) return@Frame Loading()
        val c = content.constitution ?: return@Frame Broken()
        val (section, article) = c.article(num) ?: return@Frame Broken()
        Text(stringResource(R.string.const_section, section.num, section.title.lowercase().replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                article.parts.forEach { Text(it, style = ReadingText) }
            }
        }
        article.notes.forEach { Text("{$it}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val all = c.sections.flatMap { it.articles }
        val at = all.indexOfFirst { it.num == num }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            all.getOrNull(at - 1)?.let { prev ->
                OutlinedButton(onClick = { nav.replace(Route.ConstitutionRoute(prev.num)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("← " + stringResource(R.string.const_article, prev.num)) }
            }
            all.getOrNull(at + 1)?.let { next ->
                OutlinedButton(onClick = { nav.replace(Route.ConstitutionRoute(next.num)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.const_article, next.num) + " →") }
            }
        }
        OutlinedButton(onClick = { nav.go(Route.ConstitutionRoute("")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.const_all)) }
        ConstitutionSource(c)
    }
}

@Composable
private fun ConstitutionSource(c: Constitution) {
    SourceBox(c.act, c.license, note = stringResource(R.string.const_official))
    Text(
        listOf(c.edition, stringResource(R.string.const_retrieved, c.retrieved), stringResource(R.string.const_no_changes)).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
