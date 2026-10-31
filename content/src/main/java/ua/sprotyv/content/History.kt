package ua.sprotyv.content

import org.json.JSONObject

/**
 * "Why resist": short true stories, each with its sources and the date they were checked
 * (built by scripts/content/history.py).
 */
data class History(val title: String, val stories: List<Story>) {
    enum class Kind { STOOD, LOST, WORD, SUMMARY }
    data class Source(val title: String, val url: String)
    data class Story(
        val id: String, val kind: Kind, val title: String, val time: String,
        val happened: String, val outcome: String, val lesson: String, val sources: List<Source>, val checked: String,
    )

    fun story(id: String): Story? = stories.firstOrNull { it.id == id }
}

object HistoryReader {
    private const val MAX_TEXT = 2_000
    private val ID = Regex("^[a-z0-9-]{1,40}$")

    fun parse(json: String): History {
        val o = JSONObject(json)
        val list = o.getJSONArray("stories")
        require(list.length() in 1..80)
        return History(
            text(o, "title"),
            (0 until list.length()).map { i ->
                val s = list.getJSONObject(i)
                val src = s.getJSONArray("sources")
                require(src.length() <= 6)
                History.Story(
                    text(s, "id").also { require(it.matches(ID)) },
                    History.Kind.valueOf(text(s, "kind").uppercase()),
                    text(s, "title"), text(s, "when"), text(s, "happened"), text(s, "outcome"), text(s, "lesson"),
                    (0 until src.length()).map { j ->
                        val x = src.getJSONObject(j)
                        // Only web addresses: they are opened outside Sprotyv, never inside
                        History.Source(text(x, "title"), text(x, "url").also { require(it.startsWith("https://") || it.startsWith("http://")) })
                    },
                    text(s, "checked"),
                )
            },
        )
    }

    private fun text(o: JSONObject, name: String): String = o.getString(name).also { require(it.length <= MAX_TEXT) }
}
