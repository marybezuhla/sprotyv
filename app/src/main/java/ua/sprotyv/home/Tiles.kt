package ua.sprotyv.home

/**
 * What can be put on the home screen, and how it is chosen. Plain Kotlin, so it is checked
 * without a phone.
 *
 * A tile is a short text id: "card:<id>" (a card), "chapter:<id>" (a guide chapter) or
 * "tool:<NAME>" (a part of the app). Ids come from the built-in content and the [Tool] list;
 * anything else read back from storage is dropped.
 *
 * The person decides what is where; the app never moves tiles by itself, so a tile is found in the
 * same place every time (adaptable, not adaptive: Findlater & McGrenere 2004).
 */
/** KID: a child's phone (6-10 years): six pictures, a call to family and 112. */
enum class Mode { SIMPLE, FULL, KID }

/** Parts of the app that can be a tile. */
enum class Tool {
    NOW, SAFE, ALARM, HOME_LIFE, GUIDE, SEARCH,
    MINE, MEDICAL, PEOPLE, PLAN, KIT, SUPPLIES, RESCUER, PRINT, SCAN,
}

object Tiles {
    fun card(id: String) = "card:$id"
    fun chapter(id: String) = "chapter:$id"
    fun tool(t: Tool) = "tool:${t.name}"

    /** Always on the home screen, first, in both modes: what to do now, and "I am safe". 112 is the bar below. */
    val FIXED = listOf(tool(Tool.NOW), tool(Tool.SAFE))

    /** Simple mode: the two fixed tiles and the first four chosen ones (Hick's law: six choices at most). */
    const val SIMPLE_CHOSEN = 4

    /** Enough for anyone's own home screen, few enough to stay a home screen. */
    const val MAX_PINS = 16

    fun isValid(id: String, cards: Set<String>, chapters: Set<String>): Boolean {
        val (kind, value) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return when (kind) {
            "card" -> value in cards
            "chapter" -> value in chapters
            "tool" -> Tool.entries.any { it.name == value } && id !in FIXED
            else -> false
        }
    }
}

/** The chosen tiles, in the person's order. Every change returns a new list; nothing else moves. */
object Pins {
    fun parse(stored: String?): List<String> =
        stored.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() && it !in Tiles.FIXED }.distinct().take(Tiles.MAX_PINS)

    fun write(pins: List<String>): String = pins.joinToString("\n")

    /** Added at the end, where the person will look for it. */
    fun pin(pins: List<String>, id: String): List<String> =
        if (id in pins || id in Tiles.FIXED || pins.size >= Tiles.MAX_PINS) pins else pins + id

    fun unpin(pins: List<String>, id: String): List<String> = pins - id

    /** One place up (-1) or down (+1); at the edge nothing happens. */
    fun move(pins: List<String>, id: String, by: Int): List<String> {
        val from = pins.indexOf(id)
        val to = from + by
        if (from < 0 || to !in pins.indices) return pins
        return pins.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** What simple mode shows: the fixed tiles and the first chosen ones. */
    fun simple(pins: List<String>): List<String> = Tiles.FIXED + pins.take(Tiles.SIMPLE_CHOSEN)
}

/**
 * Ready sets of tiles for a start. They only fill the home screen; the person changes it freely.
 * The order puts the most likely need first: in simple mode only the first four are seen.
 */
