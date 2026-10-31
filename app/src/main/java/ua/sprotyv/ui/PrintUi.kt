package ua.sprotyv.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Service
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.print.Pdf
import ua.sprotyv.qr.Qr
import ua.sprotyv.qr.RescuerCode
import ua.sprotyv.qr.Scanned
import ua.sprotyv.reach.Reach
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** A QR code as a black-and-white picture. */
fun qrBitmap(text: String, size: Int, maxBytes: Int = Qr.MAX_BYTES): Bitmap {
    val m = Qr.encode(text, size, maxBytes)
    val pixels = IntArray(size * size) { i -> if (m[i % size, i / size]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

private fun labels(context: Context) = RescuerCode.Labels(
    title = context.getString(R.string.rescuer_title),
    blood = context.getString(R.string.med_blood),
    allergies = context.getString(R.string.med_allergies),
    medicines = context.getString(R.string.rescuer_medicines),
    conditions = context.getString(R.string.rescuer_conditions),
    contact = context.getString(R.string.rescuer_contact),
)

/** What goes on the owner's paper: chosen by the owner, part by part. */
enum class PrintPart { MEDICAL, PLAN, PEOPLE, SERVICES, NOTES, KIT }

/** Everything the owner's printout needs, read in one go. */
private class OwnerData(
    val card: MedicalCard, val people: List<Person>, val plan: FamilyPlan, val ifThen: List<PlanRow>, val roles: List<PlanRow>,
    val notes: List<ua.sprotyv.personal.Note>, val kit: Set<String>,
)

private suspend fun readOwner(): OwnerData? = PersonalStore.read { db ->
    OwnerData(
        db.medical().get() ?: MedicalCard(),
        sortedPeople(db.people().observe().first()),
        db.plan().get() ?: FamilyPlan(),
        db.plan().observeRows(PlanRow.KIND_IF_THEN).first(),
        db.plan().observeRows(PlanRow.KIND_ROLE).first(),
        db.notes().observe().first(),
        db.kit().observe().first().filter { it.checked }.map { it.key }.toSet(),
    )
}

/**
 * Only the parts the owner ticked, and of the notes only the ones chosen. The kit list comes from
 * the guide, with the owner's ticks.
 */
private fun ownerPdf(context: Context, d: OwnerData, parts: Set<PrintPart>, noteIds: Set<Long>, content: Content?): ByteArray {
    val s: (Int) -> String = { context.getString(it) }
    // No app name on the page: the paper should not say which app it came from
    val pdf = Pdf(LocalDate.now().toString())
    pdf.title(s(R.string.print_owner_title))
    pdf.text(s(R.string.print_private), bold = true)
    if (PrintPart.MEDICAL in parts) medicalPart(pdf, s, d)
    if (PrintPart.PLAN in parts) planPart(pdf, s, d)
    if (PrintPart.PEOPLE in parts) {
        pdf.heading(s(R.string.people))
        d.people.forEach { pdf.text(listOf(it.name, it.phone, it.relation).filter { x -> x.isNotBlank() }.joinToString(" · ")) }
    }
    if (PrintPart.SERVICES in parts) {
        pdf.heading(s(R.string.print_services))
        servicesLines(context).forEach { pdf.text(it) }
    }
    if (PrintPart.NOTES in parts) {
        val chosen = d.notes.filter { it.id in noteIds }
        if (chosen.isNotEmpty()) {
            pdf.heading(s(R.string.notes_title_screen))
            chosen.forEach { n ->
                pdf.text(n.name().ifBlank { s(R.string.notes_untitled) }, bold = true)
                val body = if (n.title.isNotBlank()) n.text else n.text.lineSequence().dropWhile { it.isBlank() }.drop(1).joinToString("\n")
                if (body.isNotBlank()) pdf.text(body.trim())
            }
        }
    }
    if (PrintPart.KIT in parts && content != null) {
        pdf.heading(s(R.string.kit))
        ua.sprotyv.content.Kit.groups(content.guide).forEach { g ->
            pdf.text(g.title, bold = true)
            g.items.forEach { pdf.text((if (it.key in d.kit) "✓ " else "○ ") + ua.sprotyv.content.plain(it.text)) }
        }
    }
    return pdf.build()
}

private fun medicalPart(pdf: Pdf, s: (Int) -> String, d: OwnerData) {
    pdf.heading(s(R.string.medical_card))
    pdf.field(s(R.string.med_name), d.card.name)
    pdf.field(s(R.string.med_birth), d.card.birthDate)
    pdf.field(s(R.string.med_blood), d.card.bloodGroup)
    pdf.field(s(R.string.med_allergies), d.card.allergies)
    pdf.field(s(R.string.med_medicines), d.card.medicines)
    pdf.field(s(R.string.med_conditions), d.card.conditions)
    pdf.field(s(R.string.med_notes), d.card.notes)
    d.people.filter { it.ice }.forEach { pdf.field(s(R.string.ice_title), "${it.name} · ${it.phone}") }
}

private fun planPart(pdf: Pdf, s: (Int) -> String, d: OwnerData) {
    pdf.heading(s(R.string.family_plan))
    pdf.field(s(R.string.plan_point1), d.plan.point1)
    pdf.field(s(R.string.plan_point2), d.plan.point2)
    pdf.field(s(R.string.plan_point3), d.plan.point3)
    pdf.field(s(R.string.plan_go_between), listOf(d.plan.goBetweenName, d.plan.goBetweenPhone).filter { it.isNotBlank() }.joinToString(" · "))
    if (d.ifThen.isNotEmpty()) {
        pdf.text(s(R.string.plan_if_then), bold = true)
        d.ifThen.forEach { pdf.text("${s(R.string.plan_if)} ${it.first} — ${s(R.string.plan_then)} ${it.second}") }
    }
    if (d.roles.isNotEmpty()) {
        pdf.text(s(R.string.plan_roles), bold = true)
        d.roles.forEach { pdf.text("${it.first} — ${it.second}") }
    }
    pdf.field(s(R.string.plan_notes), d.plan.notes)
}

private fun servicesLines(context: Context): List<String> = Service.entries.map {
    context.getString(
        when (it) {
            Service.UNIFIED -> R.string.service_unified
            Service.RESCUE -> R.string.service_rescue
            Service.POLICE -> R.string.service_police
            Service.AMBULANCE -> R.string.service_ambulance
            Service.GAS -> R.string.service_gas
        },
    )
}

/** The action cards on paper: nothing personal, for the fridge, the bag and the shelter wall. */
fun cardsPdf(context: Context, content: Content): ByteArray {
    val s: (Int) -> String = { context.getString(it) }
    val pdf = Pdf(s(R.string.app_name) + " · " + LocalDate.now())
    pdf.title(s(R.string.print_cards_title))
    pdf.heading(s(R.string.print_services))
    servicesLines(context).forEach { pdf.text(it) }
    content.cards.forEach { card ->
        pdf.heading(card.title)
        card.steps.forEachIndexed { i, step -> pdf.text("${i + 1}. ${step.action}") }
        pdf.add(Pdf.Part.Text(s(R.string.source_line).format(card.source) + " · " +
            s(R.string.checked_on).format(card.checkedOn) + " · " +
            if (card.reviewedBy.isBlank()) s(R.string.not_reviewed) else s(R.string.reviewed_by).format(card.reviewedBy)))
    }
    return pdf.build()
}

private fun write(context: Context, uri: Uri, bytes: ByteArray): Boolean {
    for (mode in listOf("wt", "w")) {
        try {
            if (context.contentResolver.openOutputStream(uri, mode)?.use { it.write(bytes) } != null) return true
        } catch (e: Exception) {
            // Some providers do not know "wt"; the next mode is tried
        }
    }
    return false
}

/**
 * Saving runs outside any screen: choosing a place takes the app off the screen for a moment,
 * which closes the owner's data and may rebuild the screen, and must not cancel the save.
 * A file that could not be written is removed, so no empty file is left behind.
 */
object Exports {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    /** The last result, with the kind of save it belongs to, so it shows only on its own screen. */
    private val _result = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, Int>?>(null)
    val result: kotlinx.coroutines.flow.StateFlow<Pair<String, Int>?> = _result

    fun clear() { _result.value = null }

    /**
     * [produce] gets the app context (never a screen's), and has 20 seconds: a save that waits
     * longer (the owner's data closed and not reopened) is given up and its empty file removed.
     */
    fun save(context: Context, uri: Uri, kind: String, produce: suspend (Context) -> ByteArray?) {
        val app = context.applicationContext
        scope.launch {
            val ok = try {
                val bytes = kotlinx.coroutines.withTimeoutOrNull(20_000) { produce(app) }
                bytes != null && write(app, uri, bytes)
            } catch (e: Exception) {
                false
            }
            if (!ok) {
                try { android.provider.DocumentsContract.deleteDocument(app.contentResolver, uri) } catch (e: Exception) { }
            }
            _result.value = kind to if (ok) R.string.print_saved else R.string.print_failed
        }
    }
}

/**
 * Printing the owner's own papers: the owner ticks what goes on them (the medical card, the plan,
 * people, services, chosen notes, the go-bag list). The file is not encrypted, and the screen says
 * so first.
 */
@Composable
fun PrintScreen(content: Content?) {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    var parts by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf(PrintPart.MEDICAL, PrintPart.PLAN, PrintPart.PEOPLE, PrintPart.SERVICES).map { it.name }) }
    var noteIds by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(listOf<Long>()) }
    val notes by remember { PersonalStore.observe { it.notes().observe() } }.collectAsStateWithLifecycle(null)
    val result = Exports.result.collectAsStateWithLifecycle().value?.takeIf { it.first == KIND_OWNER }?.second
    val chosen = parts.mapNotNull { n -> PrintPart.entries.firstOrNull { it.name == n } }.toSet()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // What was chosen at the moment of saving, never a later change
        val p = chosen
        val ids = noteIds.toSet()
        Exports.save(context, uri, KIND_OWNER) { app -> readOwner()?.let { ownerPdf(app, it, p, ids, content) } }
    }
    Text(stringResource(R.string.print_intro), style = MaterialTheme.typography.bodyLarge)
    Block(stringResource(R.string.print_choose), ua.sprotyv.home.Group.GUIDE) {
        listOf(
            PrintPart.MEDICAL to R.string.medical_card, PrintPart.PLAN to R.string.family_plan, PrintPart.PEOPLE to R.string.people,
            PrintPart.SERVICES to R.string.print_services, PrintPart.KIT to R.string.kit, PrintPart.NOTES to R.string.notes_title_screen,
        ).forEach { (part, label) ->
            Toggle(stringResource(label), part in chosen) { on ->
                parts = if (on) parts + part.name else parts - part.name
                // Notes: the pinned ones are offered first; the owner adds or removes any
                if (part == PrintPart.NOTES && on && noteIds.isEmpty()) noteIds = notes.orEmpty().filter { it.pinned }.map { it.id }
            }
        }
        if (PrintPart.NOTES in chosen) {
            val list = notes.orEmpty()
            if (list.isEmpty()) Text(stringResource(R.string.notes_empty), style = MaterialTheme.typography.bodyMedium)
            list.forEach { n ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).androidx_toggle(n.id in noteIds) { on -> noteIds = if (on) noteIds + n.id else noteIds - n.id }
                        .padding(start = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = n.id in noteIds, onCheckedChange = null)
                    Text(n.name().ifBlank { stringResource(R.string.notes_untitled) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    val nothing = chosen.isEmpty() || (chosen == setOf(PrintPart.NOTES) && noteIds.isEmpty())
    Button(enabled = !nothing, onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.print_owner_button))
    }
    result?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.print_warning_title)) },
            text = { Text(stringResource(R.string.print_warning)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; Exports.clear(); Away.forOwnWindow(); save.launch("plan.pdf") }) { Text(stringResource(R.string.print_continue)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A button that saves the action cards as a PDF; used on the "What to do now" screen. */
@Composable
fun CardsPdfButton(content: Content) {
    val context = LocalContext.current
    val result = Exports.result.collectAsStateWithLifecycle().value?.takeIf { it.first == KIND_CARDS }?.second
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Exports.save(context, uri, KIND_CARDS) { app -> cardsPdf(app, content) }
    }
    OutlinedButton(onClick = { Exports.clear(); Away.forOwnWindow(); save.launch("cards.pdf") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.print_cards_button))
    }
    result?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
}

