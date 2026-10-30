package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Preset
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

// The visual language of tiles. Each group has its own colour, always with its icon and its words
// (colour is never the only sign). The same colour marks the group everywhere, so a tile is found
// by colour before it is read (Treisman: colour is seen at a glance). Red is not among them.

/** A tile ready to draw: what it says, how it looks and where it leads. */
data class TileInfo(val id: String, val title: String, val hint: String, val icon: ImageVector, val group: Group, val route: Route)

data class Tint(val container: Color, val ink: Color)

@Composable
fun Group.tint(): Tint {
    val dark = LocalDark.current
    // High contrast: no pale fills, the plain text colours of the theme; icon and words still say the group
    if (LocalContrast.current) return Tint(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
    return when (this) {
        Group.DANGER -> if (dark) Tint(Color(0xFF4A2A12), Color(0xFFFFC9A0)) else Tint(Color(0xFFFFE9D6), Color(0xFF8A3B00))
        Group.FIRST_AID -> if (dark) Tint(Color(0xFF103F3A), Color(0xFF9EE4D9)) else Tint(Color(0xFFD5F0EC), Color(0xFF00574E))
        Group.FAMILY -> if (dark) Tint(Color(0xFF352552), Color(0xFFD9C6F5)) else Tint(Color(0xFFECE0F7), Color(0xFF4E2A84))
        Group.HOME_LIFE -> if (dark) Tint(Color(0xFF45380A), Color(0xFFFFE08A)) else Tint(Color(0xFFFFF1C2), Color(0xFF6B4E00))
        Group.PREPARE -> if (dark) Tint(Color(0xFF1F3D1A), Color(0xFFB7E3A8)) else Tint(Color(0xFFDDF0D8), Color(0xFF2E5E1F))
        Group.LEAVE -> if (dark) Tint(Color(0xFF0F3550), Color(0xFFA9D6F7)) else Tint(Color(0xFFD8ECFA), Color(0xFF0B4F7A))
        Group.SAFETY -> if (dark) Tint(Color(0xFF2C323B), Color(0xFFC9D1DC)) else Tint(Color(0xFFE4E7EC), Color(0xFF37404D))
        Group.MINE -> if (dark) Tint(Color(0xFF00428C), Color(0xFFD6E6F8)) else Tint(Color(0xFFD6E6F8), Color(0xFF00214A))
        Group.GUIDE -> if (dark) Tint(Color(0xFF2E2D2A), Color(0xFFD6D3CB)) else Tint(Color(0xFFEEECE6), Color(0xFF4A4945))
    }
}

private val Group.title: Int get() = when (this) {
    Group.DANGER -> R.string.group_danger
    Group.FIRST_AID -> R.string.group_first_aid
    Group.FAMILY -> R.string.group_family
    Group.HOME_LIFE -> R.string.group_home_life
    Group.PREPARE -> R.string.group_prepare
    Group.LEAVE -> R.string.group_leave
    Group.SAFETY -> R.string.group_safety
    Group.MINE -> R.string.group_mine
    Group.GUIDE -> R.string.group_guide
}

private val Group.icon: ImageVector get() = when (this) {
    Group.DANGER -> Icons.Filled.Warning
    Group.FIRST_AID -> Icons.Filled.Favorite
    Group.FAMILY -> Icons.Filled.Face
    Group.HOME_LIFE -> Icons.Filled.Home
    Group.PREPARE -> Icons.AutoMirrored.Filled.List
    Group.LEAVE -> Icons.Filled.Place
    Group.SAFETY -> Icons.Filled.Lock
    Group.MINE -> Icons.Filled.AccountBox
    Group.GUIDE -> Icons.Filled.Info
}

private val Tool.title: Int get() = when (this) {
    Tool.NOW -> R.string.tile_now
    Tool.SAFE -> R.string.tile_safe
    Tool.ALARM -> R.string.tile_alarm
    Tool.SITUATION -> R.string.situation_title
    Tool.POWER -> R.string.power_title
    Tool.DIRECTORY -> R.string.dir_title
    Tool.HOME_LIFE -> R.string.tile_home_life
    Tool.GUIDE -> R.string.tile_guide
    Tool.SEARCH -> R.string.search_guide
    Tool.MINE -> R.string.tile_mine
    Tool.MEDICAL -> R.string.medical_card
    Tool.PEOPLE -> R.string.people
    Tool.PLAN -> R.string.family_plan
    Tool.KIT -> R.string.kit
    Tool.SUPPLIES -> R.string.supplies
    Tool.RESCUER -> R.string.rescuer_screen
    Tool.PRINT -> R.string.print_title
    Tool.SCAN -> R.string.scan_title
    Tool.PRACTICE -> R.string.practice_title
}

private val Tool.hint: Int get() = when (this) {
    Tool.NOW -> R.string.tile_now_hint
    Tool.SAFE -> R.string.tile_safe_hint
    Tool.ALARM -> R.string.tile_alarm_hint
    Tool.SITUATION -> R.string.situation_hint
    Tool.POWER -> R.string.power_hint
    Tool.DIRECTORY -> R.string.dir_hint
    Tool.HOME_LIFE -> R.string.tile_home_life_hint
    Tool.GUIDE -> R.string.tile_guide_hint
    Tool.SEARCH -> R.string.search_tile_hint
    Tool.MINE -> R.string.tile_mine_hint
    Tool.MEDICAL -> R.string.medical_card_hint
    Tool.PEOPLE -> R.string.people_hint
    Tool.PLAN -> R.string.family_plan_hint
    Tool.KIT -> R.string.kit_hint
    Tool.SUPPLIES -> R.string.supplies_hint
    Tool.RESCUER -> R.string.rescuer_hint
    Tool.PRINT -> R.string.print_hint
    Tool.SCAN -> R.string.scan_hint
    Tool.PRACTICE -> R.string.practice_hint
}

private val Tool.icon: ImageVector? get() = when (this) {
    Tool.NOW -> Icons.Filled.Warning
    Tool.SAFE -> Icons.AutoMirrored.Filled.Send
    Tool.ALARM -> Icons.Filled.Notifications
    Tool.SITUATION -> Icons.Filled.LocationOn
    Tool.POWER -> Icons.Filled.DateRange
    Tool.DIRECTORY -> Icons.Filled.Phone
    Tool.SEARCH -> Icons.Filled.Search
    Tool.MEDICAL -> Icons.Filled.Favorite
    Tool.PEOPLE -> Icons.Filled.Person
    Tool.SUPPLIES -> Icons.Filled.ShoppingCart
    Tool.PRACTICE -> Icons.Filled.PlayArrow
    Tool.PRINT, Tool.RESCUER, Tool.SCAN -> Icons.Filled.Share
    else -> null
}

val Tool.route: Route get() = when (this) {
    Tool.NOW -> Route.Open(Section.NOW)
    Tool.SAFE -> Route.Open(Section.SAFE)
    Tool.ALARM -> Route.Open(Section.ALARM)
    Tool.SITUATION -> Route.Situation
    Tool.POWER -> Route.Power
    Tool.DIRECTORY -> Route.Directory
    Tool.HOME_LIFE -> Route.Open(Section.HOME_LIFE)
    Tool.GUIDE -> Route.Open(Section.GUIDE)
    Tool.SEARCH -> Route.Search
    Tool.MINE -> Route.Open(Section.MINE)
    Tool.MEDICAL -> Route.Mine(MinePart.MEDICAL)
    Tool.PEOPLE -> Route.Mine(MinePart.PEOPLE)
    Tool.PLAN -> Route.Mine(MinePart.PLAN)
    Tool.KIT -> Route.Mine(MinePart.KIT)
    Tool.SUPPLIES -> Route.SuppliesRoute
    Tool.RESCUER -> Route.Mine(MinePart.RESCUER)
    Tool.PRINT -> Route.Mine(MinePart.PRINT)
    Tool.SCAN -> Route.Scan
    Tool.PRACTICE -> Route.PracticeRoute("")
}

/** The group a tile belongs to: the first that names it. "What to do now" and "I am safe" have their own. */
fun groupOf(id: String): Group = when (id) {
    Tiles.tool(Tool.NOW) -> Group.DANGER
    Tiles.tool(Tool.SAFE) -> Group.FAMILY
    else -> if (id.startsWith("link:")) Group.SAFETY else Group.entries.firstOrNull { id in it.tiles } ?: Group.GUIDE
}

/** Null for an id that is not (or no longer) in the app; such a tile is simply not shown. */
@Composable
fun tileInfo(id: String, content: Content?): TileInfo? {
    val kind = id.substringBefore(':')
    val value = id.substringAfter(':', "")
    val group = groupOf(id)
    return when (kind) {
        "card" -> content?.card(value)?.let { TileInfo(id, it.title, "", group.icon, group, Route.CardRoute(value)) }
        "chapter" -> content?.guide?.chapters?.firstOrNull { it.id == value }?.let { ch ->
            // "Water: finding, cleaning..." becomes "Water" with the rest below it
            val title = ch.title.substringBefore(':').trim()
            val hint = if (':' in ch.title) ch.title.substringAfter(':').trim().replaceFirstChar { it.uppercase() } else ""
            TileInfo(id, title, hint, group.icon, group, Route.ChapterRoute(value))
        }
        "link" -> content?.links?.firstOrNull { it.id == value }?.let { l ->
            TileInfo(id, l.title, l.note, Icons.Filled.LocationOn, group, Route.External(l.id))
        }
        "tool" -> Tool.entries.firstOrNull { it.name == value }?.let { tool ->
            TileInfo(id, stringResource(tool.title), stringResource(tool.hint), tool.icon ?: group.icon, group, tool.route)
        }
        else -> null
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Tint, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(tint.container),
        contentAlignment = Alignment.Center,
    ) {
        // The words next to it say what it is; the icon only helps find it
        Icon(icon, contentDescription = null, tint = tint.ink, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * One tile. [big]: a full-width row for simple mode (large target, one per line, read top to
 * bottom); otherwise a card for the grid. "What to do now" is the one marked out.
 */
@Composable
fun TileCard(info: TileInfo, big: Boolean, onClick: () -> Unit) {
    val marked = info.id == Tiles.tool(Tool.NOW)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (marked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (marked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (big) 88.dp else 120.dp),
    ) {
        if (big) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(info.icon, info.group.tint(), 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    // Simple look: large print (24 and 18 sp), still growing with the phone's text size
                    Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (info.hint.isNotBlank()) {
                        Text(info.hint, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                IconBadge(info.icon, info.group.tint(), 40.dp)
                Spacer(Modifier.size(10.dp))
                Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (info.hint.isNotBlank()) {
                    Text(info.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Simple mode: six large buttons in one column, read top to bottom, and 112 below. */
@Composable
fun SimpleHome(content: Content?, pins: List<String>, nav: Nav, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
            }
            CornerCall()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Only tiles that can be shown count towards the six, so the screen matches the editor
            val fixed = Tiles.FIXED.mapNotNull { tileInfo(it, content) }
            val chosen = pins.mapNotNull { tileInfo(it, content) }.take(Tiles.SIMPLE_CHOSEN)
            (fixed + chosen).forEach { info -> TileCard(info, big = true) { nav.go(info.route) } }
        }
        CallBar()
    }
}

/** Full mode, "Home": the two fixed tiles, then the person's own, in their order. */
@Composable
fun FullHome(content: Content?, pins: List<String>, nav: Nav, onSettings: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit) {
    val fixed = Tiles.FIXED.mapNotNull { tileInfo(it, content) }
    val mine = pins.mapNotNull { tileInfo(it, content) }
    LazyVerticalGrid(
        // Two columns on a phone; one when the text is made large, so nothing is cut off
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                CornerCall()
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        items(fixed, key = { it.id }) { info -> TileCard(info, big = false) { nav.go(info.route) } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.my_home),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit))
                }
            }
        }
        if (mine.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.add_from_sections)) }
                }
            }
        }
        items(mine, key = { it.id }) { info -> TileCard(info, big = false) { nav.go(info.route) } }
    }
}

