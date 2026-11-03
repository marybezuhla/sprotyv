package ua.sprotyv.ui

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
data class TileInfo(val id: String, val title: String, val hint: String, val icon: ImageVector, val group: Group, val route: Route) {
    /** The colour it is drawn in: by what it is for, the same on every screen ([toneOf]). */
    val tone: Group get() = toneOf(id)
}

/**
 * One colour code for the whole app, by what a thing is for, wherever it is shown:
 * - orange ([Group.DANGER]): danger, act now: what to do now, alerts, the danger cards, the shelter;
 * - green ([Group.FIRST_AID]): safety, health and rescue: "I am safe", first aid, the medical card, the screen for rescuers,
 *   the Ministry's first-aid procedures, leaving for safety;
 * - blue ([Group.PREPARE]): preparing and keeping: readiness, the bag, supplies, people, the family plan, practice;
 * - violet ([Group.GUIDE]): everyday life, reading, sources and settings; copies of one's own data are blue.
 * The [Group] carries the palette ([tint]); groups of the same meaning share a colour.
 */
fun toneOf(id: String): Group = when (id) {
    Tiles.tool(Tool.NOW), Tiles.tool(Tool.ALARM) -> Group.DANGER
    // "I am safe" is green: the colour of a safe condition on safety signs
    Tiles.tool(Tool.SAFE), Tiles.tool(Tool.MEDICAL), Tiles.tool(Tool.RESCUER), Tiles.tool(Tool.DIRECTORY), Tiles.tool(Tool.PHONES) -> Group.FIRST_AID
    Tiles.tool(Tool.MINE), Tiles.tool(Tool.PEOPLE), Tiles.tool(Tool.PLAN), Tiles.tool(Tool.SUPPLIES) -> Group.PREPARE
    // Copies of one's own data: blue, as "Mine"
    Tiles.tool(Tool.PRINT), Tiles.tool(Tool.SCAN), Tiles.tool(Tool.QR_TEXT) -> Group.MINE
    Tiles.tool(Tool.SITUATION) -> Group.GUIDE
    else -> if (id.startsWith("link:")) Group.GUIDE else groupOf(id)
}

data class Tint(val container: Color, val ink: Color)

/**
 * Four colours only, each with one meaning, after the safety colours of ISO 3864-1 that people
 * already know from signs: orange-red for danger, green for first aid and the way out (the green
 * first-aid cross and exit signs), blue for things to do and keep (the "mandatory" blue), and a
 * calm violet for everyday life, knowing and reading (not a safety colour, so it never competes). Few categories are told apart at a glance (about
 * five or six at most); the rest is said by the icon and the words. Soft fills, so a screen of
 * blocks stays calm, and strong colour is left for what matters.
 */