enum class Preset(val pins: List<String>) {
    GENERAL(
        listOf(
            Tiles.card("air-alert"), Tiles.card("bleeding"), Tiles.card("blackout"), Tiles.tool(Tool.MEDICAL),
            Tiles.card("first-60"), Tiles.tool(Tool.KIT), Tiles.tool(Tool.SUPPLIES), Tiles.tool(Tool.GUIDE),
        ),
    ),
    ELDER(
        listOf(
            Tiles.tool(Tool.MEDICAL), Tiles.card("air-alert"), Tiles.card("blackout"), Tiles.card("unconscious"),
            Tiles.tool(Tool.PEOPLE), Tiles.tool(Tool.RESCUER),
        ),
    ),
    PARENTS(
        listOf(
            Tiles.chapter("ch22"), Tiles.card("unconscious"), Tiles.card("burn"), Tiles.card("air-alert"),
            Tiles.card("bleeding"), Tiles.tool(Tool.KIT), Tiles.tool(Tool.SUPPLIES), Tiles.tool(Tool.PLAN),
        ),
    ),
    FRONTLINE(
        listOf(
            Tiles.card("air-alert"), Tiles.card("drone"), Tiles.card("bleeding"), Tiles.card("leave-home"),
            Tiles.card("ballistic"), Tiles.card("suspicious"), Tiles.card("rubble"), Tiles.tool(Tool.KIT),
            Tiles.tool(Tool.PLAN),
        ),
    ),
    WINTER(
        listOf(
            Tiles.card("blackout"), Tiles.tool(Tool.SUPPLIES), Tiles.chapter("ch12"), Tiles.chapter("ch8"),
            Tiles.chapter("ch20"), Tiles.card("home-fire"), Tiles.chapter("ch16"),
        ),
    ),
}

/**
 * The "Sections" tab: everything that can be a tile, in small groups that each answer one need.
 * Chapters not named in any group are listed last, so every part of the guide can be pinned.
 */
enum class Group(val tiles: List<String>) {
    DANGER(
        listOf(
            Tiles.card("first-60"), Tiles.card("air-alert"), Tiles.card("ballistic"), Tiles.card("drone"),
            Tiles.card("rubble"), Tiles.card("suspicious"), Tiles.card("home-fire"), Tiles.card("chemical"),
            Tiles.card("radiation"), Tiles.tool(Tool.ALARM),
        ),
    ),
    FIRST_AID(
        listOf(
            Tiles.card("bleeding"), Tiles.card("unconscious"), Tiles.card("burn"),
            Tiles.chapter("ch7"), Tiles.chapter("ch21"),
        ),
    ),
    FAMILY(listOf(Tiles.chapter("ch22"), Tiles.tool(Tool.PEOPLE), Tiles.tool(Tool.PLAN))),
    HOME_LIFE(
        listOf(
            Tiles.card("blackout"), Tiles.tool(Tool.HOME_LIFE), Tiles.tool(Tool.SUPPLIES), Tiles.chapter("ch20"),
            Tiles.chapter("ch8"), Tiles.chapter("ch9"), Tiles.chapter("ch12"), Tiles.chapter("ch10"),
            Tiles.chapter("ch16"),
        ),
    ),
    PREPARE(listOf(Tiles.tool(Tool.KIT), Tiles.chapter("ch2"), Tiles.chapter("ch30"), Tiles.chapter("planA"))),
    LEAVE(listOf(Tiles.card("leave-home"), Tiles.chapter("ch26"), Tiles.chapter("abroad"))),
    SAFETY(listOf(Tiles.chapter("ch5"), Tiles.chapter("ch17"), Tiles.chapter("ch18"), Tiles.chapter("ch28"), Tiles.chapter("ch19"))),
    MINE(
        listOf(
            Tiles.tool(Tool.MINE), Tiles.tool(Tool.MEDICAL), Tiles.tool(Tool.RESCUER), Tiles.tool(Tool.PRINT),
            Tiles.tool(Tool.SCAN),
        ),
    ),
    GUIDE(listOf(Tiles.tool(Tool.GUIDE), Tiles.tool(Tool.SEARCH))),
    ;

    companion object {
        /** The guide's chapters that no group names, in the guide's order. */
        fun rest(chapterIds: List<String>): List<String> {
            val named = entries.flatMap { it.tiles }.toSet()
            return chapterIds.map(Tiles::chapter).filter { it !in named }
        }
    }
}
