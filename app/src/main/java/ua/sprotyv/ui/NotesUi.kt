package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.sprotyv.R
import ua.sprotyv.personal.Note
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.qr.Qr

/** Text for the QR screen, handed over in memory only (never in the saved screen state). */
object QrInbox {
    @Volatile var text: String = ""
}

private const val MAX_NOTE = 20_000
private const val MAX_NOTE_TITLE = 200
private const val MAX_NOTES = 500
/** Letters in all notes together: about 3 MB in the copy at most, under the 4 MB a restore accepts. */
private const val MAX_TOTAL = 1_400_000

/**
 * "My notes": free text, encrypted with the rest of "Mine", carried by the encrypted copy, hidden
 * in trip mode. Pinned notes first, then the newest. A search finds words in titles and texts.
 */
@Composable
fun NotesScreen(nav: Nav) {
    val notes by androidx.compose.runtime.remember { PersonalStore.observe { it.notes().observe() } }.collectAsStateWithLifecycle(null)
    val list = notes ?: return Loading()
    var query by remember { mutableStateOf("") }
    Text(stringResource(R.string.notes_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Room for the encrypted copy: the whole of the notes stays well within what a restore accepts
    val total = list.sumOf { it.title.length + it.text.length }
    val full = list.size >= MAX_NOTES || total >= MAX_TOTAL
    Button(enabled = !full, onClick = {
        // The note exists before its screen opens, so it has one id whatever happens to the screen
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        PersonalStore.save("note-new:" + java.util.UUID.randomUUID()) { db ->
            val id = db.notes().save(Note(updatedAt = System.currentTimeMillis()))
            if (id > 0) main.post { nav.go(Route.NoteEdit(id)) }
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.notes_new)) }
    if (full) Text(stringResource(R.string.notes_full), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    if (list.size > 3) {
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            label = { Text(stringResource(R.string.notes_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) list else list.filter { it.title.contains(q, true) || it.text.contains(q, true) }
    if (list.isEmpty()) {
        Text(stringResource(R.string.notes_empty), style = MaterialTheme.typography.bodyLarge)
        return
    }
    if (shown.isEmpty()) Text(stringResource(R.string.notes_nothing_found), style = MaterialTheme.typography.bodyLarge)
    if (shown.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            shown.forEachIndexed { i, n ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { nav.go(Route.NoteEdit(n.id)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (n.pinned) Text("📌 ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            n.title.ifBlank { n.text.lineSequence().firstOrNull().orEmpty() }.ifBlank { stringResource(R.string.notes_untitled) },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (n.text.isNotBlank()) {
                        Text(
                            n.text.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One note. Saved as it is typed (a new note is created with its first letter), so nothing is
 * lost if the app is left. A note left empty is removed. It can be shown as a QR code to pass to a
 * phone nearby with no connection, pinned, or deleted after a question.
 */
@Composable
fun NoteEditScreen(id: Long, nav: Nav) {
    // Read once; "could not read" is told apart from "not there" (deleted meanwhile)
    val read by androidx.compose.runtime.produceState<Loaded<Note?>>(Loaded.Waiting, id) {
        value = PersonalStore.read { db -> listOf(db.notes().get(id)) }?.let { Loaded.Value(it.first()) } ?: Loaded.Failed
    }
    val first = when (val r = read) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> return Text(stringResource(R.string.notes_read_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        is Loaded.Value -> r.value ?: return Text(stringResource(R.string.notes_gone), style = MaterialTheme.typography.bodyLarge)
    }
    var title by remember(first) { mutableStateOf(first.title) }
    var text by remember(first) { mutableStateOf(first.text) }
    var pinned by remember(first) { mutableStateOf(first.pinned) }
    var confirmDelete by remember { mutableStateOf(false) }
    val key = "note:${first.id}"
    fun save() {
        val n = Note(first.id, title, text, pinned, System.currentTimeMillis())
        PersonalStore.save(key) { db -> if (db.notes().get(n.id) != null) db.notes().save(n) }
    }
    // A note left empty goes; decided on the worker, after every save before it
    DisposableEffect(first.id) {
        onDispose {
            val gone = first.id
            PersonalStore.save("note-empty:$gone") { db ->
                db.notes().get(gone)?.let { n -> if (n.title.isBlank() && n.text.isBlank()) db.notes().delete(gone) }
            }
        }
    }
    OutlinedTextField(
        value = title, onValueChange = { title = it.take(MAX_NOTE_TITLE); save() },
        label = { Text(stringResource(R.string.notes_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(MAX_NOTE); save() },
        label = { Text(stringResource(R.string.notes_text)) }, minLines = 8, modifier = Modifier.fillMaxWidth(),
        supportingText = if (text.length > MAX_NOTE - 2_000) ({ Text(stringResource(R.string.notes_length, text.length, MAX_NOTE)) }) else null,
    )
    Toggle(stringResource(R.string.notes_pin), pinned) { pinned = it; save() }
    val whole = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
    val fits = whole.isNotBlank() && whole.toByteArray(Charsets.UTF_8).size <= Qr.MAX_PLAN_BYTES
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = fits, onClick = { QrInbox.text = whole; nav.go(Route.QrText) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.notes_as_qr))
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.delete_row), color = MaterialTheme.colorScheme.error)
        }
    }
    if (whole.isNotBlank() && !fits) Text(stringResource(R.string.notes_too_long_for_qr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.notes_saved_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.notes_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Same key: a save still waiting is replaced by the deletion
                    PersonalStore.save(key) { it.notes().delete(first.id) }
                    nav.back()
                }) { Text(stringResource(R.string.delete_row)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
