package ua.sprotyv.content

import java.security.MessageDigest
import kotlin.math.ceil

/** A group of the emergency kit list ("Документи та гроші") and its items, as the guide has them. */
data class KitGroup(val title: String, val items: List<KitItem>)

/** One kit item. [key] stays the same while the item's text in the guide stays the same. */
data class KitItem(val key: String, val text: String)

object Kit {
    /** The guide section with the full kit list. */
    const val SECTION = "2.2"

    /**
     * The kit list straight from the guide (headings and the check lists under them), so the app
     * never keeps a second, drifting copy of it.
     */
    fun groups(guide: Guide): List<KitGroup> {
        val section = guide.section(SECTION)?.second ?: return emptyList()
        val groups = ArrayList<KitGroup>()
        var title = section.title
        for (b in section.blocks) when (b) {
            is Block.Heading -> title = plain(b.text)
            is Block.Items -> groups += KitGroup(title, b.items.map { KitItem(key(it.text), it.text) })
            else -> {}
        }
        return groups
    }

    fun key(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plain(text).trim().toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

/** One line of the home supplies: what, how much for the household and period, and a note. */
data class SupplyLine(val what: String, val amount: String, val note: String)

/**
 * Home supplies scaled from the guide (2.5): water 4 litres per person per day, and the food
 * table, which the guide gives for one person for two weeks. Amounts that are not numbers
 * ("за звичкою") are shown as they are.
 */
object Supplies {
    const val SECTION = "2.5"
    const val WATER_PER_PERSON_DAY = 4.0
    private const val TABLE_DAYS = 14.0
    val LEVELS = listOf(3, 14, 30)

    fun waterLitres(people: Int, days: Int): Int = ceil(WATER_PER_PERSON_DAY * people * days).toInt()

    fun food(guide: Guide, people: Int, days: Int): List<SupplyLine> {
        val table = guide.section(SECTION)?.second?.blocks?.filterIsInstance<Block.Table>()?.firstOrNull() ?: return emptyList()
        val factor = people * days / TABLE_DAYS
        return table.rows.filter { !it.head && it.cells.size >= 2 }.map { r ->
            SupplyLine(plain(r.cells[0]), scale(plain(r.cells[1]), factor), plain(r.cells.getOrElse(2) { "" }))
        }
    }

    private val AMOUNT = Regex("^(\\d+(?:[.,]\\d+)?)(?:\\s*[–-]\\s*(\\d+(?:[.,]\\d+)?))?\\s+(.+)$")

    /**
     * "4–5 кг" × 2 → "8–10 кг"; "за звичкою" stays. Weights and volumes are rounded up to one
     * decimal; things counted in pieces (cans, packs) up to whole pieces, with the right word form.
     */
    fun scale(amount: String, factor: Double): String {
        val m = AMOUNT.matchEntire(amount.trim()) ?: return amount
        val lo = m.groupValues[1].replace(',', '.').toDouble() * factor
        val hi = m.groupValues[2].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDouble()?.times(factor)
        val unit = m.groupValues[3]
        val piece = PIECES.entries.firstOrNull { unit.startsWith(it.key) }?.value
        if (piece != null) {
            val a = ceil(lo - 1e-9).toLong().coerceAtLeast(1)
            val b = hi?.let { ceil(it - 1e-9).toLong().coerceAtLeast(a) } ?: a
            return if (b == a) "$a ${piece.form(a)}" else "$a–$b ${piece.form(b)}"
        }
        return if (hi == null) "${fmt(lo)} $unit" else "${fmt(lo)}–${fmt(hi)} $unit"
    }

    /** Ukrainian forms for counted things: 1 банка, 2 банки, 5 банок. */
    class Forms(private val one: String, private val few: String, private val many: String) {
        fun form(n: Long): String {
            val d = n % 10
            val dd = n % 100
            return when {
                d == 1L && dd != 11L -> one
                d in 2..4 && dd !in 12..14 -> few
                else -> many
            }
        }
    }

    private val PIECES = mapOf(
        "бан" to Forms("банка", "банки", "банок"),
        "упаков" to Forms("упаковка", "упаковки", "упаковок"),
        "шт" to Forms("шт.", "шт.", "шт."),
    )

    private fun fmt(x: Double): String {
        // The small margin keeps 8.0000001 (a rounding artefact) from becoming 8,1
        val up = ceil(x * 10 - 1e-9) / 10
        return if (up == Math.floor(up)) up.toLong().toString() else up.toString().replace('.', ',')
    }
}

/**
 * What the owner has at home: the household and goal, litres of water, which food lines are in
 * place and until when they keep. Kept as one encrypted setting; read leniently, so a damaged or
 * older value never stops the screen, it only loses what cannot be read.
 */
data class MySupplies(
    val people: Int = 1,
    val days: Int = Supplies.LEVELS.first(),
    val water: Int = 0,
    val waterUntil: String = "",
    val have: Set<String> = emptySet(),
    /** Line key ("water" for water) to the last month it keeps, "2027-03". */
    val until: Map<String, String> = emptyMap(),
) {
    /** Litres the goal needs. */
    val waterGoal: Int get() = Supplies.waterLitres(people, days)

    /** Whole days the water lasts for this household. */
    val waterDays: Int get() = (water / (Supplies.WATER_PER_PERSON_DAY * people)).toInt()

    /** Lines whose time runs out by the end of next month (or has run out), soonest first. */
    fun expiring(now: java.time.YearMonth): List<Pair<String, java.time.YearMonth>> =
        (until + (if (waterUntil.isNotEmpty()) mapOf(WATER to waterUntil) else emptyMap()))
            .mapNotNull { (k, v) -> month(v)?.let { k to it } }
            .filter { (k, m) -> (k == WATER || k in have) && !m.isAfter(now.plusMonths(1)) }
            .sortedBy { it.second }

    fun toJson(): String = org.json.JSONObject()
        .put("people", people).put("days", days).put("water", water).put("waterUntil", waterUntil)
        .put("have", org.json.JSONArray(have.sorted()))
        .put("until", org.json.JSONObject(until.toSortedMap() as Map<*, *>))
        .toString()

    companion object {
        const val WATER = "water"
        private val KEY = Regex("^[a-z0-9]{1,16}$")

        fun month(s: String): java.time.YearMonth? = try {
            java.time.YearMonth.parse(s)
        } catch (e: Exception) {
            null
        }

        fun fromJson(s: String?): MySupplies {
            if (s.isNullOrBlank()) return MySupplies()
            return try {
                val o = org.json.JSONObject(s)
                val have = o.optJSONArray("have")?.let { a -> (0 until minOf(a.length(), 200)).map { a.optString(it) } }
                    ?.filter { KEY.matches(it) }?.toSet() ?: emptySet()
                val u = o.optJSONObject("until")
                val until = u?.keys()?.asSequence()?.take(200)
                    ?.mapNotNull { k -> u.optString(k).takeIf { KEY.matches(k) && month(it) != null }?.let { k to it } }?.toMap() ?: emptyMap()
                MySupplies(
                    people = o.optInt("people", 1).coerceIn(1, 20),
                    days = o.optInt("days", Supplies.LEVELS.first()).takeIf { it in Supplies.LEVELS } ?: Supplies.LEVELS.first(),
                    water = o.optInt("water", 0).coerceIn(0, 2000),
                    waterUntil = o.optString("waterUntil", "").takeIf { month(it) != null } ?: "",
                    have = have,
                    until = until,
                )
            } catch (e: Exception) {
                MySupplies()
            }
        }
    }
}
