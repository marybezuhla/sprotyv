package ua.sprotyv.reach

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Calling and writing through the phone's own apps. Sprotyv only hands over the number and the
 * text; the person presses "Send" in that app. Nothing is sent by Sprotyv itself and nothing is
 * read back.
 */
object Reach {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val SIGNAL = "org.thoughtcrime.securesms"

    /** Opens an app already on the phone; false when it is not there (nothing is installed from here). */
    fun launch(context: Context, pkg: String): Boolean = try {
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) false else { context.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    } catch (e: Exception) { false }

    /** Whether an app is on the phone (only the packages named in the manifest's queries can be seen). */
    fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getLaunchIntentForPackage(pkg) != null
    } catch (e: Exception) { false }

    /** Opens the dialer with the number filled in; the person presses call. */
    fun dial(context: Context, number: String) =
        startPreferring(context, Intent(Intent.ACTION_DIAL, Uri.parse(Links.tel(number))), defaultDialer(context))

    /**
     * Calls and SMS go to the phone's own default dialler and SMS app, not to whichever app also
     * answers (which number was dialled and the "I am safe" text are not for an app that only
     * pretends). With no default set, the phone chooses as before.
     */
    private fun startPreferring(context: Context, intent: Intent, pkg: String?) {
        if (pkg != null && tryStart(context, Intent(intent).setPackage(pkg))) return
        start(context, intent)
    }

    private fun defaultDialer(context: Context): String? = try {
        (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)?.defaultDialerPackage
    } catch (e: Exception) { null }

    private fun defaultSms(context: Context): String? = try {
        android.provider.Telephony.Sms.getDefaultSmsPackage(context)
    } catch (e: Exception) { null }

    /** One SMS to several numbers at once, with the text ready. Works with no internet. */
    fun sms(context: Context, numbers: List<String>, text: String) {
        val link = try { Links.smsTo(numbers) } catch (e: IllegalArgumentException) { return noNumber(context) }
        startPreferring(context, Intent(Intent.ACTION_SENDTO, Uri.parse(link)).putExtra("sms_body", text), defaultSms(context))
    }

    /**
     * The messenger is the real one: signed with its maker's own certificate, wherever it was
     * installed from. A look-alike under the same name never receives the text.
     */
    fun genuine(context: Context, pkg: String): Boolean {
        val pins = SIGNERS[pkg] ?: return false
        return try {
            val info = context.packageManager.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return false
            // Only one signer, and that one is the maker's (a rotated key keeps its history)
            if (info.hasMultipleSigners()) return false
            info.signingCertificateHistory.orEmpty().any { cert -> digests(cert.toByteArray()).any { it in pins } }
        } catch (e: Exception) {
            false
        }
    }

    private fun digests(cert: ByteArray): List<String> = listOf("SHA-1", "SHA-256").map { alg ->
        java.security.MessageDigest.getInstance(alg).digest(cert).joinToString("") { "%02x".format(it) }
    }

    /**
     * The makers' signing certificates. Signal: its current 4096-bit one and the older 1024-bit one
     * it rotated from (both on signal.org/android/apk). WhatsApp and WhatsApp Business share
     * WhatsApp Inc.'s long-standing certificate (SHA-1).
     */
    private val SIGNERS = mapOf(
        SIGNAL to setOf(
            "4be4f6cd5be844083e900279dc822af65a547fecc26aba7ff1f5203a45518cd8",
            "29f34e5f27f211b424bc5bf9d67162c0eafba2da35af35c16416fc446276ba26",
        ),
        WHATSAPP to setOf("38a0f7d505fe18fec64fbf343ecaaaf310dbd799"),
        WHATSAPP_BUSINESS to setOf("38a0f7d505fe18fec64fbf343ecaaaf310dbd799"),
    )

    /** On the phone at all, icon or not (a hidden look-alike counts too, so it is checked). */
    private fun present(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    /**
     * Opens the chat with this person in the WhatsApp app itself (never a browser), text ready;
     * only the genuine app ([genuine]) gets it.
     */
    fun whatsapp(context: Context, phone: String, text: String) {
        val link = try { Links.whatsapp(phone, text) } catch (e: IllegalArgumentException) { return noNumber(context) }
        val uri = Uri.parse(link)
        val apps = listOf(WHATSAPP, WHATSAPP_BUSINESS).filter { present(context, it) }
        if (apps.isEmpty()) return notFound(context)
        val real = apps.filter { genuine(context, it) }
        if (real.isEmpty()) return notGenuine(context)
        // One of them is a look-alike: said, even though the real one opens
        if (real.size < apps.size) notGenuine(context)
        if (real.none { tryStart(context, Intent(Intent.ACTION_VIEW, uri).setPackage(it)) }) notFound(context)
    }

    /**
     * Hands the text to Signal, where the person picks the chats. Signal has no official way to
     * fill in a text for one given number, so this is the honest option.
     */
    fun signal(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
            .setPackage(SIGNAL)
        // Checked whenever anything by that name is there, icon or not
        if (!present(context, SIGNAL)) return notFound(context)
        if (!genuine(context, SIGNAL)) return notGenuine(context)
        if (!tryStart(context, intent)) notFound(context)
    }

    /** Opens the chat with this number in Signal itself (the genuine app only); no text is filled in. */
    fun signalTo(context: Context, phone: String) {
        val link = try { Links.signalChat(phone) } catch (e: IllegalArgumentException) { return noNumber(context) }
        if (!present(context, SIGNAL)) return notFound(context)
        if (!genuine(context, SIGNAL)) return notGenuine(context)
        if (!tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(SIGNAL))) notFound(context)
    }

    /** One SMS to one person, text ready or empty. */
    fun smsOne(context: Context, phone: String, text: String = "") = sms(context, listOf(phone), text)

    /**
     * Opens a trusted page outside Sprotyv: the source's own app when installed, else the phone's
     * browser as a Custom Tab, which looks part of Sprotyv but loads the page itself (Sprotyv has
     * no internet).
     * Only https addresses from the built-in list.
     */
    fun open(context: Context, url: String, app: String = "", toolbarColor: Int? = null) {
        // The source's own app only when it came from Google Play: a look-alike installed from a
        // file under the same name never opens in its place; the page opens instead
        if (app.isNotEmpty() && fromPlay(context, app)) {
            context.packageManager.getLaunchIntentForPackage(app)?.let { if (tryStart(context, it)) return }
        }
        if (!url.startsWith("https://")) return notFound(context)
        val uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        // Firefox Focus, when the person has it from Google Play, opens every page, whatever the main
        // browser is: its Erase button removes history and cookies
        focus(context)?.let { pkg -> if (tryStart(context, Intent(intent).setPackage(pkg))) return }
        // Focus did not take it: a Custom Tab in the main browser
        run {
            // The Custom Tabs protocol by its documented extras, without an extra library
            intent.putExtras(android.os.Bundle().apply { putBinder(EXTRA_SESSION, null) })
            // Leave no trace where the browser can: no history, cookies or cache after the tab closes.
            // Browsers that cannot simply ignore it; the screen says which is the case
            intent.putExtra(EXTRA_EPHEMERAL, true)
            toolbarColor?.let { intent.putExtra(EXTRA_TOOLBAR_COLOR, it) }
        }
        start(context, intent)
    }

    private fun fromPlay(context: Context, app: String): Boolean = try {
        context.packageManager.getInstallSourceInfo(app).installingPackageName == "com.android.vending"
    } catch (e: Exception) {
        false
    }

    /** How pages from Sprotyv are kept on this phone, said plainly on the screen. */
    enum class Trace { FOCUS, FOCUS_NOT_FROM_PLAY, CHROME_TAB, BROWSER }

    fun trace(context: Context): Trace = when {
        focus(context) != null -> Trace.FOCUS
        FOCUS.any { enabled(context, it) } -> Trace.FOCUS_NOT_FROM_PLAY
        chromeTraceless(context) -> Trace.CHROME_TAB
        else -> Trace.BROWSER
    }

    /** Google Play is on this phone (so a link to it can install something). */
    fun hasPlay(context: Context): Boolean = enabled(context, "com.android.vending")

    /**
     * Sprotyv itself came from an F-Droid store app: then it does not send people to Google Play.
     * The installing app is the one Android recorded, not anything the store says about itself.
     */
    fun fromFdroid(context: Context): Boolean = try {
        context.packageManager.getInstallSourceInfo(context.packageName).initiatingPackageName in FDROID_CLIENTS
    } catch (e: Exception) {
        false
    }

    private val FDROID_CLIENTS = setOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid", "in.sunilpaulmathew.izzyondroid")

    /** Firefox Focus (or Klar) installed from Google Play and turned on, or null. */
    private fun focus(context: Context): String? = FOCUS.firstOrNull { enabled(context, it) && fromPlay(context, it) }

    private fun enabled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (e: Exception) {
        false
    }

    private val FOCUS = listOf("org.mozilla.focus", "org.mozilla.klar")

    /**
     * Whether the default browser is Chrome 137 or newer (Ephemeral Custom Tabs). Other browsers
     * are not assumed to support it.
     */
    private fun chromeTraceless(context: Context): Boolean = try {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org")).addCategory(Intent.CATEGORY_BROWSABLE)
        val pkg = context.packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        pkg == CHROME && (context.packageManager.getPackageInfo(CHROME, 0).versionName?.substringBefore('.')?.toIntOrNull() ?: 0) >= 137
    } catch (e: Exception) {
        false
    }

    private const val CHROME = "com.android.chrome"
    private const val EXTRA_EPHEMERAL = "androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING"
    private const val EXTRA_SESSION = "android.support.customtabs.extra.SESSION"
    private const val EXTRA_TOOLBAR_COLOR = "android.support.customtabs.extra.TOOLBAR_COLOR"

    private fun start(context: Context, intent: Intent) {
        if (!tryStart(context, intent)) notFound(context)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun notFound(context: Context) =
        Toast.makeText(context, R.string.reach_no_app, Toast.LENGTH_SHORT).show()

    private fun notGenuine(context: Context) =
        Toast.makeText(context, R.string.reach_not_genuine, Toast.LENGTH_LONG).show()

    private fun noNumber(context: Context) =
        Toast.makeText(context, R.string.reach_no_number, Toast.LENGTH_SHORT).show()
}

