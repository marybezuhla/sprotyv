package ua.sprotyv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ua.sprotyv.R

/** Text with **bold** parts, as the guide marks them. */
fun rich(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
    }
}

@Composable
fun RichText(text: String, style: TextStyle = MaterialTheme.typography.bodyLarge, modifier: Modifier = Modifier) {
    Text(rich(text), style = style, modifier = modifier)
}

/** The built-in content could not be read. Calls still work from the bar below. */
@Composable
fun Broken() {
    Text(stringResource(R.string.content_broken), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}

/** Shown for the moment the built-in content is being read. */
@Composable
fun Loading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.opening))
    }
}

/**
 * The one style for a block's title on every screen: larger and bolder than anything inside the
 * block, so the eye finds the block first and then reads it top down.
 */
val BlockTitle: androidx.compose.ui.text.TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium.copy(
        fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = FontWeight.SemiBold,
    )

/**
 * A block of one theme: a coloured header with the title (the colour of the theme's group on the
 * home screen, so the same colour means the same theme everywhere), then the body on a plain
 * surface. Inside: main lines in bodyLarge, explanations in bodyMedium, the source in bodySmall.
 * [padded] false for lists that draw their own rows and dividers.
 */
@Composable
fun Block(
    title: String,
    group: ua.sprotyv.home.Group,
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Folded until tapped; [summary] says in the header what is inside. */
    foldable: Boolean = false,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    /** Its own colours instead of the group's (resistance's khaki). */
    tint: Tint? = null,
    /** A foldable block that starts open (the one to work on next). */
    startOpen: Boolean = !foldable,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tint = tint ?: group.tint()
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(startOpen) }
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.container),
        modifier = modifier.fillMaxWidth(),
    ) {
        val header = Modifier.fillMaxWidth().background(tint.container)
        Row(
            (if (foldable) header.clickable(onClickLabel = stringResource(if (open) R.string.shelf_fold else R.string.shelf_unfold)) { open = !open }.heightIn(min = 56.dp) else header)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(title, style = BlockTitle, color = tint.ink)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = tint.ink)
            }
            trailing?.invoke()
            if (foldable) {
                androidx.compose.material3.Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tint.ink,
                )
            }
        }
        if (open) {
            androidx.compose.runtime.CompositionLocalProvider(LocalBlockTint provides tint) {
                androidx.compose.foundation.layout.Column(
                    if (padded) Modifier.padding(16.dp) else Modifier.padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(if (padded) 10.dp else 0.dp),
                    content = content,
                )
            }
        }
    }
}

/** One line inside a [Block]: the name, a short line on what it is, and an arrow; split by hairlines. */
@Composable
fun BlockRow(title: String, hint: String = "", first: Boolean = false, onClick: () -> Unit) {
    if (!first) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What the four colours mean: shown in settings, so the code is never a guess. */
@Composable
fun ColourLegend() {
    listOf(
        ua.sprotyv.home.Group.DANGER to R.string.colour_danger,
        ua.sprotyv.home.Group.FIRST_AID to R.string.colour_health,
        ua.sprotyv.home.Group.PREPARE to R.string.colour_prepare,
        ua.sprotyv.home.Group.GUIDE to R.string.colour_calm,
        null to R.string.colour_resist,
    ).forEach { (g, words) ->
        val tint = g?.tint() ?: resist()
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(end = 12.dp).size(28.dp)
                    .background(tint.container, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .border(1.dp, tint.ink, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            )
            Text(stringResource(words), style = MaterialTheme.typography.bodyMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start), modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Long reading text (the guide, the Ministry's procedures): left-aligned with whole words. Justified
 * lines with hyphenation were tried and left gaps and broken words on a phone; not to be turned back on.
 */
val ReadingText: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(
        // Left-aligned, words whole: justified lines with hyphens left gaps and broken words on a phone
        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
        hyphens = androidx.compose.ui.text.style.Hyphens.None,
        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
    )

/**
 * "Done" has no colour of its own (green already means first aid and the way out): a done row takes
 * the colour of the block it sits in — the tick filled in that colour, a faint shade of it behind
 * the row — and the words stay as they are. Nothing is struck out.
 */
val LocalBlockTint = androidx.compose.runtime.staticCompositionLocalOf<Tint?> { null }

@Composable
fun doneTint(): Tint = LocalBlockTint.current ?: ua.sprotyv.home.Group.MINE.tint()

@Composable
fun Modifier.doneRow(done: Boolean): Modifier =
    if (done) this.then(Modifier.background(doneTint().container.copy(alpha = 0.55f))) else this

@Composable
fun doneCheckbox(): androidx.compose.material3.CheckboxColors =
    androidx.compose.material3.CheckboxDefaults.colors(checkedColor = doneTint().ink, checkmarkColor = MaterialTheme.colorScheme.surface)

@Composable
fun doneText(@Suppress("UNUSED_PARAMETER") done: Boolean): androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
