package ua.sprotyv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Kit
import ua.sprotyv.content.Supplies
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.KitCheck
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.personal.Setting
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The family plan (guide 1.5): three meeting points, a go-between, who does what, and "if — then"
 * rules, which people follow far more reliably than general intentions.
 */
@Composable
fun PlanScreen() {
    val start = when (val l = rememberRead { it.plan().get() ?: FamilyPlan() }) {
        Loaded.Waiting -> return Loading()
        Loaded.Failed -> return ReadFailed()
        is Loaded.Value -> l.value
    }
    var plan by remember(start) { mutableStateOf(start) }
    fun change(next: FamilyPlan) {
        plan = next
        val saved = next.copy(updatedAt = System.currentTimeMillis())
        PersonalStore.save("plan") { it.plan().save(saved) }
    }
    Text(stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Heading(R.string.plan_points)
    Field(stringResource(R.string.plan_point1), plan.point1, { change(plan.copy(point1 = it)) }, hint = stringResource(R.string.plan_point1_hint))
    Field(stringResource(R.string.plan_point2), plan.point2, { change(plan.copy(point2 = it)) }, hint = stringResource(R.string.plan_point2_hint))
    Field(stringResource(R.string.plan_point3), plan.point3, { change(plan.copy(point3 = it)) }, hint = stringResource(R.string.plan_point3_hint))
    Heading(R.string.plan_go_between)
    Text(stringResource(R.string.plan_go_between_hint), style = MaterialTheme.typography.bodyMedium)
    Field(stringResource(R.string.person_name), plan.goBetweenName, { change(plan.copy(goBetweenName = it)) })
    Field(stringResource(R.string.person_phone), plan.goBetweenPhone, { change(plan.copy(goBetweenPhone = it)) }, phone = true)

    Heading(R.string.plan_if_then)
    Text(stringResource(R.string.plan_if_then_hint), style = MaterialTheme.typography.bodyMedium)
    Rows(PlanRow.KIND_IF_THEN, R.string.plan_if, R.string.plan_then, R.string.plan_if_example, R.string.plan_then_example)

    Heading(R.string.plan_roles)
    Rows(PlanRow.KIND_ROLE, R.string.plan_who, R.string.plan_does, R.string.plan_who_example, R.string.plan_does_example)

    Heading(R.string.plan_notes)
    Field(stringResource(R.string.plan_notes), plan.notes, { change(plan.copy(notes = it)) }, multiline = true)
    Text(stringResource(R.string.plan_practice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(stringResource(R.string.saved_automatically), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Rows(kind: String, firstLabel: Int, secondLabel: Int, firstHint: Int, secondHint: Int) {
    val loaded by remember(kind) { PersonalStore.observe { it.plan().observeRows(kind) } }.collectAsStateWithLifecycle(null)
    val rows = loaded ?: return Loading()
    var editing by remember { mutableStateOf<PlanRow?>(null) }
    rows.forEach { r ->
        NavRow(stringResource(firstLabel) + " " + r.first, stringResource(secondLabel) + " " + r.second) { editing = r }
    }
    OutlinedButton(
        onClick = { editing = PlanRow(kind = kind, first = "", second = "", sort = (rows.maxOfOrNull { it.sort } ?: 0) + 1) },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.add_row)) }
    editing?.let { start ->
        var r by remember(start) { mutableStateOf(start) }
        AlertDialog(
            onDismissRequest = { editing = null },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(firstLabel), r.first, { r = r.copy(first = it) }, multiline = true, hint = stringResource(firstHint))
                    Field(stringResource(secondLabel), r.second, { r = r.copy(second = it) }, multiline = true, hint = stringResource(secondHint))
                    if (start.id != 0L) {
                        TextButton(onClick = {
                            PersonalStore.save("delete:" + java.util.UUID.randomUUID()) { it.plan().deleteRow(start) }
                            editing = null
                        }) { Text(stringResource(R.string.delete_row), color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = r.first.isNotBlank() && r.second.isNotBlank(), onClick = {
                    val saved = r.copy(first = r.first.trim(), second = r.second.trim())
                    PersonalStore.save("row:" + java.util.UUID.randomUUID()) { it.plan().saveRow(saved) }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The emergency kit list from the guide (2.2), ticked off by the owner, with a check every six months. */
@Composable
fun KitScreen(content: Content?, nav: Nav) {
    if (content == null) return Loading()
    if (content.guideBroken) return Broken()
    val checks by remember { PersonalStore.observe { it.kit().observe() } }.collectAsStateWithLifecycle(null)
    val checked = (checks ?: return Loading()).filter { it.checked }.map { it.key }.toSet()
    val reviewed by remember { PersonalStore.observe { it.settings().observe(KEY_KIT_REVIEWED) } }.collectAsStateWithLifecycle(null)
    val groups = Kit.groups(content.guide)
    val total = groups.sumOf { it.items.size }
    val done = groups.sumOf { g -> g.items.count { it.key in checked } }
    var confirmReset by remember { mutableStateOf(false) }

    Text(stringResource(R.string.kit_progress, done, total), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.kit_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    groups.forEach { g ->
        Text(g.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        g.items.forEach { item ->
            CheckRow(item.text, item.key in checked) { on ->
                PersonalStore.save("kit:" + item.key) { it.kit().set(KitCheck(item.key, on, System.currentTimeMillis())) }
            }
        }
    }
    HorizontalDivider()
    val last = reviewed?.toLongOrNull()
    if (last != null) {
        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val zone = ZoneId.systemDefault()
        Text(stringResource(R.string.kit_reviewed_on, Instant.ofEpochMilli(last).atZone(zone).format(fmt)), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.kit_next_check, Instant.ofEpochMilli(last).atZone(zone).plusMonths(6).format(fmt)), style = MaterialTheme.typography.bodyLarge)
    }
    Button(onClick = {
        PersonalStore.save("setting:$KEY_KIT_REVIEWED") { it.settings().put(Setting(KEY_KIT_REVIEWED, System.currentTimeMillis().toString())) }
    }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.kit_mark_reviewed)) }
    Text(stringResource(R.string.kit_rotation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = { nav.go(Route.GuideSection(Kit.SECTION)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.in_detail)) }
    TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.kit_reset)) }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(stringResource(R.string.kit_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    // Ticks still waiting would land after the clear; they go too
                    PersonalStore.dropPending("kit:")
                    PersonalStore.save("delete:" + java.util.UUID.randomUUID()) { it.kit().clear() }
                    confirmReset = false
                }) { Text(stringResource(R.string.kit_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

const val KEY_KIT_REVIEWED = "kit_reviewed_at"

/**
 * Home supplies for the household and period, from the guide's norms (2.5). Starts small on
 * purpose: three days is a goal anyone can reach, then two weeks, then a month.
 * Nothing here is personal, so it works without opening the owner's data.
 */
@Composable
fun SuppliesScreen(content: Content?, nav: Nav) {
    var people by rememberSaveable { mutableIntStateOf(1) }
    var days by rememberSaveable { mutableIntStateOf(Supplies.LEVELS.first()) }
    Frame(stringResource(R.string.supplies), nav::back) {
        if (content == null) return@Frame Loading()
        if (content.guideBroken) return@Frame Broken()
        Text(stringResource(R.string.supplies_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { if (people > 1) people-- }, modifier = Modifier.heightIn(min = 56.dp)) { Text("−") }
            Text(stringResource(R.string.supplies_people, people), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { if (people < 20) people++ }, modifier = Modifier.heightIn(min = 56.dp)) { Text("+") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Supplies.LEVELS.forEach { d ->
                FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(R.string.supplies_days, d)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text(stringResource(R.string.supplies_water, Supplies.waterLitres(people, days)), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.supplies_water_note), style = MaterialTheme.typography.bodyMedium)
        Heading(R.string.supplies_food)
        Supplies.food(content.guide, people, days).forEach { line ->
            Column(Modifier.fillMaxWidth()) {
                Text("${line.what}: ${line.amount}", style = MaterialTheme.typography.bodyLarge)
                if (line.note.isNotBlank()) Text(line.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.supplies_rotation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { nav.go(Route.GuideSection(Supplies.SECTION)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.in_detail)) }
    }
}
