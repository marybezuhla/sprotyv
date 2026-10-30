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

    /** Opens the dialer with the number filled in; the person presses call. */
    fun dial(context: Context, number: String) =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse(Links.tel(number))))

    /** One SMS to several numbers at once, with the text ready. Works with no internet. */
    fun sms(context: Context, numbers: List<String>, text: String) {
        val link = try { Links.smsTo(numbers) } catch (e: IllegalArgumentException) { return noNumber(context) }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(link)).putExtra("sms_body", text)
        start(context, intent)
    }

    /**
     * Opens the chat with this person in the WhatsApp app itself (never a browser), text ready.
     * Stage 3: also check the app's signing certificate, so a look-alike app cannot receive the text.
     */
    fun whatsapp(context: Context, phone: String, text: String) {
        val link = try { Links.whatsapp(phone, text) } catch (e: IllegalArgumentException) { return noNumber(context) }
        val uri = Uri.parse(link)
        if (!tryStart(context, Intent(Intent.ACTION_VIEW, uri).setPackage(WHATSAPP)) &&
            !tryStart(context, Intent(Intent.ACTION_VIEW, uri).setPackage(WHATSAPP_BUSINESS))
        ) notFound(context)
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
        if (!tryStart(context, intent)) notFound(context)
    }

    /**
     * Opens a trusted page outside Sprotyv: the source's own app when installed, else the phone's
     * browser as a Custom Tab, which looks part of Sprotyv but loads the page itself (Sprotyv has
     * no internet). Telegram links open in Telegram when it handles them, else in the browser.
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
        if (uri.host != "t.me") {
            // The Custom Tabs protocol by its documented extras, without an extra library
            intent.putExtras(android.os.Bundle().apply { putBinder(EXTRA_SESSION, null) })
            toolbarColor?.let { intent.putExtra(EXTRA_TOOLBAR_COLOR, it) }
        }
        start(context, intent)
    }

    private fun fromPlay(context: Context, app: String): Boolean = try {
        context.packageManager.getInstallSourceInfo(app).installingPackageName == "com.android.vending"
    } catch (e: Exception) {
        false
    }

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

    private fun noNumber(context: Context) =
        Toast.makeText(context, R.string.reach_no_number, Toast.LENGTH_SHORT).show()
}

/** The links themselves, as plain text, so they can be checked without a phone. */
object Links {
    /** Digits only, with a leading + kept: anything else in a number is dropped. */
    fun clean(number: String): String {
        val digits = number.filter { it.isDigit() }
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
     * WhatsApp needs the international form without the +. A Ukrainian local number (0XXXXXXXXX)
     * becomes 380XXXXXXXXX; without that WhatsApp would open the wrong chat. An empty number would
     * open a picker instead.
     */
    fun whatsapp(phone: String, text: String): String {
        var digits = clean(phone).removePrefix("+")
        if (!phone.trim().startsWith("+") && digits.length == 10 && digits.startsWith("0")) digits = "38$digits"
        require(digits.isNotEmpty()) { "No number" }
        return "https://wa.me/$digits?text=" + encode(text)
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
