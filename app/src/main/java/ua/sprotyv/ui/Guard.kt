package ua.sprotyv.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The settings lock: changing the mode or the tiles asks for the phone's own screen lock (PIN,
 * pattern or password), through Android's own window. Sprotyv never sees or keeps the PIN.
 *
 * It keeps settings from being changed by accident, for example on a phone set up for an older
 * relative. It is not a lock on the owner's data and does not hide anything.
 *
 * Once confirmed, settings stay open until the app leaves the screen.
 */
object Guard {
    private val open = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = open.asStateFlow()

    /** What to do once the PIN is confirmed; read by the screen, so it survives the screen being rebuilt. */
    private val waiting = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = waiting.asStateFlow()

    /** The system PIN window is up: leaving the screen for it does not close settings again. */
    @Volatile var prompting = false
        private set

    /** The phone has a screen lock the lock can ask for. */
    fun available(context: Context): Boolean {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val bio = context.getSystemService(BiometricManager::class.java)
        return keyguard?.isDeviceSecure == true &&
            bio?.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Runs [then] at once when settings are not locked (or already confirmed, or the phone has no
     * screen lock left to ask for). Otherwise asks for the PIN and sets [then] as [pending].
     */
    fun ask(activity: Activity, locked: Boolean, title: String, then: String, run: (String) -> Unit) {
        if (!locked || open.value || !available(activity)) {
            run(then)
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .setConfirmationRequired(false)
            .build()
        prompting = true
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prompting = false
                open.value = true
                waiting.value = then
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                // Cancelled by the person: nothing to say. Anything else is said, never silent
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                    android.widget.Toast.makeText(activity.applicationContext, errString, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    /** Turning the lock on from open settings keeps them open until the app leaves the screen. */
    fun keepOpen() {
        open.value = true
    }

    /** The screen has done what was waiting. */
    fun done() {
        waiting.value = null
    }

    /** Leaving the screen locks settings again (not while the PIN window itself is up). */
    fun close() {
        if (prompting) return
        open.value = false
        waiting.value = null
    }
}
