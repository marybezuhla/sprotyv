package ua.sprotyv.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ua.sprotyv.R
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.backup.BackupCrypto
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.personal.backup.BackupLines
import ua.sprotyv.personal.backup.BackupStream
import ua.sprotyv.personal.docs.DocFiles
import java.io.ByteArrayOutputStream

/** The EFF long word list (7776 words), for suggested passwords. Read once. */
object Words {
    @Volatile private var list: List<String>? = null
    fun get(context: Context): List<String> = list ?: context.applicationContext.assets.open("eff_large_wordlist.txt")
        .bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }.also { list = it }
}

/** A relative's plan code, scanned and waiting for its password. In memory only. */
object PlanInbox {
    @Volatile var text: String? = null
}

/**
 * Backups run outside any screen, so leaving for a file picker or rotating the phone never cuts
 * one off. Decrypted data waits in memory only, and only until the owner says yes or no.
 */
object BackupJobs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice
    private val _pending = MutableStateFlow<Opened?>(null)
    val pending: StateFlow<Opened?> = _pending

    /**
     * A copy opened and checked, waiting for the owner's yes. A streamed copy keeps only its
     * summary and key here (the data is read again from the file on "yes"); an older one-piece
     * copy keeps its decrypted data. Both only in memory, and only until yes or no.
     */
    sealed class Opened(val created: Long, val people: Int, val notes: Int, val records: Int, val skipped: Int, val home: Map<String, String>, val docs: Int = -1, val docBytes: Long = 0) {
        class Piece(val snap: BackupData.Snapshot) : Opened(snap.created, snap.people.size, snap.notes.size, snap.records, 0, snap.home)
        class Stream(val uri: Uri, val key: BackupStream.Key, val summary: BackupLines.Summary, val fingerprint: ByteArray) :
            Opened(summary.created, summary.people, summary.notes, summary.records, summary.skipped, summary.home, summary.docs, summary.docBytes)

        fun wipe() { if (this is Stream) key.wipe() }
    }

    private fun setPending(o: Opened?) {
        val old = _pending.value
        _pending.value = o
        if (old !== o) old?.wipe()
    }

    /**
     * The suggested password and the "written down" tick live here, in memory, so turning the
     * phone never swaps the words under the owner's eyes. Never in saved state.
     */
    val suggested = MutableStateFlow<String?>(null)
    val written = MutableStateFlow(false)

    /** Documents go into the copy too (the owner can leave them out: a lighter file). */
    val withDocs = MutableStateFlow(true)

    /** The password, kept until the file picker returns. Cleared after use. */
    @Volatile private var password: CharArray? = null

    /** Each open gets a number; a result for a screen already left is thrown away. */
    @Volatile private var generation = 0

    fun hold(pw: String) {
        password?.fill(' ')
        password = pw.toCharArray()
    }

    /** The file picker was cancelled: the held password goes. */
    fun release() {
        password?.fill(' ')
        password = null
    }

    fun clearNotice() { _notice.value = null }

    private fun job(work: suspend () -> Int?): Boolean {
        if (_busy.value) return false
        _busy.value = true
        _notice.value = null
        scope.launch {
            val result = try {
                work()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                R.string.backup_no_memory
            } catch (e: Throwable) {
                R.string.backup_failed
            }
            _busy.value = false
            _notice.value = result
        }
        return true
    }

    /** Writes an encrypted copy of everything into a new, empty file, then reads it back to check it. */
    fun save(context: Context, uri: Uri) {
        val app = context.applicationContext
        val pw = password
        password = null
        if (pw == null) {
            // The password went with the screen (the system closed it meanwhile): no half-made file stays
            scope.launch { if (isNewDocument(app, uri)) deleteQuietly(app, uri) }
            _notice.value = R.string.backup_again
            return
        }
        val started = job {
            var fresh = false
            var key: BackupStream.Key? = null
            var docs: List<ua.sprotyv.personal.Document>? = null
            try {
                fresh = isNewDocument(app, uri)
                if (!fresh) return@job R.string.backup_existing
                // Waits for the data to open again after the file picker (it closes while the app is away)
                val snap = withTimeoutOrNull(20_000) { PersonalStore.read { BackupData.read(it, BACKED_UP_SETTINGS) } }
                    ?.copy(home = homeForBackup(app))
                    ?: return@job removeNew(app, uri, R.string.backup_failed)
                // The documents' rows (their keys only in memory, wiped below); their files are read as they are written
                docs = if (withDocs.value) PersonalStore.read { it.documents().all() } ?: return@job removeNew(app, uri, R.string.backup_failed) else null
                val files = DocFiles.of(app)
                val k = withContext(Dispatchers.Default) { BackupStream.newKey(pw) }
                key = k
                // Written in parts, with a fingerprint of what went in, to compare with what reads back
                val wrote = java.security.MessageDigest.getInstance("SHA-256")
                val out = app.contentResolver.openOutputStream(uri, "w") ?: return@job removeNew(app, uri, R.string.backup_failed)
                out.use { o ->
                    java.io.OutputStreamWriter(java.security.DigestOutputStream(BackupStream.writer(o, k), wrote), Charsets.UTF_8).buffered().use { w ->
                        BackupLines.write(snap, w, docs) { d -> files.open(d.file, d.key) }
                    }
                }
                // Check what actually landed on the storage, not what we meant to write: it must
                // decrypt, read as a whole copy, and match what went in
                val read = java.security.MessageDigest.getInstance("SHA-256")
                val back = app.contentResolver.openInputStream(uri) ?: return@job removeNew(app, uri, R.string.backup_failed)
                val ok = back.use { input ->
                    if (!k.matches(BackupStream.readHeader(input))) return@use false
                    val summary = java.io.InputStreamReader(java.security.DigestInputStream(BackupStream.reader(input, k), read), Charsets.UTF_8).use { r ->
                        BackupLines.read(r) { }
                    }
                    summary.records == snap.records && summary.skipped == 0 && summary.docs == (docs?.size?.takeIf { it > 0 } ?: -1) && read.digest().contentEquals(wrote.digest())
                }
                if (ok) {
                    PersonalStore.save("setting:$KEY_BACKUP_AT") { it.settings().put(ua.sprotyv.personal.Setting(KEY_BACKUP_AT, System.currentTimeMillis().toString())) }
                    // Next time, new words
                    suggested.value = null
                    written.value = false
                    R.string.backup_saved
                } else {
                    removeNew(app, uri, R.string.backup_failed)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A half-written file must not stay behind looking like a backup
                if (fresh) removeNew(app, uri, if (e is OutOfMemoryError) R.string.backup_no_memory else R.string.backup_failed)
                else R.string.backup_failed
            } finally {
                key?.wipe()
                docs?.forEach { it.key.fill(0) }
                pw.fill(' ')
            }
        }
        if (!started) {
            pw.fill(' ')
            // Busy with another copy: the empty file the save dialog made does not stay behind
            scope.launch { if (isNewDocument(app, uri)) deleteQuietly(app, uri) }
        }
    }

    /** Removes the file the save dialog made; says so if the file app does not allow it. */
    private fun removeNew(context: Context, uri: Uri, notice: Int): Int =
        if (deleteQuietly(context, uri)) notice else R.string.backup_delete_by_hand

    /** Opens a backup and shows what is in it; nothing is changed yet. */
    fun open(context: Context, uri: Uri, typed: String) {
        val app = context.applicationContext
        val pw = typed.toCharArray()
        val mine = ++generation
        val started = job {
            var plain: ByteArray? = null
            var words: CharArray? = null
            try {
                val head = app.contentResolver.openInputStream(uri)?.use { readUpTo(it, BackupStream.HEADER_BYTES) } ?: throw BackupCrypto.NotABackup()
                val opened: Opened = if (BackupStream.isStream(head)) {
                    // Checked to the end, one record at a time; only the summary and the key stay
                    suspend fun tryWith(p: CharArray): Opened {
                        val key = withContext(Dispatchers.Default) { BackupStream.keyFor(head, p) }
                        try {
                            val md = java.security.MessageDigest.getInstance("SHA-256")
                            val summary = readCopy(app, uri, key, md) { BackupLines.read(it) { } }
                            return Opened.Stream(uri, key, summary, md.digest())
                        } catch (e: Throwable) {
                            key.wipe()
                            throw e
                        }
                    }
                    try {
                        tryWith(pw)
                    } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                        // The suggested words typed with spaces or capitals: tried once more as words
                        words = BackupCrypto.asWords(pw)
                        val w = words
                        if (w == null || w.contentEquals(pw)) throw e
                        tryWith(w)
                    }
                } else {
                    // A copy from before copies were streamed: one piece, small by its own limit
                    val file = readLimited(app, uri)
                    plain = withContext(Dispatchers.Default) {
                        try {
                            BackupCrypto.decrypt(file, pw, BackupCrypto.Purpose.BACKUP)
                        } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                            words = BackupCrypto.asWords(pw)
                            val w = words
                            if (w == null || w.contentEquals(pw)) throw e
                            BackupCrypto.decrypt(file, w, BackupCrypto.Purpose.BACKUP)
                        }
                    }
                    Opened.Piece(BackupData.decode(plain!!))
                }
                // The screen was left meanwhile: nothing decrypted stays behind
                if (mine == generation) setPending(opened) else opened.wipe()
                // Left in between the check and the setting: wiped all the same
                if (mine != generation) setPending(null)
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                R.string.backup_wrong_password
            } catch (e: BackupCrypto.FileUnreadable) {
                R.string.backup_unreadable
            } catch (e: java.io.IOException) {
                // Opening the file or reading its first bytes failed: the file, not the password
                R.string.backup_unreadable
            } catch (e: BackupData.FromNewer) {
                R.string.backup_from_newer
            } catch (e: OutOfMemoryError) {
                R.string.backup_no_memory
            } catch (e: Throwable) {
                R.string.backup_not_ours
            } finally {
                plain?.fill(0)
                words?.fill(' ')
                pw.fill(' ')
            }
        }
        if (!started) pw.fill(' ')
    }

    /** The owner said yes: everything now on the phone is replaced by the copy, all or nothing. */
    fun confirmRestore(context: Context) {
        val app = context.applicationContext
        val snap = _pending.value ?: return
        _pending.value = null
        val started = job {
            // Changes still waiting to be saved would land on top of the restored data
            PersonalStore.dropPending("")
            // Never later and unseen: if the data does not open in time, nothing is replaced.
            // A streamed copy is read again from the file and checked once more while it goes in;
            // anything different from what the owner saw changes nothing
            val ok = try {
                withTimeoutOrNull(600_000) {
                    PersonalStore.read { db ->
                        when (snap) {
                            is Opened.Piece -> BackupData.replaceAll(db, snap.snap, BACKED_UP_SETTINGS)
                            is Opened.Stream -> {
                                val md = java.security.MessageDigest.getInstance("SHA-256")
                                readCopy(app, snap.uri, snap.key, md) { BackupLines.restore(db, it, BACKED_UP_SETTINGS, snap.summary, DocFiles.of(app)) { md.digest().contentEquals(snap.fingerprint) } }
                            }
                        }
                        // A backup from before 1.1 has the marks among its plain settings: into the encrypted data, now
                        Marks.fromOldBackup(snap.home).forEach { db.settings().put(it) }
                        true
                    }
                } == true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            } finally {
                snap.wipe()
            }
            if (!ok) return@job R.string.backup_restore_failed
            // Count widgets show the restored counts, not the old ones
            runCatching { Steps.flushCounts(app) }
            // The look and set-up too, so nothing has to be set up again on a new phone
            // The data is back even if the look could not be set: never report that as a failure
            runCatching { withContext(Dispatchers.Main) { restoreHome(app, snap.home) } }
            val notifyOff = Notify.Kind.entries.any { snap.home["notify_" + it.key] == "true" } && !Notify.allowedBySystem(app)
            when {
                snap.home["lock_mine"] == "true" && PersonalStore.lockMode(app)?.locked != true -> R.string.backup_restored_lock
                // A tourniquet reminder that cannot sound must not look switched on
                notifyOff -> R.string.backup_restored_notify
                // Made by a newer Sprotyv: what this version knows is back, the rest waits for an update
                snap.skipped > 0 -> R.string.backup_restored_partly
                else -> R.string.backup_restored
            }
        }
        // Still busy with the opening: the question stays, so the owner can say yes again
        if (!started) _pending.value = snap
    }

    fun cancelRestore() { setPending(null) }

    /** The chosen copy, kept while the screen is drawn again (the data reopening after the picker, a lock). */
    val openUri = MutableStateFlow<Uri?>(null)

    /** Screens showing the backup now; when none is left for a moment, the screen was really left. */
    @Volatile private var screens = 0

    fun shown() { screens++ }

    /**
     * The screen went away. If it is not drawn again soon (it is redrawn when the data reopens or
     * after unlocking), the owner left it: then nothing decrypted or secret stays waiting.
     */
    fun hidden() {
        screens--
        scope.launch {
            kotlinx.coroutines.delay(3_000)
            if (screens <= 0) { forget(); clearNotice() }
        }
    }

    /** The screen is gone for good (not turned): nothing decrypted or secret stays waiting. */
    fun forget() {
        openUri.value = null
        generation++
        setPending(null)
        password?.fill(' ')
        password = null
        suggested.value = null
        written.value = false
    }
}

