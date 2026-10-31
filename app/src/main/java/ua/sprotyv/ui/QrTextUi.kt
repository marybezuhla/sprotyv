package ua.sprotyv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.qr.Qr

/**
 * Any text as a QR code, to pass to someone nearby when there is no connection: an address, a
 * number, a short note. The other phone reads it with its camera. The text lives only on this
 * screen (not saved, not kept when the screen closes), and the screen stays off screenshots
 * unless the owner allowed them.
 */
@Composable
fun QrTextScreen(nav: Nav) {
    // remember, not rememberSaveable: the text is never written into the saved screen state
    var text by remember { mutableStateOf("") }
    val bytes = text.toByteArray(Charsets.UTF_8).size
    val tooLong = bytes > Qr.MAX_PLAN_BYTES
    SecureScreen()
    Frame(stringResource(R.string.qr_text_title), nav::back) {
        Text(stringResource(R.string.qr_text_intro), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(2_000) },
            label = { Text(stringResource(R.string.qr_text_field)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(if (tooLong) R.string.qr_text_too_long else R.string.qr_text_size, bytes, Qr.MAX_PLAN_BYTES),
            style = MaterialTheme.typography.bodyMedium,
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (text.isNotBlank() && !tooLong) {
            // Fewer bytes, a coarser code: read more easily from a screen in poor light
            val bitmap = remember(text) { qrBitmap(text, 720, if (bytes <= Qr.MAX_BYTES) Qr.MAX_BYTES else Qr.MAX_PLAN_BYTES).asImageBitmap() }
            val described = stringResource(R.string.qr_text_code_desc)
            // Always black on white, also in the dark theme: cameras read it best so
            Box(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
                Image(
                    bitmap, contentDescription = null, filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = described },
                )
            }
            Text(stringResource(R.string.qr_text_bright), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { text = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.qr_text_clear)) }
        }
    }
}