/**
 * The rescuer's code: blood group, allergies, medicines and the emergency contact as a QR code
 * any phone camera reads, and as a picture to keep on paper or on the lock screen.
 */
@Composable
fun RescuerQrScreen(nav: Nav) {
    val context = LocalContext.current
    val people = rememberPeople() ?: return Loading()
    val card = when (val l = rememberRead { it.medical().get() ?: MedicalCard() }) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> return ReadFailed()
        is Loaded.Value -> l.value
    }
    if (card.bloodGroup.isBlank() && card.allergies.isBlank() && card.medicines.isBlank() && card.conditions.isBlank()) {
        Text(stringResource(R.string.rescuer_empty), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { nav.go(Route.Mine(MinePart.MEDICAL)) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.medical_card)) }
        return
    }
    val code = remember(card, people) { RescuerCode.text(card, people.filter { it.ice }, labels(context)) }
    val text = code.text
    val bitmap = remember(text) { qrBitmap(text, 640) }
    val result = Exports.result.collectAsStateWithLifecycle().value?.takeIf { it.first == KIND_RESCUER }?.second
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Exports.save(context, uri, KIND_RESCUER) {
            java.io.ByteArrayOutputStream().use { out -> rescuerPicture(text, bitmap).compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        }
    }
    Text(stringResource(R.string.rescuer_intro), style = MaterialTheme.typography.bodyLarge)
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.rescuer_qr_desc),
        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
    )
    // Plain text, not selectable: medical facts should not end up in the clipboard by accident
    Text(text, style = MaterialTheme.typography.bodyLarge)
    if (code.cut) Text(stringResource(R.string.rescuer_cut), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.rescuer_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    Button(onClick = { Exports.clear(); Away.forOwnWindow(); save.launch("medical.png") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.rescuer_save))
    }
    result?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    Text(stringResource(R.string.rescuer_lock_screen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A tall picture for the lock screen: the key facts in large letters and the code below. */
private fun rescuerPicture(text: String, qr: Bitmap): Bitmap {
    val w = 1080
    // Tall enough for the text (at most ~400 bytes) and the code below it
    val h = 2400
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(Color.WHITE)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 44f; typeface = Typeface.DEFAULT_BOLD }
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w - 160).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    // Below the lock screen clock; the code under the text, never over it
    val textTop = 600f
    c.save()
    c.translate(80f, textTop)
    layout.draw(c)
    c.restore()
    val size = 640
    val top = (textTop.toInt() + layout.height + 60)
    c.drawBitmap(qr, null, android.graphics.Rect((w - size) / 2, top, (w + size) / 2, top + size), Paint())
    return out
}

