package ua.sprotyv.ui

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.background

import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.personal.KeyVault
import ua.sprotyv.personal.LockChange
import ua.sprotyv.personal.PersonalStore
import javax.crypto.Cipher

/**
 * Asks the owner to confirm [cipher] in Android's own window: a fingerprint or the phone's PIN, or
 * the PIN only. Sprotyv never sees the PIN or the fingerprint; the key itself refuses to work
 * without this confirmation. [onDone] gets the confirmed cipher, or null when cancelled or failed.
 */
fun askOwner(activity: Activity, title: String, cipher: Cipher, pinOnly: Boolean, onDone: (Cipher?) -> Unit) {
    val prompt = BiometricPrompt.Builder(activity)
        .setTitle(title)
        .setAllowedAuthenticators(
            if (pinOnly) BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .setConfirmationRequired(false)
        .build()
    try {
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // A fingerprint window may not even pause the app: the flag is cleared here, always
                Away.back()
                onDone(result.cryptoObject?.cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                    android.widget.Toast.makeText(activity.applicationContext, errString, android.widget.Toast.LENGTH_LONG).show()
                }
                Away.back()
                onDone(null)
            }
        })
    } catch (e: Exception) {
        // The window could not even start: said, and the reason kept for feedback
        PersonalStore.noteLockTrouble(e)
        android.widget.Toast.makeText(activity.applicationContext, activity.getString(R.string.lock_mine_failed), android.widget.Toast.LENGTH_LONG).show()
        Away.back()
        onDone(null)
    }
}

/**
 * "Mine" is locked: one button to open it. No automatic asking: the PIN window sends the app to
 * the background, and asking again on every return could loop. If the key cannot be reached for a
 * moment, it says so and offers to try again, instead of asking in circles.
 */
@Composable
fun LockedView(pinOnly: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity
    val title = stringResource(R.string.lock_mine_open)
    val scope = rememberCoroutineScope()
    var trouble by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun ask() {
        if (busy || activity == null) return
        busy = true
        scope.launch {
            val unlocking = withContext(Dispatchers.IO) { PersonalStore.unlockCipher(context) }
            if (unlocking == null) { trouble = true; busy = false; return@launch }
            trouble = false
            Away.forOwnWindow()
            askOwner(activity, title, unlocking.cipher, pinOnly) { c ->
                busy = false
                if (c != null) PersonalStore.unlock(context, unlocking, c)
            }
        }
    }
    // A calm closed door in the middle of the screen: the shield of the app, a line, one big button
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Icon(Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
        }
        Text(stringResource(R.string.lock_mine_locked), style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(stringResource(if (pinOnly) R.string.lock_mine_locked_pin else R.string.lock_mine_locked_text), style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (trouble) Text(stringResource(R.string.lock_mine_trouble), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = ::ask, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(title, style = MaterialTheme.typography.titleMedium) }
        Text(stringResource(R.string.lock_mine_112_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/**
 * Settings: open "Mine" with a fingerprint or the PIN, and "PIN only". Off by default, so the data
 * opens at once in an emergency. Each change asks the owner in Android's own window. Keystore work
 * runs off the main thread (making a key in secure hardware can take seconds).
 */
@Composable
fun LockMineSettings() {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<KeyVault.Mode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { mode = withContext(Dispatchers.IO) { PersonalStore.lockMode(context) } }
    var askOn by rememberSaveable { mutableStateOf(false) }
    val canLock = Guard.available(context)
    val title = stringResource(R.string.lock_mine_change)
    val failed = stringResource(R.string.lock_mine_failed)
    // The technical reason of the last failure, shown so the owner can send it in feedback
    var reason by remember { mutableStateOf<String?>(null) }
    fun fail() {
        busy = false
        reason = PersonalStore.lockTrouble
        android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show()
    }

    fun finish(change: LockChange?) {
        if (change == null) return fail()
        val store = {
            PersonalStore.finishLock(context, change) { ok ->
                busy = false
                reason = if (ok) null else PersonalStore.lockTrouble
                if (!ok) android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_LONG).show()
                PersonalStore.close()
                refresh++
            }
        }
        if (change.mode.locked) {
            Away.forOwnWindow()
            askOwner(activity, title, change.cipher, change.mode.pinOnly) { c -> if (c != null) store() else { change.discard(); busy = false; reason = PersonalStore.lockTrouble } }
        } else {
            store()
        }
    }

    /** From a locked mode to [target]: the owner first opens the current key. */
    fun changeFromLocked(current: KeyVault.Mode, target: KeyVault.Mode) {
        busy = true
        PersonalStore.clearLockTrouble()
        scope.launch {
            val unlocking = withContext(Dispatchers.IO) { PersonalStore.unlockCipher(context) } ?: return@launch fail()
            Away.forOwnWindow()
            askOwner(activity, title, unlocking.cipher, current.pinOnly) { c ->
                if (c == null) { busy = false; return@askOwner }
                scope.launch { finish(withContext(Dispatchers.IO) { PersonalStore.startChange(context, unlocking, c, target) }) }
            }
        }
    }

    val m = mode
    if (!canLock && m?.locked != true) {
        Text(stringResource(R.string.lock_mine_no_screen_lock), style = MaterialTheme.typography.bodyMedium)
        return
    }
    // Never an empty heading: if the key's state could not be read, say what to do
    if (m == null) {
        Text(stringResource(R.string.lock_mine_unknown), style = MaterialTheme.typography.bodyMedium)
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { refresh++; onPauseOrDispose { } }
        return
    }
    Toggle(stringResource(if (m.pinOnly) R.string.lock_mine_toggle_pin else R.string.lock_mine_toggle), m.locked) { on ->
        if (busy) return@Toggle
        if (on) askOn = true else changeFromLocked(m, KeyVault.Mode.OPEN)
    }
    if (m.locked) {
        Toggle(stringResource(R.string.lock_mine_pin_only), m.pinOnly) { on ->
            if (!busy) changeFromLocked(m, KeyVault.Mode(locked = true, pinOnly = on))
        }
        Text(stringResource(R.string.lock_mine_pin_only_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.lock_mine_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    reason?.let { r ->
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(stringResource(R.string.lock_mine_reason, r), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (askOn) {
        AlertDialog(
            onDismissRequest = { askOn = false },
            title = { Text(stringResource(R.string.lock_mine_confirm_title)) },
            text = { Text(stringResource(R.string.lock_mine_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askOn = false
                    busy = true
                    scope.launch { finish(withContext(Dispatchers.IO) { PersonalStore.startLock(context, pinOnly = false) }) }
                }) { Text(stringResource(R.string.lock_mine_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { askOn = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
