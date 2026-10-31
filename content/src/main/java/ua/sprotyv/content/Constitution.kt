package ua.sprotyv.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Constitution of Ukraine word for word, the current edition (built by
 * scripts/content/constitution.py from zakon.rada.gov.ua). An official document: not protected by
 * copyright. The editorial notes of the edition are kept apart from the text.
 */
data class Constitution(
    val title: String, val act: String, val edition: String, val source: String, val retrieved: String, val license: String,
    val preamble: List<String>, val sections: List<Section>, val closing: List<String>,
) {
    data class Article(val num: String, val parts: List<String>, val notes: List<String>) {
        val allText: String get() = "Стаття $num. " + parts.joinToString(" ")
    }
    data class Section(val num: String, val title: String, val articles: List<Article>, val text: List<String>, val notes: List<String>)

    fun article(num: String): Pair<Section, Article>? {
        sections.forEach { s -> s.articles.firstOrNull { it.num == num }?.let { return s to it } }
        return null
    }

    fun section(num: String): Section? = sections.firstOrNull { it.num == num }
}

object ConstitutionReader {
    private const val MAX_TEXT = 6_000
    private val ARTICLE = Regex("^\\d{1,3}(-\\d)?$")
    private val SECTION = Regex("^[IVXL]{1,6}$")

    fun parse(json: String): Constitution {
        val o = JSONObject(json)
        val s = o.getJSONArray("sections")
        require(s.length() in 1..20)
        val pre = o.getJSONArray("preamble")
        return Constitution(
            text(o, "title"), text(o, "act"), text(o, "edition"), text(o, "source"), text(o, "retrieved"), text(o, "license"),
            // The preamble's own notes are left out: the edition's history, not its words
            (0 until pre.length()).mapNotNull { pre.getJSONObject(it).optString("text").takeIf(String::isNotBlank)?.also { t -> require(t.length <= MAX_TEXT) } },
            (0 until s.length()).map { i ->
                val x = s.getJSONObject(i)
                val num = text(x, "num").also { require(it.matches(SECTION)) }
                val arts = x.getJSONArray("articles")
                require(arts.length() <= 80)
                Constitution.Section(
                    num, text(x, "title"),
                    (0 until arts.length()).map { j ->
                        val a = arts.getJSONObject(j)
                        Constitution.Article(text(a, "num").also { require(it.matches(ARTICLE)) }, strings(a.getJSONArray("parts")), strings(a.getJSONArray("notes")))
                    },
                    strings(x.getJSONArray("text")), strings(x.getJSONArray("notes")),
                )
            },
            strings(o.getJSONArray("closing")),
        )
    }

    private fun strings(a: JSONArray): List<String> {
        require(a.length() <= 120)
        return (0 until a.length()).map { a.getString(it).also { s -> require(s.length <= MAX_TEXT) } }
    }

    private fun text(o: JSONObject, name: String): String = o.getString(name).also { require(it.length <= MAX_TEXT) }
}
