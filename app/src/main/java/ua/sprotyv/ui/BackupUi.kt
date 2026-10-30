package ua.sprotyv.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import ua.sprotyv.personal.backup.PlanCode
import ua.sprotyv.qr.Qr
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    private val _pending = MutableStateFlow<BackupData.Snapshot?>(null)
    val pending: StateFlow<BackupData.Snapshot?> = _pending

    /**
     * The suggested password and the "written down" tick live here, in memory, so turning the
     * phone never swaps the words under the owner's eyes. Never in saved state.
     */
    val suggested = MutableStateFlow<String?>(null)
    val written = MutableStateFlow(false)

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
            var plain: ByteArray? = null
            try {
                fresh = isNewDocument(app, uri)
                if (!fresh) return@job R.string.backup_existing
                // Waits for the data to open again after the file picker (it closes while the app is away)
                val snap = withTimeoutOrNull(20_000) { PersonalStore.read { BackupData.read(it, BACKED_UP_SETTINGS) } }
                    ?: return@job removeNew(app, uri, R.string.backup_failed)
                plain = BackupData.encode(snap)
                val file = withContext(Dispatchers.Default) { BackupCrypto.encrypt(plain!!, pw, BackupCrypto.Purpose.BACKUP) }
                val out = app.contentResolver.openOutputStream(uri, "w") ?: return@job removeNew(app, uri, R.string.backup_failed)
                out.use { it.write(file) }
                // Check what actually landed on the storage, not what we meant to write
                val back = withContext(Dispatchers.Default) { BackupCrypto.decrypt(readLimited(app, uri), pw, BackupCrypto.Purpose.BACKUP) }
                val ok = back.contentEquals(plain!!)
                back.fill(0)
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
                plain?.fill(0)
                pw.fill(' ')
            }
        }
        if (!started) pw.fill(' ')
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
                val file = readLimited(app, uri)
                plain = withContext(Dispatchers.Default) {
                    try {
                        BackupCrypto.decrypt(file, pw, BackupCrypto.Purpose.BACKUP)
                    } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                        // The suggested words typed with spaces or capitals: tried once more as words
                        words = BackupCrypto.asWords(pw)
                        val w = words
                        if (w == null || w.contentEquals(pw)) throw e
                        BackupCrypto.decrypt(file, w, BackupCrypto.Purpose.BACKUP)
                    }
                }
                val snap = BackupData.decode(plain!!)
                // The screen was left meanwhile: nothing decrypted stays behind
                if (mine == generation) _pending.value = snap
                null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                R.string.backup_wrong_password
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
    fun confirmRestore() {
        val snap = _pending.value ?: return
        _pending.value = null
        job {
            // Changes still waiting to be saved would land on top of the restored data
            PersonalStore.dropPending("")
            // Never later and unseen: if the data does not open in time, nothing is replaced
            val ok = withTimeoutOrNull(20_000) { PersonalStore.read { BackupData.replaceAll(it, snap, BACKED_UP_SETTINGS); true } } == true
            if (ok) R.string.backup_restored else R.string.backup_restore_failed
        }
    }

    fun cancelRestore() { _pending.value = null }

    /** The screen is gone for good (not turned): nothing decrypted or secret stays waiting. */
    fun forget() {
        generation++
        _pending.value = null
        password?.fill(' ')
        password = null
        suggested.value = null
        written.value = false
    }
}

/** The settings a backup carries; others (like the date of the last backup) stay on this phone. */
val BACKED_UP_SETTINGS = setOf(KEY_SAFE_MESSAGE, KEY_KIT_REVIEWED, KEY_SUPPLIES)

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

private fun deleteQuietly(context: Context, uri: Uri): Boolean = try {
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

/** A password field: hidden, no suggestions, no learning by the keyboard. */
@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A suggested password, large, to copy by hand. Not selectable: a copied password would sit in
 * the clipboard, where keyboards and sync can see it.
 */
@Composable
private fun ShownPassword(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** The word list, read off the main thread; null for the first moment. */
@Composable
private fun rememberWords(): List<String>? {
    val context = LocalContext.current
    return produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { Words.get(context) } }.value
}

/** True only when the screen goes for good: turning the phone keeps what it holds. */
@Composable
private fun OnLeave(action: () -> Unit) {
    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) { onDispose { if (activity?.isChangingConfigurations != true) action() } }
}

