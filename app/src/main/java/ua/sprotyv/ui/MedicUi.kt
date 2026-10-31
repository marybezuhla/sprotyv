package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.qr.Qr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Who a step is given to: the person holding the phone, or helper 1-3. */
private val HELPERS = listOf(0, 1, 2, 3)

/**
 * "Hand out the steps": the order's own steps of one procedure, each given to a person nearby,
 * then shown to each in large letters ("you call 103", "you press on the wound"). One person who
 * knows the procedure can lead several who do not. Kept in memory only, for the moment.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun DelegateScreen(content: Content?, n: Int, nav: Nav) {
    val p = content?.moz441?.procedure(n)
    Frame(stringResource(R.string.delegate_title), nav::back) {
        if (content == null) return@Frame Loading()
        val steps = p?.actions?.items ?: return@Frame Broken()
        val given = remember(n) { mutableStateMapOf<String, Int>() }
        var showing by remember(n) { mutableStateOf<Int?>(null) }
        val who = showing
        if (who != null) {
            // One person's steps, large, to show them the screen
            Text(helperName(who), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            steps.filter { (given[it.n] ?: 0) == who }.forEach { s ->
                Card(colors = CardDefaults.cardColors(containerColor = Group.FIRST_AID.tint().container), modifier = Modifier.fillMaxWidth()) {
                    Text(s.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp), modifier = Modifier.padding(16.dp))
                }
            }
            OutlinedButton(onClick = { showing = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.delegate_back)) }
            return@Frame
        }
        Text(stringResource(R.string.delegate_intro, p.short), style = MaterialTheme.typography.bodyLarge)
        steps.forEach { s ->
            Block(stringResource(R.string.delegate_step, s.n), Group.FIRST_AID) {
                // The order's words; only the first letter made capital for reading
                Text(s.text.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.delegate_who), style = MaterialTheme.typography.labelLarge)
                    HELPERS.forEach { h ->
                        FilterChip(selected = (given[s.n] ?: 0) == h, onClick = { given[s.n] = h }, label = { Text(if (h == 0) stringResource(R.string.delegate_me) else "$h") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
            }
        }
        Text(stringResource(R.string.delegate_show), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HELPERS.filter { h -> steps.any { (given[it.n] ?: 0) == h } }.forEach { h ->
                Button(onClick = { showing = h }, modifier = Modifier.heightIn(min = 56.dp)) { Text(helperName(h)) }
            }
        }
        Text(stringResource(R.string.moz_layout_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun helperName(h: Int) = if (h == 0) stringResource(R.string.delegate_me) else stringResource(R.string.delegate_helper, h)

/**
 * "For the ambulance crew": what happened and what was done, with times, to hand over in words or
 * as a QR code. The procedures ask to gather what is known about the circumstances and pass it to
 * the crew (e.g. order 441, procedure 19, point 4.12). Nothing is stored: it is about another
 * person, so it lives on this screen only. The tourniquet time comes from the card's own timer.
 */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun HandoverScreen(nav: Nav) {
    val context = LocalContext.current
    var when_ by remember { mutableStateOf<Long?>(null) }
    var what by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val done = remember { mutableStateListOf<Pair<Int, Long>>() }
    val tourniquet = remember { tourniquetAt(context) }
    SecureScreen()
    Frame(stringResource(R.string.handover_title), nav::back) {
        Text(stringResource(R.string.handover_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Block(stringResource(R.string.handover_when), Group.FIRST_AID) {
            Text(when_?.let(::clockOf) ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { when_ = System.currentTimeMillis() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.handover_now)) }
        }
        OutlinedTextField(value = what, onValueChange = { what = it.take(500) }, label = { Text(stringResource(R.string.handover_what)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Block(stringResource(R.string.handover_done), Group.FIRST_AID) {
            if (tourniquet != null) Text(stringResource(R.string.handover_tourniquet, clockOf(tourniquet)), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.handover_tap), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HANDOVER_ACTIONS.forEach { a ->
                    FilterChip(selected = done.any { it.first == a }, onClick = {
                        val i = done.indexOfFirst { it.first == a }
                        if (i >= 0) done.removeAt(i) else done.add(a to System.currentTimeMillis())
                    }, label = { Text(stringResource(a)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            done.forEach { (a, t) -> Text("${clockOf(t)} — ${stringResource(a)}", style = MaterialTheme.typography.bodyLarge) }
        }
        OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text(stringResource(R.string.handover_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
        val summary = buildString {
            append(context.getString(R.string.handover_title)); append('\n')
            when_?.let { append(context.getString(R.string.handover_when)).append(": ").append(clockOf(it)).append('\n') }
            if (what.isNotBlank()) append(context.getString(R.string.handover_what)).append(": ").append(what.trim()).append('\n')
            tourniquet?.let { append(context.getString(R.string.handover_tourniquet, clockOf(it))).append('\n') }
            done.forEach { (a, t) -> append(clockOf(t)).append(" — ").append(context.getString(a)).append('\n') }
            if (notes.isNotBlank()) append(notes.trim())
        }.trim()
        val fits = summary.toByteArray(Charsets.UTF_8).size <= Qr.MAX_PLAN_BYTES
        OutlinedButton(enabled = fits, onClick = { QrInbox.text = summary; nav.go(Route.QrText) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.handover_qr))
        }
        // Kept only if the owner asks: then encrypted in "My notes"
        val noteTitle = stringResource(R.string.handover_note_title, clockOf(System.currentTimeMillis()))
        OutlinedButton(enabled = summary.isNotBlank(), onClick = {
            NoteInbox.title = noteTitle
            NoteInbox.text = summary
            nav.go(Route.Mine(MinePart.NOTES))
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.handover_to_notes)) }
        Text(stringResource(R.string.handover_kept_nowhere), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val HANDOVER_ACTIONS = listOf(
    R.string.handover_a_pressure, R.string.handover_a_dressing, R.string.handover_a_cpr, R.string.handover_a_recovery,
    R.string.handover_a_cooling, R.string.handover_a_warming, R.string.handover_a_splint, R.string.handover_a_called,
)

private fun clockOf(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** The tourniquet time kept by the bleeding card (a day at most), or null. */
private fun tourniquetAt(context: Context): Long? {
    val t = context.applicationContext.getSharedPreferences("tourniquet", Context.MODE_PRIVATE).getLong("at", 0L)
    return t.takeIf { it > 0 && System.currentTimeMillis() - it < 24 * 3600_000L }
}
