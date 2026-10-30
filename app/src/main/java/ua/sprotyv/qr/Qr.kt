package ua.sprotyv.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person

/** Making and reading QR codes. Plain Java parts only, so it can be checked without a phone. */
object Qr {
    /** UTF-8 text a phone camera reads easily, also from paper or a lock screen in poor light. */
    const val MAX_BYTES = 400

    /** Room for the encrypted family plan, read from a phone screen (about version 30). */
    const val MAX_PLAN_BYTES = 1200

    fun encode(text: String, size: Int, maxBytes: Int = MAX_BYTES): BitMatrix {
        require(maxBytes <= MAX_PLAN_BYTES && text.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Too long for a QR code" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    }

    /** Reads a QR code from pixels (ARGB). Null if there is none. */
    fun decode(width: Int, height: Int, pixels: IntArray): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            // A damaged or unreadable code: same as none
            null
        }
    }
}

/** What a scanned code holds, so the screen can show it plainly before anything is done with it. */
sealed interface Scanned {
    val text: String

    /** A web link. [host] is shown large, because a look-alike address is the usual trick. */
    data class Link(override val text: String, val host: String, val suspicious: List<String>) : Scanned
    data class Phone(override val text: String, val number: String) : Scanned
    data class Plain(override val text: String) : Scanned

    companion object {
        // A backslash ends the address for browsers, so it ends it here too: no look-alike host
        private val URL = Regex("^(https?)://([^/?#\\s\\\\]+)(.*)$", RegexOption.IGNORE_CASE)

        fun of(raw: String): Scanned {
            val text = raw.trim()
            // Tabs, line breaks and other control characters are dropped by browsers and can
            // join two addresses into one: such a text is never offered as a link
            if (text.any { it.code <= 0x20 && it != ' ' } || text.contains(' ')) {
                if (URL.containsMatchIn(text)) return Plain(text)
            }
            URL.matchEntire(text)?.let { m ->
                val scheme = m.groupValues[1].lowercase()
                val authority = m.groupValues[2]
                val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
                return Link(text, host, warnings(scheme, authority, host))
            }
            if (text.startsWith("tel:", ignoreCase = true)) return Phone(text, text.substring(4))
            return Plain(text)
        }

        /** Plain signs of a trick link. Not proof either way; the person decides. */
        fun warnings(scheme: String, authority: String, host: String): List<String> = buildSet {
            if (scheme != "https") add(NOT_HTTPS)
            if ('@' in authority) add(HIDDEN_ADDRESS)
            if ('%' in host) add(ENCODED)
            if (host.startsWith("xn--") || host.contains(".xn--") || host.any { it.code > 127 }) add(LOOKALIKE_LETTERS)
            if (Regex("^[0-9.]+$").matches(host) || host.startsWith("[")) add(BARE_NUMBERS)
            if (host.count { it == '.' } >= 4) add(MANY_PARTS)
        }.toList()

        const val NOT_HTTPS = "not_https"
        const val HIDDEN_ADDRESS = "hidden_address"
        const val LOOKALIKE_LETTERS = "lookalike_letters"
        const val BARE_NUMBERS = "bare_numbers"
        const val MANY_PARTS = "many_parts"
        const val ENCODED = "encoded"
    }
}

/**
 * The rescuer's code: only what helps in the first minutes, as plain text any phone camera reads.
 * No name and no date of birth: on a lock screen they would tell a stranger who the owner is.
 * Long fields are cut so the code stays easy to scan; [Result.cut] says when something did not fit.
 */
object RescuerCode {
    private const val FIELD = 90

    data class Result(val text: String, val cut: Boolean)

    fun text(card: MedicalCard, ice: List<Person>, labels: Labels): Result {
        var cut = false
        val lines = buildList {
            add(labels.title)
            fun line(label: String, value: String) {
                val v = value.trim().replace('\n', ' ')
                if (v.isEmpty()) return
                if (v.length > FIELD) cut = true
                add("$label: " + if (v.length > FIELD) safeTake(v, FIELD - 1) + "…" else v)
            }
            // Most needed first: lines at the end are the ones dropped if it does not fit
            line(labels.blood, card.bloodGroup)
            line(labels.allergies, card.allergies)
            ice.take(1).forEach { line(labels.contact, "${it.name} ${it.phone}") }
            line(labels.medicines, card.medicines)
            line(labels.conditions, card.conditions)
            ice.drop(1).take(1).forEach { line(labels.contact, "${it.name} ${it.phone}") }
        }
        // Drop the last lines until it fits; the title and the first fields matter most
        var out = lines
        while (out.joinToString("\n").toByteArray(Charsets.UTF_8).size > Qr.MAX_BYTES && out.size > 1) {
            out = out.dropLast(1)
            cut = true
        }
        return Result(out.joinToString("\n"), cut)
    }

    /** Cuts without splitting a character made of two parts (an emoji, for example). */
    private fun safeTake(s: String, n: Int): String {
        val end = if (n > 0 && n < s.length && Character.isHighSurrogate(s[n - 1])) n - 1 else n
        return s.take(end)
    }

    /** Labels come from the app's strings, so the code is in the app's language. */
    data class Labels(
        val title: String,
        val blood: String,
        val allergies: String,
        val medicines: String,
        val conditions: String,
        val contact: String,
    )
}
