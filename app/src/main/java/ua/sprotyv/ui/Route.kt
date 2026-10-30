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

    /** Screens that show the owner's data: opened only with the data, kept off screenshots. */
    val personal: Boolean get() = this is Mine || this == Open(Section.MINE) || this == Open(Section.SAFE)

    fun save(): String = when (this) {
        is Open -> "open:" + section.name
        is CardRoute -> "card:$id"
        is ChapterRoute -> "chapter:$id"
        is GuideSection -> "section:$num"
        Search -> "search"
        About -> "about"
        is Mine -> "mine:" + part.name
        SuppliesRoute -> "supplies"
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
                else -> null
            }
        }
    }
}

enum class MinePart { MEDICAL, PEOPLE, PLAN, KIT }
