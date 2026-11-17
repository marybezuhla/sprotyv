package ua.sprotyv.personal.backup

import androidx.room.withTransaction
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import ua.sprotyv.personal.PersonalDb
import java.io.Reader
import java.io.Writer

/**
 * The copy's content, one record per line, so it is written and read one record at a time.
 *
 *     {"app":"sprotyv","kind":"backup","format":2,"created":…}
 *     {"t":"person","v":{…}}   (one line for each record, table by table)
 *     {"t":"docs","v":{"n":…}}  (only when documents were included; then they replace those on the phone)
 *     {"t":"doc","v":{…}}      (a document's name, type and size, then its bytes in order:)
 *     {"t":"docpart","v":{"id":…,"n":…,"b":"base64"}}
 *     {"t":"end","n":…}        (how many records came before: a cut copy is never taken as whole)
 *
 * A record of a kind this version does not know (from a newer Sprotyv) is skipped and counted,
 * so an older app still restores everything it knows and says that part was left out. Every value
 * is checked as in the older one-piece copies ([BackupData]).
 */
object BackupLines {
    const val FORMAT = 2
    /** One line at most: a full medical card is about 140 000 letters. */
    private const val MAX_LINE = 1024 * 1024
    /** Records of unknown kinds at most: a newer copy has a few new kinds, not a flood. */
    private const val MAX_SKIPPED = 100_000
    private val KIND = Regex("^[a-z_]{1,30}$")

    /** What a copy holds, counted while it was checked. Two readings of the same file give the same summary. */
    data class Summary(
        val created: Long,
        val people: Int,
        val notes: Int,
        val records: Int,
        /** Records of kinds this version does not know: left out, and the owner is told. */
        val skipped: Int,
        val home: Map<String, String>,
        /** Documents in the copy; -1 when it was made without them (then those on the phone stay). */
        val docs: Int = -1,
        val docBytes: Long = 0,
    )

    /** What the reader hands on for documents, in order: the marker, then each document's start, parts and end. */
    object DocsMarker
    class DocStart(val id: Long, val title: String, val mime: String, val size: Long, val addedAt: Long)
    class DocPart(val bytes: ByteArray)
    object DocEnd

    /** Bytes of a document per line: about 700 000 letters once in base64, inside the line limit. */
    private const val PART_BYTES = 512 * 1024

    /** Where a document's plain bytes come from while a copy is written. */
    fun interface DocSource {
        fun open(d: ua.sprotyv.personal.Document): java.io.InputStream
    }

    fun write(s: BackupData.Snapshot, w: Writer, docs: List<ua.sprotyv.personal.Document>? = null, source: DocSource? = null) {
        w.line(JSONObject().put("app", "sprotyv").put("kind", "backup").put("format", FORMAT).put("created", s.created))
        var n = 0
        fun rec(t: String, v: JSONObject) { w.line(JSONObject().put("t", t).put("v", v)); n++ }
        s.medical?.let { rec("medical", BackupData.medical(it)) }
        s.people.forEach { rec("person", BackupData.person(it)) }
        s.plan?.let { rec("plan", BackupData.plan(it)) }
        s.rows.forEach { rec("row", BackupData.row(it)) }
        s.kit.forEach { rec("kit", BackupData.kit(it)) }
        s.settings.forEach { rec("setting", BackupData.setting(it)) }
        s.notes.forEach { rec("note", BackupData.note(it)) }
        if (s.home.isNotEmpty()) rec("home", JSONObject().apply { s.home.toSortedMap().forEach { (k, v) -> put(k, v) } })
        // No marker for an empty list: restoring such a copy leaves the phone's documents as they are
        if (!docs.isNullOrEmpty() && source != null) {
            rec("docs", JSONObject().put("n", docs.size))
            val buf = ByteArray(PART_BYTES)
            docs.forEachIndexed { i, d ->
                rec("doc", JSONObject().put("id", i + 1L).put("title", d.title).put("mime", d.mime).put("size", d.size).put("added", d.addedAt))
                var part = 0
                var total = 0L
                source.open(d).use { input ->
                    while (true) {
                        var got = 0
                        while (got < buf.size) {
                            val r = input.read(buf, got, buf.size - got)
                            if (r < 0) break
                            got += r
                        }
                        if (got == 0) break
                        total += got
                        rec("docpart", JSONObject().put("id", i + 1L).put("n", part++).put("b", java.util.Base64.getEncoder().encodeToString(if (got == buf.size) buf else buf.copyOf(got))))
                        if (got < buf.size) break
                    }
                }
                // The file must give exactly what its row says, or the copy would not read back
                if (total != d.size) throw java.io.IOException("Document size changed")
            }
        }
        w.line(JSONObject().put("t", "end").put("n", n))
        w.flush()
    }

    private fun Writer.line(o: JSONObject) { write(o.toString()); write("\n") }

