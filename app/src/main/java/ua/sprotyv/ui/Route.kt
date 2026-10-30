package ua.sprotyv.ui

/**
 * Where the person is. Kept as plain text so the whole back stack survives rotation and the app
 * being sent to the background. Ids come only from the built-in content.
 */
sealed interface Route {
    data class Open(val section: Section) : Route
    data class CardRoute(val id: String) : Route
    data class ChapterRoute(val id: String) : Route
    data class GuideSection(val num: String) : Route
    data object Search : Route
    data object About : Route
    /** A part of the owner's own section: medical card, people, plan, kit. */
    data class Mine(val part: MinePart) : Route
    data object SuppliesRoute : Route
    data object Scan : Route
    data object Settings : Route
    data object EditTiles : Route
    data object Sections : Route
    data object Presets : Route
    data object Situation : Route
    data object Power : Route
    data object Directory : Route
    /** Opens an outside source from "Situation" and comes straight back. */
    data class External(val id: String) : Route

    /** Screens that show the owner's data: opened only with the data, kept off screenshots. */
    val personal: Boolean get() = this is Mine || this == Open(Section.MINE) || this == Open(Section.SAFE)

    /** Screens that change settings: behind the PIN when settings are locked. */
    val settings: Boolean get() = this == Settings || this == EditTiles || this == Sections || this == Presets

    fun save(): String = when (this) {
        is Open -> "open:" + section.name
        is CardRoute -> "card:$id"
        is ChapterRoute -> "chapter:$id"
        is GuideSection -> "section:$num"
        Search -> "search"
        About -> "about"
        is Mine -> "mine:" + part.name
        SuppliesRoute -> "supplies"
        Scan -> "scan"
        Settings -> "settings"
        EditTiles -> "edit-tiles"
        Sections -> "sections"
        Presets -> "presets"
        Situation -> "situation"
        Power -> "power"
        Directory -> "directory"
        is External -> "external:$id"
    }

    companion object {
        fun restore(s: String): Route? {
            val (kind, value) = s.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            return when (kind) {
                "open" -> Section.entries.firstOrNull { it.name == value }?.let(::Open)
                "card" -> CardRoute(value)
                "chapter" -> ChapterRoute(value)
                "section" -> GuideSection(value)
                "search" -> Search
                "about" -> About
                "mine" -> MinePart.entries.firstOrNull { it.name == value }?.let(::Mine)
                "supplies" -> SuppliesRoute
                "scan" -> Scan
                "settings" -> Settings
                "edit-tiles" -> EditTiles
                "sections" -> Sections
                "presets" -> Presets
                "situation" -> Situation
                "power" -> Power
                "directory" -> Directory
                // Never reopened after the app is restored: that would open a page unasked
                "external" -> null
                else -> null
            }
        }
    }
}

enum class MinePart { MEDICAL, PEOPLE, PLAN, KIT, PRINT, RESCUER, BACKUP, PLAN_QR, PLAN_IMPORT, KID_CALL }
