package ua.sprotyv.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.home.Group
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
    else -> when {
        id.startsWith("link:") -> Group.GUIDE
        // A number: orange only for the emergency services, green as all help and rescue (resistance's khaki is a mark, see [isResist])
        id.startsWith("phone:") -> when (ua.sprotyv.content.Phone.categoryOf(id.substringAfter(':'))) {
            "emergency" -> Group.DANGER
            "power" -> Group.HOME_LIFE
            else -> Group.FIRST_AID
        }
        else -> groupOf(id)
    }
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
    return Palette.of(this, dark)
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
internal class Shelf(val title: Int, val desc: Int, val group: Group, val ids: List<String>, val resist: Boolean = false, val icon: ImageVector? = null)

/**
 * Resistance runs through many sections, so it has its own quiet mark: a muted khaki and the
 * app's shield. It is a theme, not a fifth level of urgency: the four colours keep their meaning,
 * and khaki never fills a whole screen.
 */
@Composable
fun resist(): Tint = when {
    LocalContrast.current -> Tint(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
    else -> Palette.resist(LocalDark.current)
}

/** The app's own shield, the same shape as its icon. */
val Shield: ImageVector by lazy {
    ImageVector.Builder("shield", 24.dp, 24.dp, 24f, 24f).addPath(
        androidx.compose.ui.graphics.vector.PathParser().parsePathString("M12,2.5L19.5,5.2L19.5,10.6C19.5,15.4 16.4,18.9 12,20.6C7.6,18.9 4.5,15.4 4.5,10.6L4.5,5.2Z").toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black),
    ).build()
}

/** The tiles that belong to resistance: they wear its mark wherever they are. */
internal fun isResist(id: String) = id == Tiles.tool(Tool.RESIST) || id == Tiles.tool(Tool.CONSTITUTION) || id == Tiles.tool(Tool.HISTORY) ||
    (id.startsWith("phone:") && ua.sprotyv.content.Phone.categoryOf(id.substringAfter(':')) in setOf("security", "veterans", "learn"))

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
    LocalDark.current -> Palette.URGENT_DARK
    else -> Palette.URGENT_LIGHT
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
