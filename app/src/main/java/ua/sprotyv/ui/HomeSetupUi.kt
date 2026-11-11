package ua.sprotyv.ui

import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Preset
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

/** The person's tiles in their order, moved with buttons. In simple mode a line shows where the screen ends. */
@Composable
fun EditTilesScreen(content: Content?, mode: Mode?, pins: List<String>, nav: Nav) {
    val context = LocalContext.current
    Frame(stringResource(R.string.edit_tiles), nav::back) {
        Text(stringResource(R.string.edit_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.edit_fixed), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.edit_fixed_note), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.edit_yours), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        val shown = pins.mapNotNull { tileInfo(it, content) }
        val ids = shown.map { it.id }
        // Tiles that cannot be shown now (content unreadable, say) are kept, at the end, not lost
        val hidden = pins.filter { it !in ids }
        fun change(next: List<String>) = HomePrefs.update(context) { it.copy(pins = next + hidden) }
        if (shown.isEmpty()) Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge)
        shown.forEachIndexed { i, info -> key(info.id) {
            if (mode == Mode.SIMPLE && i == Tiles.SIMPLE_CHOSEN) {
                HorizontalDivider(thickness = 2.dp)
                Text(stringResource(R.string.edit_simple_rest), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(info.icon, info.badge(), 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(info.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { change(Pins.move(ids, info.id, -1)) }, enabled = i > 0, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.move_up, info.title))
                }
                IconButton(onClick = { change(Pins.move(ids, info.id, 1)) }, enabled = i < shown.lastIndex, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.move_down, info.title))
                }
                IconButton(onClick = { change(Pins.unpin(ids, info.id)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_tile, info.title))
                }
            }
        } }
        OutlinedButton(onClick = { nav.go(Route.Sections) }, modifier = Modifier.heightIn(min = 56.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add_tile))
        }
        OutlinedButton(onClick = { nav.go(Route.Presets) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.presets_title)) }
    }
}

internal val ThemeChoice.title: Int get() = when (this) {
    ThemeChoice.SYSTEM -> R.string.theme_system
    ThemeChoice.LIGHT -> R.string.theme_light
    ThemeChoice.DARK -> R.string.theme_dark
    ThemeChoice.CONTRAST -> R.string.theme_contrast
}

val Preset.title: Int get() = when (this) {
    Preset.GENERAL -> R.string.preset_general
    Preset.ELDER -> R.string.preset_elder
    Preset.PARENTS -> R.string.preset_parents
    Preset.FRONTLINE -> R.string.preset_frontline
    Preset.TEEN -> R.string.preset_teen
    Preset.WINTER -> R.string.preset_winter
}

internal val Preset.hint: Int get() = when (this) {
    Preset.GENERAL -> R.string.preset_general_hint
    Preset.ELDER -> R.string.preset_elder_hint
    Preset.PARENTS -> R.string.preset_parents_hint
    Preset.FRONTLINE -> R.string.preset_frontline_hint
    Preset.TEEN -> R.string.preset_teen_hint
    Preset.WINTER -> R.string.preset_winter_hint
}

@Composable
fun PresetList(onPick: (Preset) -> Unit) {
    Preset.entries.forEach { p -> NavRow(stringResource(p.title), stringResource(p.hint), ua.sprotyv.home.Group.PREPARE) { onPick(p) } }
}