/** The look and set-up a backup carries, besides the data. */
private fun homeForBackup(context: Context): Map<String, String> = HomePrefs.export(context) + mapOf(
    "neutral" to Neutral.isOn(context).toString(),
    // Only a wish: a new phone has a new key, so the lock is switched on again by hand (a notice says so)
    "lock_mine" to (PersonalStore.lockMode(context)?.locked == true).toString(),
) + Notify.Kind.entries.associate { "notify_" + it.key to Notify.isOn(context, it).toString() } +
    // Only the switches; reminders in notes are set again on the new phone
    mapOf("notify_trip_all" to Notify.hideAllInTrip(context).toString())

private fun restoreHome(context: Context, home: Map<String, String>) {
    HomePrefs.import(context, home)
    home["neutral"]?.toBooleanStrictOrNull()?.let { want -> if (Neutral.isOn(context) != want) Neutral.set(context, want) }
    // Notifications as they were; Android's own permission is not carried: the owner is told to allow it
    Notify.Kind.entries.forEach { k -> home["notify_" + k.key]?.toBooleanStrictOrNull()?.let { if (Notify.isOn(context, k) != it) Notify.set(context, k, it) } }
    home["notify_trip_all"]?.toBooleanStrictOrNull()?.let { if (Notify.hideAllInTrip(context) != it) Notify.setHideAllInTrip(context, it) }
}

