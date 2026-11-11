package ua.sprotyv.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import ua.sprotyv.R
import ua.sprotyv.personal.PersonalStore
import java.io.File
import java.security.KeyStore

/**
 * "Erase all data": everything personal leaves the phone at once (the guide is built in and stays).
 * It cannot be undone, so it asks for a typed word first and says what goes and what stays.
 */
object Wipe {
    /** Both words work whatever the app's language: a phone may have only one keyboard. */
    val WORDS = setOf("СТЕРТИ", "ERASE")

    fun matches(typed: String): Boolean = typed.trim().uppercase() in WORDS

    /** True from the tap until the app closes. In memory only, so a restarted app never shows it stuck. */
    val running = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun everything(context: Context) {
        val app = context.applicationContext
        running.value = true
        Notices.finishPending()
        // The keys go at once, before waiting for anything: from this moment nothing left can be read
        Thread { deleteKeys() }.start()
        PersonalStore.closeThen { erase(app) }
    }

    private fun deleteKeys() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.aliases().toList().forEach { alias ->
                try { ks.deleteEntry(alias) } catch (e: Exception) { /* the other keys still go */ }
            }
        } catch (e: Exception) {
            // The keystore would not open: the files are erased all the same
        }
    }

    private fun erase(app: Context) {
        try {
            // Again, now that the data is closed: a key made meanwhile goes too
            deleteKeys()
            // Nothing personal stays on the lock screen or the home screen
            NotificationManagerCompat.from(app).cancelAll()
            Widgets.blank(app)
        } catch (e: Exception) {
            // Whatever failed here, the files below are erased and the app closes all the same
        }
        // Android erases all of the app's files, settings and databases, cancels its alarms and closes it
        val done = try {
            app.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
        } catch (e: Exception) {
            // Refused by this phone: erased by hand just below
            false
        }
        if (done) return
        // Android refused: the same by hand, then the app closes so nothing in memory writes back
        val data = File(app.applicationInfo.dataDir)
        listOf(app.filesDir, app.noBackupFilesDir, app.cacheDir, app.codeCacheDir, File(data, "databases"), File(data, "shared_prefs"))
            .forEach { dir -> try { dir.deleteRecursively() } catch (e: Exception) { /* the next folder still goes */ } }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

/** The line in "If the phone is in other hands", the question with the typed word, then "Erasing…" until the app closes. */
@Composable
internal fun WipeAll() {
    val context = LocalContext.current
    var ask by rememberSaveable { mutableStateOf(false) }
    val erasing by Wipe.running.collectAsState()
    var typed by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.wipe_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(
        onClick = { typed = ""; ask = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.wipe_button), color = MaterialTheme.colorScheme.error) }
    if (ask) {
        val ready = Wipe.matches(typed)
        fun go() { ask = false; Wipe.everything(context) }
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.wipe_confirm_title)) },
            text = {
                // Scrolls: with the largest text and the keyboard up the field must still be reachable
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.wipe_confirm_text))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(20) },
                        label = { Text(stringResource(R.string.wipe_type)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (ready) go() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { go() }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.wipe_yes), color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { ask = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (erasing) {
        // Nothing to press: the app closes by itself when it is done
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = { Text(stringResource(R.string.wipe_erasing), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { },
        )
    }
}
