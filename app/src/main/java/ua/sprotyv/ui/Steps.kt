package ua.sprotyv.ui

import org.json.JSONArray
import org.json.JSONObject
import ua.sprotyv.personal.Opened
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting

const val KEY_STEPS = "steps"

/**
 * What the owner marked or counted for "My shields", kept encrypted in "Mine" and in the backup:
 * big and small steps ticked on their own word, a few counts with "+1" (blood given, volunteer
 * shifts, people taught), the practice themes passed without a mistake and the situations solved.
 */
data class Steps(
    val marked: Set<String> = emptySet(),
    val counts: Map<String, Int> = emptyMap(),
    val topics: Set<String> = emptySet(),
    val solved: Set<String> = emptySet(),
) {
    fun json(): String = JSONObject()
        .put("marked", JSONArray(marked.sorted()))
        .put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
        .put("topics", JSONArray(topics.sorted()))
        .put("solved", JSONArray(solved.sorted()))
        .toString()

    companion object {
        val MARKS = listOf("rescue", "left", "newlife", "volunteer", "defence", "donate", "twosources")
        val COUNTS = listOf("blood", "shifts", "taught")
        private val ID = Regex("^[a-z0-9:-]{1,60}$")

        /** Anything odd is dropped; numbers kept in sane bounds. */
        fun parse(s: String?): Steps {
            if (s.isNullOrBlank() || s.length > 20_000) return Steps()
            return try {
                val o = JSONObject(s)
                fun set(name: String, max: Int, ok: (String) -> Boolean): Set<String> {
                    val a = o.optJSONArray(name) ?: JSONArray()
                    return (0 until minOf(a.length(), max)).map { a.optString(it) }.filter { ID.matches(it) && ok(it) }.toSet()
                }
                val c = o.optJSONObject("counts") ?: JSONObject()
                Steps(
                    marked = set("marked", 20) { it in MARKS },
                    counts = COUNTS.associateWith { c.optInt(it, 0).coerceIn(0, 10_000) }.filterValues { it > 0 },
                    topics = set("topics", 30) { true },
                    solved = set("solved", 300) { true },
                )
            } catch (e: Exception) {
                Steps()
            }
        }

        /** A change, saved only while "Mine" is open (practice can be done with it locked: then nothing is kept). */
        fun change(f: (Steps) -> Steps) {
            if (PersonalStore.state.value !is Opened.Ready) return
            PersonalStore.save("setting:$KEY_STEPS:" + java.util.UUID.randomUUID()) { db ->
                db.settings().put(Setting(KEY_STEPS, f(parse(db.settings().get(KEY_STEPS))).json()))
            }
        }
    }
}
