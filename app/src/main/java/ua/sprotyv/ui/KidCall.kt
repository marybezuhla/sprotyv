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
 * "Call my people" for a child, when the grown-up allows it without the lock on "My data": a copy
 * of only the names and numbers the child may call, encrypted with a key in the phone's secure
 * keystore that needs no fingerprint. Made from "Trusted people" whenever they are open, so it
 * follows changes; turned off, the copy is deleted.
 */
object KidCall {
    data class Entry(val name: String, val phone: String)

    private const val FILE = "kid_call"
    private const val ON = "on"
    private const val LIST = "list"
    private const val KEY = "sprotyv_kid_call"
    private const val MAX = 20

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isOn(c: Context): Boolean = prefs(c).getBoolean(ON, false)

    fun set(c: Context, on: Boolean) {
        if (on) prefs(c).edit().putBoolean(ON, true).apply()
        else prefs(c).edit().clear().apply()
    }

    /** Keeps the copy in step with "Trusted people": those marked for "I am safe" or as an emergency contact. */
    fun sync(c: Context, people: List<ua.sprotyv.personal.Person>) {
        if (!isOn(c)) return
        val family = sortedPeople(people).filter { (it.ice || it.safeList) && it.phone.isNotBlank() }.take(MAX)
        val json = JSONArray().apply { family.forEach { put(JSONObject().put("n", it.name.take(80)).put("p", it.phone.take(30))) } }.toString()
        // Not sealed: no copy at all, rather than an old one with someone since removed
        val sealed = seal(json) ?: return prefs(c).edit().remove(LIST).apply()
        prefs(c).edit().putString(LIST, sealed).apply()
    }

    /** Fills the copy now from "Trusted people" if My data is open (otherwise the next time it is). */
    suspend fun syncNow(c: Context) {
        if (!isOn(c)) return
        val people = ua.sprotyv.personal.PersonalStore.read { it.people().all() } ?: return
        sync(c, people)
    }

    /** The child's list; empty when off, not yet made, or it does not open (a new phone). */
    fun list(c: Context): List<Entry> {
        if (!isOn(c)) return emptyList()
        val json = open(prefs(c).getString(LIST, null) ?: return emptyList()) ?: return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until minOf(a.length(), MAX)).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val phone = o.optString("p")
                if (phone.isBlank()) null else Entry(o.optString("n"), phone)
            }
        } catch (e: Exception) { emptyList() }
    }

    @Volatile private var cached: SecretKey? = null

    @Synchronized
    private fun key(): SecretKey {
        cached?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY, null) as? SecretKey)?.let { cached = it; return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Not readable while the phone itself is locked (a seized phone after its first unlock)
                .setUnlockedDeviceRequired(true)
                .build(),
        )
        return gen.generateKey().also { cached = it }
    }

    private fun seal(text: String): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        "v1:" + Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    } catch (e: Exception) { cached = null; null }

    private fun open(sealed: String): String? {
        if (!sealed.startsWith("v1:")) return null
        return try {
            val bytes = Base64.decode(sealed.substring(3), Base64.NO_WRAP)
            if (bytes.size < 12 + 16) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }
}