/** The links themselves, as plain text, so they can be checked without a phone. */
object Links {
    /** ASCII digits only, with a leading + kept: anything else (look-alike digits of other scripts too) is dropped. */
    fun clean(number: String): String {
        val digits = number.filter { it in '0'..'9' }
        return if (number.trim().startsWith("+")) "+$digits" else digits
    }

    fun tel(number: String) = "tel:" + clean(number)

    fun smsTo(numbers: List<String>): String {
        val kept = numbers.map(::clean).filter { it.removePrefix("+").isNotEmpty() }
        // Checked after cleaning: a bare "smsto:" would open the SMS app with no one in it
        require(kept.isNotEmpty()) { "No numbers" }
        return "smsto:" + kept.joinToString(";")
    }

    /**
     * The international form without the +, or null when it is unclear which number is meant.
     * Ukrainian forms are made whole: 0XXXXXXXXX and the old 80XXXXXXXXX become 380XXXXXXXXX.
     * Anything else without a + would open a chat with a stranger in another country.
     */
    fun international(phone: String): String? {
        val c = clean(phone)
        val digits = c.removePrefix("+")
        return when {
            c.startsWith("+") -> digits.takeIf { it.length in 8..15 }
            digits.length == 10 && digits.startsWith("0") -> "38$digits"
            digits.length == 11 && digits.startsWith("80") -> "3$digits"
            digits.length == 12 && digits.startsWith("380") -> digits
            else -> null
        }
    }

    /** WhatsApp's link to a chat with the text ready (international form without the +). */
    fun whatsapp(phone: String, text: String): String {
        val digits = requireNotNull(international(phone)) { "No number" }
        return "https://wa.me/$digits?text=" + encode(text)
    }

    /** Signal's own link to the chat with a number (international form with +); Signal opens it itself. */
    fun signalChat(phone: String): String {
        val digits = requireNotNull(international(phone)) { "No number" }
        return "https://signal.me/#p/+$digits"
    }

    /** Percent-encoding of the text as UTF-8, so any letter or emoji survives. */
    fun encode(text: String): String {
        val out = StringBuilder()
        for (b in text.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                out.append(ch)
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
            }
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
