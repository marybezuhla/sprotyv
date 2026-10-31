package ua.sprotyv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ua.sprotyv.R

/**
 * Settings: each kind of notification on its own switch, all off by default. Turning one on asks
 * Android's permission once; if the owner said no in Android, a button opens that setting.
 */
@Composable
fun NotifySettings() {
    val context = LocalContext.current
    val version by Notify.version.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
    var waiting by remember { mutableStateOf<Notify.Kind?>(null) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        waiting?.let { if (granted) Notify.set(context, it, true) }
        denied = !granted
        waiting = null
        refresh++
    }
    // Read again after a return from Android's settings
    val system = remember(refresh, version) { Notify.allowedBySystem(context) }
    val neutral = remember(refresh) { Neutral.isOn(context) }

    fun turn(kind: Notify.Kind, on: Boolean) {
        if (!on) return Notify.set(context, kind, false)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted || android.os.Build.VERSION.SDK_INT < 33) Notify.set(context, kind, true)
        else { waiting = kind; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    Text(stringResource(R.string.notify_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(
        Notify.Kind.TOURNIQUET to R.string.notify_kind_tourniquet,
        Notify.Kind.WAIT to R.string.notify_kind_wait,
        Notify.Kind.COUNTS to R.string.notify_kind_counts,
        Notify.Kind.SUPPLIES to R.string.notify_kind_supplies,
        Notify.Kind.NOTES to R.string.notify_kind_notes,
        Notify.Kind.KIT to R.string.notify_kind_kit,
    ).forEach { (kind, label) ->
        val on = remember(version, refresh) { Notify.isOn(context, kind) }
        Toggle(stringResource(label), on) { turn(kind, it) }
    }
    if (neutral) Text(stringResource(R.string.notify_neutral), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
    val anyOn = remember(version, refresh) { Notify.Kind.entries.any { Notify.isOn(context, it) } }
    // Also after a "no" in Android: the switch alone would seem to do nothing
    if ((anyOn || denied) && !system) {
        Text(stringResource(R.string.notify_blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.notify_open_settings)) }
    }
}

/**
 * Turns [kind] on, asking Android's permission first when needed; then [then] runs. The returned
 * function is what a button calls.
 */
@Composable
fun rememberTurnOn(kind: Notify.Kind): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Notify.set(context, kind, true); after?.invoke() }
        after = null
    }
    return { then ->
        val granted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) { if (!Notify.isOn(context, kind)) Notify.set(context, kind, true); then() }
        else { after = then; ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

/**
 * A note's reminder: a day and a time, then a notification with no words of the note in it.
 * Only the note's number and the time are kept outside the encrypted data.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NoteReminder(id: Long) {
    val context = LocalContext.current
    val version by Notify.version.collectAsState()
    val at = remember(version, id) { Notify.noteReminder(context, id)?.takeIf { it > System.currentTimeMillis() - 60_000 } }
    val turnOn = rememberTurnOn(Notify.Kind.NOTES)
    var pickDay by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val zone = java.time.ZoneId.systemDefault()

    if (at != null) {
        val words = java.time.Instant.ofEpochMilli(at).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        Text(stringResource(R.string.notes_remind_at, words), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDay = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.notes_remind_change)) }
            androidx.compose.material3.TextButton(onClick = { Notify.setNoteReminder(context, id, null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.notes_remind_off))
            }
        }
    } else {
        OutlinedButton(onClick = { pickDay = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.notes_remind)) }
    }

    if (pickDay) {
        // The picker counts days in UTC
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis >= java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickDay = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pickDay = false
                    day = state.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                }) { Text(stringResource(R.string.notes_remind_next)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pickDay = false }) { Text(stringResource(R.string.cancel)) } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
    day?.let { d ->
        val time = androidx.compose.material3.rememberTimePickerState(9, 0, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { day = null },
            text = { androidx.compose.material3.TimePicker(time) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    day = null
                    val chosen = d.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
                    // A time already gone today: tomorrow is not guessed, the owner picks again
                    if (chosen > System.currentTimeMillis()) turnOn { Notify.setNoteReminder(context, id, chosen) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { day = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
