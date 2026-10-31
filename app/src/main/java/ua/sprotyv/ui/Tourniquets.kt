package ua.sprotyv.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * The tourniquets put on now: one or several people (a strike can wound many). Each has its number,
 * the time it went on and an optional short note ("girl, left arm") to tell people apart.
 *
 * Kept outside the encrypted data on purpose, so the time works at once from a card, even with
 * "Mine" locked (a decision on record). The notes are never shown in notifications or widgets, are
 * not in backups, and go when every tourniquet is handed over, or after a day.
 */
object Tourniquets {
    data class One(val n: Int, val at: Long, val note: String)

    private const val FILE = "tourniquet"
    private const val DAY = 24 * 3600_000L
    const val MAX = 12
    const val NOTE_MAX = 60

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The ones on now, the longest first; older than a day removed from the phone. */
    fun all(c: Context, now: Long = System.currentTimeMillis()): List<One> {
        val p = prefs(c)
        val list = read(c).filter { now - it.at in 0 until DAY }
        if (list.isEmpty() && (p.contains("list") || p.contains("at"))) p.edit().clear().apply()
        return list.sortedBy { it.at }
    }

    fun earliest(c: Context, now: Long = System.currentTimeMillis()): Long? = all(c, now).firstOrNull()?.at

    /** One more, from now. Returns its number, or null when the list is full. */
    fun add(c: Context, now: Long = System.currentTimeMillis()): Int? {
        val list = all(c, now)
        if (list.size >= MAX) return null
        val n = (list.maxOfOrNull { it.n } ?: 0) + 1
        write(c, list + One(n, now, ""))
        return n
    }

    fun note(c: Context, n: Int, text: String) =
        write(c, all(c).map { if (it.n == n) it.copy(note = clean(text)) else it })

    /** Put on earlier than the tap: the time moved back (never into the future, never past a day). */
    fun shift(c: Context, n: Int, minutes: Int, now: Long = System.currentTimeMillis()) =
        write(c, all(c, now).map { if (it.n == n) it.copy(at = (it.at + minutes * 60_000L).coerceIn(now - DAY + 60_000L, now)) else it })

    /** Handed over to the medics (or removed by them): gone with its note. */
    fun remove(c: Context, n: Int) = write(c, all(c).filter { it.n != n })

    fun clear(c: Context) = prefs(c).edit().clear().commit()

    private fun clean(s: String) = s.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(NOTE_MAX)

    private fun read(c: Context): List<One> {
        val p = prefs(c)
        // The single time kept before several were possible becomes number 1
        val old = p.getLong("at", 0L)
        val raw = p.getString("list", null)
        if (raw == null) return if (old > 0) listOf(One(1, old, "")) else emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until minOf(a.length(), MAX)).map { i ->
                val o = a.getJSONObject(i)
                One(o.getInt("n").coerceIn(1, 999), o.getLong("at"), clean(o.optString("note")))
            }.distinctBy { it.n }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(c: Context, list: List<One>) {
        val a = JSONArray()
        list.take(MAX).forEach { a.put(JSONObject().put("n", it.n).put("at", it.at).put("note", it.note)) }
        val e = prefs(c).edit().remove("at")
        if (list.isEmpty()) e.clear() else e.putString("list", a.toString())
        e.commit()
    }
}
