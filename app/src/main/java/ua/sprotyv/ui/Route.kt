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
    /** A QR code made from any text typed here; the text is kept nowhere. */
    data object QrText : Route
    /** The Ministry of Health's first-aid procedures (order No. 441), and one of them. */
    data object MozList : Route
    /** "Resistance": what everyone can do, where to learn, service for adults. Hidden in trip mode. */
    data object Resistance : Route
    /** One of the owner's notes; 0 for a new one. */
    data class NoteEdit(val id: Long) : Route
    data class Moz(val n: Int) : Route
    /** The Constitution of Ukraine: all of it when [article] is empty, or one article. */
    data class ConstitutionRoute(val article: String) : Route
    /** "Why resist": stories from history, and one of them. */
    data object HistoryRoute : Route
    data class Story(val id: String) : Route
    /** Hand out the steps of a procedure to people nearby. */
    data class Delegate(val n: Int) : Route
    /** What happened and what was done, for the ambulance crew; kept nowhere. */
    data object Handover : Route
    /** "What would you do?" for one card, or for all adult cards when [card] is empty. */
    data class PracticeRoute(val card: String) : Route
    /** "When the network is down": phone-to-phone and radio messengers, set up in advance. */
    data object Comms : Route
    /** "Check yourself" for one step of "My path": that step's situations; all right marks it as checked. */
    data class PathCheck(val step: String) : Route
    /** One role's page ("Who I am now"): kept in "Mine". */
    data class RoleRoute(val id: String) : Route
    /** Opens an outside source from "Situation" and comes straight back. */
    data class External(val id: String) : Route

    /** Screens that show the owner's data: opened only with the data, kept off screenshots. */
    val personal: Boolean get() = this is Mine || this is NoteEdit || this == Open(Section.MINE) || this == Open(Section.SAFE) || this == SuppliesRoute || this is PathCheck || this is RoleRoute

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
        QrText -> "qr-text"
        MozList -> "moz"
        Resistance -> "resistance"
        is NoteEdit -> "note:$id"
        is Moz -> "moz:$n"
        HistoryRoute -> "history"
        is Story -> "story:$id"
        is ConstitutionRoute -> if (article.isEmpty()) "constitution" else "constitution:$article"
        is Delegate -> "delegate:$n"
        Handover -> "handover"
        is PracticeRoute -> "practice:$card"
        Comms -> "comms"
        is PathCheck -> "pathcheck:$step"
        is RoleRoute -> "role:$id"
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
                "qr-text" -> QrText
                "delegate" -> value.toIntOrNull()?.takeIf { it in 1..60 }?.let(::Delegate)
                "handover" -> Handover
                "resistance" -> Resistance
                "note" -> value.toLongOrNull()?.takeIf { it >= 0 }?.let(::NoteEdit)
                "history" -> HistoryRoute
                "story" -> value.takeIf { it.matches(Regex("^[a-z0-9-]{1,40}$")) }?.let(::Story)
                "constitution" -> if (value.isEmpty()) ConstitutionRoute("") else value.takeIf { it.matches(Regex("^\\d{1,3}(-\\d)?$")) }?.let(::ConstitutionRoute)
                "moz" -> if (value.isEmpty()) MozList else value.toIntOrNull()?.takeIf { it in 1..60 }?.let(::Moz)
                "practice" -> PracticeRoute(value)
                "comms" -> Comms
                "pathcheck" -> value.takeIf { it in PATH_CHECKS }?.let(::PathCheck)
                "role" -> value.takeIf { it in ROLES }?.let(::RoleRoute)
                // Never reopened after the app is restored: that would open a page unasked
                "external" -> null
                else -> null
            }
        }
    }
}

enum class MinePart { MEDICAL, PEOPLE, PLAN, KIT, PRINT, RESCUER, BACKUP, PLAN_QR, PLAN_IMPORT, KID_CALL, BOOKMARKS, NOTES, PATH, NEW_PLACE, GART, ROLES }
