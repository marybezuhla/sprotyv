package ua.sprotyv.content

import org.json.JSONArray
import org.json.JSONObject

/**
 * An action card: "what to do now" in one situation, one step per screen.
 * Every card names its source and the date it was checked; [reviewedBy] is empty until a
 * specialist has reviewed it, and the screen then says so plainly.
 */
data class Card(
    val id: String,
    val title: String,
    val steps: List<Step>,
    val source: String,
    val checkedOn: String,
    val reviewedBy: String,
    /** Guide sections this card comes from ("30.5", "7.2"), first is opened by "In detail". */
    val guide: List<String> = emptyList(),
    /** The service to call from this card, or null. Always one of [Service]. */
    val call: Service? = null,
) {
    init {
        require(id.matches(ID)) { "Bad card id" }
        require(title.isNotBlank()) { "Empty title" }
        require(steps.isNotEmpty()) { "A card needs steps" }
        require(source.isNotBlank()) { "A card needs a source" }
        require(checkedOn.matches(DATE)) { "Bad check date" }
        guide.forEach { require(it.matches(SECTION)) { "Bad guide reference" } }
    }

    companion object {
        private val ID = Regex("[a-z0-9-]{1,40}")
        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val SECTION = Regex("[0-9А-ЯҐЄІЇ]{1,3}(\\.[0-9]{1,3})?")
    }
}

/**
 * One step. [action] starts with a verb and stays short (27/9/3 rule: about 27 words, one idea);
 * [why] is the second level, shown only when asked. [figure] names a guide section whose picture
 * shows the step (empty when none); [icon] names a pictogram drawn for the step (empty when none);
 * [wait] is the minutes the step says to wait, which the card can count down (0 when none).
 */
data class Step(val action: String, val why: String, val figure: String = "", val icon: String = "", val wait: Int = 0) {
    init {
        require(action.isNotBlank()) { "Empty step" }
        require(action.length <= MAX_ACTION) { "Step too long for a stressed reader" }
        require(icon.isEmpty() || Regex("^[a-z_]{1,30}$").matches(icon)) { "Bad pictogram name" }
        require(wait in 0..240) { "Bad wait" }
    }

    companion object {
        const val MAX_ACTION = 200
    }
}

/** Reads the built-in cards. The file ships inside the app, so this never touches the network. */
object CardsReader {
    const val MAX_CARDS = 200
    const val MAX_STEPS = 12

    fun parse(json: String): List<Card> {
        val array = JSONArray(json)
        require(array.length() <= MAX_CARDS) { "Too many cards" }
        val cards = (0 until array.length()).map { card(array.getJSONObject(it)) }
        require(cards.map { it.id }.toSet().size == cards.size) { "Duplicate card id" }
        return cards
    }

    private fun card(o: JSONObject): Card {
        val stepsJson = o.getJSONArray("steps")
        require(stepsJson.length() <= MAX_STEPS) { "Too many steps" }
        val steps = (0 until stepsJson.length()).map {
            val s = stepsJson.getJSONObject(it)
            Step(s.getString("action"), s.optString("why", ""), s.optString("figure", ""), s.optString("icon", ""), s.optInt("wait", 0))
        }
        val guideJson = o.optJSONArray("guide") ?: JSONArray()
        val call = o.optString("call", "")
        return Card(
            id = o.getString("id"),
            title = o.getString("title"),
            steps = steps,
            source = o.getString("source"),
            checkedOn = o.getString("checkedOn"),
            reviewedBy = o.optString("reviewedBy", ""),
            guide = (0 until guideJson.length()).map { guideJson.getString(it) },
            call = if (call.isEmpty()) null else requireNotNull(Service.entries.firstOrNull { it.number == call }) { "Unknown service" },
        )
    }
}
