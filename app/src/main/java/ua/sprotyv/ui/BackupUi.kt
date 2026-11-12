package ua.sprotyv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.backup.BackupCrypto
import java.time.LocalDate

/** A password field: hidden, no suggestions, no learning by the keyboard. */
@Composable
internal fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    // "Show" lets the owner see what was typed: a keyboard adding a letter or a capital is then seen
    var shown by remember { mutableStateOf(false) }
    // Hidden again whenever the app leaves the screen, so a shown password never stays in recents
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { onPauseOrDispose { shown = false } }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { shown = !shown }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (shown) R.string.password_hide else R.string.password_show)) }
        },
        supportingText = if (value.isNotEmpty()) { { Text(stringResource(R.string.password_length, value.length)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A suggested password, large, to copy by hand. Not selectable: a copied password would sit in
 * the clipboard, where keyboards and sync can see it.
 */
@Composable
internal fun ShownPassword(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            // The same words one by one, numbered: easier to copy onto paper and to dictate
            val words = text.split("-")
            if (words.size > 1) {
                Text(
                    words.mapIndexed { i, w -> "${i + 1}. $w" }.joinToString("\n"),
                    style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** The word list, read off the main thread; null for the first moment. */
@Composable
internal fun rememberWords(): List<String>? {
    val context = LocalContext.current
    return produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { Words.get(context) } }.value
}

/** True only when the screen goes for good: turning the phone keeps what it holds. */
@Composable
internal fun OnLeave(action: () -> Unit) {
    val activity = androidx.activity.compose.LocalActivity.current
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) action() } }
}

/**
 * Backup: one encrypted file with everything, for a new phone or a lost one. Six random words
 * are suggested; an own password must be long. Written only into a new file, then checked.
 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val busy by BackupJobs.busy.collectAsStateWithLifecycle()
    val notice by BackupJobs.notice.collectAsStateWithLifecycle()
    val words = rememberWords()
    val suggested by BackupJobs.suggested.collectAsStateWithLifecycle()
    val written by BackupJobs.written.collectAsStateWithLifecycle()
    LaunchedEffect(words, suggested) {
        if (words != null && suggested == null) BackupJobs.suggested.value = BackupCrypto.suggestPassword(words, 6)
    }
    // Typed passwords live only while this screen does: never in saved state
    var own by remember { mutableStateOf(false) }
    var ownText by remember { mutableStateOf("") }
    var ownAgain by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        BackupJobs.shown()
        onDispose { BackupJobs.hidden() }
    }
    val last by remember { PersonalStore.observe { it.settings().observe(KEY_BACKUP_AT) } }.collectAsStateWithLifecycle(null)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) BackupJobs.release() else BackupJobs.save(context, uri)
    }

    Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
    last?.toLongOrNull()?.takeIf { it > 0 }?.let { at ->
        val date = Times.date(at)
        Text(stringResource(R.string.backup_last, date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // The result in a window over the screen: seen wherever the screen is scrolled to
    notice?.let { n ->
        val good = n in setOf(R.string.backup_saved, R.string.backup_restored, R.string.backup_restored_lock, R.string.backup_restored_notify)
        AlertDialog(
            onDismissRequest = { BackupJobs.clearNotice() },
            title = { SensitiveWindow(); Text(stringResource(if (good) R.string.backup_done_title else R.string.backup_problem_title)) },
            text = { Text(stringResource(n), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { BackupJobs.clearNotice() }) { Text(stringResource(R.string.ok)) } },
        )
    }

    Block(stringResource(R.string.backup_save_title), ua.sprotyv.home.Group.MINE) {
    val pw = if (own) ownText else suggested.orEmpty()
    if (!own) {
        Text(stringResource(R.string.backup_suggested), style = MaterialTheme.typography.bodyLarge)
        suggested?.let { ShownPassword(it) }
        TextButton(onClick = {
            words?.let { BackupJobs.suggested.value = BackupCrypto.suggestPassword(it, 6) }
            BackupJobs.written.value = false
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_another))
        }
        TextButton(onClick = { own = true; BackupJobs.written.value = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_own)) }
    } else {
        PasswordField(stringResource(R.string.backup_password), ownText) { ownText = it }
        PasswordField(stringResource(R.string.backup_password_again), ownAgain) { ownAgain = it }
        val weak = ownText.isNotEmpty() && BackupCrypto.isTooSimple(ownText.toCharArray())
        if (weak) Text(stringResource(R.string.backup_weak), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (ownAgain.isNotEmpty() && ownAgain != ownText) Text(stringResource(R.string.backup_mismatch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { own = false; ownText = ""; ownAgain = ""; BackupJobs.written.value = false }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_use_suggested))
        }
    }
    Toggle(stringResource(R.string.backup_written), written) { BackupJobs.written.value = it }
    val withDocs by BackupJobs.withDocs.collectAsStateWithLifecycle()
    Toggle(stringResource(R.string.backup_with_docs), withDocs) { BackupJobs.withDocs.value = it }
    Text(stringResource(R.string.backup_with_docs_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val ready = written && !busy && pw.isNotEmpty() && (!own || (ownText == ownAgain && !BackupCrypto.isTooSimple(ownText.toCharArray())))
    Button(onClick = {
        BackupJobs.hold(pw)
        // A neutral name: the file does not say which app made it
        Away.forOwnWindow()
        // No file app to choose with: nothing left the screen after all
        try { save.launch("backup-" + LocalDate.now() + ".bin") } catch (e: android.content.ActivityNotFoundException) { Away.back() }
    }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(if (busy) R.string.backup_busy else R.string.backup_save)) }
    Text(stringResource(R.string.backup_save_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    }
    BackupRestore(busy)
}

/** Opening a backup: choose the file, type its password, see what it holds, then replace or not. */
@Composable
private fun BackupRestore(busy: Boolean) {
    val context = LocalContext.current
    val pending by BackupJobs.pending.collectAsStateWithLifecycle()
    val openUri by BackupJobs.openUri.collectAsStateWithLifecycle()
    // The typed password lives only while this screen does: never in saved state
    var openText by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { BackupJobs.openUri.value = uri; openText = "" }
    }
    Block(stringResource(R.string.backup_restore_title), ua.sprotyv.home.Group.MINE) {
    Text(stringResource(R.string.backup_restore_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { BackupJobs.clearNotice(); Away.forOwnWindow(); try { pick.launch(arrayOf("*/*")) } catch (e: android.content.ActivityNotFoundException) { Away.back() } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.backup_choose_file))
    }
    openUri?.let { uri ->
        PasswordField(stringResource(R.string.backup_password), openText) { openText = it }
        Button(onClick = { BackupJobs.open(context, uri, openText) }, enabled = openText.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (busy) R.string.backup_busy else R.string.backup_open))
        }
    }
    pending?.let { snap ->
        val date = Times.date(snap.created)
        AlertDialog(
            onDismissRequest = { BackupJobs.cancelRestore() },
            title = { SensitiveWindow(); Text(stringResource(R.string.backup_replace_title)) },
            text = {
                Text(
                    stringResource(R.string.backup_replace_text, date, snap.people, snap.records) +
                        (if (snap.skipped > 0) " " + stringResource(R.string.backup_replace_skipped) else "") +
                        (if (snap.home.isNotEmpty()) " " + stringResource(R.string.backup_replace_home) else "") +
                        " " + stringResource(R.string.backup_replace_notes, snap.notes) +
                        " " + (if (snap.docs < 0) stringResource(R.string.backup_replace_no_docs) else stringResource(R.string.backup_replace_docs, snap.docs, android.text.format.Formatter.formatShortFileSize(context, snap.docBytes))),
                )
            },
            confirmButton = {
                TextButton(onClick = { BackupJobs.openUri.value = null; openText = ""; BackupJobs.confirmRestore(context) }) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = { TextButton(onClick = { BackupJobs.cancelRestore() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    }
}

