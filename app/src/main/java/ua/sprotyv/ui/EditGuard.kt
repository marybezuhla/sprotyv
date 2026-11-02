package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.sprotyv.R

/**
 * Unsaved changes: while a block is being edited and differs from what is stored, Back asks first
 * instead of leaving, so nothing typed is lost and nothing is changed by a stray tap.
 */
object EditGuard {
    private val dirty = mutableStateSetOf<String>()
    var asking by mutableStateOf(false)
    /** Where to go once the owner agrees to drop the changes; null: back. */
    var then: Route? = null
    /** The owner asked for the home screen. */
    var goHome = false
    /**
     * Drafts by block, in memory only (never in the saved state Android may write out): a turn
     * of the screen or a change of theme keeps what was typed. Gone when saved, cancelled or dropped.
     */
    internal val drafts = HashMap<String, Any?>()
    val isDirty: Boolean get() = dirty.isNotEmpty()
    fun mark(key: String, changed: Boolean) { if (changed) dirty.add(key) else dirty.remove(key) }
    fun clear() { dirty.clear(); drafts.clear(); asking = false }
}

/** The question Back asks while something is unsaved. */
@Composable
fun EditGuardDialog(leave: () -> Unit) {
    if (!EditGuard.asking) return
    AlertDialog(
        onDismissRequest = { EditGuard.asking = false; EditGuard.then = null; EditGuard.goHome = false },
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_text)) },
        confirmButton = { TextButton(onClick = { EditGuard.clear(); leave() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { EditGuard.asking = false; EditGuard.then = null; EditGuard.goHome = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.keep_editing)) } },
    )
}

/**
 * A block of the owner's own words shown as text; "Change" opens it for editing, and only "Save"
 * stores it. [view] shows a stored value, [edit] the fields for a draft.
 */
@Composable
fun <T> EditableBlock(
    key: String,
    title: String,
    group: ua.sprotyv.home.Group,
    stored: T,
    onSave: (T) -> Unit,
    foldable: Boolean = false,
    view: @Composable ColumnScope.(T) -> Unit,
    edit: @Composable ColumnScope.(T, (T) -> Unit) -> Unit,
) {
    var saved by remember(stored) { mutableStateOf(stored) }
    var editing by rememberSaveable(key) { mutableStateOf(false) }
    @Suppress("UNCHECKED_CAST")
    var draft by remember(saved, editing) { mutableStateOf(if (editing) (EditGuard.drafts[key] as? T) ?: saved else saved) }
    // Leaving for good (not a turn of the screen): nothing of the draft stays, not even in memory
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(key) { onDispose { if (activity?.isChangingConfigurations != true) { EditGuard.mark(key, false); EditGuard.drafts.remove(key) } } }
    LaunchedEffect(draft, saved, editing) {
        EditGuard.mark(key, editing && draft != saved)
        if (editing && draft != saved) EditGuard.drafts[key] = draft else EditGuard.drafts.remove(key)
    }
    Block(title, group, foldable = foldable, startOpen = !foldable || editing) {
        if (!editing) {
            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { view(saved) } }
            OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
        } else {
            edit(draft) { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { EditGuard.drafts.remove(key); editing = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(draft); saved = draft; EditGuard.drafts.remove(key); editing = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/** One stored value with its name above; an empty one says so quietly. */
@Composable
fun ReadField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value.isBlank()) Text(stringResource(R.string.not_filled), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
