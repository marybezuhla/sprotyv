package ua.sprotyv.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The tourniquets put on now: one or several people (a strike can wound many). Each has its number,
 * the time it went on and an optional short note ("girl, left arm") to tell people apart.
 *
 * The times are kept outside the encrypted data on purpose, so they work at once from a card, even
 * with "Mine" locked (a decision on record). The notes are about people, so they are encrypted with
 * a key of the phone's keystore that needs no unlocking (AES-GCM, Android Keystore). They are never
 * shown in notifications or widgets, are not in backups, and go when every tourniquet is handed
 * over, or after a day (an alarm clears them even when nothing is opened).
 */
object Tourniquets {
    data class One(val n: Int, val at: Long, val note: String, val told: Int = 0)

    private const val FILE = "tourniquet"
    private const val DAY = 24 * 3600_000L
    /** A clock moved a little back must not wipe tourniquets that are on. */
    private const val SKEW = 10 * 60_000L
    const val MAX = 12
    const val NOTE_MAX = 60

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun alive(t: One, now: Long) = now - t.at in -SKEW until DAY

    /** The ones on now, the longest first; anything older than a day is removed from the phone. */
    fun all(c: Context, now: Long = System.currentTimeMillis()): List<One> {
        val read = read(c)
        val list = read.filter { alive(it, now) }
        if (list.size != read.size || (list.isEmpty() && prefs(c).all.isNotEmpty())) write(c, list)
        return list.sortedBy { it.at }
    }

    fun earliest(c: Context, now: Long = System.currentTimeMillis()): Long? = all(c, now).firstOrNull()?.at

    /** When the oldest one turns a day old and must be cleared; null when none is on. */
    fun expires(c: Context, now: Long = System.currentTimeMillis()): Long? = all(c, now).minOfOrNull { it.at + DAY }

    /** One more, from now. Returns its number, or null when the list is full. Numbers are never reused until all are gone. */
    fun add(c: Context, now: Long = System.currentTimeMillis()): Int? {
        val list = all(c, now)
        if (list.size >= MAX) return null
        val p = prefs(c)
        val n = maxOf(p.getInt("next", 1), (list.maxOfOrNull { it.n } ?: 0) + 1).coerceAtMost(9_999)
        write(c, list + One(n, now, ""), next = n + 1)
        return n
    }

    /** Written while typing: saved without waiting for the disk. */
    fun note(c: Context, n: Int, text: String) =
        write(c, all(c).map { if (it.n == n) it.copy(note = clean(text)) else it }, now = false)

    /** Put on earlier than the tap: the time moved back (never into the future, never past a day). */
    fun shift(c: Context, n: Int, minutes: Int, now: Long = System.currentTimeMillis()) =
        write(c, all(c, now).map { if (it.n == n) it.copy(at = (it.at + minutes * 60_000L).coerceIn(now - DAY + 60_000L, now)) else it })

    /** Handed over to the medics (or removed by them): gone with its note. */
    fun remove(c: Context, n: Int) = write(c, all(c).filter { it.n != n })

    /**
     * The ones whose next full hour has come and was not yet said, marked as said.
     * However late the alarm comes, each hour is said once, and none is missed.
     */
    fun dueHours(c: Context, now: Long = System.currentTimeMillis()): List<Pair<One, Int>> {
        val list = all(c, now)
        val due = list.mapNotNull { t -> ((now - t.at) / (3600_000L)).toInt().takeIf { it >= 1 && it > t.told }?.let { t to it } }
        if (due.isNotEmpty()) write(c, list.map { t -> due.firstOrNull { it.first.n == t.n }?.let { t.copy(told = it.second) } ?: t })
        return due
    }

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
                One(o.getInt("n").coerceIn(1, 9_999), o.getLong("at"), clean(open(o.optString("note"))), o.optInt("told", 0).coerceIn(0, 24))
            }.distinctBy { it.n }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(c: Context, list: List<One>, next: Int? = null, now: Boolean = true) {
        val e = prefs(c).edit().remove("at")
        if (list.isEmpty()) {
            e.clear()
        } else {
            val a = JSONArray()
            list.take(MAX).forEach { a.put(JSONObject().put("n", it.n).put("at", it.at).put("note", seal(it.note)).put("told", it.told)) }
            e.putString("list", a.toString())
            next?.let { e.putInt("next", it) }
        }
        if (now) e.commit() else e.apply()
    }

    // ---- the notes, sealed with a keystore key that needs no unlocking

    private const val KEY = "sprotyv_tourniquet_notes"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    /** Empty stays empty; otherwise "v1:" + base64(iv + ciphertext). A note that cannot be sealed is not kept. */
    private fun seal(note: String): String {
        if (note.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val out = cipher.iv + cipher.doFinal(note.toByteArray(Charsets.UTF_8))
            "v1:" + Base64.encodeToString(out, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    /** A note that does not open (a new phone, a damaged file) is simply empty. */
    private fun open(sealed: String): String {
        if (!sealed.startsWith("v1:")) return ""
        return try {
            val bytes = Base64.decode(sealed.substring(3), Base64.NO_WRAP)
            if (bytes.size < 12 + 16) return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