/**
 * Reading a QR code with the phone's own camera app (no camera permission) or from a picture.
 * What the code holds is shown first; nothing is opened without the person's choice.
 */
@Composable
fun ScanScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanned by remember { mutableStateOf<Scanned?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var noCamera by remember { mutableStateOf(false) }
    var openLink by remember { mutableStateOf<Scanned.Link?>(null) }
    val photo = remember { scanFile(context) }

    fun read(uri: Uri, delete: Boolean) {
        scope.launch {
            val text = try {
                withContext(Dispatchers.Default) { decodeUri(context, uri) }
            } finally {
                // Deleted even if the screen is left while reading
                if (delete) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { photo.delete() }
            }
            scanned = text?.let(Scanned::of)
            notFound = text == null
        }
    }
    // No clean-up when the screen goes away: rotating the phone while the camera is open would
    // delete the photo before it is read. The photo is deleted after reading, when the camera is
    // cancelled, and on the next fresh start

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        // ok == false: cancelled, the (empty) photo goes at once
        if (ok) read(photoUri(context, photo), delete = true) else photo.delete()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri, delete = false)
    }

    SecureScreen()
    Frame(stringResource(R.string.scan_title), nav::back) {
        ScreenshotsStrip()
        Text(stringResource(R.string.scan_intro), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = {
            scanned = null; notFound = false; noCamera = false
            try { Away.forOwnWindow(); camera.launch(photoUri(context, photo)) } catch (e: Exception) { Away.back(); noCamera = true }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.scan_camera)) }
        OutlinedButton(onClick = {
            scanned = null; notFound = false
            Away.forOwnWindow()
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.scan_picture)) }
        if (notFound) Text(stringResource(R.string.scan_none), style = MaterialTheme.typography.bodyLarge)
        if (noCamera) Text(stringResource(R.string.scan_no_camera), style = MaterialTheme.typography.bodyLarge)
        scanned?.let { s ->
            if (s is Scanned.Plain && ua.sprotyv.personal.backup.PlanCode.isPlanCode(s.text)) {
                // A relative's family plan: opened on the owner's protected screen, with the spoken password
                Text(stringResource(R.string.plan_code_found), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.plan_code_found_hint), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { PlanInbox.text = s.text; nav.go(Route.Mine(MinePart.PLAN_IMPORT)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.plan_code_open))
                }
            } else {
                ScanResult(s, onOpen = { openLink = it })
            }
        }
    }
    openLink?.let { link ->
        AlertDialog(
            onDismissRequest = { openLink = null },
            title = { Text(stringResource(R.string.scan_open_title)) },
            text = { Text(stringResource(R.string.scan_open_text, link.host)) },
            confirmButton = {
                TextButton(onClick = {
                    openLink = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.text)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e: Exception) { }
                }) { Text(stringResource(R.string.scan_open)) }
            },
            dismissButton = { TextButton(onClick = { openLink = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ScanResult(s: Scanned, onOpen: (Scanned.Link) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (s) {
            is Scanned.Link -> {
                Text(stringResource(R.string.scan_link_to), style = MaterialTheme.typography.labelLarge)
                Text(s.host, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                SelectionContainer { Text(s.text, style = MaterialTheme.typography.bodyMedium) }
                s.suspicious.forEach { w ->
                    Text("⚠ " + stringResource(warning(w)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.scan_link_note), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onOpen(s) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.scan_open)) }
            }
            is Scanned.Phone -> {
                // The number shown is exactly the number that will be dialled
                val number = ua.sprotyv.reach.Links.clean(s.number)
                Text(number, style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { Reach.dial(context, number) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.call_number, number)) }
            }
            is Scanned.Plain -> SelectionContainer { Text(s.text, style = MaterialTheme.typography.bodyLarge) }
        }
        OutlinedButton(onClick = { copy(context, s.text) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.scan_copy)) }
    }
}

