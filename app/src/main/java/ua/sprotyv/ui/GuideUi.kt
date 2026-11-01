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
    // The whole row in its group's soft colour, as the tiles: the contents are read by colour
    val tint = groupOf(ua.sprotyv.home.Tiles.chapter(chapter.id)).tint()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = tint.container),
        border = BorderStroke(1.dp, tint.ink.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        // The chapter's colour and picture, the same as on its tile in "Sections"
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ChapterBadge(chapter.id, 40.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column {
                if (chapter.label.isNotBlank()) {
                    Text(chapter.label, style = MaterialTheme.typography.labelMedium, color = groupOf(ua.sprotyv.home.Tiles.chapter(chapter.id)).tint().ink)
                }
                Text(chapter.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
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
        // The official first-aid procedures first: a second source beside the guide
        if (content.moz441 != null) {
            Block(stringResource(R.string.moz_title), ua.sprotyv.home.Group.FIRST_AID, padded = false) {
                BlockRow(stringResource(R.string.moz_short), stringResource(R.string.moz_hint), first = true) { nav.go(Route.MozList) }
            }
        }
        content.guide.chapters.forEach { ch -> ChapterRow(ch) { nav.go(Route.ChapterRoute(ch.id)) } }
        OutlinedButton(onClick = { nav.go(Route.About) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
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
        // The sections in one plate of the chapter's colour
        Block(stringResource(R.string.guide_sections), groupOf(ua.sprotyv.home.Tiles.chapter(chapter.id)), padded = false) {
            chapter.sections.forEachIndexed { i, s -> BlockRow((if (s.num.isNotBlank()) shownNum(s.num) + ". " else "") + s.title, first = i == 0) { nav.go(Route.GuideSection(s.num)) } }
        }
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
    Frame(found?.second?.let { "${shownNum(it.num)}. ${it.title}" } ?: "", nav::back) {
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        if (found == null) return@Frame Text(stringResource(R.string.not_found))
        Text(found.first.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Blocks(found.second.blocks)
        // The Ministry's official procedures on the same subject, one tap away
        GUIDE_MOZ[found.second.num]?.mapNotNull { content.moz441?.procedure(it) }?.takeIf { it.isNotEmpty() }?.let { list ->
            Block(stringResource(R.string.moz_compare_title), ua.sprotyv.home.Group.FIRST_AID, padded = false) {
                list.forEachIndexed { i, pr -> BlockRow("${pr.n}. ${pr.short}", first = i == 0) { nav.go(Route.Moz(pr.n)) } }
            }
        }
        SourceBox(
            stringResource(R.string.source_section, content.guide.title, content.guide.version, found.second.num),
            content.guide.license,
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
            style = if (b.intro) MaterialTheme.typography.titleMedium else ReadingText,
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
                // The colour code: danger orange for critical and warnings, green for tips, neutral for notes
                Block.Callout.Kind.CRITICAL -> ua.sprotyv.home.Group.DANGER.tint().container to R.string.callout_critical
                Block.Callout.Kind.WARNING -> ua.sprotyv.home.Group.DANGER.tint().container to R.string.callout_warning
                Block.Callout.Kind.TIP -> ua.sprotyv.home.Group.FIRST_AID.tint().container to R.string.callout_tip
                Block.Callout.Kind.NOTE -> ua.sprotyv.home.Group.GUIDE.tint().container to R.string.callout_note
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
            RichText(item.text, style = ReadingText)
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
                            RichText(cell, style = if (row.head) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
                return@Column
            }
            rows.forEachIndexed { r, row ->
                if (r > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // A row that starts with its number ("1", "2") reads as "1. Shelter", not a lone digit
                val numbered = row.cells.size >= 2 && row.cells[0].trim().replace("**", "").matches(Regex("\\d{1,2}"))
                val cells = if (numbered) listOf(row.cells[0].trim().replace("**", "") + ". " + row.cells[1]) + row.cells.drop(2) else row.cells
                val head2 = if (numbered && head != null) listOf("") + head.drop(2) else head
                val title = cells.firstOrNull().orEmpty()
                val second = cells.getOrNull(1).orEmpty()
                // A short second value (an amount, a time) goes on the title's line
                val inline = second.isNotBlank() && second.length <= 22
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        RichText(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        if (inline) {
                            Spacer(Modifier.width(12.dp))
                            RichText(second.replace("**", ""), style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                        }
                    }
                    val rest = cells.drop(if (inline) 2 else 1)
                    rest.forEachIndexed { k, cell ->
                        if (cell.isBlank()) return@forEachIndexed
                        val name = head2?.getOrNull(k + if (inline) 2 else 1)?.replace("**", "")
                        // Column names only where two or more texts sit under the title and must be told apart
                        if (rest.size >= 2 && !name.isNullOrBlank()) {
                            // The column's name, one step quieter than its text, with air above it after the first
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = if (k > 0) 6.dp else 2.dp))
                        }
                        RichText(cell, style = ReadingText)
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
            RichText(f.caption, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A section number as shown: the appendices are numbered with Ukrainian letters (А.1, Б.2, Г.3),
 * which in English read as A, B, C, D. The number itself stays the key for links.
 */
@Composable
internal fun shownNum(num: String): String {
    if (androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language != "en") return num
    val first = when (num.firstOrNull()) { 'А' -> 'A'; 'Б' -> 'B'; 'В' -> 'C'; 'Г' -> 'D'; else -> return num }
    return first + num.drop(1)
}

/** The words last searched, in memory only, so coming back from a section shows the same results. */
private object SearchMemory {
    @Volatile var query: String = ""
}

/** Offline search over the whole guide; results show where the words were found. */
@Composable
fun SearchScreen(content: Content?, nav: Nav) {
    var query by remember { mutableStateOf(SearchMemory.query) }
    val focus = remember { FocusRequester() }
    Frame(stringResource(R.string.search_guide), nav::back) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80); SearchMemory.query = query },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        // The keyboard only for a new search; coming back to results, they show first
        LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
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
                    Text("${shownNum(h.section.num)}. ${h.section.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        Text("${g.title} ${g.version}", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        g.disclaimer.forEach { RichText(it) }
        Text(stringResource(R.string.about_source, g.source), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_license, g.license), style = MaterialTheme.typography.bodyLarge)
        Text(g.changes, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.about_icons), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A quiet last line: the Constitution and the map are always inside, wherever one is
        Text(
            stringResource(R.string.about_with_you),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        )
    }
}

/**
 * Where a text comes from, the same way under every section: the source and its part, the
 * licence, and whether a specialist has reviewed it. Kept apart from the text in a quiet frame.
 */
@Composable
fun SourceBox(source: String, license: String, reviewedBy: String = "", note: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(source, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(stringResource(R.string.source_license, license), style = MaterialTheme.typography.bodyMedium)
            Text(
                note ?: if (reviewedBy.isBlank()) stringResource(R.string.not_reviewed) else stringResource(R.string.reviewed_by, reviewedBy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Guide sections and the Ministry's procedures (order No. 441) on the same subject. */
private val GUIDE_MOZ = mapOf(
    "7.1" to listOf(28, 29), "7.2" to listOf(4, 14), "7.3" to listOf(1), "7.4" to listOf(1),
    "7.5" to listOf(7, 8, 9, 10), "7.6" to listOf(16), "7.7" to listOf(1, 2, 3), "7.8" to listOf(19),
    "7.9" to listOf(13, 11), "7.10" to listOf(20, 23), "7.11" to listOf(21, 26, 27, 24, 25, 5, 6),
    "7.12" to listOf(22), "7.13" to listOf(18, 17),
)
