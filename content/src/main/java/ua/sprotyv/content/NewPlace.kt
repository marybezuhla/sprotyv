package ua.sprotyv.content

import org.json.JSONObject

/**
 * "New place": steps after leaving home for another region, and for those abroad
 * (built by scripts/content/new_place.py). Each with sources and the date checked.
 */
data class NewPlace(val title: String, val steps: List<Step>) {
    enum class Stage { DAYS, WEEK, MONTH, LATER, ABROAD }
    data class Source(val title: String, val url: String)
    data class Step(
        val id: String, val stage: Stage, val title: String, val text: String, val where: String,
        val route: String, val sources: List<Source>, val checked: String,
    )
}

object NewPlaceReader {
    private const val MAX_TEXT = 2_000
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    private val ROUTES = setOf("", "safe", "resistance", "directory")

    fun parse(json: String): NewPlace {
        val o = JSONObject(json)
        val list = o.getJSONArray("steps")
        require(list.length() in 1..80)
        return NewPlace(
            text(o, "title"),
            (0 until list.length()).map { i ->
                val s = list.getJSONObject(i)
                val src = s.getJSONArray("sources")
                require(src.length() <= 6)
                NewPlace.Step(
                    text(s, "id").also { require(it.matches(ID)) },
                    NewPlace.Stage.valueOf(text(s, "stage").uppercase()),
                    text(s, "title"), text(s, "text"), text(s, "where"),
                    text(s, "route").also { require(it in ROUTES) },
                    (0 until src.length()).map { j ->
                        val x = src.getJSONObject(j)
                        NewPlace.Source(text(x, "title"), text(x, "url").also { require(it.startsWith("https://")) })
                    },
                    text(s, "checked"),
                )
            },
        )
    }

    private fun text(o: JSONObject, name: String): String = o.getString(name).also { require(it.length <= MAX_TEXT) }
}