private fun warning(code: String): Int = when (code) {
    Scanned.NOT_HTTPS -> R.string.scan_warn_not_https
    Scanned.HIDDEN_ADDRESS -> R.string.scan_warn_hidden
    Scanned.LOOKALIKE_LETTERS -> R.string.scan_warn_lookalike
    Scanned.BARE_NUMBERS -> R.string.scan_warn_numbers
    Scanned.ENCODED -> R.string.scan_warn_encoded
    else -> R.string.scan_warn_many_parts
}

/** Copies text, marked as sensitive so Android does not show it in the clipboard preview. */
private fun copy(context: Context, text: String) {
    val clip = ClipData.newPlainText("", text)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** The camera writes here (the app's own cache), and the photo is deleted right after reading. */
private fun scanFile(context: Context): File {
    val dir = File(context.cacheDir, "scan").apply { mkdirs() }
    return File(dir, "scan.jpg")
}

const val KIND_OWNER = "owner"
const val KIND_CARDS = "cards"
const val KIND_RESCUER = "rescuer"

/** Removes every photo in the scan folder. */
fun clearScans(context: Context) {
    File(context.cacheDir, "scan").listFiles()?.forEach { it.delete() }
}

private fun photoUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/** Reads a picture, made smaller first so a large photo does not run out of memory. */
private fun decodeUri(context: Context, uri: Uri): String? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    // Large enough for the family plan code (about 137 squares a side) taken from a screen
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    bitmap?.let { b ->
        val pixels = IntArray(b.width * b.height)
        b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
        Qr.decode(b.width, b.height, pixels).also { b.recycle() }
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

private fun Modifier.androidx_toggle(on: Boolean, change: (Boolean) -> Unit): Modifier =
    this.then(Modifier.toggleable(value = on, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = change))
