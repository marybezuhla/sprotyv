package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tint = group.tint()
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.container),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().background(tint.container).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = BlockTitle, color = tint.ink, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.invoke()
        }
        androidx.compose.foundation.layout.Column(
            if (padded) Modifier.padding(16.dp) else Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (padded) 10.dp else 0.dp),
            content = content,
        )
    }
}
