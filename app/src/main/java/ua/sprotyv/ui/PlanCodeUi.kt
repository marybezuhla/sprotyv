package ua.sprotyv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ua.sprotyv.R
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.backup.BackupCrypto
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.personal.backup.PlanCode
import ua.sprotyv.qr.Qr

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
    SharedPlanView(p)
    Button(onClick = { confirm = true }, enabled = !working, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.plan_code_take)) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { SensitiveWindow(); Text(stringResource(R.string.plan_code_replace)) },
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

/** What a received plan holds, line by line, before the owner decides to take it. */
@Composable
private fun SharedPlanView(p: BackupData.SharedPlan) {
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
}