    /**
     * Reads and checks the whole copy, handing each record to [each] (nothing is kept here, so
     * memory stays small whatever the size). Any odd value refuses the whole file.
     */
    suspend fun read(r: Reader, each: suspend (Any) -> Unit): Summary {
        val lines = LineReader(r)
        val created = readHead(lines)
        var medical = 0; var plan = 0; var homeSeen = 0
        val people = HashSet<Long>(); val rows = HashSet<Long>(); val kit = HashSet<String>(); val settings = HashSet<String>(); val notes = HashSet<Long>()
        var notesText = 0L
        var rowCount = 0
        var count = 0; var skipped = 0
        var home = emptyMap<String, String>()
        val docs = DocCheck()
        // Document lines are counted apart: "records" stays the owner's records, as the screen shows them
        var docLines = 0
        while (true) {
            // Stops promptly when the work is cancelled (the screen left, a time limit)
            kotlin.coroutines.coroutineContext.ensureActive()
            val o = parse(lines.next() ?: throw BackupData.Incompatible())
            val t = o.optString("t")
            // A document's parts come right after it, all of them
            if (t != "docpart" && docs.inDoc) throw BackupData.Incompatible()
            if (t == "end") {
                if (o.optInt("n", -1) != count || lines.next() != null) throw BackupData.Incompatible()
                break
            }
            if (!KIND.matches(t)) throw BackupData.Incompatible()
            count++
            val v = o.optJSONObject("v") ?: throw BackupData.Incompatible()
            val record: Any? = guard {
                when (t) {
                    "medical" -> BackupData.medicalOf(v).also { require(++medical == 1) }
                    "person" -> BackupData.personOf(v).also { require(people.add(it.id) && people.size <= BackupData.MAX_ITEMS) }
                    "plan" -> BackupData.planOf(v).also { require(++plan == 1) }
                    "row" -> BackupData.rowOf(v).also { require((it.id == 0L || rows.add(it.id)) && ++rowCount <= BackupData.MAX_ITEMS) }
                    "kit" -> BackupData.kitOf(v).also { require(kit.add(it.key) && kit.size <= BackupData.MAX_ITEMS) }
                    "setting" -> BackupData.settingOf(v).also { require(settings.add(it.key) && settings.size <= BackupData.MAX_ITEMS) }
                    "note" -> BackupData.noteOf(v).also {
                        notesText += it.title.length + it.text.length
                        require(notes.add(it.id) && notes.size <= BackupData.MAX_NOTES && notesText <= BackupData.MAX_NOTES_TEXT)
                    }
                    "home" -> { require(++homeSeen == 1); home = BackupData.homeOf(v); null }
                    "docs" -> docs.list(v)
                    "doc" -> docs.start(v)
                    "docpart" -> docs.part(v)
                    else -> { require(++skipped <= MAX_SKIPPED); null }
                }
            }
            if (t == "docs" || t == "doc" || t == "docpart") docLines++
            if (record != null) each(record)
            // The last part of a document closes it
            if (t == "docpart" && docs.closeIfWhole()) each(DocEnd)
        }
        // As many documents, each whole, as the copy said it holds
        if (docs.seen >= 0 && docs.seen != docs.promised) throw BackupData.Incompatible()
        return Summary(created, people.size, notes.size, count - skipped - homeSeen - docLines, skipped, home, docs.seen, docs.bytes)
    }

    /** The first line: this app's backup, in the format this version reads; gives when it was made. */
    private fun readHead(lines: LineReader): Long {
        val head = parse(lines.next() ?: throw BackupData.Incompatible())
        if (head.optString("app") != "sprotyv" || head.optString("kind") != "backup") throw BackupData.Incompatible()
        val format = head.optInt("format", -1)
        if (format > FORMAT) throw BackupData.FromNewer()
        if (format != FORMAT) throw BackupData.Incompatible()
        return head.optLong("created", -1).also { if (it < 0) throw BackupData.Incompatible() }
    }

    /**
     * Documents, counted and checked as they come: within the limits, each arriving whole and in
     * order. Each call throws (through [require]) on anything odd, which refuses the whole file.
     */
    private class DocCheck {
        var seen = -1
            private set
        var promised = -1
            private set
        var bytes = 0L
            private set
        var inDoc = false
            private set
        private val ids = HashSet<Long>()
        private var id = -1L
        private var size = 0L
        private var got = 0L
        private var part = 0

        /** The list's head: how many documents follow. An empty list replaces nothing: the phone's documents stay. */
        fun list(v: JSONObject): Any? {
            require(seen < 0)
            seen = 0
            promised = v.getInt("n")
            require(promised in 0..ua.sprotyv.personal.docs.DocFiles.MAX_DOCS)
            return if (promised > 0) DocsMarker else null
        }

