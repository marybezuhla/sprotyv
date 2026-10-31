package ua.sprotyv.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import ua.sprotyv.home.Mode

/** A page the owner saved: its address and the name the owner gave it. */
data class Bookmark(val url: String, val title: String, val at: Long)

/**
 * The owner's own bookmarks: pages shared from a browser ("Share" → Sprotyv) or pasted, kept with
 * the rest of "Mine", encrypted, and carried by the encrypted copy. Only https pages are kept, and
 * never Telegram (the service is partly Russian). Everything that arrives from another app is
 * checked again here, and again whenever the list is read back.
 */
object Bookmarks {
    /** Several settings, each well under the copy's limit for one value, so any list fits a copy. */
    val KEYS = (0 until 10).map { "bookmarks_$it" }
    const val MAX = 200
    private const val PAGE_CHARS = 16_000
    private const val MAX_URL = 2_000
    private const val MAX_TITLE = 150

    /** Telegram's own addresses, with any subdomain. */
    private val TELEGRAM = listOf(
        "t.me", "telegram.me", "telegram.org", "telegram.dog", "telegra.ph", "telesco.pe",
        "telegram.space", "tdesktop.com", "fragment.com",
    )
    private val URL = Regex("""(?i)\b(?:https?|tg)://[^\s<>"'«»]+""")

    sealed interface Check {
        data class Page(val url: String) : Check
        data object Telegram : Check
        data object NotSecure : Check
        data object NoPage : Check
    }

    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /**
     * What a shared text or a pasted line holds: the first page address in it, checked. The site
     * part is read by hand, strictly: plain letters, digits, dots and dashes only (a Ukrainian
     * domain such as .укр in its punycode form), no "name@" before it, so what is shown is where it
     * leads. Android's own reading of the result must agree, as that is what opens it.
     */
    fun check(raw: String): Check {
        val found = URL.find(raw.take(10_000)) ?: return Check.NoPage
        val url = found.value.trimEnd('.', ',', ')', ';', '!', '?', ':')
        if (url.startsWith("tg:", ignoreCase = true)) return Check.Telegram
        if ('\\' in url) return Check.NoPage
        val start = url.indexOf("://") + 3
        val end = url.indexOfAny(charArrayOf('/', '?', '#'), start).let { if (it < 0) url.length else it }
        val authority = url.substring(start, end)
        // "https://bank.example@other.example" shows one site and opens another: not kept
        if ('@' in authority) return Check.NoPage
        val port = authority.substringAfterLast(':', "").takeIf { ':' in authority }
        if (port != null && (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' })) return Check.NoPage
        val host = ascii(authority.removeSuffix(":" + (port ?: "")).lowercase().trimEnd('.')) ?: return Check.NoPage
        if (!HOST.matches(host)) return Check.NoPage
        if (TELEGRAM.any { host == it || host.endsWith(".$it") }) return Check.Telegram
        if (!url.substring(0, start - 3).equals("https", ignoreCase = true)) return Check.NotSecure
        val clean = "https://" + host + (port?.let { ":$it" } ?: "") + url.substring(end)
        if (clean.length > MAX_URL) return Check.NoPage
        val seen = try {
            android.net.Uri.parse(clean).host?.lowercase()
        } catch (e: Exception) {
            null
        }
        return if (seen == host) Check.Page(clean) else Check.NoPage
    }

    /** A host in plain Latin letters (punycode); null if it cannot be written so. */
    private fun ascii(host: String): String? {
        if (host.all { it.code < 128 }) return host
        return try {
            val out = StringBuilder()
            val info = android.icu.text.IDNA.Info()
            android.icu.text.IDNA.getUTS46Instance(android.icu.text.IDNA.DEFAULT).nameToASCII(host, out, info)
            if (info.hasErrors()) null else out.toString().lowercase()
        } catch (e: Exception) {
            null
        }
    }

    /** A name as typed or shared: no control or direction characters (they can make text lie), short. */
    fun cleanTitle(s: String): String =
        s.filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() }.trim().take(MAX_TITLE)

    /** The site's name to show, in its own letters (a .укр domain reads as .укр). */
    fun host(url: String): String = try {
        val h = android.net.Uri.parse(url).host.orEmpty()
        val out = StringBuilder()
        android.icu.text.IDNA.getUTS46Instance(android.icu.text.IDNA.DEFAULT).nameToUnicode(h, out, android.icu.text.IDNA.Info())
        out.toString().removePrefix("www.")
    } catch (e: Exception) {
        ""
    }

    /** Reads the stored pages back; anything that does not pass the same checks is left out. */
    fun fromPages(pages: List<String?>): List<Bookmark> {
        val out = ArrayList<Bookmark>()
        for (p in pages) {
            if (p.isNullOrBlank() || p.length > PAGE_CHARS * 2) continue
            try {
                val a = JSONArray(p)
                for (i in 0 until minOf(a.length(), MAX)) {
                    val o = a.optJSONObject(i) ?: continue
                    val page = check(o.optString("u")) as? Check.Page ?: continue
                    if (page.url != o.optString("u")) continue
                    out += Bookmark(page.url, cleanTitle(o.optString("t")), o.optLong("a", 0))
                }
            } catch (e: Throwable) {
                // One damaged page loses only its own bookmarks
            }
        }
        return out.distinctBy { it.url }.take(MAX)
    }

    /** The list as pages, in the order given; null when it does not fit. */
    fun toPages(list: List<Bookmark>): List<String>? {
        if (list.size > MAX) return null
        val pages = ArrayList<String>()
        var current = JSONArray()
        for (b in list) {
            val o = JSONObject().put("u", b.url).put("t", b.title).put("a", b.at)
            val next = JSONArray(current.toString()).put(o)
            if (next.toString().length > PAGE_CHARS) {
                pages += current.toString()
                current = JSONArray().put(o)
            } else {
                current = next
            }
        }
        if (current.length() > 0) pages += current.toString()
        return if (pages.size > KEYS.size) null else pages
    }
}

/** A page shared into Sprotyv, waiting for the owner to name and keep it. In memory only. */
object Incoming {
    data class Shared(val check: Bookmarks.Check, val title: String, val id: Long = System.nanoTime())

    private val _state = MutableStateFlow<Shared?>(null)
    val state: StateFlow<Shared?> = _state

    fun take(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        // Reopened from the recent apps: the share was already handled (or dropped) then
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            intent.action = null
            return
        }
        // Another app chose what is in the extras: anything odd is dropped, never a crash
        val shared = try {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            Shared(Bookmarks.check(text), Bookmarks.cleanTitle(subject))
        } catch (t: Throwable) {
            null
        }
        // Taken once: rotating the screen or coming back does not bring it again
        intent.action = null
        if (shared != null) _state.value = shared
    }

    fun clear() {
        _state.value = null
    }
}

/**
 * "Share → Sprotyv" in other apps. Off with the neutral icon (the share list would name Sprotyv),
 * in trip mode and in kid mode, and before the first set-up.
 */
object ShareIn {
    private fun entry(context: Context) = ComponentName(context, "ua.sprotyv.ShareIn")

    fun sync(context: Context) {
        val prefs = HomePrefs.load(context)
        val on = !Neutral.isOn(context) && !prefs.trip && (prefs.mode == Mode.FULL || prefs.mode == Mode.SIMPLE)
        val want = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            val pm = context.packageManager
            if (pm.getComponentEnabledSetting(entry(context)) != want) pm.setComponentEnabledSetting(entry(context), want, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            // Sharing into Sprotyv is a convenience; the app works without it
        }
    }
}
