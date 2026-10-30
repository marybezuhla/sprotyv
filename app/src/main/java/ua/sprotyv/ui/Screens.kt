package ua.sprotyv.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.personal.Opened

/** The frame of every inner screen: "Back" always in the same place, the "Call" bar always below. */
@Composable
fun Frame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
        CallBar()
    }
}

/** Sections not built yet say so plainly. */
@Composable
fun SectionScreen(section: Section, onBack: () -> Unit) {
    Frame(stringResource(section.title), onBack) {
        if (section == Section.ALARM) {
            Text(stringResource(R.string.alarm_not_yet), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * No screenshots, screen recordings or app-switcher pictures while the owner's data is on screen.
 * Removed again on leaving, so the guide and cards can still be captured and shared.
 */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(false)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(true)
        }
    }
}

/**
 * The owner's section opens only when the encrypted data is ready. A passing failure or damaged
 * only offers to try again. A lost key or damaged data also offers a fresh start, which sets the
 * old data aside and deletes nothing, after one more confirmation.
 */
@Composable
fun PersonalGate(state: Opened?, onTryAgain: () -> Unit, onStartFresh: () -> Unit, onBack: () -> Unit) {
    SecureScreen()
    Frame(stringResource(Section.MINE.title), onBack) {
        when (state) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.opening))
            }
            is Opened.Ready -> {
                if (state.softwareKey) {
                    Text(stringResource(R.string.software_key_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
            }
            Opened.TryAgain -> Problem(R.string.try_again_title, R.string.try_again_text, onTryAgain)
            Opened.Damaged -> {
                Problem(R.string.damaged_title, R.string.damaged_text, onTryAgain)
                StartFresh(onStartFresh)
            }
            Opened.KeyLost -> {
                Text(stringResource(R.string.key_lost_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.key_lost_text), style = MaterialTheme.typography.bodyLarge)
                StartFresh(onStartFresh)
            }
        }
    }
}

@Composable
private fun Problem(title: Int, text: Int, onTryAgain: () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onTryAgain, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.try_again)) }
}

/** Sets the old data aside (never deletes it) and starts fresh, after one more confirmation. */
@Composable
private fun StartFresh(onStartFresh: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.heightIn(min = 56.dp)) {
        Text(stringResource(R.string.key_lost_fresh))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.key_lost_fresh_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onStartFresh() }) { Text(stringResource(R.string.key_lost_fresh)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.key_lost_keep)) }
            },
        )
    }
}
