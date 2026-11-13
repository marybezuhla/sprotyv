package ua.sprotyv.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

/** A chapter's picture in its group's colours, as on its tile (the guide's contents, related chapters). */
@Composable
fun ChapterBadge(chapterId: String, size: Dp) {
    val g = groupOf(Tiles.chapter(chapterId))
    val icon = CHAPTER_ICONS[chapterId]?.let { ImageVector.vectorResource(it) } ?: g.icon
    IconBadge(icon, g.tint(), size)
}

/** Null for an id that is not (or no longer) in the app; such a tile is simply not shown. */
@Composable
fun tileInfo(id: String, content: Content?): TileInfo? {
    // Trip mode: what it hides is not a tile anywhere
    val trip = HomePrefs.state.collectAsState().value?.trip == true
    if (trip) {
        if (id.startsWith("link:") && content?.links?.firstOrNull { it.id == id.substringAfter(':') }?.let(Trip::hides) == true) return null
        if (id.startsWith("tool:") && Tool.entries.firstOrNull { it.name == id.substringAfter(':') }?.route?.let(Trip::hides) == true) return null
        if (id.startsWith("phone:") && ua.sprotyv.content.Phone.tripHides(id.substringAfter(':'))) return null
    }
    val kind = id.substringBefore(':')
    val value = id.substringAfter(':', "")
    val group = groupOf(id)
    return when (kind) {
        "card" -> content?.card(value)?.let { c ->
            val own = CARD_ICONS[value]?.let { ImageVector.vectorResource(it) }
            TileInfo(id, c.title, "", own ?: group.icon, group, Route.CardRoute(value))
        }
        "chapter" -> content?.guide?.chapters?.firstOrNull { it.id == value }?.let { ch ->
            // "Water: finding, cleaning..." becomes "Water" with the rest below it
            val title = ch.title.substringBefore(':').trim()
            val hint = if (':' in ch.title) ch.title.substringAfter(':').trim().replaceFirstChar { it.uppercase() } else ""
            val own = CHAPTER_ICONS[value]?.let { ImageVector.vectorResource(it) }
            TileInfo(id, title, hint, own ?: group.icon, group, Route.ChapterRoute(value))
        }
        "link" -> content?.links?.firstOrNull { it.id == value }?.let { l ->
            TileInfo(id, l.title, l.note, Icons.Filled.LocationOn, group, Route.External(l.id))
        }
        // The number large, the service below it; a tap opens the dialler
        "phone" -> content?.phones?.firstOrNull { it.id == value }?.let { p ->
            TileInfo(id, ua.sprotyv.content.PhonesReader.shown(p.number), phoneShort(p), Icons.Filled.Call, toneOf(id), Route.Dial(value))
        }
        "tool" -> Tool.entries.firstOrNull { it.name == value }?.let { tool ->
            val own = TOOL_ICONS[tool]?.let { ImageVector.vectorResource(it) }
            TileInfo(id, stringResource(tool.title), stringResource(tool.hint), own ?: tool.icon ?: group.icon, group, tool.route)
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
fun TileCard(info: TileInfo, big: Boolean, modifier: Modifier = Modifier, status: String? = null, onClick: () -> Unit) {
    val marked = info.id == Tiles.tool(Tool.NOW)
    // "I am safe" beside it in its own green, so the two ways in sit side by side in harmony
    val safe = info.id == Tiles.tool(Tool.SAFE)
    val u = urgent()
    val green = Group.FIRST_AID.tint()
    // Each tile in its group's soft colour, so the home screen is read by colour at a glance
    val own = info.badge()
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            // Dark: softened over the surface; light: the full colour, or the tiles fade into white
            containerColor = when { marked -> u.container; safe -> green.container; else -> own.container }.let { if (LocalDark.current) it.copy(alpha = 0.7f).compositeOver(MaterialTheme.colorScheme.surface) else it },
            contentColor = when { marked -> u.ink; safe -> green.ink; else -> MaterialTheme.colorScheme.onSurface },
        ),
        border = when {
            marked -> BorderStroke(1.dp, u.edge.copy(alpha = 0.7f))
            safe -> BorderStroke(1.dp, green.ink.copy(alpha = if (LocalDark.current) 0.3f else 0.45f))
            else -> BorderStroke(1.dp, own.ink.copy(alpha = if (LocalDark.current) 0.2f else 0.35f))
        },
        modifier = modifier.fillMaxWidth().heightIn(min = if (big) 88.dp else 76.dp),
    ) {
        if (big) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(info.icon, if (marked) Tint(MaterialTheme.colorScheme.surface, u.edge) else if (safe) Tint(MaterialTheme.colorScheme.surface, green.ink) else Tint(MaterialTheme.colorScheme.surface, own.ink), 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    // Simple look: large print (24 and 18 sp), still growing with the phone's text size
                    Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Start), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    } else if (info.hint.isNotBlank()) {
                        Text(info.hint, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Start), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (info.id.startsWith("phone:")) {
            // A number: on its own line across the whole tile, never broken in two (a little smaller
            // only when it would not fit); the service below it, with a small phone mark
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FitOneLine(info.title, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), 15.sp, own.ink)
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = own.ink, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(info.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            // Compact: icon beside the title, so more fits on the screen; a live status replaces the hint
            // Every line starts at the same edge, right of the icon; one size for titles, one for hints
            Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                IconBadge(info.icon, if (marked) Tint(MaterialTheme.colorScheme.surface, u.edge) else if (safe) Tint(MaterialTheme.colorScheme.surface, green.ink) else Tint(MaterialTheme.colorScheme.surface, own.ink), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (status != null) {
                        Text(status, style = MaterialTheme.typography.bodyMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    } else if (info.hint.isNotBlank()) {
                        Text(info.hint, style = MaterialTheme.typography.bodyMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
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
            Text(appTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // With very large text the word would squeeze the title: then the gear alone, still named aloud
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
            } else {
                TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
                }
            }
            CornerCall()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Only tiles that can be shown count towards the six, so the screen matches the editor
            val shown = Pins.simple(pins.filter { tileInfo(it, content) != null }).mapNotNull { tileInfo(it, content) }
            val now = remember { System.currentTimeMillis() }
            ActiveStrip(nav)
            shown.forEach { info -> TileCard(info, big = true, status = tileStatus(info.id, now)) { nav.go(info.route) } }
        }
        CallBar()
    }
}

/** Full mode, "Home": the two fixed tiles, then the person's own, in their order. */
@Composable
fun FullHome(content: Content?, pins: List<String>, nav: Nav, onSettings: () -> Unit, onEdit: () -> Unit, onAdd: () -> Unit) {
    // Live states on tiles (power out for how long) are worked out again every minute
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); now = System.currentTimeMillis() } }
    val fixed = Tiles.FIXED.mapNotNull { tileInfo(it, content) }
    val mine = pins.mapNotNull { tileInfo(it, content) }
    // Compact tiles two (or three) to a row, as before; one only when the text is made huge
    val density = androidx.compose.ui.platform.LocalDensity.current
    val widthDp = with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp().value }.toInt()
    val cols = if (density.fontScale >= 1.3f) 1 else ((widthDp - 20) / 160).coerceIn(2, 3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // Search the guide from anywhere on the home screen, one tap
                IconButton(onClick = { nav.go(Route.Search) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_guide))
                }
                CornerCall()
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                }
            }
        }
        // Counts running now come first, by themselves
        item(span = { GridItemSpan(maxLineSpan) }) { ActiveStrip(nav) }
        tileRows(fixed, cols) { info, m -> TileCard(info, big = false, modifier = m) { nav.go(info.route) } }
        item(span = { GridItemSpan(maxLineSpan) }) { ReadinessStrip(nav) }
        // Today's daily hardening, when habits are chosen: counts only, a tap to tick them
        item(span = { GridItemSpan(maxLineSpan) }) { GartStrip(nav) }
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
        tileRows(mine, cols) { info, m -> TileCard(info, big = false, status = tileStatus(info.id, now), modifier = m) { nav.go(info.route) } }
    }
}

