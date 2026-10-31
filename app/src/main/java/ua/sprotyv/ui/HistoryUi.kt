package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.History
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach

private val History.Kind.title: Int get() = when (this) {
    History.Kind.STOOD -> R.string.history_stood
    History.Kind.LOST -> R.string.history_lost
    History.Kind.WORD -> R.string.history_word
    History.Kind.SUMMARY -> R.string.history_summary
}

private val History.Kind.hint: Int get() = when (this) {
    History.Kind.STOOD -> R.string.history_stood_hint
    History.Kind.LOST -> R.string.history_lost_hint
    History.Kind.WORD -> R.string.history_word_hint
    History.Kind.SUMMARY -> R.string.history_summary_hint
}

/** "Why resist": the stories in three plates, the conclusion first. */
@Composable
fun HistoryScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.history_title), nav::back) {
        if (content == null) return@Frame Loading()
        val h = content.history ?: return@Frame Broken()
        Text(stringResource(R.string.history_intro), style = MaterialTheme.typography.bodyLarge)
        h.stories.filter { it.kind == History.Kind.SUMMARY }.forEach { s ->
            Block(s.title, Group.PREPARE, tint = resist(), padded = false) {
                BlockRow(stringResource(R.string.history_summary_open), s.lesson, first = true) { nav.go(Route.Story(s.id)) }
            }
        }
        listOf(History.Kind.STOOD, History.Kind.LOST, History.Kind.WORD).forEach { kind ->
            val list = h.stories.filter { it.kind == kind }
            if (list.isEmpty()) return@forEach
            Block(stringResource(kind.title), Group.PREPARE, tint = resist(), padded = false, foldable = true, startOpen = kind == History.Kind.STOOD,
                summary = stringResource(kind.hint)) {
                list.forEachIndexed { i, s -> BlockRow(s.title, s.time, first = i == 0) { nav.go(Route.Story(s.id)) } }
            }
        }
    }
}

/** One story on one screen: what happened, how it ended, the lesson; then its sources. */
@Composable
fun StoryScreen(content: Content?, id: String, nav: Nav) {
    val s = content?.history?.story(id)
    Frame(s?.title ?: stringResource(R.string.history_title), nav::back) {
        if (content == null) return@Frame Loading()
        val h = content.history
        if (h == null || s == null) return@Frame Broken()
        if (s.time.isNotBlank()) Text(s.time, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.kind == History.Kind.SUMMARY) {
            Text(s.happened, style = ReadingText)
            Text(s.outcome, style = ReadingText)
        } else {
            Block(stringResource(R.string.history_happened), Group.GUIDE) { Text(s.happened, style = ReadingText) }
            Block(stringResource(R.string.history_outcome), Group.GUIDE) { Text(s.outcome, style = ReadingText) }
        }
        Block(stringResource(R.string.history_lesson), Group.PREPARE, tint = resist()) {
            Text(s.lesson, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (s.sources.isNotEmpty()) {
            val context = LocalContext.current
            Block(stringResource(R.string.history_sources), Group.GUIDE, padded = false) {
                s.sources.forEachIndexed { i, src -> BlockRow(src.title, stringResource(R.string.history_source_open), first = i == 0) { Reach.open(context, src.url) } }
            }
            Text(stringResource(R.string.history_checked, s.checked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The neighbours, so the stories read one after another
        val all = h.stories
        val at = all.indexOfFirst { it.id == id }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            all.getOrNull(at - 1)?.let { prev -> OutlinedButton(onClick = { nav.replace(Route.Story(prev.id)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("← " + stringResource(R.string.history_prev)) } }
            all.getOrNull(at + 1)?.let { next -> OutlinedButton(onClick = { nav.replace(Route.Story(next.id)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.history_next) + " →") } }
        }
        OutlinedButton(onClick = { nav.go(Route.HistoryRoute) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.history_all)) }
    }
}
