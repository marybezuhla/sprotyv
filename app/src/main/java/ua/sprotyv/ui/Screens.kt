package ua.sprotyv.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import ua.sprotyv.R
import ua.sprotyv.personal.Opened
import ua.sprotyv.personal.PersonalStore

/** True inside a tab of full mode: the tabs draw the "Call" bar, and there is no "Back" to go to. */
val LocalInTab = staticCompositionLocalOf { false }

/** The frame of every inner screen: "Back" always in the same place, the "Call" bar always below. */
@Composable
fun Frame(title: String, onBack: () -> Unit, bottom: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val inTab = LocalInTab.current
    // While typing, the keyboard pushes the screen up instead of covering what is typed
    val typing = typing()
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!inTab) {
                IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = if (inTab) 12.dp else 0.dp).semantics { heading() },
            )
            ScreenshotsMark()
            CornerCall()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
        if (!inTab && !typing) { if (bottom != null) bottom() else CallBar() }
    }
}

/** True while the on-screen keyboard is up. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun typing(): Boolean = WindowInsets.isImeVisible

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
 * The owner may allow them in settings (off by default, kept until changed); while allowed, every
 * screen that calls this shows a small camera in its title bar, so it is never allowed unseen.
 */
@Composable
fun SecureScreen() {
    // Counted while on screen, so the title bar knows to show the camera mark
    DisposableEffect(Unit) {
        SecureScreens.open.intValue++
        onDispose { SecureScreens.open.intValue-- }
    }
    val activity = LocalContext.current as? Activity ?: return
    val allowed = HomePrefs.state.collectAsStateWithLifecycle().value?.screenshots == true
    if (allowed) return
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
 * Every screen with the owner's data goes through here: it opens only when the encrypted data is
 * ready, is kept off screenshots, and on trouble shows what happened. A passing failure or damaged
 * data offers to try again; a lost key or damaged data also offers a fresh start, which sets the
 * old data aside and deletes nothing, after one more confirmation.
 */
@Composable
fun Personal(title: Int, state: Opened?, nav: Nav, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val saveFailed by PersonalStore.saveFailed.collectAsStateWithLifecycle()
    // Once open, the screen stays while the data closes and opens again (leaving the app and
    // coming back), so a half-filled dialog is not lost
    val seen = remember { BooleanArray(1) }
    if (state is Opened.Ready) seen[0] = true
    if (state != null && state !is Opened.Ready) seen[0] = false
    val wasReady = seen[0]
    SecureScreen()
    Frame(stringResource(title), nav::back) {
        if (saveFailed) {
            Text(stringResource(R.string.save_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        }
        if (state is Opened.Ready && state.softwareKey) {
            Text(stringResource(R.string.software_key_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        // One call site for the content: while the data closes and reopens (a file picker, a
        // camera), the screen and everything it remembers stays as it is
        when {
            state is Opened.Ready || (state == null && wasReady) -> content()
            state == null -> {
                // Opening never takes long: if it seems stuck, the owner can start it again
                var slow by remember { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(6_000); slow = true }
                Loading()
                if (slow) Problem(R.string.opening_slow_title, R.string.opening_slow_text) { PersonalStore.close(); PersonalStore.open(context) }
            }
            state is Opened.Locked -> LockedView(state.pinOnly)
            state == Opened.TryAgain -> Problem(R.string.try_again_title, R.string.try_again_text) { PersonalStore.close(); PersonalStore.open(context) }
            state == Opened.NeedsUpdate -> {
                Text(stringResource(R.string.needs_update_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(stringResource(R.string.needs_update_text), style = MaterialTheme.typography.bodyLarge)
            }
            state == Opened.Damaged -> {
                Problem(R.string.damaged_title, R.string.damaged_text) { PersonalStore.close(); PersonalStore.open(context) }
                StartFresh { PersonalStore.startFresh(context) }
            }
            else -> {
                Text(stringResource(R.string.key_lost_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Text(stringResource(R.string.key_lost_text), style = MaterialTheme.typography.bodyLarge)
                StartFresh { PersonalStore.startFresh(context) }
            }
        }
    }
}

@Composable
private fun Problem(title: Int, text: Int, onTryAgain: () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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

/** How many screens with the owner's data are showing now (see [SecureScreen]). */
object SecureScreens {
    val open = androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * While screenshots of the owner's screens are allowed: a small camera in the title bar of each of
 * them — seen, but quiet. A tap says what it means and offers to switch it off.
 */
@Composable
fun ScreenshotsMark() {
    val context = LocalContext.current
    val allowed = HomePrefs.state.collectAsStateWithLifecycle().value?.screenshots == true
    if (!allowed || SecureScreens.open.intValue <= 0) return
    var asking by remember { androidx.compose.runtime.mutableStateOf(false) }
    IconButton(onClick = { asking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_camera_on), contentDescription = stringResource(R.string.screenshots_banner), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (asking) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.screenshots_banner)) },
            text = { Text(stringResource(R.string.screenshots_mark_text)) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { HomePrefs.update(context) { it.copy(screenshots = false) }; asking = false }) { Text(stringResource(R.string.screenshots_turn_off)) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text(stringResource(R.string.screenshots_keep)) } },
        )
    }
}
