package ua.sprotyv.content

import org.json.JSONObject

/**
 * The Rules of the Road of Ukraine word for word, the current edition (built by
 * scripts/content/pdr.py from zakon.rada.gov.ua). An official document: not protected by copyright.
 * A section is a run of blocks: a point of the rules, a subheading or a table.
 */
data class Pdr(
    val title: String, val act: String, val edition: String, val source: String, val retrieved: String, val license: String,
    val sections: List<Section>,
) {
    sealed interface Block
    data class Para(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block

    data class Section(val num: String, val title: String, val blocks: List<Block>)

    fun section(num: String): Section? = sections.firstOrNull { it.num == num }
}

object PdrReader {
    private const val MAX_TEXT = 6_000
    private val NUM = Regex("^\\d{1,2}$")

    fun parse(json: String): Pdr {
        val o = JSONObject(json)
        val s = o.getJSONArray("sections")
        require(s.length() in 1..40)
        return Pdr(
            text(o, "title"), text(o, "act"), text(o, "edition"), text(o, "source"), text(o, "retrieved"), text(o, "license"),
            (0 until s.length()).map { i ->
                val x = s.getJSONObject(i)
                val blocks = x.getJSONArray("blocks")
                require(blocks.length() in 1..600)
                Pdr.Section(
                    text(x, "num").also { require(it.matches(NUM)) }, text(x, "title"),
                    (0 until blocks.length()).map { j ->
                        val b = blocks.getJSONObject(j)
                        when {
                            b.has("p") -> Pdr.Para(text(b, "p"))
                            b.has("h") -> Pdr.Heading(text(b, "h"))
                            else -> {
                                val rows = b.getJSONArray("rows")
                                require(rows.length() in 1..60)
                                Pdr.Table((0 until rows.length()).map { r ->
                                    val cells = rows.getJSONArray(r)
                                    require(cells.length() in 1..6)
                                    (0 until cells.length()).map { c -> cells.getString(c).also { t -> require(t.length <= MAX_TEXT) } }
                                })
                            }
                        }
                    },
                )
            },
        )
    }

    private fun text(o: JSONObject, name: String): String = o.getString(name).also { require(it.length <= MAX_TEXT) }
}
