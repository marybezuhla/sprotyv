package ua.sprotyv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.sprotyv.personal.Opened
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting

/** "I treat and save": the Ministry's procedures open in full, with the medic's tools. */
const val KEY_MEDIC = "mark_medic"

/** Military service facts and places shown in "Resistance". */
const val KEY_SERVICE = "mark_service"

/**
 * Marks that say something about the person (medical training, military service) are kept in the
 * encrypted data, never in the plain settings: they show only while "My data" is open, so with its
 * lock on they appear after the fingerprint or PIN, and in trip mode not at all.
 */
object Marks {
    /** Before 1.1 the marks were plain settings ("home": medic, service); they move in once, then go. */
    private const val LEGACY_FILE = "home"
    private val LEGACY = mapOf("medic" to KEY_MEDIC, "service" to KEY_SERVICE)

    fun set(key: String, on: Boolean) {
        PersonalStore.save("setting:$key") { db -> db.settings().put(Setting(key, on.toString())) }
    }

    /** An old plain mark (or one from an old backup) goes into the encrypted data and off the plain file. */
    fun moveIn(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val waiting = LEGACY.keys.filter { prefs.contains(it) }
        if (waiting.isEmpty()) return
        PersonalStore.save("marks:move") { db ->
            waiting.forEach { old -> if (prefs.getBoolean(old, false)) db.settings().put(Setting(LEGACY.getValue(old), "true")) }
            // Only once they are in the encrypted data
            prefs.edit().apply { waiting.forEach { remove(it) } }.commit()
        }
    }

    /** An old backup's plain marks, as the encrypted settings to write while the restore holds the data. */
    fun fromOldBackup(home: Map<String, String>): List<Setting> =
        LEGACY.filter { (old, _) -> home[old]?.toBooleanStrictOrNull() == true }.map { (_, key) -> Setting(key, "true") }

    /** Whether the encrypted data exists yet: screens that only read marks never create it. */
    fun dataExists(context: Context): Boolean = PersonalStore.lockMode(context) != null

    /** A mark shown on a screen outside "My data": the recent-apps picture then keeps it hidden, as on the data screens. */
    @Composable
    fun HideFromRecents(on: Boolean) {
        val activity = LocalContext.current as? android.app.Activity ?: return
        if (!on || android.os.Build.VERSION.SDK_INT < 33) return
        androidx.compose.runtime.DisposableEffect(activity) {
            activity.setRecentsScreenshotEnabled(false)
            onDispose { activity.setRecentsScreenshotEnabled(true) }
        }
    }
}

/** A mark as it is now; false while "My data" is closed or locked. */
@Composable
fun rememberMark(key: String): Boolean {
    val context = LocalContext.current
    val ready = PersonalStore.state.collectAsStateWithLifecycle().value is Opened.Ready
    LaunchedEffect(ready) { if (ready) Marks.moveIn(context) }
    val value by remember(key) { PersonalStore.observe { it.settings().observe(key) } }.collectAsStateWithLifecycle(null)
    return ready && value == "true"
}

/** Whether "My data" is open now, so marks can be read and changed. */
@Composable
fun marksOpen(): Boolean = PersonalStore.state.collectAsStateWithLifecycle().value is Opened.Ready
