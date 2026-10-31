package ua.sprotyv.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * The first-aid procedures of order No. 441 of the Ministry of Health (09.03.2022), word for word
 * (built by scripts/content/moz441.py). An official document: not protected by copyright.
 * Numbering as in the order: points "1.", items "1)", sub-items "а)".
 */
data class Moz441(val title: String, val act: String, val source: String, val retrieved: String, val license: String, val procedures: List<Procedure>, val status: String = "", val order: Order? = null) {
    /** The order itself: the preamble, point 1 (the list of procedures) and points 2-5, the signature. */
    data class Order(val preamble: String, val approve: String, val points: List<String>, val signed: String)

    data class Item(val n: String, val text: String, val items: List<Item> = emptyList())
    data class Procedure(val n: Int, val title: String, val points: List<Item>) {
        /** "При масивній зовнішній кровотечі": the title without "надання домедичної допомоги постраждалим". */
        val short: String get() = title.removePrefix("надання домедичної допомоги ").removePrefix("постраждалим ")
            .replaceFirstChar { it.uppercase() }

        /** Every word of the procedure, for search. */
        val allText: String get() = buildString {
            append(title)
            fun add(i: Item) { append(' '); append(i.text); i.items.forEach(::add) }
            points.forEach(::add)
        }

        /** The point with the sequence of actions; null if the procedure has none by that name. */
        val actions: Item? get() = points.firstOrNull { it.text.startsWith("Послідовність дій") && it.items.isNotEmpty() }
    }

    fun procedure(n: Int): Procedure? = procedures.firstOrNull { it.n == n }
}

object Moz441Reader {
    private const val MAX_TEXT = 4_000

    fun parse(json: String): Moz441 {
        val o = JSONObject(json)
        val list = o.getJSONArray("procedures")
        require(list.length() in 1..60)
        return Moz441(
            text(o, "title"), text(o, "act"), text(o, "source"), text(o, "retrieved"), text(o, "license"),
            (0 until list.length()).map { i ->
                val p = list.getJSONObject(i)
                Moz441.Procedure(p.getInt("n"), text(p, "title"), items(p.getJSONArray("points"), 0))
            },
            o.optString("status").take(MAX_TEXT),
            o.optJSONObject("order")?.let { r ->
                val pts = r.getJSONArray("points")
                require(pts.length() <= 10)
                Moz441.Order(text(r, "preamble"), text(r, "approve"), (0 until pts.length()).map { pts.getString(it).also { s -> require(s.length <= MAX_TEXT) } }, text(r, "signed"))
            },
        )
    }

    private fun items(a: JSONArray, depth: Int): List<Moz441.Item> {
        require(depth < 3 && a.length() <= 40)
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            Moz441.Item(text(x, "n"), text(x, "text"), x.optJSONArray("items")?.let { items(it, depth + 1) } ?: emptyList())
        }
    }

    private fun text(o: JSONObject, name: String): String = o.getString(name).also { require(it.length <= MAX_TEXT) }
}
