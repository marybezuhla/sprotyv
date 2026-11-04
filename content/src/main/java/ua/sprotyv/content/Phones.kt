package ua.sprotyv.content

import org.json.JSONArray

/**
 * Phone numbers of official services and reputable helplines, by category, each with where it
 * was checked and when. Tapping one opens the dialler with the number; nothing is called by itself.
 */
data class Phone(
    val id: String,
    val category: String,
    val title: String,
    val note: String,
    /** As dialled: digits, with a leading + only for a number to call from abroad ("+380442841915"). */
    val number: String,
    val source: String,
    val checkedOn: String,
) {
    companion object {
        /** In the order shown: the most urgent first. */
        val CATEGORIES = listOf("emergency", "evacuation", "mind", "violence", "children", "health", "security", "veterans", "state")

        /** Each id starts with its category's mark, so a pinned number's category is known from its id alone. */
        private val PREFIX = mapOf(
            "emergency" to "e-", "evacuation" to "ev-", "mind" to "m-", "violence" to "v-", "children" to "c-",
            "health" to "h-", "security" to "s-", "veterans" to "vt-", "state" to "st-",
        )

        /** Hidden in trip mode, like the same links elsewhere: defence, captivity and veterans' lines are not for a checked phone. */
        val TRIP_HIDES = setOf("security", "veterans")

        fun categoryOf(id: String): String? = PREFIX.entries.firstOrNull { id.startsWith(it.value) }?.key

        fun prefixOf(category: String): String = PREFIX.getValue(category)

        /** A number hidden in trip mode, by its id (an unknown one is hidden too, to be sure). */
        fun tripHides(id: String): Boolean = categoryOf(id)?.let { it in TRIP_HIDES } ?: true
    }
}

object PhonesReader {
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val NUMBER = Regex("^(\\+380[0-9]{9}|[0-9]{3,10})$")
    private val SOURCE = Regex("^https://[a-z0-9.-]+(/[^\\s]*)?$")

    /** Strict: known categories, plain numbers, an https source and a date for every number. */
    fun parse(json: String): List<Phone> {
        val a = JSONArray(json)
        require(a.length() in 1..300)
        val phones = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Phone(o.getString("id"), o.getString("category"), o.getString("title"), o.getString("note"), o.getString("number"), o.getString("source"), o.getString("checkedOn")).also { p ->
                require(ID.matches(p.id) && p.category in Phone.CATEGORIES && Phone.categoryOf(p.id) == p.category) { "Bad id or category" }
                require(NUMBER.matches(p.number)) { "Bad number" }
                require(SOURCE.matches(p.source) && DATE.matches(p.checkedOn)) { "Bad source or date" }
                require(p.title.length in 1..80 && p.note.length in 1..240) { "Bad text" }
            }
        }
        require(phones.map { it.id }.toSet().size == phones.size) { "Repeated id" }
        return phones
    }

    /** How a number is shown: short ones as they are, 0 800 and others in groups for reading aloud. */
    fun shown(number: String): String = when {
        number.length <= 6 -> number
        number.startsWith("0800") && number.length == 10 -> "0 800 " + number.substring(4, 7) + " " + number.substring(7)
        number.startsWith("+380") && number.length == 13 -> "+380 " + number.substring(4, 6) + " " + number.substring(6, 9) + " " + number.substring(9, 11) + " " + number.substring(11)
        number.length == 10 -> number.substring(0, 3) + " " + number.substring(3, 6) + " " + number.substring(6, 8) + " " + number.substring(8)
        else -> number
    }
}
