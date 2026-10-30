package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
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