/**
 * Everything that can be a tile, in small groups. A tap opens it; the button beside it puts it on
 * the home screen or takes it off. Buttons, not dragging, so it works with TalkBack and a shaky hand.
 */
@Composable
fun SectionsList(content: Content?, pins: List<String>, nav: Nav, onPin: (String, Boolean) -> Unit) {
    if (content == null) return Loading()
    if (content.guideBroken || content.cardsBroken) Broken()
    val full = pins.size >= Tiles.MAX_PINS
    if (full) Text(stringResource(R.string.pins_full), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lists = Group.entries.map { it.title to it.tiles } +
        (R.string.situation_title to content.links.filter { it.group != ua.sprotyv.content.Link.POWER && it.group !in ua.sprotyv.content.Link.DIRECTORY }.map { Tiles.link(it.id) }) +
        (R.string.power_title to content.links.filter { it.group == ua.sprotyv.content.Link.POWER }.map { Tiles.link(it.id) }) +
        (R.string.dir_title to content.links.filter { it.group in ua.sprotyv.content.Link.DIRECTORY }.map { Tiles.link(it.id) }) +
        (R.string.group_rest to Group.rest(content.guide.chapters.map { it.id }))
    lists.forEach { (title, ids) ->
        val infos = ids.mapNotNull { tileInfo(it, content) }
        if (infos.isEmpty()) return@forEach
        Text(
            stringResource(title) + " · " + infos.size,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        // One frame per group, rows split by hairlines: the group is seen before its parts
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            infos.forEachIndexed { i, info ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val pinned = info.id in pins
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { nav.go(info.route) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(info.icon, info.group.tint(), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (info.hint.isNotBlank()) Text(info.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // A small round switch, not a wide button: the title keeps the whole line
                    if (pinned) {
                        FilledTonalIconButton(onClick = { onPin(info.id, false) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pin_remove_desc, info.title))
                        }
                    } else {
                        OutlinedIconButton(onClick = { onPin(info.id, true) }, enabled = !full, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pin_add_desc, info.title))
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.pin_legend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The person's tiles in their order, moved with buttons. In simple mode a line shows where the screen ends. */
@Composable
fun EditTilesScreen(content: Content?, mode: Mode?, pins: List<String>, nav: Nav) {
    val context = LocalContext.current
    Frame(stringResource(R.string.edit_tiles), nav::back) {
        Text(stringResource(R.string.edit_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.edit_fixed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.edit_fixed_note), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.edit_yours), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
                IconBadge(info.icon, info.group.tint(), 36.dp)
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

private val ThemeChoice.title: Int get() = when (this) {
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
    Preset.WINTER -> R.string.preset_winter
}

private val Preset.hint: Int get() = when (this) {
    Preset.GENERAL -> R.string.preset_general_hint
    Preset.ELDER -> R.string.preset_elder_hint
    Preset.PARENTS -> R.string.preset_parents_hint
    Preset.FRONTLINE -> R.string.preset_frontline_hint
    Preset.WINTER -> R.string.preset_winter_hint
}

@Composable
fun PresetList(onPick: (Preset) -> Unit) {
    Preset.entries.forEach { p -> NavRow(stringResource(p.title), stringResource(p.hint)) { onPick(p) } }
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
private fun ModePicture(mode: Mode) {
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

/** Settings: the look, the tiles and the lock. */
@Composable
fun SettingsScreen(prefs: HomePrefs.State, nav: Nav) {
    val context = LocalContext.current
    val canLock = Guard.available(context)
    val locked = prefs.locked
    Frame(stringResource(R.string.settings), nav::back) {
        Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        ModeChoice(prefs.mode) { m -> HomePrefs.update(context) { it.copy(mode = m) } }
        if (prefs.mode != Mode.KID) {
            Toggle(stringResource(R.string.call_bar_toggle), prefs.callBar) { on -> HomePrefs.update(context) { it.copy(callBar = on) } }
            Text(stringResource(R.string.call_bar_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        ThemeChoice.entries.forEach { t ->
            val on = t == prefs.theme
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton) { HomePrefs.update(context) { it.copy(theme = t) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = on, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(t.title), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (prefs.theme != ThemeChoice.CONTRAST) {
            Toggle(stringResource(R.string.theme_dynamic), prefs.dynamic) { on -> HomePrefs.update(context) { it.copy(dynamic = on) } }
            Text(stringResource(R.string.theme_dynamic_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (prefs.mode != Mode.KID) {
            NavRow(stringResource(R.string.edit_tiles), stringResource(R.string.edit_tiles_hint)) { nav.go(Route.EditTiles) }
            NavRow(stringResource(R.string.presets_title), stringResource(R.string.presets_hint)) { nav.go(Route.Presets) }
        }
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (prefs.mode == Mode.KID && !locked) {
            Text(stringResource(R.string.kid_lock_advice), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        if (canLock) {
            Toggle(stringResource(R.string.lock_toggle), locked) { on ->
                // Settings are open now; turning the lock on does not shut them on the spot
                if (on) Guard.keepOpen()
                HomePrefs.update(context) { it.copy(locked = on) }
            }
        } else {
            Text(stringResource(R.string.lock_no_screen_lock), style = MaterialTheme.typography.bodyLarge)
        }
        Text(stringResource(R.string.lock_explain), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Screenshots of the owner's screens: off by default, turned on only after reading what it means
        var askScreenshots by rememberSaveable { mutableStateOf(false) }
        Toggle(stringResource(R.string.screenshots_allow), prefs.screenshots) { on ->
            if (on) askScreenshots = true else HomePrefs.update(context) { it.copy(screenshots = false) }
        }
        Text(stringResource(R.string.screenshots_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (askScreenshots) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askScreenshots = false },
                title = { Text(stringResource(R.string.screenshots_confirm_title)) },
                text = { Text(stringResource(R.string.screenshots_confirm_text)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        askScreenshots = false
                        HomePrefs.update(context) { it.copy(screenshots = true) }
                    }) { Text(stringResource(R.string.screenshots_confirm_yes)) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askScreenshots = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        NavRow(stringResource(R.string.about_guide)) { nav.go(Route.About) }
        FeedbackBlock()
    }
}

/** Feedback, and the last crash's technical description if there is one: shown in full, sent only by tap. */
@Composable
private fun FeedbackBlock() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.pending(context)) }
    Text(stringResource(R.string.feedback_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    crash?.let { t ->
        Text(stringResource(R.string.crash_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.crash_text), style = MaterialTheme.typography.bodyMedium)
        Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                Feedback.open(context, context.getString(R.string.crash_issue_title), context.getString(R.string.crash_issue_body) + "\n\n```\n" + t + "\n```")
                CrashLog.clear(context)
                crash = null
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.crash_send)) }
            androidx.compose.material3.TextButton(onClick = { CrashLog.clear(context); crash = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.crash_no))
            }
        }
    }
    OutlinedButton(onClick = {
        Feedback.open(context, context.getString(R.string.feedback_title), context.getString(R.string.feedback_body, Feedback.version(context)))
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.feedback)) }
    Text(stringResource(R.string.feedback_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            tileInfo(Tiles.tool(Tool.NOW), null)?.let { info -> TileCard(info, big = true) { nav.go(info.route) } }
            if (step == 0 || mode == null) {
                Text(stringResource(R.string.welcome_mode_q), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                ModeChoice(mode) { chosen = it.name; step = 1 }
            } else if (mode == Mode.KID) {
                // No tile sets for a child; the grown-up is asked once to lock the settings
                Text(stringResource(R.string.kid_setup_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.kid_setup_text), style = MaterialTheme.typography.bodyLarge)
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
                Text(stringResource(R.string.welcome_preset_q), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                PresetList { p -> HomePrefs.update(context) { it.copy(mode = mode, pins = p.pins) } }
                TextButton(onClick = { step = 0 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.welcome_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CallBar()
    }
}
