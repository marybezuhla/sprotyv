package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import ua.sprotyv.R
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PersonalDb
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.reach.Reach

// Personal data is held in plain `remember`, never `rememberSaveable`: Android's saved state lives
// outside the encrypted database. Screens read and change it only through PersonalStore.

/** A one-time read of the owner's data: still loading, failed, or the value. */
sealed interface Loaded<out T> {
    data object Waiting : Loaded<Nothing>
    data object Failed : Loaded<Nothing>
    data class Value<T>(val value: T) : Loaded<T>
}

@Composable
fun <T : Any> rememberRead(query: suspend (PersonalDb) -> T): Loaded<T> {
    val state by produceState<Loaded<T>>(Loaded.Waiting) {
        value = PersonalStore.read(query)?.let { Loaded.Value(it) } ?: Loaded.Failed
    }
    return state
}

/** People sorted by name the Ukrainian way (Є, І, Ї, Ґ in their places). */
fun sortedPeople(list: List<Person>): List<Person> {
    val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("uk"))
    // The owner's order first (most important on top), then by name
    return list.sortedWith(compareBy<Person> { it.sort }.thenComparator { a, b -> collator.compare(a.name, b.name) })
}

@Composable
fun rememberPeople(): List<Person>? {
    val people by remember { PersonalStore.observe { it.people().observe() } }.collectAsStateWithLifecycle(null)
    return remember(people) { people?.let(::sortedPeople) }
}

