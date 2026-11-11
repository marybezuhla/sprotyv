package ua.sprotyv.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Preset
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

// Settings, the language, trip mode and the neutral icon, and the About part (moved out of TilesUi.kt)

/** Settings: the look, the tiles and the lock. */
@Composable
fun SettingsScreen(prefs: HomePrefs.State, nav: Nav) {
    val context = LocalContext.current
    val canLock = Guard.available(context)
    val locked = prefs.locked
    Frame(stringResource(R.string.settings), nav::back) {
        // Settings in a few calm blocks, each answering one question
        Block(stringResource(R.string.mode_title), ua.sprotyv.home.Group.PREPARE, foldable = true) {
        ModeChoice(prefs.mode) { m -> HomePrefs.update(context) { it.copy(mode = m) } }
        if (prefs.mode != Mode.KID) {
            Toggle(stringResource(R.string.call_bar_toggle), prefs.callBar) { on -> HomePrefs.update(context) { it.copy(callBar = on) } }
            Text(stringResource(R.string.call_bar_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (prefs.mode == Mode.FULL) {
            Toggle(stringResource(R.string.ready_on_home), !prefs.readinessHidden) { on -> HomePrefs.update(context) { it.copy(readinessHidden = !on) } }
        }
        }
        Block(stringResource(R.string.lang_title), ua.sprotyv.home.Group.GUIDE, foldable = true) { LanguageChoice() }
        Block(stringResource(R.string.theme_title), ua.sprotyv.home.Group.GUIDE, foldable = true) {
        ThemeChoice.entries.forEach { t ->
            val on = t == prefs.theme
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton) { HomePrefs.update(context) { it.copy(theme = t) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = on, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(t.title), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (prefs.theme != ThemeChoice.CONTRAST) {
            Toggle(stringResource(R.string.theme_dynamic), prefs.dynamic) { on -> HomePrefs.update(context) { it.copy(dynamic = on) } }
            Text(stringResource(R.string.theme_dynamic_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        if (prefs.mode != Mode.KID) {
            Block(stringResource(R.string.my_home), ua.sprotyv.home.Group.PREPARE, padded = false, foldable = true) {
                BlockRow(stringResource(R.string.edit_tiles), stringResource(R.string.edit_tiles_hint), first = true) { nav.go(Route.EditTiles) }
                BlockRow(stringResource(R.string.presets_title), stringResource(R.string.presets_hint)) { nav.go(Route.Presets) }
            }
        }
        Block(stringResource(R.string.lock_title), ua.sprotyv.home.Group.MINE, foldable = true) {
        if (prefs.mode == Mode.KID && !locked) {
            Text(stringResource(R.string.kid_lock_advice), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        if (canLock) {
            Toggle(stringResource(R.string.lock_toggle), locked) { on ->
                // Settings are open now; turning the lock on does not shut them on the spot
                if (on) Guard.keepOpen()
                HomePrefs.update(context) { it.copy(locked = on) }
            }
        } else {
            Text(stringResource(R.string.lock_no_screen_lock), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.lock_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The child's "Call my people" without the lock on "My data"
        var kidOpen by remember { mutableStateOf(KidCall.isOn(context)) }
        val kidScope = androidx.compose.runtime.rememberCoroutineScope()
        Toggle(stringResource(R.string.kid_call_open_toggle), kidOpen) { on -> KidCall.set(context, on); kidOpen = on; if (on) kidScope.launch { KidCall.syncNow(context) } }
        Text(stringResource(R.string.kid_call_open_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Screenshots of the owner's screens: off by default, turned on only after reading what it means
        var askScreenshots by rememberSaveable { mutableStateOf(false) }
        Toggle(stringResource(R.string.screenshots_allow), prefs.screenshots) { on ->
            if (on) askScreenshots = true else HomePrefs.update(context) { it.copy(screenshots = false) }
        }
        Text(stringResource(R.string.screenshots_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (askScreenshots) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askScreenshots = false },
                title = { Text(stringResource(R.string.screenshots_confirm_title)) },
                text = { Text(stringResource(R.string.screenshots_confirm_text)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        askScreenshots = false
                        HomePrefs.update(context) { it.copy(screenshots = true) }
                    }) { Text(stringResource(R.string.screenshots_confirm_yes)) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askScreenshots = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        }
        Block(stringResource(R.string.lock_mine_title), ua.sprotyv.home.Group.MINE, foldable = true) { LockMineSettings() }
        Block(stringResource(R.string.notify_title), ua.sprotyv.home.Group.PREPARE, foldable = true) { NotifySettings() }
        // Backup and moving to a new phone, where people look for it;
        // the screen itself is in "Mine", so trip mode hides it here too
        if (!prefs.trip) {
            Block(stringResource(R.string.backup_title), ua.sprotyv.home.Group.MINE, padded = false, foldable = true) {
                BlockRow(stringResource(R.string.backup_settings_row), stringResource(R.string.backup_settings_hint), first = true) { nav.go(Route.Mine(MinePart.BACKUP)) }
            }
        }
        Block(stringResource(R.string.settings_other_hands), ua.sprotyv.home.Group.DANGER, foldable = true) {
            TripToggle(prefs.trip)
            NeutralToggle()
        }
        // Medical training is a role, kept encrypted in "My data" (no longer a plain setting)
        Block(stringResource(R.string.medic_title), ua.sprotyv.home.Group.FIRST_AID, foldable = true) {
            Text(stringResource(R.string.medic_where), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BlockRow(stringResource(R.string.roles_title), first = true) { nav.go(Route.Mine(MinePart.ROLES)) }
        }
        Block(stringResource(R.string.colour_title), ua.sprotyv.home.Group.GUIDE, foldable = true) { ColourLegend() }
        Block(stringResource(R.string.settings_about), ua.sprotyv.home.Group.GUIDE, foldable = true) {
            AboutApp()
            // The author's letter and Support Ukraine, as in the author's other apps; never in trip mode
            // or with the neutral icon: a phone in the wrong hands must not show them
            val context = androidx.compose.ui.platform.LocalContext.current
            val hidden = prefs.trip || remember(prefs.trip) { ua.sprotyv.ui.Neutral.isOn(context) }
            if (!hidden) {
                AuthorLetter()
                SupportUkraine(open = { ua.sprotyv.reach.Reach.open(context, it) })
            }
            BlockRow(stringResource(R.string.about_guide), first = true) { nav.go(Route.About) }
            FeedbackBlock()
        }
    }
}

/** Feedback, and the last crash's technical description if there is one: shown in full, sent only by tap. */
@Composable
internal fun FeedbackBlock() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.pending(context)) }
    Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    crash?.let { t ->
        Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.crash_text), style = MaterialTheme.typography.bodyMedium)
        Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                Feedback.open(context, context.getString(R.string.crash_issue_title), context.getString(R.string.crash_issue_body) + "\n\n```\n" + t + "\n```")
                CrashLog.clear(context)
                crash = null
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crash_send)) }
            androidx.compose.material3.TextButton(onClick = { CrashLog.clear(context); crash = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.crash_no))
            }
        }
    }
    OutlinedButton(onClick = {
        Feedback.open(context, context.getString(R.string.feedback_title), context.getString(R.string.feedback_body, Feedback.version(context)))
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.feedback)) }
    Text(stringResource(R.string.feedback_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The neutral name and icon, turned on only after reading what it does and does not hide. */
@Composable
internal fun NeutralToggle() {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var on by remember { mutableStateOf(Neutral.isOn(context)) }
    var ask by rememberSaveable { mutableStateOf(false) }
    Toggle(stringResource(R.string.neutral_toggle), on) { want ->
        if (want) ask = true else { Neutral.set(context, false); on = false; activity?.let(Neutral::applyToTask) }
    }
    Text(stringResource(R.string.neutral_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.neutral_confirm_title)) },
            text = { Text(stringResource(R.string.neutral_confirm_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ask = false
                    Neutral.set(context, true); on = true; activity?.let(Neutral::applyToTask)
                }) { Text(stringResource(R.string.neutral_confirm_yes)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { ask = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Trip mode: on in one tap after reading what it does; off only with the phone's PIN, asked every
 * time. Needs a screen lock, or anyone could turn it off.
 */
@Composable
internal fun TripToggle(on: Boolean) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var ask by rememberSaveable { mutableStateOf(false) }
    val canLock = Guard.available(context)
    val prompt = stringResource(R.string.trip_off_prompt)
    Toggle(stringResource(R.string.trip_toggle), on) { want ->
        when {
            want && canLock -> ask = true
            !want && activity != null -> Guard.confirm(activity, prompt, ACTION_TRIP_OFF) { HomePrefs.update(context) { it.copy(trip = false) } }
        }
    }
    Text(
        stringResource(if (canLock) R.string.trip_hint else R.string.trip_no_lock),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (ask) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ask = false },
            title = { Text(stringResource(R.string.trip_confirm_title)) },
            text = { Text(stringResource(R.string.trip_confirm_text)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    ask = false
                    HomePrefs.update(context) { it.copy(trip = true) }
                    // No plain copy of a shared document stays behind either
                    Docs.clearShared(context)
                }) { Text(stringResource(R.string.trip_confirm_yes)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { ask = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The app's language, whatever the phone's: Ukrainian or English. Android 13 and later keep the
 * choice for this app alone; on older phones the phone's own language is used.
 */
@Composable
internal fun LanguageChoice() {
    if (android.os.Build.VERSION.SDK_INT < 33) {
        Text(stringResource(R.string.lang_old_phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val current = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
    ChoiceRow {
        listOf("" to R.string.lang_phone, "uk" to R.string.lang_uk, "en" to R.string.lang_en).forEach { (tag, label) ->
            androidx.compose.material3.FilterChip(
                selected = current.substringBefore('-') == tag,
                onClick = { androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag)) },
                label = { ChipText(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    Text(stringResource(R.string.lang_content_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The name at the top of home: "Notes" with the neutral icon or in trip mode, so a glance at the open app says no more than its icon. */
@Composable
fun appTitle(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val trip = HomePrefs.state.collectAsState().value?.trip == true
    val neutral = remember(trip) { Neutral.isOn(context) }
    return stringResource(if (neutral || trip) R.string.neutral_name else R.string.app_name)
}

/** The app's own notices, as the GPL asks of an interactive program: version, licence, no warranty, where the source is. */
@Composable
internal fun AboutApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    androidx.compose.foundation.text.selection.SelectionContainer {
        androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.about_app_version, version), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.about_app_licence), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_app_libraries), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    androidx.compose.material3.OutlinedButton(
        onClick = { ua.sprotyv.reach.Reach.open(context, SOURCE_URL) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.about_app_source)) }
    // The full licence texts travel inside the app, as the licences ask; read here without a network
    var texts by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(onClick = { texts = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.about_app_texts))
    }
    if (texts) LicenceTexts { texts = false }
}

@Composable
internal fun LicenceTexts(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val parts = remember {
        runCatching { context.assets.open("licenses.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
            .split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.statusBarsPadding().then(Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars))) {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    items(parts.size) { i ->
                        val p = parts[i]
                        if (p.startsWith("===")) Text(p.trim('=', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        else Text(p.trim(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).padding(8.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
            }
        }
    }
}

private const val SOURCE_URL = "https://github.com/marybezuhla/sprotyv"
