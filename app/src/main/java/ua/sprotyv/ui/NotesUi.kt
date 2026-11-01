package ua.sprotyv.ui

import androidx.compose.material.icons.filled.Close

import androidx.compose.foundation.layout.size

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/** A note to create when "My notes" opens (from the screen for the ambulance crew). In memory only. */
object NoteInbox {
    @Volatile var title: String = ""
    @Volatile var text: String = ""
}

private const val MAX_NOTE = 20_000
private const val MAX_NOTE_TITLE = 200
private const val MAX_NOTES = ua.sprotyv.personal.backup.BackupData.MAX_NOTES
/** Letters in all notes together: about 3 MB in the copy at most, under the 8 MB a restore accepts. */
private const val MAX_TOTAL = ua.sprotyv.personal.backup.BackupData.MAX_NOTES_TEXT

/**
 * "My notes": free text, encrypted with the rest of "Mine", carried by the encrypted copy, hidden
 * in trip mode. Pinned notes first, then the newest. A search finds words in titles and texts.
 */
@Composable
fun NotesScreen(nav: Nav) {
    val notes by androidx.compose.runtime.remember { PersonalStore.observe { it.notes().observe() } }.collectAsStateWithLifecycle(null)
    val list = notes ?: return Loading()
    var query by remember { mutableStateOf("") }
    // A note handed over from another screen: created once, then opened
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val t = NoteInbox.text
        if (t.isNotBlank()) {
            val title = NoteInbox.title
            NoteInbox.text = ""; NoteInbox.title = ""
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            PersonalStore.save("note-new:" + java.util.UUID.randomUUID()) { db ->
                val id = db.notes().save(Note(text = listOf(title.take(MAX_NOTE_TITLE), t).filter { it.isNotBlank() }.joinToString("\n").take(MAX_NOTE), updatedAt = System.currentTimeMillis()))
                if (id > 0) main.post { nav.go(Route.NoteEdit(id)) }
            }
        }
    }
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
    // "My experience": when the guide was needed in life — only when the owner wants to write it
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.OutlinedButton(enabled = !full, onClick = {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val text = experienceTemplate(context)
        PersonalStore.save("note-new:" + java.util.UUID.randomUUID()) { db ->
            val id = db.notes().save(Note(text = text, updatedAt = System.currentTimeMillis()))
            if (id > 0) main.post { nav.go(Route.NoteEdit(id)) }
        }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.experience_new)) }
    androidx.compose.material3.TextButton(onClick = { nav.go(Route.Situation) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.experience_support)) }
    if (full) Text(stringResource(R.string.notes_full), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    if (list.size > 3) {
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(100) },
            label = { ChipText(stringResource(R.string.notes_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
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
        val (top, rest) = shown.partition { it.pinned }
        if (top.isNotEmpty()) {
            Text(stringResource(R.string.notes_pinned), style = BlockTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.forEach { NoteCard(it, pinned = true) { nav.go(Route.NoteEdit(it.id)) } }
        }
        if (rest.isNotEmpty() && top.isNotEmpty()) {
            Text(stringResource(R.string.notes_others), style = BlockTitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rest.forEach { NoteCard(it, pinned = false) { nav.go(Route.NoteEdit(it.id)) } }
    }
}

/** A note's name in the list: its first line that has words (a title from older notes first). */
fun Note.name(): String = title.ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.trim()

/** The rest of the note after its name, on one line; list items with their boxes. */
fun Note.preview(): String {
    val body = if (title.isNotBlank()) text else text.lineSequence().dropWhile { it.isBlank() }.drop(1).joinToString("\n")
    return readable(body).replace('\n', ' ').trim()
}

// ---- Lists: a note whose lines are "[ ] item" / "[x] item", kept in the same encrypted text

private const val BOX = "[ ] "
private const val TICK = "[x] "

/** One item of a list note. */
private data class Item(val text: String, val done: Boolean)

private fun isList(body: List<String>) = body.any { it.startsWith(BOX) || it.startsWith(TICK) }

private fun itemsOf(body: List<String>): List<Item> =
    body.filter { it.isNotBlank() }.map { l -> if (l.startsWith(TICK)) Item(l.removePrefix(TICK), true) else Item(l.removePrefix(BOX), false) }

private fun listText(name: String, items: List<Item>) =
    (listOf(name) + items.map { (if (it.done) TICK else BOX) + it.text }).joinToString("\n")

/** For QR codes and sending: boxes people can read. */
fun readable(text: String): String = text.lines().joinToString("\n") { l ->
    when { l.startsWith(TICK) -> "☑ " + l.removePrefix(TICK); l.startsWith(BOX) -> "☐ " + l.removePrefix(BOX); else -> l }
}

/** Paper: the light plate of notes, a neutral colour, a little lighter than the screen. */
@Composable
fun paper(): androidx.compose.ui.graphics.Color =
    if (LocalContrast.current) MaterialTheme.colorScheme.surfaceVariant
    else if (LocalDark.current) androidx.compose.ui.graphics.Color(0xFF3A3B40) else androidx.compose.ui.graphics.Color(0xFFFBF8EF)

/** One note in the list: pinned ones on paper with a pin, the others plain with a thin border. */
@Composable
private fun NoteCard(n: Note, pinned: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (pinned) paper() else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (pinned) 2.dp else 1.dp, if (pinned) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) Text("📌 ", style = MaterialTheme.typography.titleMedium)
                Text(
                    n.name().ifBlank { stringResource(R.string.notes_untitled) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val more = n.preview()
            if (more.isNotBlank()) {
                Text(
                    more, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Start), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Mine": the notes on their own paper plate, with up to three pinned or newest names. */
@Composable
fun NotesPlate(nav: Nav) {
    val notes by remember { PersonalStore.observe { it.notes().observe() } }.collectAsStateWithLifecycle(null)
    val list = notes.orEmpty()
    Card(
        colors = CardDefaults.cardColors(containerColor = paper()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().clickable { nav.go(Route.Mine(MinePart.NOTES)) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Text(
                    stringResource(R.string.notes_title_screen), style = BlockTitle,
                    modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { heading() },
                )
                Text(if (list.isEmpty()) "" else list.size.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
            if (list.isEmpty()) {
                Text(stringResource(R.string.notes_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                list.take(3).forEach { n ->
                    Text(
                        (if (n.pinned) "📌 " else "• ") + n.name().ifBlank { stringResource(R.string.notes_untitled) },
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
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
    val context = androidx.compose.ui.platform.LocalContext.current
    // Read once; "could not read" is told apart from "not there" (deleted meanwhile)
    val read by androidx.compose.runtime.produceState<Loaded<Note?>>(Loaded.Waiting, id) {
        value = PersonalStore.read { db -> listOf(db.notes().get(id)) }?.let { Loaded.Value(it.first()) } ?: Loaded.Failed
    }
    val first = when (val r = read) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> return Text(stringResource(R.string.notes_read_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        is Loaded.Value -> r.value ?: return Text(stringResource(R.string.notes_gone), style = MaterialTheme.typography.bodyLarge)
    }
    // One field: the first line is what the list shows as the note's name. A note written before
    // with a separate title shows it as its first line, and is kept so at the first change.
    var text by remember(first) { mutableStateOf(listOf(first.title, first.text).filter { it.isNotBlank() }.joinToString("\n")) }
    var pinned by remember(first) { mutableStateOf(first.pinned) }
    var confirmDelete by remember { mutableStateOf(false) }
    val key = "note:${first.id}"
    fun save() {
        val n = Note(first.id, "", text, pinned, System.currentTimeMillis())
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
    // A new note starts with the cursor in it
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(first.id) { if (text.isEmpty()) runCatching { focus.requestFocus() } }
    // A plain note or a list with boxes, as the owner likes; switching keeps every word
    val lines = text.lines()
    val list = isList(lines.drop(1))
    ChoiceRow {
        androidx.compose.material3.FilterChip(selected = !list, onClick = {
            if (list) { text = (listOf(lines.first()) + itemsOf(lines.drop(1)).map { it.text }).joinToString("\n"); save() }
        }, label = { ChipText(stringResource(R.string.notes_kind_text)) }, modifier = Modifier.heightIn(min = 48.dp))
        androidx.compose.material3.FilterChip(selected = list, onClick = {
            if (!list) { text = listText(lines.first(), lines.drop(1).filter { it.isNotBlank() }.map { Item(it, false) }.ifEmpty { listOf(Item("", false)) }); save() }
        }, label = { ChipText(stringResource(R.string.notes_kind_list)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    if (!list) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(MAX_NOTE); save() },
            placeholder = { Text(stringResource(R.string.notes_text_hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 6, modifier = Modifier.fillMaxWidth().focusRequester(focus),
            supportingText = if (text.length > MAX_NOTE - 2_000) ({ Text(stringResource(R.string.notes_length, text.length, MAX_NOTE)) }) else null,
        )
    } else {
        val name = lines.first()
        val items = itemsOf(lines.drop(1))
        fun put(newName: String, newItems: List<Item>) { text = listText(newName, newItems).take(MAX_NOTE); save() }
        OutlinedTextField(
            value = name, onValueChange = { v -> put(v.replace("\n", " "), items) }, singleLine = true,
            label = { ChipText(stringResource(R.string.notes_list_name)) }, textStyle = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        // Not done first, done below, each keeping its own order
        val order = items.indices.sortedBy { if (items[it].done) 1 else 0 }
        order.forEach { i ->
            val it0 = items[i]
            androidx.compose.runtime.key(i) {
                Row(Modifier.doneRow(it0.done), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(colors = doneCheckbox(), checked = it0.done, onCheckedChange = { v -> put(name, items.toMutableList().also { l -> l[i] = it0.copy(done = v) }) })
                    androidx.compose.foundation.text.BasicTextField(
                        value = it0.text,
                        onValueChange = { v -> put(name, items.toMutableList().also { l -> l[i] = it0.copy(text = v.replace("\n", " ")) }) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = doneText(it0.done),
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).padding(vertical = 12.dp),
                    )
                    androidx.compose.material3.IconButton(onClick = { put(name, items.filterIndexed { k, _ -> k != i }) }, modifier = Modifier.size(48.dp)) {
                        androidx.compose.material3.Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete_row))
                    }
                }
            }
        }
        TextButton(onClick = { put(name, items + Item("", false)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.notes_list_add)) }
    }
    Toggle(stringResource(R.string.notes_pin), pinned) { pinned = it; save() }
    NoteReminder(first.id)
    val whole = readable(text).trim()
    val fits = whole.isNotBlank() && whole.toByteArray(Charsets.UTF_8).size <= Qr.MAX_PLAN_BYTES
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = fits, onClick = { QrInbox.text = whole; nav.go(Route.QrText) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.notes_as_qr))
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.delete_row), color = MaterialTheme.colorScheme.error)
        }
    }
    // Sending openly: the text leaves the encryption, so the owner reads what that means first
    var confirmSend by remember { mutableStateOf(false) }
    OutlinedButton(enabled = whole.isNotBlank(), onClick = { confirmSend = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.notes_send_open))
    }
    if (confirmSend) {
        AlertDialog(
            onDismissRequest = { confirmSend = false },
            title = { Text(stringResource(R.string.notes_send_title)) },
            text = { Text(stringResource(R.string.notes_send_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSend = false
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, whole)
                    val chooser = android.content.Intent.createChooser(send, null)
                        // Never back into Sprotyv's own "Share" entry (it keeps web pages)
                        .putExtra(android.content.Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(context, "ua.sprotyv.ShareIn")))
                    // No result comes back, so "Mine" closes as usual once another app takes over
                    try { context.startActivity(chooser) } catch (e: Exception) { }
                }) { Text(stringResource(R.string.notes_send_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirmSend = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (whole.isNotBlank() && !fits) Text(stringResource(R.string.notes_too_long_for_qr), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.notes_saved_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.notes_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Same key: a save still waiting is replaced by the deletion
                    PersonalStore.save(key) { it.notes().delete(first.id) }
                    Notify.setNoteReminder(context, first.id, null)
                    nav.back()
                }) { Text(stringResource(R.string.delete_row)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}


/** "My experience" notes start with this mark, so they can be told apart and counted for a shield. */
const val EXPERIENCE_MARK = "◆ "

/**
 * A short look back, as rescuers and medics do after a call: what happened, what helped, what to
 * do differently. Calmly reviewing it keeps the skill and gives back a sense of control; it can also
 * be a hard memory, so it is never asked for, and support is one tap away on the notes screen.
 */
fun experienceTemplate(context: android.content.Context): String =
    EXPERIENCE_MARK + context.getString(R.string.experience_title) + "\n" +
        context.getString(R.string.experience_what) + " \n" +
        context.getString(R.string.experience_helped) + " \n" +
        context.getString(R.string.experience_next) + " "