/**
 * Tiles side by side in rows of the same height, so a short tile never sits beside a tall one and
 * the screen does not look scattered. Each row is one full-width item of the grid.
 */
internal fun androidx.compose.foundation.lazy.grid.LazyGridScope.tileRows(
    list: List<TileInfo>, cols: Int, tile: @Composable (TileInfo, Modifier) -> Unit,
) {
    list.chunked(cols).forEach { row ->
        item(key = "row:" + row.joinToString(",") { it.id }, span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { info -> Box(Modifier.weight(1f).fillMaxHeight()) { tile(info, Modifier.fillMaxHeight()) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
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
    val links = content.links
    val resistLinks = links.filter { it.group in ua.sprotyv.content.Link.RESIST && it.group != "service" }.map { Tiles.link(it.id) }
    val lists = listOf(Shelf(R.string.resist_title, R.string.shelf_resist_desc, Group.PREPARE, listOf(Tiles.tool(Tool.RESIST), Tiles.tool(Tool.HISTORY), Tiles.tool(Tool.CONSTITUTION), Tiles.tool(Tool.MOZ), Tiles.tool(Tool.PRACTICE), Tiles.tool(Tool.KIT)) + resistLinks, resist = true)) +
        Group.entries.map { Shelf(it.title, it.desc, it, it.tiles) } +
        Shelf(R.string.situation_title, R.string.shelf_situation_desc, Group.GUIDE, icon = ImageVector.vectorResource(R.drawable.ocha_map), ids = links.filter { it.group != ua.sprotyv.content.Link.POWER && it.group != ua.sprotyv.content.Link.ROLES && it.group !in ua.sprotyv.content.Link.DIRECTORY && it.group !in ua.sprotyv.content.Link.RESIST }.map { Tiles.link(it.id) }) +
        Shelf(R.string.power_title, R.string.shelf_power_desc, Group.HOME_LIFE, icon = ImageVector.vectorResource(R.drawable.ocha_power_outage), ids = links.filter { it.group == ua.sprotyv.content.Link.POWER }.map { Tiles.link(it.id) }) +
        Shelf(R.string.dir_title, R.string.shelf_dir_desc, Group.FIRST_AID, icon = ImageVector.vectorResource(R.drawable.ocha_help), ids = links.filter { it.group in ua.sprotyv.content.Link.DIRECTORY }.map { Tiles.link(it.id) }) +
        Shelf(R.string.group_rest, R.string.shelf_rest_desc, Group.GUIDE, Group.rest(content.guide.chapters.map { it.id }))
    lists.forEach { shelf ->
        val infos = shelf.ids.mapNotNull { tileInfo(it, content) }
        if (infos.isEmpty()) return@forEach
        // Folded by default: the groups are seen first, each with its colour and a line on what is in it
        var open by rememberSaveable(shelf.title) { mutableStateOf(false) }
        val tint = if (shelf.resist) resist() else shelf.group.tint()
        val onHome = infos.count { it.id in pins }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, tint.container),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().background(tint.container).clickable(onClickLabel = stringResource(if (open) R.string.shelf_fold else R.string.shelf_unfold)) { open = !open }
                    .heightIn(min = 64.dp).padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(if (shelf.resist) Shield else shelf.icon ?: shelf.group.icon, Tint(MaterialTheme.colorScheme.surface, tint.ink), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(stringResource(shelf.title), style = BlockTitle, color = tint.ink)
                    Text(stringResource(shelf.desc), style = MaterialTheme.typography.bodyMedium, color = tint.ink)
                    Text(
                        stringResource(R.string.shelf_count, infos.size) + if (onHome > 0) " · " + stringResource(R.string.shelf_on_home, onHome) else "",
                        style = MaterialTheme.typography.labelLarge, color = tint.ink,
                    )
                }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = tint.ink)
            }
            if (open) infos.forEachIndexed { i, info ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val pinned = info.id in pins
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { nav.go(info.route) }
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(info.icon, info.badge(), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (info.hint.isNotBlank()) Text(info.hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // A small round switch, not a wide button: the title keeps the whole line
                    if (pinned) {
                        FilledTonalIconButton(onClick = { onPin(info.id, false) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.pin_remove_desc, info.title))
                        }
                    } else {
                        // Not greyed out when full: a tap says why nothing was added
                        OutlinedIconButton(onClick = { onPin(info.id, true) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pin_add_desc, info.title))
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.pin_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A tile's live state, when it has one: how long the power has been out, and how far the
 * owner's own parts are (the shares last seen in "Mine", never the records).
 */
@Composable
fun tileStatus(id: String, now: Long): String? {
    val context = LocalContext.current
    val prefs = HomePrefs.state.collectAsState().value
    // Running counts, from the moments kept on this phone
    if (id == Tiles.tool(Tool.POWER) || id == Tiles.tool(Tool.HOME_LIFE) || id == Tiles.card("blackout")) {
        val power = Since.POWER.get(context, now).takeIf { it != 0L }?.let { stringResource(R.string.tile_power_out, span((now - it) / 60_000)) }
        val heat = Since.HEAT.get(context, now).takeIf { it != 0L }?.let { stringResource(R.string.tile_heat_out, span((now - it) / 60_000)) }
        return listOfNotNull(power, heat).joinToString(" · ").ifEmpty { null }
    }
    if (id == Tiles.tool(Tool.ALARM) || id == Tiles.card("air-alert")) {
        return Since.SHELTER.get(context, now).takeIf { it != 0L }?.let { stringResource(R.string.tile_shelter, span((now - it) / 60_000)) }
    }
    if (prefs == null || prefs.trip) return null
    val index = listOf(Tool.MEDICAL, Tool.PEOPLE, Tool.PLAN, Tool.KIT, Tool.SUPPLIES).indexOfFirst { Tiles.tool(it) == id }
    if (index < 0) return null
    val share = prefs.readiness.split(",").getOrNull(index)?.split("/") ?: return null
    val d = share.getOrNull(0)?.toIntOrNull() ?: return null
    val t = share.getOrNull(1)?.toIntOrNull() ?: return null
    return if (t > 0 && d >= t) stringResource(R.string.tile_ready) else stringResource(R.string.ready_count, d, t)
}

/** One line of text that gets a little smaller instead of breaking, down to [min]. */
@Composable
fun FitOneLine(text: String, style: androidx.compose.ui.text.TextStyle, min: androidx.compose.ui.unit.TextUnit, color: Color) {
    var size by remember(text, style.fontSize) { mutableStateOf(style.fontSize) }
    Text(
        text, style = style.copy(fontSize = size), color = color, maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && size.value > min.value) size = (size.value - 1f).sp },
    )
}