        /** One document's head: a new id, a known type, a size within the limits, all documents together too. */
        fun start(v: JSONObject): DocStart {
            require(seen >= 0)
            val newId = v.getLong("id")
            require(newId > 0)
            val title = v.getString("title")
            val mime = v.getString("mime")
            val newSize = v.getLong("size")
            require(ids.add(newId) && ++seen <= ua.sprotyv.personal.docs.DocFiles.MAX_DOCS)
            require(title.length in 1..MAX_DOC_TITLE && mime in DOC_TYPES)
            require(newSize in 1..ua.sprotyv.personal.docs.DocFiles.MAX_FILE_BYTES)
            bytes += newSize
            require(bytes <= ua.sprotyv.personal.docs.DocFiles.MAX_TOTAL_BYTES)
            id = newId; size = newSize; got = 0; part = 0; inDoc = true
            return DocStart(newId, title, mime, newSize, v.getLong("added").also { require(it >= 0) })
        }

        fun part(v: JSONObject): DocPart {
            require(inDoc && v.getLong("id") == id && v.getInt("n") == part++)
            val b = java.util.Base64.getDecoder().decode(v.getString("b"))
            require(b.isNotEmpty() && b.size <= PART_BYTES)
            got += b.size
            require(got <= size)
            return DocPart(b)
        }

        /** Closes the current document once its last part has come, and says whether it did. */
        fun closeIfWhole(): Boolean = (inDoc && got == size).also { if (it) inDoc = false }
    }

    /**
     * Replaces all of the owner's data with the copy read from [r], all or nothing: everything is
     * checked again while it goes in, and must match what the owner saw ([expected], and
     * [sameContent] for the fingerprint of the content); otherwise nothing changes.
     */
    suspend fun restore(db: PersonalDb, r: Reader, settings: Set<String>, expected: Summary, files: ua.sprotyv.personal.docs.DocFiles, sameContent: () -> Boolean) {
        var writer: ua.sprotyv.personal.docs.DocFiles.Writer? = null
        var start: DocStart? = null
        val made = ArrayList<String>()
        var ok = false
        try {
            db.withTransaction {
                BackupData.clearAll(db, settings)
                val got = read(r) { rec ->
                    when (rec) {
                        // Documents in the copy replace those on the phone; a copy without them leaves them
                        DocsMarker -> db.documents().clear()
                        is DocStart -> {
                            // The reader closes each document before the next; anything else is not ours
                            if (writer != null) throw BackupData.Incompatible()
                            start = rec; writer = files.writer()
                        }
                        is DocPart -> writer!!.write(rec.bytes)
                        DocEnd -> {
                            val s = start!!
                            val w = writer!!.finish()
                            writer = null
                            made += w.file
                            db.documents().add(ua.sprotyv.personal.Document(title = s.title, mime = s.mime, size = w.size, file = w.file, key = w.key, addedAt = s.addedAt))
                            w.key.fill(0)
                        }
                        else -> BackupData.put(db, rec, settings)
                    }
                }
                // The same records, and byte for byte the same content as when it was opened
                if (got != expected || !sameContent()) throw BackupData.Incompatible()
            }
            ok = true
        } finally {
            writer?.abort()
            // Files this restore wrote: named now if it went in, removed at once if it did not
            made.forEach { if (!ok) files.delete(it); files.done(it) }
            // Files no row names (the old ones replaced, or new ones of a restore that did not finish) go
            // (recent files are spared: an import may be about to name its file; they go at a later sweep)
            try { files.sweep(db.documents().files().toSet()) } catch (e: Exception) { /* tidying only: swept again later */ }
        }
    }

    private const val MAX_DOC_TITLE = 200
    val DOC_TYPES = setOf("image/jpeg", "application/pdf")

    private fun parse(line: String): JSONObject {
        if (!BackupData.shallow(line.toByteArray(Charsets.UTF_8))) throw BackupData.Incompatible()
        return try {
            JSONObject(line)
        } catch (e: Exception) {
            throw BackupData.Incompatible()
        }
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: BackupData.Incompatible) {
        throw e
    } catch (e: Exception) {
        throw BackupData.Incompatible()
    }

    /** Lines of at most [MAX_LINE] letters; a longer one is not ours. */
    private class LineReader(private val r: Reader) {
        private val sb = StringBuilder()
        private val buf = CharArray(8192)
        private var pos = 0
        private var len = 0
        private var eof = false

        fun next(): String? {
            sb.setLength(0)
            while (true) {
                if (pos == len) {
                    if (eof) return if (sb.isEmpty()) null else sb.toString()
                    len = r.read(buf); pos = 0
                    if (len < 0) { len = 0; eof = true; continue }
                }
                val c = buf[pos++]
                if (c == '\n') return sb.toString()
                sb.append(c)
                if (sb.length > MAX_LINE) throw BackupData.Incompatible()
            }
        }
    }
}
