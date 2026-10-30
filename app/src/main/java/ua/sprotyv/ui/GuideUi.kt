package ua.sprotyv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.content.Block
import ua.sprotyv.content.Chapter
import ua.sprotyv.content.Content
import ua.sprotyv.content.GuideSearch
import ua.sprotyv.content.Item
import ua.sprotyv.content.Section as GuideSection

@Composable
fun ChapterRow(chapter: Chapter, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (chapter.label.isNotBlank()) {
                Text(chapter.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(chapter.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The guide: search first (it is the quickest way in), then the chapters. */
@Composable
fun GuideScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.tile_guide), nav::back) {
        OutlinedButton(onClick = { nav.go(Route.Search) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text("  " + stringResource(R.string.search_guide))
        }
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        content.guide.chapters.forEach { ch -> ChapterRow(ch) { nav.go(Route.ChapterRoute(ch.id)) } }
        OutlinedButton(onClick = { nav.go(Route.About) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.about_guide))
        }
    }
}

/** A chapter: its short introduction and the list of its sections. */
@Composable
fun ChapterScreen(content: Content?, id: String, nav: Nav) {
    val chapter = content?.guide?.chapters?.firstOrNull { it.id == id }
    Frame(chapter?.title ?: "", nav::back) {
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        if (chapter == null) return@Frame Text(stringResource(R.string.not_found))
        Blocks(chapter.intro)
        chapter.sections.forEach { s -> SectionRow(s) { nav.go(Route.GuideSection(s.num)) } }
    }
}

@Composable
private fun SectionRow(s: GuideSection, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text(
            (if (s.num.isNotBlank()) s.num + ". " else "") + s.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

/** One section to read. A whole-chapter reference ("26") opens the chapter instead. */
@Composable
fun GuideSectionScreen(content: Content?, num: String, nav: Nav) {
    val found = content?.guide?.section(num)
    if (content != null && found == null) {
        val chapter = content.guide.chapters.firstOrNull { it.label.endsWith(" $num") }
        if (chapter != null) return ChapterScreen(content, chapter.id, nav)
    }
    Frame(found?.second?.let { "${it.num}. ${it.title}" } ?: "", nav::back) {
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        if (found == null) return@Frame Text(stringResource(R.string.not_found))
        Text(found.first.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Blocks(found.second.blocks)
        Text(
            stringResource(R.string.guide_attribution, content.guide.title, content.guide.version, content.guide.license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Blocks(blocks: List<Block>) {
    blocks.forEach { BlockView(it) }
}

@Composable
private fun BlockView(b: Block) {
    when (b) {
        is Block.Paragraph -> RichText(
            b.text,
            style = if (b.intro) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
        is Block.Heading -> Text(
            rich(b.text),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        is Block.Items -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.items.forEachIndexed { i, item ->
                val mark = when (b.kind) {
                    Block.Items.Kind.STEPS -> "${i + 1}."
                    Block.Items.Kind.CHECK -> "☐"
                    Block.Items.Kind.BULLETS -> "•"
                }
                ItemView(mark, item)
            }
        }
        is Block.Table -> TableView(b)
        is Block.Figure -> FigureView(b)
        is Block.Callout -> {
            // Red only for the guide's "critical" notes; the word, not the colour, carries the meaning
            val (bg, label) = when (b.kind) {
                Block.Callout.Kind.CRITICAL -> MaterialTheme.colorScheme.errorContainer to R.string.callout_critical
                Block.Callout.Kind.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to R.string.callout_warning
                Block.Callout.Kind.TIP -> MaterialTheme.colorScheme.primaryContainer to R.string.callout_tip
                Block.Callout.Kind.NOTE -> MaterialTheme.colorScheme.surfaceVariant to R.string.callout_note
            }
            Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Blocks(b.blocks)
                }
            }
        }
    }
}

@Composable
private fun ItemView(mark: String, item: Item, depth: Int = 0) {
    Row(Modifier.padding(start = (depth * 16).dp)) {
        val markModifier = Modifier.width(28.dp).let { m -> if (mark.first().isDigit()) m else m.clearAndSetSemantics { } }
        Text(mark, style = MaterialTheme.typography.bodyLarge, modifier = markModifier)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RichText(item.text)
            item.sub.forEach { ItemView("–", it, depth + 1) }
        }
    }
}

/**
 * Tables read as one framed list, a row per thing: its name first, a short value (an amount, a
 * time) aligned on the right of the same line, longer columns below with a quiet column name.
 * A phone is too narrow for columns; one frame with hairlines keeps the table seen as one whole,
 * and the eye runs down one edge of names and one edge of values. Wide code tables stay a grid.
 */
@Composable
private fun TableView(t: Block.Table) {
    val head = t.rows.firstOrNull()?.takeIf { it.head }?.cells
    val rows = t.rows.filter { !it.head }
    val columns = t.rows.maxOfOrNull { it.cells.size } ?: 0
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (columns > 5) {
                // A code table (letters and signs): a real grid reads best
                (listOfNotNull(t.rows.firstOrNull()?.takeIf { it.head }) + rows).forEachIndexed { r, row ->
                    if (r > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        row.cells.forEach { cell ->
                            RichText(cell, style = if (row.head) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
                return@Column
            }
            rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val title = row.cells.firstOrNull().orEmpty()
                val second = row.cells.getOrNull(1).orEmpty()
                // A short second value (an amount, a time) goes on the title's line
                val inline = second.isNotBlank() && second.length <= 22
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        RichText(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (inline) {
                            Spacer(Modifier.width(12.dp))
                            RichText(second.replace("**", ""), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    val rest = row.cells.drop(if (inline) 2 else 1)
                    rest.forEachIndexed { k, cell ->
                        if (cell.isBlank()) return@forEachIndexed
                        val name = head?.getOrNull(k + if (inline) 2 else 1)?.replace("**", "")
                        // Column names only where two or more texts sit under the title and must be told apart
                        if (rest.size >= 2 && !name.isNullOrBlank()) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RichText(cell, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Figures are drawings on white; they stay on white in the dark theme too, so they read the same. */
@Composable
fun FigureView(f: Block.Figure) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        f.pictures.forEach { name ->
            val bitmap by produceState<Bitmap?>(null, name) {
                value = withContext(Dispatchers.IO) { Content.picture(context, name) }
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    // The caption below says what it shows; reading it twice helps no one
                    contentDescription = if (f.caption.isBlank()) stringResource(R.string.figure) else null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp),
                )
            }
        }
        if (f.caption.isNotBlank()) {
            RichText(f.caption, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Offline search over the whole guide; results show where the words were found. */
@Composable
fun SearchScreen(content: Content?, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    Frame(stringResource(R.string.search_guide), nav::back) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        val result by produceState(query to emptyList<ua.sprotyv.content.Hit>(), query) {
            value = query to withContext(Dispatchers.Default) { GuideSearch.search(content.guide, query) }
        }
        val hits = result.second
        if (result.first == query && query.trim().length >= 2 && hits.isEmpty()) {
            Text(stringResource(R.string.search_none), style = MaterialTheme.typography.bodyLarge)
        }
        hits.forEach { h ->
            Card(
                onClick = { nav.go(Route.GuideSection(h.section.num)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${h.section.num}. ${h.section.title}", style = MaterialTheme.typography.titleMedium)
                    Text(h.snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Where the guide comes from, its licence and what was changed (CC BY-SA 4.0 asks for all three). */
@Composable
fun AboutScreen(content: Content?, nav: Nav) {
    Frame(stringResource(R.string.about_guide), nav::back) {
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        val g = content.guide
        Text("${g.title} ${g.version}", style = MaterialTheme.typography.titleMedium)
        g.disclaimer.forEach { RichText(it) }
        Text(stringResource(R.string.about_source, g.source), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_license, g.license), style = MaterialTheme.typography.bodyLarge)
        Text(g.changes, style = MaterialTheme.typography.bodyLarge)
    }
}