/** The settings a backup carries; others (like the date of the last backup) stay on this phone. */
val BACKED_UP_SETTINGS = setOf(KEY_SAFE_MESSAGE, KEY_KIT_REVIEWED, KEY_SUPPLIES, KEY_PATH, KEY_NEW_PLACE, KEY_HABITS, KEY_ROLES, KEY_STRENGTH, KEY_STEPS, KEY_MEDIC, KEY_SERVICE) + Bookmarks.KEYS

const val KEY_BACKUP_AT = "backup_at"

/** True for a new, empty file from the save dialog: an existing file is never written over. */
private fun isNewDocument(context: Context, uri: Uri): Boolean = try {
    context.contentResolver.openInputStream(uri)?.use { it.read() < 0 } ?: false
} catch (e: Exception) {
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
            ?.use { c -> c.moveToFirst() && !c.isNull(0) && c.getLong(0) == 0L } == true
    } catch (e: Exception) {
        false
    }
}

internal fun deleteQuietly(context: Context, uri: Uri): Boolean = try {
    DocumentsContract.deleteDocument(context.contentResolver, uri)
} catch (e: Exception) {
    false
}

/** Reads at most [BackupCrypto.MAX_FILE_BYTES]; anything larger is not ours. */
private fun readLimited(context: Context, uri: Uri): ByteArray {
    val limit = BackupCrypto.MAX_FILE_BYTES
    context.contentResolver.openInputStream(uri)!!.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupCrypto.NotABackup()
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

/** The first [n] bytes of a file, or fewer if it is shorter. */
private fun readUpTo(input: java.io.InputStream, n: Int): ByteArray {
    val out = ByteArray(n)
    var got = 0
    while (got < n) {
        val r = input.read(out, got, n - got)
        if (r < 0) break
        got += r
    }
    return out.copyOf(got)
}

/** Opens a streamed copy with [key] and hands its content, read in parts, to [block]; [digest] sees every byte of it. */
private suspend fun <T> readCopy(context: Context, uri: Uri, key: BackupStream.Key, digest: java.security.MessageDigest, block: suspend (java.io.Reader) -> T): T =
    withContext(Dispatchers.IO) {
        (context.contentResolver.openInputStream(uri) ?: throw BackupCrypto.NotABackup()).use { input ->
            if (!key.matches(BackupStream.readHeader(input))) throw BackupCrypto.NotABackup()
            java.io.InputStreamReader(java.security.DigestInputStream(BackupStream.reader(input, key), digest), Charsets.UTF_8).use { block(it) }
        }
    }
