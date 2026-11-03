package ua.sprotyv.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Phone
import ua.sprotyv.content.PhonesReader
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach

/** A category's title and what it is for, in the order shown. */
private fun categoryTitle(c: String): Int = when (c) {
    "emergency" -> R.string.phones_cat_emergency
    "evacuation" -> R.string.phones_cat_evacuation
    "mind" -> R.string.phones_cat_mind
    "violence" -> R.string.phones_cat_violence
    "children" -> R.string.phones_cat_children
    "health" -> R.string.phones_cat_health
    "security" -> R.string.phones_cat_security
    "veterans" -> R.string.phones_cat_veterans
    else -> R.string.phones_cat_state
}

/**
 * The app's one colour code, by what a line is for: orange only to act now (emergencies); green for
 * help to people; khaki, resistance's own mark, for defence and those who serve; violet for the state.
 */
@Composable
private fun categoryTint(c: String): Tint = when (c) {
    "emergency" -> Group.DANGER.tint()
    "evacuation", "mind", "children", "violence", "health" -> Group.FIRST_AID.tint()
    "security", "veterans" -> resist()
    else -> Group.GUIDE.tint()
}

/** Hidden in trip mode, like the same links elsewhere: defence, captivity and veterans' lines are not for a checked phone. */
private val TRIP_HIDES = setOf("security", "veterans")

/**
 * Phone numbers of services and helplines, every one from an official or the organisation's own
 * source, with the date it was checked. One tap opens the dialler with the number.
 */
@Composable
fun PhonesScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    Frame(stringResource(R.string.phones_title), nav::back) {
        Text(stringResource(R.string.phones_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (content == null) return@Frame Loading()
        if (content.phonesBroken) return@Frame Broken()
        val trip = HomePrefs.state.collectAsState().value?.trip == true
        var query by remember { mutableStateOf("") }
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(40) },
            label = { Text(stringResource(R.string.phones_search)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        fun matches(p: Phone) = words.all { w -> (p.title + " " + p.note + " " + p.number + " " + PhonesReader.shown(p.number)).lowercase().contains(w) }
        val shown = content.phones.filter { !(trip && it.category in TRIP_HIDES) && matches(it) }
        if (shown.isEmpty()) Text(stringResource(R.string.phones_none), style = MaterialTheme.typography.bodyLarge)
        Phone.CATEGORIES.forEach { c ->
            val list = shown.filter { it.category == c }
            if (list.isEmpty()) return@forEach
            // Emergencies always open; the rest folded, unless a search is on
            Block(
                stringResource(categoryTitle(c)), Group.GUIDE, tint = categoryTint(c), padded = false,
                foldable = c != "emergency" && words.isEmpty(), summary = stringResource(R.string.shelf_count, list.size),
            ) {
                list.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PhoneRow(p, categoryTint(c)) { Reach.dial(context, p.number) }
                }
            }
        }
        Text(stringResource(R.string.phones_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One line: the number large, what it is for, where it was checked; the whole line dials. */
@Composable
private fun PhoneRow(p: Phone, tint: Tint, onClick: () -> Unit) {
    val shown = PhonesReader.shown(p.number)
    val say = stringResource(R.string.phones_call, shown, p.title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = stringResource(R.string.person_call)) { onClick() }
            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = say }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The number first and large; long ones (from abroad) still fit on a narrow screen
        Column(Modifier.weight(1f).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(shown, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint.ink)
            Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(p.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.phones_checked, Bookmarks.host(p.source), p.checkedOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Call, contentDescription = null, tint = tint.ink)
    }
}

/** The way into the phones from "Knowledge": first in the list, so it is never lost. */
@Composable
fun PhonesEntry(nav: Nav) {
    val tint = Group.FIRST_AID.tint()
    androidx.compose.material3.Card(
        onClick = { nav.go(Route.Phones) },
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = tint.container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Call, Tint(MaterialTheme.colorScheme.surface, tint.ink), 40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.phones_title), style = BlockTitle, color = tint.ink)
                Text(stringResource(R.string.phones_hint), style = MaterialTheme.typography.bodyMedium, color = tint.ink)
            }
        }
    }
}