/**
 * Backup: one encrypted file with everything, for a new phone or a lost one. Six random words
 * are suggested; an own password must be long. Written only into a new file, then checked.
 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val busy by BackupJobs.busy.collectAsStateWithLifecycle()
    val notice by BackupJobs.notice.collectAsStateWithLifecycle()
    val pending by BackupJobs.pending.collectAsStateWithLifecycle()
    val words = rememberWords()
    val suggested by BackupJobs.suggested.collectAsStateWithLifecycle()
    val written by BackupJobs.written.collectAsStateWithLifecycle()
    LaunchedEffect(words, suggested) {
        if (words != null && suggested == null) BackupJobs.suggested.value = BackupCrypto.suggestPassword(words, 6)
    }
    // Typed passwords live only while this screen does: never in saved state
    var own by remember { mutableStateOf(false) }
    var ownText by remember { mutableStateOf("") }
    var ownAgain by remember { mutableStateOf("") }
    var openUri by remember { mutableStateOf<Uri?>(null) }
    var openText by remember { mutableStateOf("") }
    OnLeave { BackupJobs.forget(); BackupJobs.clearNotice() }
    val last by remember { PersonalStore.observe { it.settings().observe(KEY_BACKUP_AT) } }.collectAsStateWithLifecycle(null)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) BackupJobs.release() else BackupJobs.save(context, uri)
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { openUri = uri; openText = "" }
    }

    Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
    last?.toLongOrNull()?.takeIf { it > 0 }?.let { at ->
        val date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        Text(stringResource(R.string.backup_last, date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (busy) Text(stringResource(R.string.backup_busy), style = MaterialTheme.typography.bodyLarge)
    notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }

    Heading(R.string.backup_save_title)
    val pw = if (own) ownText else suggested.orEmpty()
    if (!own) {
        Text(stringResource(R.string.backup_suggested), style = MaterialTheme.typography.bodyLarge)
        suggested?.let { ShownPassword(it) }
        TextButton(onClick = {
            words?.let { BackupJobs.suggested.value = BackupCrypto.suggestPassword(it, 6) }
            BackupJobs.written.value = false
        }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_another))
        }
        TextButton(onClick = { own = true; BackupJobs.written.value = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.backup_own)) }
    } else {
        PasswordField(stringResource(R.string.backup_password), ownText) { ownText = it }
        PasswordField(stringResource(R.string.backup_password_again), ownAgain) { ownAgain = it }
        val weak = ownText.isNotEmpty() && BackupCrypto.isTooSimple(ownText.toCharArray())
        if (weak) Text(stringResource(R.string.backup_weak), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (ownAgain.isNotEmpty() && ownAgain != ownText) Text(stringResource(R.string.backup_mismatch), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { own = false; ownText = ""; ownAgain = ""; BackupJobs.written.value = false }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_use_suggested))
        }
    }
    Toggle(stringResource(R.string.backup_written), written) { BackupJobs.written.value = it }
    val ready = written && !busy && pw.isNotEmpty() && (!own || (ownText == ownAgain && !BackupCrypto.isTooSimple(ownText.toCharArray())))
    Button(onClick = {
        BackupJobs.hold(pw)
        // A neutral name: the file does not say which app made it
        save.launch("backup-" + LocalDate.now() + ".bin")
    }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.backup_save)) }
    Text(stringResource(R.string.backup_save_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    HorizontalDivider()
    Heading(R.string.backup_restore_title)
    Text(stringResource(R.string.backup_restore_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { BackupJobs.clearNotice(); pick.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.backup_choose_file))
    }
    openUri?.let { uri ->
        PasswordField(stringResource(R.string.backup_password), openText) { openText = it }
        Button(onClick = { BackupJobs.open(context, uri, openText) }, enabled = openText.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.backup_open))
        }
    }
    pending?.let { snap ->
        val date = Instant.ofEpochMilli(snap.created).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        AlertDialog(
            onDismissRequest = { BackupJobs.cancelRestore() },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = { Text(stringResource(R.string.backup_replace_text, date, snap.people.size, snap.records)) },
            confirmButton = {
                TextButton(onClick = { openUri = null; openText = ""; BackupJobs.confirmRestore() }) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = { TextButton(onClick = { BackupJobs.cancelRestore() }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** What a read of the plan gave: the plan, no plan yet, or a failure. */
private sealed interface PlanRead {
    data class Got(val plan: BackupData.SharedPlan) : PlanRead
    data object Empty : PlanRead
}

/**
 * The family plan as an encrypted QR code for a relative's phone. Two steps: first the
 * password is shown and said aloud, then the code is shown without it, so one photo of the
 * screen never holds both.
 */
