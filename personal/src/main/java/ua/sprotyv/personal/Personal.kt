package ua.sprotyv.personal

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** How opening the owner's data went. */
sealed interface Opened {
    /** [softwareKey]: this phone keeps the key in software only; the screen says so. */
    class Ready(val db: PersonalDb, val softwareKey: Boolean) : Opened

    /**
     * Clear evidence the key is gone or does not fit. Only the owner decides on a fresh start, and
     * even then the old files are set aside, never deleted.
     */
    data object KeyLost : Opened

    /**
     * The key fits but the data would not open (damage, a failed update). The owner may set it
     * aside and start fresh, as with a lost key; nothing is ever deleted.
     */
    data object Damaged : Opened

    /** Could not open for a reason that may pass. Nothing is judged or changed. */
    data object TryAgain : Opened
}

object Personal {
    /** Opens the owner's encrypted data, creating it on the very first start. Off the main thread. Never throws. */
    fun open(context: Context): Opened = try {
        openInner(context.applicationContext)
    } catch (t: Throwable) {
        // Anything at all (a missing native library included) leaves the data untouched
        Opened.TryAgain
    }

    private fun openInner(context: Context): Opened {
        val vault = KeyVault(context)
        if (!vault.isSetUp()) {
            // Data without a key file cannot be opened; it is never overwritten
            if (PersonalDb.exists(context)) return Opened.KeyLost
            val fresh = vault.create()
            return Opened.Ready(PersonalDb.open(context, fresh.passphrase), fresh.softwareKey)
        }
        return when (val u = vault.unwrap()) {
            KeyVault.Unwrapped.Lost -> Opened.KeyLost
            KeyVault.Unwrapped.Retry -> Opened.TryAgain
            is KeyVault.Unwrapped.Ok -> try {
                Opened.Ready(PersonalDb.open(context, u.passphrase), u.softwareKey)
            } catch (e: android.database.sqlite.SQLiteFullException) {
                Opened.TryAgain
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                Opened.TryAgain
            } catch (e: Exception) {
                Opened.Damaged
            }
        }
    }

    /**
     * A fresh start after the owner confirmed it. The state is checked again right now and the
     * start goes ahead only if the key is still clearly lost or the data still damaged. The old database and key file are
     * renamed aside, never deleted, and the keystore entry is kept. Returns true if it went ahead.
     */
    fun startFresh(context: Context): Boolean = try {
        val app = context.applicationContext
        when (val now = open(app)) {
            is Opened.Ready -> { now.db.close(); false }
            Opened.KeyLost, Opened.Damaged -> { setAside(app); true }
            else -> false
        }
    } catch (t: Throwable) {
        false
    }

    private fun setAside(context: Context) {
        val stamp = System.currentTimeMillis()
        val db = context.getDatabasePath(PersonalDb.FILE_NAME)
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val f = File(db.path + suffix)
            if (f.exists() && !f.renameTo(File(db.path + suffix + ".lost-$stamp"))) error("Could not set the data aside")
        }
        KeyVault(context).setAside(stamp)
    }
}

/**
 * The one open copy of the owner's data for the whole app. Opened only when the owner's section
 * is entered, closed when the app leaves the screen, so data is not kept open in the background.
 */
object PersonalStore {
    private val mutex = Mutex()
    // One worker, so open, close and fresh start always run in the order they were asked for
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + CoroutineExceptionHandler { _, _ -> },
    )
    private val _state = MutableStateFlow<Opened?>(null)

    /** null while opening or closed. */
    val state: StateFlow<Opened?> = _state

    fun open(context: Context) {
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (_state.value is Opened.Ready) return@withLock
                _state.value = null
                _state.value = Personal.open(app)
            }
        }
    }

    fun close() {
        scope.launch {
            mutex.withLock {
                try {
                    (_state.value as? Opened.Ready)?.db?.close()
                } finally {
                    _state.value = null
                }
            }
        }
    }

    fun startFresh(context: Context) {
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                val now = _state.value
                if (now != Opened.KeyLost && now != Opened.Damaged) return@withLock
                _state.value = null
                Personal.startFresh(app)
                _state.value = Personal.open(app)
            }
        }
    }
}