/** Settings: a ready set replaces the tiles only after one more yes. */
@Composable
fun PresetsScreen(nav: Nav) {
    val context = LocalContext.current
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    Frame(stringResource(R.string.presets_title), nav::back) {
        Text(stringResource(R.string.presets_hint), style = MaterialTheme.typography.bodyLarge)
        PresetList { asking = it.name }
    }
    val preset = Preset.entries.firstOrNull { it.name == asking } ?: return
    AlertDialog(
        onDismissRequest = { asking = null },
        text = { Text(stringResource(R.string.preset_confirm, stringResource(preset.title))) },
        confirmButton = {
            TextButton(onClick = {
                HomePrefs.update(context) { it.copy(pins = preset.pins) }
                asking = null
                nav.back()
            }) { Text(stringResource(R.string.replace)) }
        },
        dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The two looks, each with a small picture of itself: recognising beats recalling, and a picture
 * says "big buttons" or "tabs" faster than the words do.
 */
@Composable
fun ModeChoice(selected: Mode?, onPick: (Mode) -> Unit) {
    listOf(
        Triple(Mode.SIMPLE, R.string.mode_simple, R.string.mode_simple_hint),
        Triple(Mode.FULL, R.string.mode_full, R.string.mode_full_hint),
        Triple(Mode.KID, R.string.mode_kid, R.string.mode_kid_hint),
    ).forEach { (mode, title, hint) ->
        val on = mode == selected
        Card(
            onClick = { onPick(mode) },
            colors = CardDefaults.cardColors(containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
            border = if (on) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { this.selected = on },
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ModePicture(mode)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A tiny drawing of the screen: three wide buttons, or a grid of tiles over a row of tabs. */
@Composable
internal fun ModePicture(mode: Mode) {
    val ink = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.width(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (mode == Mode.SIMPLE) {
            repeat(3) { i -> Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) accent else ink)) }
            Box(Modifier.fillMaxWidth().height(8.dp))
        } else if (mode == Mode.KID) {
            // Three rows of bright squares: pictures, not words
            val colours = Group.entries.take(6).map { it.tint().ink }
            repeat(3) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { j -> Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(colours[r * 2 + j])) }
                }
            }
        } else {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { j -> Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(if (it == 0 && j == 0) accent else ink)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(4) { Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ink)) }
            }
        }
    }
}

/**
 * First launch: two choices, one tap each. "What to do now" and 112 are there from the first
 * second, because someone may open the app for the first time in danger.
 */
@Composable
fun WelcomeScreen(nav: Nav) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val mode = Mode.entries.firstOrNull { it.name == chosen }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(appTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            tileInfo(Tiles.tool(Tool.NOW), null)?.let { info -> TileCard(info, big = true) { nav.go(info.route) } }
            if (step == 0 || mode == null) {
                Text(stringResource(R.string.welcome_mode_q), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                ModeChoice(mode) { chosen = it.name; step = 1 }
                // A new phone: everything, the look included, comes back from the encrypted copy
                OutlinedButton(onClick = { nav.go(Route.Mine(MinePart.BACKUP)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.welcome_restore))
                }
            } else if (mode == Mode.KID) {
                // No tile sets for a child; the grown-up is asked once to lock the settings
                Text(stringResource(R.string.kid_setup_title), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.kid_setup_text), style = MaterialTheme.typography.bodyLarge)
                // The lock on "My data" would stop the child at a fingerprint: said here, with the way round it
                Text(stringResource(R.string.kid_call_lock_warn), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                var open by remember { mutableStateOf(KidCall.isOn(context)) }
                val kidScope = androidx.compose.runtime.rememberCoroutineScope()
                Toggle(stringResource(R.string.kid_call_open_toggle), open) { on -> KidCall.set(context, on); open = on; if (on) kidScope.launch { KidCall.syncNow(context) } }
                val canLock = Guard.available(context)
                if (canLock) {
                    Button(onClick = { HomePrefs.update(context) { it.copy(mode = Mode.KID, locked = true) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.kid_setup_lock))
                    }
                } else {
                    Text(stringResource(R.string.lock_no_screen_lock), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(onClick = { HomePrefs.update(context) { it.copy(mode = Mode.KID) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.kid_setup_later))
                }
                TextButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
            } else {
                Text(stringResource(R.string.welcome_preset_q), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                PresetList { p -> HomePrefs.update(context) { it.copy(mode = mode, pins = p.pins) } }
                TextButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.welcome_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CallBar()
    }
}