@Composable
fun PlanCodeScreen() {
    val scope = rememberCoroutineScope()
    val words = rememberWords()
    var password by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(words) { if (words != null && password == null) password = BackupCrypto.suggestPassword(words, 4) }
    var code by remember { mutableStateOf<String?>(null) }
    var showCode by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }

    Text(stringResource(R.string.plan_code_intro), style = MaterialTheme.typography.bodyLarge)
    val c = code
    if (c != null && showCode) {
        val bitmap = remember(c) { qrBitmap(c, 1024, Qr.MAX_PLAN_BYTES) }
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = stringResource(R.string.plan_code_title), modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.plan_code_how), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { showCode = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.plan_code_show_password))
        }
        return
    }
    Text(stringResource(R.string.plan_code_password), style = MaterialTheme.typography.bodyLarge)
    password?.let { ShownPassword(it) }
    if (c == null) {
        TextButton(onClick = { words?.let { password = BackupCrypto.suggestPassword(it, 4) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_another))
        }
    }
    Button(onClick = {
        if (c != null) { showCode = true; return@Button }
        val pw = (password ?: return@Button).toCharArray()
        working = true; problem = null
        scope.launch {
            try {
                val read = PersonalStore.read { db -> db.plan().get()?.let { PlanRead.Got(BackupData.SharedPlan(it, db.plan().allRows())) } ?: PlanRead.Empty }
                when (read) {
                    null -> problem = R.string.backup_failed
                    PlanRead.Empty -> problem = R.string.plan_code_empty
                    is PlanRead.Got -> {
                        code = withContext(Dispatchers.Default) { PlanCode.encode(read.plan, pw) }
                        showCode = true
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: PlanCode.TooLong) {
                problem = R.string.plan_code_too_long
            } catch (e: OutOfMemoryError) {
                problem = R.string.backup_no_memory
            } catch (e: Throwable) {
                problem = R.string.backup_failed
            } finally {
                pw.fill(' ')
                working = false
            }
        }
    }, enabled = !working && password != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(if (working) R.string.backup_busy else R.string.plan_code_said))
    }
    problem?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
}

/** A relative's plan: the spoken password opens it, the owner sees it and then decides. */
@Composable
fun PlanImportScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    val text = PlanInbox.text
    var typed by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<BackupData.SharedPlan?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    // Leaving the screen drops the scanned code; turning the phone keeps it
    OnLeave { PlanInbox.text = null }

    if (text == null) return Text(stringResource(R.string.plan_code_none), style = MaterialTheme.typography.bodyLarge)
    val p = plan
    if (p == null) {
        Text(stringResource(R.string.plan_code_ask), style = MaterialTheme.typography.bodyLarge)
        PasswordField(stringResource(R.string.backup_password), typed) { typed = it }
        Button(onClick = {
            working = true; problem = null
            // The four words are typed as heard; spaces, capitals and dashes do not matter
            val raw = typed.toCharArray()
            val pw = BackupCrypto.asWords(raw) ?: raw.copyOf()
            raw.fill(' ')
            scope.launch {
                try {
                    plan = withContext(Dispatchers.Default) { PlanCode.decode(text, pw) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: BackupCrypto.WrongPasswordOrDamaged) {
                    problem = R.string.backup_wrong_password
                } catch (e: BackupData.FromNewer) {
                    problem = R.string.backup_from_newer
                } catch (e: OutOfMemoryError) {
                    problem = R.string.backup_no_memory
                } catch (e: Throwable) {
                    problem = R.string.backup_not_ours
                } finally {
                    pw.fill(' ')
                    working = false
                }
            }
        }, enabled = typed.isNotBlank() && !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (working) R.string.backup_busy else R.string.backup_open))
        }
        problem?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fun line(label: Int, value: String) = if (value.isBlank()) null else label to value
        listOfNotNull(
            line(R.string.plan_point1, p.plan.point1),
            line(R.string.plan_point2, p.plan.point2),
            line(R.string.plan_point3, p.plan.point3),
            line(R.string.plan_go_between, listOf(p.plan.goBetweenName, p.plan.goBetweenPhone).filter { it.isNotBlank() }.joinToString(" ")),
        ).forEach { (label, value) ->
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (p.rows.isNotEmpty()) Text(stringResource(R.string.plan_code_rows, p.rows.size), style = MaterialTheme.typography.bodyLarge)
    }
    Button(onClick = { confirm = true }, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.plan_code_take)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.plan_code_replace)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    working = true
                    scope.launch {
                        // Every waiting change to the plan and its rows goes first, so none lands on the new plan
                        PersonalStore.dropPending("plan")
                        val ok = withTimeoutOrNull(20_000) { PersonalStore.read { BackupData.replacePlan(it, p); true } } == true
                        working = false
                        if (ok) {
                            PlanInbox.text = null
                            nav.replace(Route.Mine(MinePart.PLAN))
                        } else {
                            problem = R.string.backup_restore_failed
                        }
                    }
                }) { Text(stringResource(R.string.replace)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    problem?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
}