@Composable
fun Group.tint(): Tint {
    val dark = LocalDark.current
    // High contrast: no pale fills, the plain text colours of the theme; icon and words still say the group
    if (LocalContrast.current) return Tint(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
    return when (this) {
        Group.DANGER -> if (dark) Tint(Color(0xFF342318), Color(0xFFF5C39E)) else Tint(Color(0xFFFFE9D6), Color(0xFF8A3B00))
        Group.FIRST_AID, Group.LEAVE -> if (dark) Tint(Color(0xFF182F28), Color(0xFFA3D3BD)) else Tint(Color(0xFFDDF0E6), Color(0xFF1E5B40))
        Group.PREPARE, Group.MINE, Group.FAMILY -> if (dark) Tint(Color(0xFF1A2A3C), Color(0xFFBBD0EA)) else Tint(Color(0xFFDCE8F7), Color(0xFF163E6E))
        // Knowing and everyday life: a calm violet, so no group is left without a colour
        Group.HOME_LIFE, Group.SAFETY, Group.GUIDE -> if (dark) Tint(Color(0xFF272339), Color(0xFFD2C7EE)) else Tint(Color(0xFFEDE7F8), Color(0xFF47387A))
    }
}

internal val Group.title: Int get() = when (this) {
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

/** One folded group in "Sections": its name, a line on what is in it, its colour, its tiles. */
private class Shelf(val title: Int, val desc: Int, val group: Group, val ids: List<String>, val resist: Boolean = false, val icon: ImageVector? = null)

/**
 * Resistance runs through many sections, so it has its own quiet mark: a muted khaki and the
 * app's shield. It is a theme, not a fifth level of urgency: the four colours keep their meaning,
 * and khaki never fills a whole screen.
 */
@Composable
fun resist(): Tint = when {
    LocalContrast.current -> Tint(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
    LocalDark.current -> Tint(Color(0xFF2A2B21), Color(0xFFD3CEA6))
    else -> Tint(Color(0xFFECE9D5), Color(0xFF4B4921))
}

/** The app's own shield, the same shape as its icon. */
val Shield: ImageVector by lazy {
    ImageVector.Builder("shield", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,2.5L19.5,5.2L19.5,10.6C19.5,15.4 16.4,18.9 12,20.6C7.6,18.9 4.5,15.4 4.5,10.6L4.5,5.2Z").toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    ).build()
}

/** The tiles that belong to resistance: they wear its mark wherever they are. */
internal fun isResist(id: String) = id == Tiles.tool(Tool.RESIST) || id == Tiles.tool(Tool.CONSTITUTION) || id == Tiles.tool(Tool.HISTORY)

/** A tile's badge colours: its group's, or resistance's khaki. */
@Composable
fun TileInfo.badge(): Tint = if (isResist(id)) resist() else tone.tint()

internal val Group.desc: Int get() = when (this) {
    Group.DANGER -> R.string.group_danger_desc
    Group.FIRST_AID -> R.string.group_first_aid_desc
    Group.FAMILY -> R.string.group_family_desc
    Group.HOME_LIFE -> R.string.group_home_life_desc
    Group.PREPARE -> R.string.group_prepare_desc
    Group.LEAVE -> R.string.group_leave_desc
    Group.SAFETY -> R.string.group_safety_desc
    Group.MINE -> R.string.group_mine_desc
    Group.GUIDE -> R.string.group_guide_desc
}

/**
 * The one colour that calls for attention: a strong orange, for the way in when something is
 * happening ("What to do now", "The first 60 seconds"). Warm, saturated colours are seen first;
 * orange rather than red, which stays for danger on the spot.
 */
data class Urgent(val container: Color, val ink: Color, val edge: Color)

@Composable
fun urgent(): Urgent = when {
    LocalContrast.current -> Urgent(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface)
    LocalDark.current -> Urgent(Color(0xFF3E2716), Color(0xFFFFD9B8), Color(0xFFC77A3E))
    else -> Urgent(Color(0xFFFFE6D1), Color(0xFF5C2600), Color(0xFFD9772B))
}

/** A card's own picture and its group's colours, for lists of cards. */
@Composable
fun cardBadge(id: String): Pair<ImageVector, Tint> {
    val group = groupOf(Tiles.card(id))
    return (CARD_ICONS[id]?.let { ImageVector.vectorResource(it) } ?: group.icon) to toneOf(Tiles.card(id)).tint()
}

/** Each group's own humanitarian picture (OCHA), the same wherever the group is shown. */
internal val Group.icon: ImageVector @Composable get() = when (this) {
    Group.DANGER -> Icons.Filled.Warning
    Group.FIRST_AID -> ImageVector.vectorResource(R.drawable.ocha_health)
    Group.FAMILY -> ImageVector.vectorResource(R.drawable.ocha_users)
    Group.HOME_LIFE -> ImageVector.vectorResource(R.drawable.ocha_house)
    Group.PREPARE -> ImageVector.vectorResource(R.drawable.ocha_preparedness)
    Group.LEAVE -> ImageVector.vectorResource(R.drawable.ocha_internally_displaced)
    Group.SAFETY -> ImageVector.vectorResource(R.drawable.ocha_security)
    Group.MINE -> Icons.Filled.AccountBox
    Group.GUIDE -> ImageVector.vectorResource(R.drawable.ocha_learning)
}

internal val Tool.title: Int get() = when (this) {
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
    Tool.QR_TEXT -> R.string.qr_text_title
    Tool.MOZ -> R.string.moz_short
    Tool.RESIST -> R.string.resist_title
    Tool.CONSTITUTION -> R.string.const_title
    Tool.HISTORY -> R.string.history_title
    Tool.NEW_PLACE -> R.string.np_title
    Tool.GART -> R.string.gart_title
    Tool.COMMS -> R.string.comms_title
    Tool.DRIVING -> R.string.driving_title
    Tool.WEAPONS -> R.string.weapons_title
    Tool.PHONES -> R.string.phones_title
}

internal val Tool.hint: Int get() = when (this) {
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
    Tool.QR_TEXT -> R.string.qr_text_hint
    Tool.MOZ -> R.string.moz_hint
    Tool.RESIST -> R.string.resist_hint
    Tool.CONSTITUTION -> R.string.const_hint
    Tool.HISTORY -> R.string.history_hint
    Tool.NEW_PLACE -> R.string.np_hint
    Tool.GART -> R.string.gart_hint
    Tool.COMMS -> R.string.comms_hint
    Tool.DRIVING -> R.string.driving_hint
    Tool.WEAPONS -> R.string.weapons_hint
    Tool.PHONES -> R.string.phones_hint
}

/** Cards and tools with a picture of their own (Health Icons, public domain), clearer than the group's. */
internal val CARD_ICONS = mapOf(
    "bleeding" to R.drawable.hi_blood_drop,
    "unconscious" to R.drawable.hi_cpr,
    "choking" to R.drawable.om_person,
    "burn" to R.drawable.hi_burn,
    "chemical" to R.drawable.hi_poison,
    "radiation" to R.drawable.hi_hazardous,
    "drone" to R.drawable.hi_drone,
    "air-alert" to R.drawable.hi_megaphone,
    // OCHA Humanitarian Icons (freely available / CC0)
    "blackout" to R.drawable.ocha_power_outage,
    "home-fire" to R.drawable.ocha_fire,
    "leave-home" to R.drawable.ocha_internally_displaced,
    "suspicious" to R.drawable.ocha_mine,
    "rubble" to R.drawable.ocha_house_destroyed,
    "ballistic" to R.drawable.ocha_attack,
)

/** Guide chapters with a picture of their own (OCHA and Health Icons), clearer than the group's. */
internal val CHAPTER_ICONS = mapOf(
    "ch1" to R.drawable.ocha_preparedness, "ch2" to R.drawable.ocha_non_food_items, "ch3" to R.drawable.ocha_shelter,
    "ch4" to R.drawable.hi_drone, "ch5" to R.drawable.ocha_mine, "ch6" to R.drawable.hi_hazardous, "ch7" to R.drawable.ocha_health,
    "ch8" to R.drawable.ocha_potable_water, "ch9" to R.drawable.ocha_food, "ch10" to R.drawable.ocha_stove, "ch11" to R.drawable.ocha_tent,
    "ch12" to R.drawable.ocha_clothing, "ch13" to R.drawable.ocha_map, "ch14" to R.drawable.ocha_walkie_talkie,
    "ch15" to R.drawable.ocha_services_and_tools, "ch16" to R.drawable.ocha_handwashing, "ch17" to R.drawable.ocha_security,
    "ch18" to R.drawable.ocha_public_information, "ch19" to R.drawable.ocha_earthquake, "ch20" to R.drawable.ocha_power_outage,
    "ch21" to R.drawable.ocha_hospital, "ch22" to R.drawable.ocha_children, "ch23" to R.drawable.ocha_storm,
    "ch24" to R.drawable.ocha_services_and_tools, "ch25" to R.drawable.ocha_agriculture, "ch26" to R.drawable.ocha_internally_displaced,
    "ch27" to R.drawable.ocha_insect_infestation, "ch28" to R.drawable.ocha_group, "ch29" to R.drawable.ocha_table,
    "ch30" to R.drawable.ocha_preparedness, "planA" to R.drawable.ocha_calendar, "abroad" to R.drawable.ocha_border_crossing,
)
internal val TOOL_ICONS = mapOf(
    Tool.ALARM to R.drawable.hi_megaphone,
    Tool.MOZ to R.drawable.hi_cpr,
    Tool.KIT to R.drawable.hi_bandage_adhesive,
    Tool.SUPPLIES to R.drawable.hi_treated_water,
    Tool.POWER to R.drawable.ocha_power_outage,
    Tool.HOME_LIFE to R.drawable.ocha_house,
    Tool.DIRECTORY to R.drawable.ocha_help,
    Tool.SITUATION to R.drawable.ocha_map,
    Tool.PEOPLE to R.drawable.ocha_users,
    Tool.PLAN to R.drawable.ocha_assembly_point,
    Tool.PRACTICE to R.drawable.ocha_training,
    Tool.GUIDE to R.drawable.ocha_learning,
    Tool.CONSTITUTION to R.drawable.ocha_public_information,
    Tool.HISTORY to R.drawable.ocha_calendar,
    Tool.NEW_PLACE to R.drawable.ocha_internally_displaced,
    Tool.RESCUER to R.drawable.ocha_health,
    Tool.GART to R.drawable.ocha_training,
    Tool.COMMS to R.drawable.ocha_walkie_talkie,
    Tool.DRIVING to R.drawable.ic_car,
    Tool.WEAPONS to R.drawable.ocha_security,
    Tool.PRINT to R.drawable.ocha_table,
)

/** The picture of a part by its id, as a drawable of the app's own; null for those drawn with a system icon. */
internal fun tileIconRes(id: String): Int? {
    val (kind, value) = id.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
    return when (kind) {
        "card" -> CARD_ICONS[value]
        "chapter" -> CHAPTER_ICONS[value]
        "tool" -> Tool.entries.firstOrNull { it.name == value }?.let { TOOL_ICONS[it] }
        else -> null
    }
}

internal val Tool.icon: ImageVector? get() = when (this) {
    Tool.NOW -> Icons.Filled.Warning
    Tool.SAFE -> Icons.Filled.CheckCircle
    Tool.ALARM -> Icons.Filled.Notifications
    Tool.SITUATION -> Icons.Filled.LocationOn
    Tool.POWER -> Icons.Filled.DateRange
    Tool.DIRECTORY -> Icons.Filled.Phone
    Tool.PHONES -> Icons.Filled.Call
    Tool.SEARCH -> Icons.Filled.Search
    Tool.MEDICAL -> Icons.Filled.Favorite
    Tool.PEOPLE -> Icons.Filled.Person
    Tool.PRACTICE -> Icons.Filled.PlayArrow
    Tool.RESIST -> Shield
    Tool.SCAN -> Icons.Filled.Search
    Tool.QR_TEXT -> Icons.Filled.Email
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
    Tool.QR_TEXT -> Route.QrText
    Tool.MOZ -> Route.MozList
    Tool.RESIST -> Route.Resistance
    Tool.CONSTITUTION -> Route.ConstitutionRoute("")
    Tool.HISTORY -> Route.HistoryRoute
    Tool.NEW_PLACE -> Route.Mine(MinePart.NEW_PLACE)
    Tool.GART -> Route.Mine(MinePart.GART)
    Tool.COMMS -> Route.Comms
    Tool.DRIVING -> Route.Driving
    Tool.WEAPONS -> Route.Weapons
    Tool.PHONES -> Route.Phones
}

/** The group a tile belongs to: the first that names it. "What to do now" and "I am safe" have their own. */
fun groupOf(id: String): Group = when (id) {
    Tiles.tool(Tool.NOW) -> Group.DANGER
    Tiles.tool(Tool.SAFE) -> Group.FIRST_AID
    else -> if (id.startsWith("link:")) Group.SAFETY else Group.entries.firstOrNull { id in it.tiles } ?: Group.GUIDE
}

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
fun TileCard(info: TileInfo, big: Boolean, status: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            containerColor = when { marked -> u.container; safe -> green.container; else -> own.container }.copy(alpha = 0.7f).compositeOver(MaterialTheme.colorScheme.surface),
            contentColor = when { marked -> u.ink; safe -> green.ink; else -> MaterialTheme.colorScheme.onSurface },
        ),
        border = when {
            marked -> BorderStroke(1.dp, u.edge.copy(alpha = 0.7f))
            safe -> BorderStroke(1.dp, green.ink.copy(alpha = 0.3f))
            else -> BorderStroke(1.dp, own.ink.copy(alpha = 0.2f))
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
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val cols = if (cfg.fontScale >= 1.3f) 1 else ((cfg.screenWidthDp - 20) / 160).coerceIn(2, 3)
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
                        OutlinedIconButton(onClick = { onPin(info.id, true) }, enabled = !full, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pin_add_desc, info.title))
                        }
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.pin_legend), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

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
