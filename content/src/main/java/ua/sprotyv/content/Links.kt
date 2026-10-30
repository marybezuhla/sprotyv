package ua.sprotyv.content

import org.json.JSONArray

/**
 * A trusted place to look in "Situation". Sprotyv opens it outside itself (the browser or the
 * source's own app) and fetches nothing. [app] is the source's own app, used when installed.
 */
data class Link(
    val id: String,
    val group: String,
    val kind: String,
    val title: String,
    val note: String,
    val url: String,
    val app: String,
    val phone: String,
    val checkedOn: String,
) {
    companion object {
        const val OFFICIAL = "official"
        const val VOLUNTEER = "volunteer"
        const val INDEPENDENT = "independent"
        val GROUPS = listOf("threats", "front", "help", "facts")
    }
}

object LinksReader {
    private val PACKAGE = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
    private val PHONE = Regex("^[0-9]{3,4}$")

    /** Strict: only https addresses, known groups and kinds, plain package names, short numbers. */
    fun parse(json: String): List<Link> {
        val a = JSONArray(json)
        require(a.length() <= 100)
        val links = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Link(
                id = o.getString("id"), group = o.getString("group"), kind = o.getString("kind"),
                title = o.getString("title"), note = o.getString("note"),
                url = o.optString("url", ""), app = o.optString("app", ""), phone = o.optString("phone", ""),
                checkedOn = o.getString("checkedOn"),
            ).also { l ->
                require(l.group in Link.GROUPS) { "Unknown group" }
                require(l.kind in setOf(Link.OFFICIAL, Link.VOLUNTEER, Link.INDEPENDENT)) { "Unknown kind" }
                require(l.url.isEmpty() || (l.url.startsWith("https://") && l.url.none { it.isWhitespace() })) { "Only https" }
                require(l.url.isNotEmpty() || l.phone.isNotEmpty()) { "Nothing to open" }
                require(l.app.isEmpty() || PACKAGE.matches(l.app)) { "Bad package" }
                require(l.phone.isEmpty() || PHONE.matches(l.phone)) { "Bad number" }
            }
        }
        require(links.map { it.id }.toSet().size == links.size) { "Duplicate link id" }
        return links
    }
}
