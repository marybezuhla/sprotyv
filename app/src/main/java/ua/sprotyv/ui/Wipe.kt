package ua.sprotyv.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    fun everything(context: Context) {
        val app = context.applicationContext
        PersonalStore.closeThen { erase(app) }
    }

    private fun erase(app: Context) {
        // Keys first: if anything below is cut short, the files left behind can no longer be read
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.aliases().toList().forEach { alias ->
                try { ks.deleteEntry(alias) } catch (e: Exception) { /* the other keys still go */ }
            }
        } catch (e: Exception) {
            // The keystore would not open: the files below are erased all the same
        }
        // Nothing personal stays on the lock screen or the home screen
        try { NotificationManagerCompat.from(app).cancelAll() } catch (e: Exception) { /* erasing goes on */ }
        Widgets.blank(app)
        // Android erases all of the app's files, settings and databases, cancels its alarms and closes it
        val done = try {
            app.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
        } catch (e: Exception) {
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

/** The line in "If the phone is in other hands", and the question with the typed word. */
@Composable
internal fun WipeAll() {
    val context = LocalContext.current
    var ask by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.wipe_word)
    Column {
        Text(stringResource(R.string.wipe_title), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.wipe_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(
        onClick = { typed = ""; ask = true },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.wipe_button), color = MaterialTheme.colorScheme.error) }
    if (ask) {
        val ready = typed.trim().equals(word, ignoreCase = true)
        AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.wipe_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.wipe_confirm_text))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.take(20) },
                        label = { Text(stringResource(R.string.wipe_type, word)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { ask = false; Wipe.everything(context) }, enabled = ready, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.wipe_yes), color = if (ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            dismissButton = {
                TextButton(onClick = { ask = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
