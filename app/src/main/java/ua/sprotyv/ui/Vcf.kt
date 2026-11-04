package ua.sprotyv.ui

/**
 * Contacts as vCard files (.vcf), the format every phone's contacts app reads and writes. Only a
 * name, one phone number and a note (the relation) go in or out; nothing else of a contact is read.
 */
object Vcf {
    /** At most this many people in "Trusted people": a short list of the most important, not a contact book. */
    const val MAX_PEOPLE = 20
    /** A file larger than this is not a short list of contacts. */
    const val MAX_FILE_BYTES = 1_000_000
    /** Cards read from one file at most: far more than anyone imports, few enough to stay quick. */
    private const val MAX_CARDS = 2000

    data class Contact(val name: String, val phone: String, val note: String = "")

    /** vCard 3.0, one card per person, in the given order. */
    fun write(contacts: List<Contact>): String = buildString {
        contacts.forEach { c ->
            val name = escape(cleanText(c.name))
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:").append(name).append("\r\n")
            append("N:").append(name).append(";;;;\r\n")
            append("TEL;TYPE=CELL:").append(cleanPhone(c.phone)).append("\r\n")
            val note = cleanText(c.note)
            if (note.isNotBlank()) append("NOTE:").append(escape(note)).append("\r\n")
            append("END:VCARD\r\n")
        }
    }

    /**
     * Text as it may be shown and kept: no control or direction-changing characters (they can make
     * one name look like another), line breaks as spaces, letters in one normal form. The joiners
     * some scripts need (U+200C, U+200D) stay.
     */
    fun cleanText(s: String): String {
        val nfc = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
        val out = StringBuilder(nfc.length)
        for (ch in nfc) {
            when {
                ch == '\n' || ch == '\r' || ch == '\t' -> out.append(' ')
                ch == '‌' || ch == '‍' -> out.append(ch)
                ch.isISOControl() || Character.getType(ch) == Character.FORMAT.toInt() -> {}
                else -> out.append(ch)
            }
        }
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /** ASCII digits only (no look-alike digits from other scripts), with a leading + kept. */
    fun cleanPhone(s: String): String {
        val digits = s.filter { it in '0'..'9' }
        return if (s.trim().startsWith("+")) "+$digits" else digits
    }

    /**
     * The contacts in a file: name and the first phone number of each card (a card without a
     * number is left out). Reads vCard 2.1, 3.0 and 4.0 as phones export them, including the
     * quoted-printable form and the older character sets (CHARSET=) Android uses for non-Latin names.
     */
    fun read(text: String): List<Contact> {
        val out = ArrayList<Contact>()
        var name = ""
        var given = ""
        var phone = ""
        var note = ""
        var inCard = false
        for (line in unfold(text)) {
            if (out.size >= MAX_CARDS) break
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val prop = head.substringBefore(';').substringAfterLast('.').uppercase()
            val params = head.uppercase()
            fun raw(): String = if (params.contains("QUOTED-PRINTABLE")) quotedPrintable(value, charset(params)) else value
            fun decoded(): String = unescape(raw()).trim()
            when (prop) {
                "BEGIN" -> if (value.trim().equals("VCARD", true)) { inCard = true; name = ""; given = ""; phone = ""; note = "" }
                "FN" -> if (inCard) name = decoded()
                // Family;Given;… : read as "Given Family" when there is no full name
                "N" -> if (inCard && given.isEmpty()) given = splitParts(raw()).map { unescape(it).trim() }
                    .let { p -> listOf(p.getOrElse(1) { "" }, p.getOrElse(0) { "" }) }.filter { it.isNotBlank() }.joinToString(" ")
                "TEL" -> if (inCard && phone.isEmpty()) phone = cleanPhone(decoded().removePrefix("tel:"))
                "NOTE" -> if (inCard && note.isEmpty()) note = decoded()
                "END" -> if (inCard) {
                    inCard = false
                    val n = cut(cleanText(name.ifBlank { given }), 200)
                    if (n.isNotBlank() && phone.removePrefix("+").length >= 3) out += Contact(n, phone.take(30), cut(cleanText(note), 200))
                }
            }
        }
        return out
    }

    /** At most [max] characters, never splitting a letter written as two (surrogate pair). */
    private fun cut(s: String, max: Int): String {
        if (s.length <= max) return s
        val end = if (Character.isHighSurrogate(s[max - 1])) max - 1 else max
        return s.substring(0, end)
    }

    private fun charset(params: String): java.nio.charset.Charset {
        val name = Regex("CHARSET=\"?([A-Z0-9_.:-]+)").find(params)?.groupValues?.get(1) ?: return Charsets.UTF_8
        return try { java.nio.charset.Charset.forName(name) } catch (e: Exception) { Charsets.UTF_8 }
    }

    /** Parts split at ; not preceded by a backslash. */
    private fun splitParts(s: String): List<String> {
        val parts = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) { cur.append(c).append(s[i + 1]); i += 2; continue }
            if (c == ';') { parts += cur.toString(); cur.setLength(0) } else cur.append(c)
            i++
        }
        parts += cur.toString()
        return parts
    }

    /**
     * Lines continued on the next one (a space or tab at its start, or a soft break in
     * quoted-printable) are joined. Built up piece by piece, so a long folded file stays quick.
     */
    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var qp = false
        var started = false
        fun flush() { if (started) out += cur.toString(); cur.setLength(0); started = false; qp = false }
        for (l in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            when {
                started && (l.startsWith(" ") || l.startsWith("\t")) -> cur.append(l, 1, l.length)
                started && qp && cur.endsWith("=") -> { cur.setLength(cur.length - 1); cur.append(l) }
                else -> {
                    flush()
                    cur.append(l); started = true
                    val colon = l.indexOf(':')
                    qp = colon > 0 && l.substring(0, colon).contains("QUOTED-PRINTABLE", ignoreCase = true)
                }
            }
        }
        flush()
        return out
    }

    private fun quotedPrintable(s: String, charset: java.nio.charset.Charset): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) { bytes.write(b); i += 3; continue }
            }
            // A plain letter outside ASCII: kept whole, as UTF-8
            if (c.code < 0x80) bytes.write(c.code) else bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return String(bytes.toByteArray(), charset)
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    /** One pass from left to right, so "\\n" stays a backslash and an n. */
    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c); i++
            }
        }
        return out.toString()
    }
}
