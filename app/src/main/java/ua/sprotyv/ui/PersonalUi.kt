package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Checkbox
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
    return list.sortedWith { a, b -> collator.compare(a.name, b.name) }
}

@Composable
fun rememberPeople(): List<Person>? {
    val people by remember { PersonalStore.observe { it.people().observe() } }.collectAsStateWithLifecycle(null)
    return remember(people) { people?.let(::sortedPeople) }
}

@Composable
fun NavRow(title: String, hint: String = "", onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    Block(stringResource(R.string.mine_block_now), ua.sprotyv.home.Group.FIRST_AID, padded = false) {
        BlockRow(stringResource(R.string.tile_safe), stringResource(R.string.tile_safe_hint), first = true) { nav.go(Route.Open(Section.SAFE)) }
    }
    // Notes on their own, on a light "paper" plate, with the pinned and newest ones visible
    NotesPlate(nav)
    ReadinessBlock(content, nav)
    Block(stringResource(R.string.mine_block_health), ua.sprotyv.home.Group.FIRST_AID, padded = false, foldable = true, summary = stringResource(R.string.mine_block_health_desc)) {
        BlockRow(stringResource(R.string.medical_card), stringResource(R.string.medical_card_hint), first = true) { nav.go(Route.Mine(MinePart.MEDICAL)) }
        BlockRow(stringResource(R.string.rescuer_screen), stringResource(R.string.rescuer_hint)) { nav.go(Route.Mine(MinePart.RESCUER)) }
    }
    Block(stringResource(R.string.mine_block_prepare), ua.sprotyv.home.Group.PREPARE, padded = false, foldable = true, summary = stringResource(R.string.mine_block_prepare_desc)) {
        BlockRow(stringResource(R.string.people), stringResource(R.string.people_hint), first = true) { nav.go(Route.Mine(MinePart.PEOPLE)) }
        BlockRow(stringResource(R.string.family_plan), stringResource(R.string.family_plan_hint)) { nav.go(Route.Mine(MinePart.PLAN)) }
        BlockRow(stringResource(R.string.kit), stringResource(R.string.kit_hint)) { nav.go(Route.Mine(MinePart.KIT)) }
        BlockRow(stringResource(R.string.supplies), stringResource(R.string.supplies_hint)) { nav.go(Route.SuppliesRoute) }
    }
    Block(stringResource(R.string.mine_block_copies), ua.sprotyv.home.Group.MINE, padded = false, foldable = true, summary = stringResource(R.string.mine_block_copies_desc)) {
        BlockRow(stringResource(R.string.backup_title), stringResource(R.string.backup_hint), first = true) { nav.go(Route.Mine(MinePart.BACKUP)) }
        BlockRow(stringResource(R.string.bookmarks_title), stringResource(R.string.bookmarks_hint)) { nav.go(Route.Mine(MinePart.BOOKMARKS)) }
        BlockRow(stringResource(R.string.print_title), stringResource(R.string.print_hint)) { nav.go(Route.Mine(MinePart.PRINT)) }
        BlockRow(stringResource(R.string.plan_code_title), stringResource(R.string.plan_code_hint)) { nav.go(Route.Mine(MinePart.PLAN_QR)) }
        BlockRow(stringResource(R.string.scan_title), stringResource(R.string.scan_hint)) { nav.go(Route.Scan) }
        BlockRow(stringResource(R.string.qr_text_title), stringResource(R.string.qr_text_hint)) { nav.go(Route.QrText) }
    }
    Text(stringResource(R.string.mine_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A text field that saves on every change. */
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
        // No autocorrect. Compose cannot yet ask the keyboard not to learn from what is typed
        // (Android's no-personalised-learning flag); noted as a known limit
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
    var card by remember(start) { mutableStateOf(start) }
    fun change(next: MedicalCard) {
        card = next
        val saved = next.copy(updatedAt = System.currentTimeMillis())
        PersonalStore.save("medical") { it.medical().save(saved) }
    }
    Text(stringResource(R.string.medical_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // The card in the green of health; the contacts below in the same green
    Block(stringResource(R.string.medical_card), ua.sprotyv.home.Group.FIRST_AID) {
    Field(stringResource(R.string.med_name), card.name, { change(card.copy(name = it)) })
    Field(stringResource(R.string.med_birth), card.birthDate, { change(card.copy(birthDate = it)) }, hint = stringResource(R.string.med_birth_hint))
    Field(stringResource(R.string.med_blood), card.bloodGroup, { change(card.copy(bloodGroup = it)) }, hint = stringResource(R.string.med_blood_hint))
    Field(stringResource(R.string.med_allergies), card.allergies, { change(card.copy(allergies = it)) }, multiline = true)
    Field(stringResource(R.string.med_medicines), card.medicines, { change(card.copy(medicines = it)) }, multiline = true, hint = stringResource(R.string.med_medicines_hint))
    Field(stringResource(R.string.med_conditions), card.conditions, { change(card.copy(conditions = it)) }, multiline = true)
    Field(stringResource(R.string.med_notes), card.notes, { change(card.copy(notes = it)) }, multiline = true)
    }

    val ice = rememberPeople().orEmpty().filter { it.ice }
    Block(stringResource(R.string.ice_title), ua.sprotyv.home.Group.FIRST_AID) {
        if (ice.isEmpty()) Text(stringResource(R.string.ice_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ice.forEach { Text("${it.name} · ${it.phone}", style = MaterialTheme.typography.bodyLarge) }
    }
    Text(stringResource(R.string.saved_automatically), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Trusted people: who gets "I am safe" and who is the emergency contact. */
@Composable
fun PeopleScreen() {
    val people = rememberPeople() ?: return Loading()
    var editing by remember { mutableStateOf<Person?>(null) }
    Text(stringResource(R.string.people_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { editing = Person(name = "", phone = "") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.add_person)) }
    // People in one blue block of the family's preparation, a line each
    if (people.isNotEmpty()) Block(stringResource(R.string.people), ua.sprotyv.home.Group.PREPARE, padded = false) {
        people.forEachIndexed { i, p ->
            BlockRow(p.name, listOfNotNull(p.phone, p.relation.ifBlank { null }, if (p.ice) stringResource(R.string.ice_short) else null).joinToString(" · "), first = i == 0) { editing = p }
        }
    }
    editing?.let { PersonDialog(it, onDone = { editing = null }) }
}

@Composable
private fun PersonDialog(start: Person, onDone: () -> Unit) {
    var p by remember(start) { mutableStateOf(start) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = p.name.isNotBlank() && ua.sprotyv.reach.Links.clean(p.phone).removePrefix("+").length >= 3
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(if (start.id == 0L) R.string.add_person else R.string.edit_person)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(stringResource(R.string.person_name), p.name, { p = p.copy(name = it) })
                Field(stringResource(R.string.person_phone), p.phone, { p = p.copy(phone = it) }, phone = true, hint = "+380…")
                if (!p.phone.trim().startsWith("+") && !ua.sprotyv.reach.Links.clean(p.phone).let { it.length == 10 && it.startsWith("0") } && p.phone.isNotBlank()) {
                    Text(stringResource(R.string.person_phone_format), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Field(stringResource(R.string.person_relation), p.relation, { p = p.copy(relation = it) })
                Toggle(stringResource(R.string.person_safe_list), p.safeList) { p = p.copy(safeList = it) }
                Toggle(stringResource(R.string.person_ice), p.ice) { p = p.copy(ice = it) }
                if (start.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.delete_person)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
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
                    SafeStatus.OK -> stored.ifBlank { defaultText }
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
    Field(stringResource(R.string.safe_message), text, {
        text = it
        // Only "I am fine" is kept as the owner's own words; the other two are written fresh each time
        if (status == SafeStatus.OK) {
            PersonalStore.save("setting:$KEY_SAFE_MESSAGE") { db -> db.settings().put(ua.sprotyv.personal.Setting(KEY_SAFE_MESSAGE, it)) }
        }
    }, multiline = true)
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
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        RichText(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun safeTint(st: SafeStatus): Tint = when (st) {
    SafeStatus.OK -> ua.sprotyv.home.Group.FIRST_AID.tint()
    SafeStatus.HELP -> ua.sprotyv.home.Group.DANGER.tint()
    SafeStatus.QUIET -> ua.sprotyv.home.Group.GUIDE.tint()
}
