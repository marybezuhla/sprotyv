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

    /** "4–5 кг" × 2 → "8–10 кг"; "за звичкою" stays. Rounded up to one decimal. */
    fun scale(amount: String, factor: Double): String {
        val m = AMOUNT.matchEntire(amount.trim()) ?: return amount
        val lo = m.groupValues[1].replace(',', '.').toDouble() * factor
        val hi = m.groupValues[2].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDouble()?.times(factor)
        val unit = m.groupValues[3]
        return if (hi == null) "${fmt(lo)} $unit" else "${fmt(lo)}–${fmt(hi)} $unit"
    }

    private fun fmt(x: Double): String {
        // The small margin keeps 8.0000001 (a rounding artefact) from becoming 8,1
        val up = ceil(x * 10 - 1e-9) / 10
        return if (up == Math.floor(up)) up.toLong().toString() else up.toString().replace('.', ',')
    }
}
