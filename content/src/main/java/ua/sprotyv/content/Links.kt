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
    /** Plain warnings shown with the link: [UNKNOWN_AUTHOR], [ADS], [BETA], [COLLECTS_DATA]. */
    val flags: List<String> = emptyList(),
) {
    companion object {
        const val OFFICIAL = "official"
        const val VOLUNTEER = "volunteer"
        const val INDEPENDENT = "independent"
        /** A civic organisation (громадська організація), charity or cooperative. */
        const val NGO = "ngo"
        /** "Resistance": where to learn, service for adults, resilience. Shown only in that section. */
        val RESIST = listOf("learn", "service", "resilience", "support")
        /** "Where to turn": help services for everyday life after and around the war. */
        val DIRECTORY = listOf("idp", "money", "docs", "kids", "veterans", "health", "care")
        val GROUPS = listOf("threats", "front", "help", "evac", "search", "mind", "rights", "facts", "power", MAPS) + DIRECTORY + RESIST
        const val POWER = "power"
        /** Open map apps with detailed offline maps: in "Situation" and on the map screen. */
        const val MAPS = "maps"
        const val UNKNOWN_AUTHOR = "unknown_author"
        const val ADS = "ads"
        const val BETA = "beta"
        const val COLLECTS_DATA = "collects_data"
        const val PAID = "paid"
        const val PAUSED = "paused"
        val FLAGS = setOf(UNKNOWN_AUTHOR, ADS, BETA, COLLECTS_DATA, PAID, PAUSED)
    }
}

object LinksReader {
    private val PACKAGE = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
    private val PHONE = Regex("^[0-9]{3,10}$")
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    // https://host[/path]: no user part, no port, no odd characters, so the host shown is the host opened
    private val URL = Regex("^https://([a-z0-9-]+(\\.[a-z0-9-]+)+)(/[A-Za-z0-9._~/?=&%-]*)?$")

    /** The host of a checked address, shown to the person as where the link leads. */
    fun host(url: String): String = URL.matchEntire(url)?.groupValues?.get(1) ?: ""

    /** Strict: only https addresses, known groups and kinds, plain package names, short numbers. */
    fun parse(json: String): List<Link> {
        val a = JSONArray(json)
        require(a.length() <= 400)
        val links = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Link(
                id = o.getString("id"), group = o.getString("group"), kind = o.getString("kind"),
                title = o.getString("title"), note = o.getString("note"),
                url = o.optString("url", ""), app = o.optString("app", ""), phone = o.optString("phone", ""),
                checkedOn = o.getString("checkedOn"),
                flags = o.optJSONArray("flags")?.let { f -> (0 until f.length()).map { f.getString(it) } } ?: emptyList(),
            ).also { l ->
                require(l.flags.all { it in Link.FLAGS } && l.flags.size <= 4) { "Unknown flag" }
                require(l.group in Link.GROUPS) { "Unknown group" }
                require(l.kind in setOf(Link.OFFICIAL, Link.VOLUNTEER, Link.INDEPENDENT, Link.NGO)) { "Unknown kind" }
                require(ID.matches(l.id) && DATE.matches(l.checkedOn)) { "Bad id or date" }
                require(l.title.length in 1..80 && l.note.length in 1..200) { "Bad text" }
                require(l.url.isEmpty() || URL.matches(l.url)) { "Only plain https addresses" }
                // No Telegram links: a decision on record (the service is partly Russian)
                require(host(l.url).let { it != "t.me" && !it.endsWith(".t.me") && !it.contains("telegram") }) { "No Telegram" }
                require(l.url.isNotEmpty() || l.phone.isNotEmpty()) { "Nothing to open" }
                require(l.app.isEmpty() || PACKAGE.matches(l.app)) { "Bad package" }
                require(l.phone.isEmpty() || PHONE.matches(l.phone)) { "Bad number" }
            }
        }
        require(links.map { it.id }.toSet().size == links.size) { "Duplicate link id" }
        return links
    }
}
