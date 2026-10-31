package ua.sprotyv.personal

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

    /**
     * This version of the app cannot bring the data to its layout (a missing or failed update
     * step). Nothing is deleted and no fresh start is offered: a fixed update opens it again.
     */
    data object NeedsUpdate : Opened

    /** Could not open for a reason that may pass. Nothing is judged or changed. */
    data object TryAgain : Opened

    /** "Mine" is locked: it opens after the owner confirms with a fingerprint or the PIN ([pinOnly]: PIN only). */
    data class Locked(val pinOnly: Boolean) : Opened
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
        return withPassphrase(context, vault.unwrap()) { vault.lockedKeyLost() }
    }

    /** Opens the data with an unwrapped passphrase, or says why not. */
    internal fun withPassphrase(context: Context, u: KeyVault.Unwrapped, lockedLost: () -> Boolean = { false }): Opened {
        return when (u) {
            KeyVault.Unwrapped.Lost -> Opened.KeyLost
            KeyVault.Unwrapped.Retry -> Opened.TryAgain
            // The screen lock removed (say) invalidates a locked key: then it is lost, said plainly
            is KeyVault.Unwrapped.NeedsOwner -> if (lockedLost()) Opened.KeyLost else Opened.Locked(u.pinOnly)
            is KeyVault.Unwrapped.Ok -> try {
                Opened.Ready(PersonalDb.open(context, u.passphrase), u.softwareKey)
            } catch (e: android.database.sqlite.SQLiteFullException) {
                Opened.TryAgain
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                Opened.TryAgain
            } catch (e: IllegalStateException) {
                // Room's way of saying a layout update is missing or failed
                Opened.NeedsUpdate
            } catch (e: android.database.SQLException) {
                // The key fits, the file itself is damaged
                Opened.Damaged
            } catch (e: Exception) {
                Opened.TryAgain
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
 * The one open copy of the owner's data for the whole app. Opened only when the owner's screens
 * are entered, closed when the app leaves the screen, so data is not kept open in the background.
 *
 * Screens never touch the database directly: they read through [observe] and [read], which stop
 * quietly when the data is closed, and change it through [save], which runs in order with opening
 * and closing. A change that could not be saved is never silent: [saveFailed] turns on.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object PersonalStore {
    private val mutex = Mutex()
    // One worker, so open, close, changes and fresh start always run in the order they were asked for
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + worker + CoroutineExceptionHandler { _, _ -> })
    private val _state = MutableStateFlow<Opened?>(null)
    private val _saveFailed = MutableStateFlow(false)
    private val pending = LinkedHashMap<String, suspend (PersonalDb) -> Unit>()

    /** null while opening or closed. */
    val state: StateFlow<Opened?> = _state

    /** True when the last change could not be saved; the screens say so plainly. */
    val saveFailed: StateFlow<Boolean> = _saveFailed

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
                } catch (e: Exception) {
                    // Closing is best effort; the state is cleared either way
                } finally {
                    _state.value = null
                }
            }
        }
    }

    /**
     * Saves a change. Changes with the same [key] are folded together: while one is waiting, a
     * newer one replaces it, so typing a word is one write, not one per letter. A change is saved
     * only while the data is open and before any later close; otherwise [saveFailed] turns on.
     */
    fun save(key: String, block: suspend (PersonalDb) -> Unit) {
        val schedule = synchronized(pending) {
            val waiting = key in pending
            pending[key] = block
            !waiting
        }
        if (!schedule) return
        scope.launch {
            mutex.withLock {
                val latest = synchronized(pending) { pending.remove(key) } ?: return@withLock
                val db = (_state.value as? Opened.Ready)?.db
                if (db == null) {
                    _saveFailed.value = true
                    return@withLock
                }
                try {
                    latest(db)
                    _saveFailed.value = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _saveFailed.value = true
                }
            }
        }
    }

    /**
     * Live data from the open database, or nothing while it is closed. Errors (a database closed
     * under a running query) end the stream quietly instead of crashing the app.
     */
    fun <T> observe(query: (PersonalDb) -> Flow<T>): Flow<T> =
        _state.flatMapLatest { s ->
            val db = (s as? Opened.Ready)?.db ?: return@flatMapLatest emptyFlow()
            query(db).catch { }
        }

    /**
     * One read, in order with opening and closing. If the data is closed for a moment (the app left
     * the screen and came back), it waits until the data is open again. Null only if the data
     * cannot be opened or the read itself failed.
     */
    suspend fun <T> read(query: suspend (PersonalDb) -> T): T? {
        while (true) {
            // Waits while closed or opening; any other state ends the wait
            val now = _state.first { it != null }
            if (now !is Opened.Ready) return null
            val result = withContext(worker) {
                mutex.withLock {
                    val db = (_state.value as? Opened.Ready)?.db ?: return@withLock Retry
                    try { Got(query(db)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Failed }
                }
            }
            when (result) {
                is Got<*> -> @Suppress("UNCHECKED_CAST") return result.value as T
                Failed -> return null
                Retry -> continue
            }
        }
    }

    private sealed interface ReadResult
    private class Got<T>(val value: T) : ReadResult
    private data object Failed : ReadResult
    private data object Retry : ReadResult

    /**
     * The lock on "Mine". [unlockCipher] gives a cipher for the owner to confirm (a fingerprint or
     * the PIN, through Android's own window); [unlock] opens the data with it. Changing the mode
     * runs in order with opening and closing, and leaves the data closed, so the next entry asks.
     */
    fun lockMode(context: Context): KeyVault.Mode? = KeyVault(context.applicationContext).mode()

    /** Off the main thread: the keystore can be slow. */
    fun unlockCipher(context: Context): KeyVault.Unlocking? = KeyVault(context.applicationContext).cipherForUnlock()

    fun unlock(context: Context, unlocking: KeyVault.Unlocking, confirmed: javax.crypto.Cipher) {
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (_state.value is Opened.Ready) return@withLock
                _state.value = null
                _state.value = try {
                    Personal.withPassphrase(app, KeyVault(app).finishUnlock(confirmed, unlocking.data))
                } catch (t: Throwable) {
                    Opened.TryAgain
                }
            }
        }
    }

    /**
     * Turning the lock on (from the open, unlocked mode): the passphrase is read with the plain key,
     * no prompt needed. Returns the cipher to confirm and finishes in [finishLock]; null if the data
     * is not in the open mode or anything is unclear (nothing changes then).
     */
    fun startLock(context: Context, pinOnly: Boolean): LockChange? = try {
        val vault = KeyVault(context.applicationContext)
        val u = vault.unwrap()
        if (u !is KeyVault.Unwrapped.Ok) null
        else {
            val mode = KeyVault.Mode(locked = true, pinOnly = pinOnly)
            val (cipher, alias) = vault.cipherForRewrap(mode)
            LockChange(cipher, alias, mode, u.passphrase)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Turning the lock off, or switching "PIN only": the owner first confirms [unlockCipher]; with
     * it the passphrase is read and a change to [target] prepared. A locked target needs one more
     * confirmation of its own cipher.
     */
    fun startChange(context: Context, unlocking: KeyVault.Unlocking, confirmedUnlock: javax.crypto.Cipher, target: KeyVault.Mode): LockChange? = try {
        val vault = KeyVault(context.applicationContext)
        val u = vault.finishUnlock(confirmedUnlock, unlocking.data)
        if (u !is KeyVault.Unwrapped.Ok) null
        else {
            val (cipher, alias) = vault.cipherForRewrap(target)
            LockChange(cipher, alias, target, u.passphrase)
        }
    } catch (e: Exception) {
        null
    }

    /** Stores the change (the cipher confirmed if the target is locked). The data closes after. */
    fun finishLock(context: Context, change: LockChange, done: (Boolean) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val ok = mutex.withLock {
                try {
                    KeyVault(app).finishRewrap(change.cipher, change.alias, change.mode, change.passphrase)
                    true
                } catch (t: Throwable) {
                    false
                } finally {
                    change.passphrase.fill(0)
                }
            }
            withContext(Dispatchers.Main) { done(ok) }
        }
    }

    /** Drops waiting changes whose key starts with [prefix] (before a change that replaces them all). */
    fun dropPending(prefix: String) {
        synchronized(pending) { pending.keys.removeAll { it.startsWith(prefix) } }
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

/** A lock change waiting for its last step: the cipher to (maybe) confirm and what it will store. */
class LockChange internal constructor(
    val cipher: javax.crypto.Cipher,
    internal val alias: String,
    val mode: KeyVault.Mode,
    internal val passphrase: ByteArray,
) {
    /** Cancelled: the passphrase copy is wiped at once. */
    fun discard() = passphrase.fill(0)
}
