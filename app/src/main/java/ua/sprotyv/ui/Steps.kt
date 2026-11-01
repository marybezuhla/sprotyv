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
    /** Each path test passed: the day (epoch day) it was last passed and how many times. */
    val checks: Map<String, Pair<Long, Int>> = emptyMap(),
) {
    /**
     * A gentle repeat: a week after the first pass, then a month after each next one. Spaced
     * retrieval keeps knowledge longest (Cepeda et al. 2006); only offered, never pushed.
     */
    fun repeatDue(step: String, today: Long): Boolean {
        val (day, times) = checks[step] ?: return false
        return today - day >= (if (times <= 1) 7 else 30)
    }

    fun json(): String = JSONObject()
        .put("marked", JSONArray(marked.sorted()))
        .put("counts", JSONObject().apply { counts.forEach { (k, v) -> put(k, v) } })
        .put("topics", JSONArray(topics.sorted()))
        .put("solved", JSONArray(solved.sorted()))
        .put("checks", JSONObject().apply { checks.forEach { (k, v) -> put(k, JSONArray(listOf(v.first, v.second))) } })
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
                    checks = (o.optJSONObject("checks") ?: JSONObject()).let { ch ->
                        ch.keys().asSequence().take(20).filter { ID.matches(it) }.mapNotNull { k ->
                            ch.optJSONArray(k)?.let { a -> (a.optLong(0, 0L) to a.optInt(1, 0)).takeIf { it.first in 1..200_000 && it.second in 1..1_000 } }?.let { k to it }
                        }.toMap()
                    },
                )
            } catch (e: Exception) {
                Steps()
            }
        }

        /**
         * Practice is done outside "Mine", where the encrypted data is closed: what was solved waits
         * here (only practice ids, no personal words) and moves into "Mine" the next time it opens.
         */
        fun practice(context: android.content.Context, solved: String? = null, topic: String? = null) {
            if (PersonalStore.state.value is Opened.Ready) {
                change { s -> s.copy(solved = if (solved != null) (s.solved + solved).take(300).toSet() else s.solved, topics = if (topic != null) s.topics + topic else s.topics) }
                return
            }
            val p = context.applicationContext.getSharedPreferences("practice_wait", android.content.Context.MODE_PRIVATE)
            fun add(key: String, v: String?) { if (v != null && ID.matches(v)) p.edit().putStringSet(key, (p.getStringSet(key, emptySet()).orEmpty() + v).take(300).toSet()).apply() }
            add("solved", solved); add("topics", topic)
        }

        /** Moves what waited into "Mine" (called when it opens), then forgets it. */
        fun flush(context: android.content.Context) {
            val p = context.applicationContext.getSharedPreferences("practice_wait", android.content.Context.MODE_PRIVATE)
            val solved = p.getStringSet("solved", emptySet()).orEmpty().filter { ID.matches(it) }
            val topics = p.getStringSet("topics", emptySet()).orEmpty().filter { ID.matches(it) }
            if (solved.isEmpty() && topics.isEmpty()) return
            change { s -> s.copy(solved = (s.solved + solved).take(300).toSet(), topics = (s.topics + topics).take(30).toSet()) }
            p.edit().clear().apply()
        }

        /** A change, saved only while "Mine" is open. */
        fun change(f: (Steps) -> Steps) {
            if (PersonalStore.state.value !is Opened.Ready) return
            PersonalStore.save("setting:$KEY_STEPS:" + java.util.UUID.randomUUID()) { db ->
                db.settings().put(Setting(KEY_STEPS, f(parse(db.settings().get(KEY_STEPS))).json()))
            }
        }
    }
}