@Composable
fun NavRow(title: String, hint: String = "", group: ua.sprotyv.home.Group = ua.sprotyv.home.Group.MINE, onClick: () -> Unit) {
    // Never a plain grey card: always the colour of what it opens
    val tint = group.tint()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = tint.container, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, tint.ink.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Mine": the owner's own parts, all encrypted and on this phone only. */
@Composable
fun MineHub(nav: Nav, content: ua.sprotyv.content.Content?) {
    // The colour code of the whole app: green safety and health, blue prepare, neutral copies
    Block(stringResource(R.string.mine_block_now), ua.sprotyv.home.Group.FIRST_AID, padded = false, leading = { ThemeIcon(R.drawable.ocha_walkie_talkie, ua.sprotyv.home.Group.FIRST_AID.tint()) }) {
        BlockRow(stringResource(R.string.tile_safe), stringResource(R.string.tile_safe_hint), first = true) { nav.go(Route.Open(Section.SAFE)) }
    }
    // Who I am now and what is already done: found right here, not only from "My path"
    Block(stringResource(R.string.mine_block_me), ua.sprotyv.home.Group.PREPARE, tint = resist(), padded = false, leading = { ThemeIcon(R.drawable.ocha_users, resist()) }) {
        BlockRow(stringResource(R.string.roles_title), stringResource(R.string.roles_hint), first = true) { nav.go(Route.Mine(MinePart.ROLES)) }
        BlockRow(stringResource(R.string.shields_title), stringResource(R.string.shields_hint)) { nav.go(Route.Mine(MinePart.SHIELDS)) }
    }
    // Notes on their own, on a light "paper" plate, with the pinned and newest ones visible
    NotesPlate(nav)
    ReadinessBlock(content, nav)
    Block(stringResource(R.string.mine_block_health), ua.sprotyv.home.Group.FIRST_AID, padded = false, foldable = true, summary = stringResource(R.string.mine_block_health_desc), leading = { ThemeIcon(R.drawable.ocha_health, ua.sprotyv.home.Group.FIRST_AID.tint()) }) {
        BlockRow(stringResource(R.string.medical_card), stringResource(R.string.medical_card_hint), first = true) { nav.go(Route.Mine(MinePart.MEDICAL)) }
        BlockRow(stringResource(R.string.rescuer_screen), stringResource(R.string.rescuer_hint)) { nav.go(Route.Mine(MinePart.RESCUER)) }
    }
    Block(stringResource(R.string.mine_block_copies), ua.sprotyv.home.Group.MINE, padded = false, foldable = true, summary = stringResource(R.string.mine_block_copies_desc), leading = { ThemeIcon(R.drawable.ocha_services_and_tools, ua.sprotyv.home.Group.MINE.tint()) }) {
        BlockRow(stringResource(R.string.docs_title), stringResource(R.string.docs_hint), first = true) { nav.go(Route.Mine(MinePart.DOCUMENTS)) }
        BlockRow(stringResource(R.string.backup_title), stringResource(R.string.backup_hint)) { nav.go(Route.Mine(MinePart.BACKUP)) }
        BlockRow(stringResource(R.string.bookmarks_title), stringResource(R.string.bookmarks_hint)) { nav.go(Route.Mine(MinePart.BOOKMARKS)) }
        BlockRow(stringResource(R.string.print_title), stringResource(R.string.print_hint)) { nav.go(Route.Mine(MinePart.PRINT)) }
        BlockRow(stringResource(R.string.plan_code_title), stringResource(R.string.plan_code_hint)) { nav.go(Route.Mine(MinePart.PLAN_QR)) }
        BlockRow(stringResource(R.string.scan_title), stringResource(R.string.scan_hint)) { nav.go(Route.Scan) }
        BlockRow(stringResource(R.string.qr_text_title), stringResource(R.string.qr_text_hint)) { nav.go(Route.QrText) }
    }
    Text(stringResource(R.string.mine_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A text field; what happens to each change is up to the caller. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    multiline: Boolean = false,
    phone: Boolean = false,
    hint: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(if (multiline) MAX_LONG else MAX_SHORT)) },
        label = { Text(label) },
        placeholder = if (hint.isNotBlank()) ({ Text(hint) }) else null,
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        // No autocorrect; on the owner's screens the keyboard is also asked not to learn (NoKeyboardLearning)
        keyboardOptions = KeyboardOptions(
            keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text,
            capitalization = if (phone) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_SHORT = 200
private const val MAX_LONG = 2000

/** The medical card: what rescuers and doctors need to know when the owner cannot tell them. */
@Composable
fun MedicalScreen() {
    val start = when (val l = rememberRead { it.medical().get() ?: MedicalCard() }) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> return ReadFailed()
        is Loaded.Value -> l.value
    }
    Text(stringResource(R.string.medical_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // Shown as text; changed only through "Change" and "Save", so a stray tap edits nothing
    EditableBlock("medical", stringResource(R.string.medical_card), ua.sprotyv.home.Group.FIRST_AID, start,
        onSave = { next -> val saved = next.copy(updatedAt = System.currentTimeMillis()); PersonalStore.save("medical") { it.medical().save(saved) } },
        view = { card ->
            ReadField(stringResource(R.string.med_name), card.name)
            ReadField(stringResource(R.string.med_birth), card.birthDate)
            ReadField(stringResource(R.string.med_blood), card.bloodGroup)
            ReadField(stringResource(R.string.med_pregnancy), card.pregnancy)
            ReadField(stringResource(R.string.med_allergies), card.allergies)
            ReadField(stringResource(R.string.med_medicines), card.medicines)
            ReadField(stringResource(R.string.med_conditions), card.conditions)
            ReadField(stringResource(R.string.med_notes), card.notes)
        },
        edit = { card, change ->
            Field(stringResource(R.string.med_name), card.name, { change(card.copy(name = it)) })
            Field(stringResource(R.string.med_birth), card.birthDate, { change(card.copy(birthDate = it)) }, hint = stringResource(R.string.med_birth_hint))
            Field(stringResource(R.string.med_blood), card.bloodGroup, { change(card.copy(bloodGroup = it)) }, hint = stringResource(R.string.med_blood_hint))
            Field(stringResource(R.string.med_pregnancy), card.pregnancy, { change(card.copy(pregnancy = it)) }, hint = stringResource(R.string.med_pregnancy_hint))
            Field(stringResource(R.string.med_allergies), card.allergies, { change(card.copy(allergies = it)) }, multiline = true)
            Field(stringResource(R.string.med_medicines), card.medicines, { change(card.copy(medicines = it)) }, multiline = true, hint = stringResource(R.string.med_medicines_hint))
            Field(stringResource(R.string.med_conditions), card.conditions, { change(card.copy(conditions = it)) }, multiline = true)
            Field(stringResource(R.string.med_notes), card.notes, { change(card.copy(notes = it)) }, multiline = true)
        },
    )

    val ice = rememberPeople().orEmpty().filter { it.ice }
    Block(stringResource(R.string.ice_title), ua.sprotyv.home.Group.FIRST_AID) {
        if (ice.isEmpty()) Text(stringResource(R.string.ice_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A tap opens the dialler with the number, as the 112 button does
        val context = androidx.compose.ui.platform.LocalContext.current
        ice.forEach { p ->
            TextButton(onClick = { ua.sprotyv.reach.Reach.dial(context, p.phone) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                Text("${p.name} · ${p.phone}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Trusted people: a short list of the most important (at most [Vcf.MAX_PEOPLE]), in the owner's
 * order. Each opens to be read, with one tap to call or write; it changes only after "Change".
 * Contacts come in and go out as .vcf files the owner picks, never from the phone's contacts.
 */
@Composable
fun PeopleScreen() {
    val context = LocalContext.current
    val people = rememberPeople() ?: return Loading()
    var editing by remember { mutableStateOf<Person?>(null) }
    var viewing by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmExport by remember { mutableStateOf(false) }
    val full = people.size >= Vcf.MAX_PEOPLE
    Text(stringResource(R.string.people_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { editing = Person(name = "", phone = "", sort = (people.maxOfOrNull { it.sort } ?: -1) + 1) }, enabled = !full, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.add_person)) }
    Text(stringResource(if (full) R.string.people_full else R.string.people_count, people.size, Vcf.MAX_PEOPLE), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // People in one blue block of the family's preparation, a line each, the most important first
    if (people.isNotEmpty()) Block(stringResource(R.string.people), ua.sprotyv.home.Group.PREPARE, padded = false) {
        people.forEachIndexed { i, p ->
            BlockRow(p.name, listOfNotNull(p.phone, p.relation.ifBlank { null }, if (p.ice) stringResource(R.string.ice_short) else null).joinToString(" · "), first = i == 0) { viewing = p.id }
        }
    }
    // Contacts in and out as a file: no access to the phone's own contacts is ever asked for
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Away.back()
        if (uri != null) PeopleFiles.import(context, uri, people) { notice = it }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        Away.back()
        if (uri != null) PeopleFiles.export(context, uri, people) { notice = it }
    }
    Block(stringResource(R.string.people_files_title), ua.sprotyv.home.Group.MINE, foldable = true, summary = stringResource(R.string.people_files_hint)) {
        Text(stringResource(R.string.people_files_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = {
            Away.forOwnWindow()
            try { pick.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*")) } catch (e: android.content.ActivityNotFoundException) { Away.back() }
        }, enabled = !full, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.people_import)) }
        OutlinedButton(onClick = { confirmExport = true }, enabled = people.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.people_export)) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            title = { Text(stringResource(R.string.people_export_title)) },
            text = { Text(stringResource(R.string.people_export_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExport = false
                    Away.forOwnWindow()
                    try { save.launch("contacts.vcf") } catch (e: android.content.ActivityNotFoundException) { Away.back() }
                }) { Text(stringResource(R.string.people_export)) }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    viewing?.let { id ->
        val p = people.firstOrNull { it.id == id }
        if (p == null) viewing = null else PersonCard(p, people, onEdit = { viewing = null; editing = p }, onClose = { viewing = null })
    }
    editing?.let { PersonDialog(it, onDone = { editing = null }) }
}

/** One person, to read: one tap to call or write, the order changed here, the words only after "Change". */
@Composable
private fun PersonCard(p: Person, people: List<Person>, onEdit: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val index = people.indexOfFirst { it.id == p.id }
    fun move(by: Int) {
        val order = people.toMutableList()
        val to = (index + by).coerceIn(0, order.size - 1)
        if (to == index) return
        order.add(to, order.removeAt(index))
        // Everyone numbered again in the new order, so the order is always plain
        val sorts = order.mapIndexed { i, x -> x.id to i }
        PersonalStore.save("people:order") { db -> sorts.forEach { (id, i) -> db.people().setSort(id, i) } }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(p.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadField(stringResource(R.string.person_phone), p.phone)
                ReadField(stringResource(R.string.person_relation), p.relation)
                val marks = listOfNotNull(
                    if (p.safeList) stringResource(R.string.person_safe_list) else null,
                    if (p.ice) stringResource(R.string.person_ice) else null,
                )
                if (marks.isNotEmpty()) Text(marks.joinToString("\n") { "✓ $it" }, style = MaterialTheme.typography.bodyMedium)
                // Ways to reach, each opening the phone's own app with this number
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ua.sprotyv.reach.Reach.dial(context, p.phone) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.person_call)) }
                    OutlinedButton(onClick = { ua.sprotyv.reach.Reach.smsOne(context, p.phone) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("SMS") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ua.sprotyv.reach.Reach.whatsapp(context, p.phone, "") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("WhatsApp") }
                    OutlinedButton(onClick = { ua.sprotyv.reach.Reach.signalTo(context, p.phone) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Signal") }
                }
                // The most important on top
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { move(-1) }, enabled = index > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.person_up)) }
                    TextButton(onClick = { move(1) }, enabled = index in 0 until people.size - 1, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.person_down)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) } },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) } },
    )
}

/** Trusted people to and from .vcf files the owner chooses. */
object PeopleFiles {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun import(context: android.content.Context, uri: android.net.Uri, now: List<Person>, done: (String) -> Unit) {
        val app = context.applicationContext
        scope.launch {
            val message = try {
                val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                    val buf = java.io.ByteArrayOutputStream()
                    val b = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(b)
                        if (n < 0) break
                        buf.write(b, 0, n)
                        if (buf.size() > Vcf.MAX_FILE_BYTES) throw IllegalArgumentException()
                    }
                    buf.toByteArray()
                } ?: throw IllegalArgumentException()
                val found = Vcf.read(String(bytes, Charsets.UTF_8))
                if (found.isEmpty()) {
                    app.getString(R.string.people_import_none)
                } else {
                    val known = now.map { ua.sprotyv.reach.Links.clean(it.phone) }.toMutableSet()
                    val room = (Vcf.MAX_PEOPLE - now.size).coerceAtLeast(0)
                    val fresh = found.filter { known.add(ua.sprotyv.reach.Links.clean(it.phone)) }
                    val taken = fresh.take(room)
                    var sort = (now.maxOfOrNull { it.sort } ?: -1) + 1
                    val added = taken.map { c -> Person(name = c.name, phone = c.phone, relation = c.note, safeList = false, ice = false, sort = sort++) }
                    val ok = PersonalStore.read { db -> added.forEach { db.people().save(it) }; true } == true
                    if (!ok) app.getString(R.string.people_import_failed)
                    else app.getString(R.string.people_import_done, added.size, found.size - added.size)
                }
            } catch (e: Exception) {
                app.getString(R.string.people_import_failed)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { done(message) }
        }
    }

    fun export(context: android.content.Context, uri: android.net.Uri, people: List<Person>, done: (String) -> Unit) {
        val app = context.applicationContext
        val text = Vcf.write(people.map { Vcf.Contact(it.name, it.phone, it.relation) })
        scope.launch {
            val ok = try {
                app.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
            } catch (e: Exception) {
                false
            }
            if (!ok) deleteQuietly(app, uri)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                done(app.getString(if (ok) R.string.people_export_done else R.string.people_export_failed))
            }
        }
    }
}

@Composable
private fun PersonDialog(start: Person, onDone: () -> Unit) {
    var p by remember(start) { mutableStateOf(start) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = p.name.isNotBlank() && ua.sprotyv.reach.Links.clean(p.phone).removePrefix("+").length >= 3
    AlertDialog(
        onDismissRequest = onDone,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        title = { Text(stringResource(if (start.id == 0L) R.string.add_person else R.string.edit_person)) },
        text = {
            // Scrolls: with large text or the keyboard up the switches below must still be reachable
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(stringResource(R.string.person_name), p.name, { p = p.copy(name = it) })
                Field(stringResource(R.string.person_phone), p.phone, { p = p.copy(phone = it) }, phone = true, hint = "+380…")
                if (!p.phone.trim().startsWith("+") && !ua.sprotyv.reach.Links.clean(p.phone).let { it.length == 10 && it.startsWith("0") } && p.phone.isNotBlank()) {
                    Text(stringResource(R.string.person_phone_format), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                // Why Save is greyed out, said plainly
                if (!valid) Text(stringResource(R.string.person_need), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(stringResource(R.string.person_relation), p.relation, { p = p.copy(relation = it) })
                Toggle(stringResource(R.string.person_safe_list), p.safeList) { p = p.copy(safeList = it) }
                Toggle(stringResource(R.string.person_ice), p.ice) { p = p.copy(ice = it) }
                if (start.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_person)) }
                }
            }
        },
        confirmButton = {
            var sent by remember { mutableStateOf(false) }
            TextButton(enabled = valid && !sent, onClick = {
                sent = true
                val saved = p.copy(name = p.name.trim(), phone = p.phone.trim(), relation = p.relation.trim())
                PersonalStore.save("person:" + java.util.UUID.randomUUID()) { it.people().save(saved) }
                onDone()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_person_confirm, start.name)) },
            confirmButton = {
                TextButton(onClick = {
                    PersonalStore.save("delete:" + java.util.UUID.randomUUID()) { it.people().delete(start) }
                    confirmDelete = false
                    onDone()
                }) { Text(stringResource(R.string.delete_person)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        // The whole row is the switch, so TalkBack reads its name with it
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * "I am safe": a ready message to the chosen people. Sprotyv only hands it to the SMS app or a
 * messenger; the owner presses Send there. SMS works with no internet.
 */
@Composable
fun SafeScreen(nav: Nav) {
    val context = LocalContext.current
    val people = rememberPeople() ?: return Loading()
    val list = people.filter { it.safeList }
    val defaultText = stringResource(R.string.safe_default_message)
    val stored = when (val l = rememberRead { it.settings().get(KEY_SAFE_MESSAGE) ?: "" }) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> ""
        is Loaded.Value -> l.value
    }
    var text by remember(stored) { mutableStateOf(stored.ifBlank { defaultText }) }
    // The owner's own "I am fine" as last typed: coming back to it from another status shows it, not the old one
    var okText by remember(stored) { mutableStateOf(stored.ifBlank { defaultText }) }
    // Three ready messages: "I am fine" (the owner's own words, saved), "I need help", "I can't talk"
    var status by remember { mutableStateOf(SafeStatus.OK) }
    val helpText = stringResource(R.string.safe_help_message)
    val quietText = stringResource(R.string.safe_quiet_message)

    if (list.isEmpty()) {
        Text(stringResource(R.string.safe_no_people), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { nav.go(Route.Mine(MinePart.PEOPLE)) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.add_person)) }
        return
    }
    Text(stringResource(R.string.safe_status), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    SafeStatus.entries.forEach { st ->
        val on = st == status
        Card(
            onClick = {
                status = st
                text = when (st) {
                    SafeStatus.OK -> okText
                    SafeStatus.HELP -> helpText
                    SafeStatus.QUIET -> quietText
                }
            },
            colors = CardDefaults.cardColors(
                // "All is well" green, "I need help" danger orange, "cannot talk" neutral
                containerColor = if (!on) MaterialTheme.colorScheme.surface else safeTint(st).container,
            ),
            border = BorderStroke(if (on) 2.dp else 1.dp, if (on) safeTint(st).ink else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = on },
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(st.mark, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(st.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    // The message is read first; it changes only after "Change", so a stray touch spoils nothing
    var editingText by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (!editingText) {
        ReadField(stringResource(R.string.safe_message), text)
        OutlinedButton(onClick = { editingText = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.edit)) }
    } else {
        // Written aside; the message changes only on "Save"
        var draft by remember(text) { mutableStateOf(text) }
        androidx.compose.runtime.LaunchedEffect(draft, text) { EditGuard.mark("safe-message", draft != text) }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { EditGuard.mark("safe-message", false) } }
        Field(stringResource(R.string.safe_message), draft, { draft = it }, multiline = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                text = draft
                // Only "I am fine" is kept as the owner's own words; the other two are written fresh each time
                if (status == SafeStatus.OK) {
                    okText = draft
                    val kept = draft
                    PersonalStore.save("setting:$KEY_SAFE_MESSAGE") { db -> db.settings().put(ua.sprotyv.personal.Setting(KEY_SAFE_MESSAGE, kept)) }
                }
                editingText = false
            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
            OutlinedButton(onClick = { draft = text; editingText = false }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
        }
    }
    // One SMS to each person is the surest: several numbers in one go may be sent as an MMS, which needs mobile data
    Text(stringResource(R.string.safe_each), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
    list.forEach { p ->
        Text(p.name, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { Reach.sms(context, listOf(p.phone), text) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("SMS") }
            OutlinedButton(onClick = { Reach.whatsapp(context, p.phone, text) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("WhatsApp") }
        }
    }
    Text(stringResource(R.string.safe_sms_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    if (list.size > 1) {
        OutlinedButton(onClick = { Reach.sms(context, list.map { it.phone }, text) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.safe_sms_all, list.size))
        }
    }
    OutlinedButton(onClick = { Reach.signal(context, text) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(stringResource(R.string.safe_signal))
    }
    Text(stringResource(R.string.safe_how), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A read of the owner's data failed; nothing was changed. */
@Composable
fun ReadFailed() {
    Text(stringResource(R.string.read_failed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}

const val KEY_SAFE_MESSAGE = "safe_message"

/** The three ready messages for family; the mark is for the eye, the words say it. */
enum class SafeStatus(val title: Int, val mark: String) {
    OK(R.string.safe_status_ok, "✅"),
    HELP(R.string.safe_status_help, "🆘"),
    QUIET(R.string.safe_status_quiet, "🤫"),
}

@Composable
private fun safeTint(st: SafeStatus): Tint = when (st) {
    SafeStatus.OK -> ua.sprotyv.home.Group.FIRST_AID.tint()
    SafeStatus.HELP -> ua.sprotyv.home.Group.DANGER.tint()
    SafeStatus.QUIET -> ua.sprotyv.home.Group.GUIDE.tint()
}
