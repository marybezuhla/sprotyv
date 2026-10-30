package ua.sprotyv.content

import org.json.JSONArray

/**
 * "What would you do?": a short situation from a card, three answers, one right, each with why.
 * Every answer comes from the card's own steps ([step] names the one it practises), so practice
 * never says more than the card. No scores and nothing saved.
 */
data class Practice(
    val id: String,
    val card: String,
    val step: Int,
    val situation: String,
    val options: List<Option>,
) {
    data class Option(val text: String, val right: Boolean, val why: String)
}

object PracticeReader {
    private val ID = Regex("^[a-z0-9-]{1,40}$")

    /** Strict: three answers, exactly one right, each tied to a real step of a built-in card. */
    fun parse(json: String, cards: List<Card>): List<Practice> {
        val a = JSONArray(json)
        require(a.length() <= 400)
        val byId = cards.associateBy { it.id }
        val all = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val opts = o.getJSONArray("options")
            Practice(
                id = o.getString("id"),
                card = o.getString("card"),
                step = o.getInt("step"),
                situation = o.getString("situation"),
                options = (0 until opts.length()).map { j ->
                    val p = opts.getJSONObject(j)
                    Practice.Option(p.getString("text"), p.getBoolean("right"), p.getString("why"))
                },
            ).also { p ->
                require(ID.matches(p.id)) { "Bad id" }
                val card = requireNotNull(byId[p.card]) { "Unknown card" }
                require(p.step in card.steps.indices) { "Unknown step" }
                require(p.situation.length in 1..200) { "Bad situation" }
                require(p.options.size == 3 && p.options.count { it.right } == 1) { "Three answers, one right" }
                require(p.options.all { it.text.length in 1..160 && it.why.length in 1..200 }) { "Bad answer" }
            }
        }
        require(all.map { it.id }.toSet().size == all.size) { "Duplicate id" }
        return all
    }
}
