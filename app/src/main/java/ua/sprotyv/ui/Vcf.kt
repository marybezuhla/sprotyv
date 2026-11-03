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

    data class Contact(val name: String, val phone: String, val note: String = "")

    /** vCard 3.0, one card per person, in the given order. */
    fun write(contacts: List<Contact>): String = buildString {
        contacts.forEach { c ->
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:").append(escape(c.name)).append("\r\n")
            append("N:").append(escape(c.name)).append(";;;;\r\n")
            append("TEL;TYPE=CELL:").append(c.phone.filter { it.isDigit() || it == '+' }).append("\r\n")
            if (c.note.isNotBlank()) append("NOTE:").append(escape(c.note)).append("\r\n")
            append("END:VCARD\r\n")
        }
    }

    /**
     * The contacts in a file: name and the first phone number of each card (a card without a
     * number is left out). Reads vCard 2.1, 3.0 and 4.0 as phones export them, including the
     * quoted-printable form Android uses for non-Latin names.
     */
    fun read(text: String): List<Contact> {
        val out = ArrayList<Contact>()
        var name = ""
        var given = ""
        var phone = ""
        var note = ""
        var inCard = false
        for (line in unfold(text)) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val prop = head.substringBefore(';').substringAfterLast('.').uppercase()
            val params = head.uppercase()
            fun raw(): String = if (params.contains("QUOTED-PRINTABLE")) quotedPrintable(value) else value
            fun decoded(): String = unescape(raw()).trim()
            when (prop) {
                "BEGIN" -> if (value.trim().equals("VCARD", true)) { inCard = true; name = ""; given = ""; phone = ""; note = "" }
                "FN" -> if (inCard) name = decoded()
                // Family;Given;… : read as "Given Family" when there is no full name
                "N" -> if (inCard && given.isEmpty()) given = raw().split(Regex("(?<!\\\\);")).map { unescape(it).trim() }
                    .let { p -> listOf(p.getOrElse(1) { "" }, p.getOrElse(0) { "" }) }.filter { it.isNotBlank() }.joinToString(" ")
                "TEL" -> if (inCard && phone.isEmpty()) phone = decoded().removePrefix("tel:").filter { it.isDigit() || it == '+' }
                "NOTE" -> if (inCard && note.isEmpty()) note = decoded()
                "END" -> if (inCard) {
                    inCard = false
                    val n = name.ifBlank { given }.take(200)
                    if (n.isNotBlank() && phone.length >= 3) out += Contact(n, phone.take(30), note.take(200))
                }
            }
        }
        return out
    }

    /** Lines continued on the next one (a space or tab at its start, or a soft break in quoted-printable) are joined. */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (l in lines) {
            when {
                (l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty() -> out[out.size - 1] = out.last() + l.substring(1)
                out.isNotEmpty() && out.last().endsWith("=") && out.last().uppercase().contains("QUOTED-PRINTABLE") -> out[out.size - 1] = out.last().dropLast(1) + l
                else -> out += l
            }
        }
        return out
    }

    private fun quotedPrintable(s: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) { bytes.write(b); i += 3; continue }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
