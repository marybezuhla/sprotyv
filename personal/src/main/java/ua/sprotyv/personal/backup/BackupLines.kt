package ua.sprotyv.personal.backup

import androidx.room.withTransaction
import org.json.JSONObject
import ua.sprotyv.personal.PersonalDb
import java.io.Reader
import java.io.Writer

/**
 * The copy's content, one record per line, so it is written and read one record at a time.
 *
 *     {"app":"sprotyv","kind":"backup","format":2,"created":…}
 *     {"t":"person","v":{…}}   (one line for each record, table by table)
 *     {"t":"end","n":…}        (how many records came before: a cut copy is never taken as whole)
 *
 * A record of a kind this version does not know (from a newer Sprotyv) is skipped and counted,
 * so an older app still restores everything it knows and says that part was left out. Every value
 * is checked as in the older one-piece copies ([BackupData]).
 */
object BackupLines {
    const val FORMAT = 2
    /** One line at most: a full medical card is about 140 000 letters. */
    private const val MAX_LINE = 512 * 1024
    private val KIND = Regex("^[a-z_]{1,30}$")

    /** What a copy holds, counted while it was checked. Two readings of the same file give the same summary. */
    data class Summary(
        val created: Long,
        val people: Int,
        val records: Int,
        /** Records of kinds this version does not know: left out, and the owner is told. */
        val skipped: Int,
        val home: Map<String, String>,
    )

    fun write(s: BackupData.Snapshot, w: Writer) {
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
        val head = parse(lines.next() ?: throw BackupData.Incompatible())
        if (head.optString("app") != "sprotyv" || head.optString("kind") != "backup") throw BackupData.Incompatible()
        val format = head.optInt("format", -1)
        if (format > FORMAT) throw BackupData.FromNewer()
        if (format != FORMAT) throw BackupData.Incompatible()
        val created = head.optLong("created", -1).also { if (it < 0) throw BackupData.Incompatible() }
        var medical = 0; var plan = 0; var homeSeen = 0
        val people = HashSet<Long>(); val rows = HashSet<Long>(); val kit = HashSet<String>(); val settings = HashSet<String>(); val notes = HashSet<Long>()
        var notesText = 0L
        var rowCount = 0
        var count = 0; var skipped = 0
        var home = emptyMap<String, String>()
        while (true) {
            val o = parse(lines.next() ?: throw BackupData.Incompatible())
            val t = o.optString("t")
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
                    else -> { skipped++; null }
                }
            }
            if (record != null) each(record)
        }
        return Summary(created, people.size, count - skipped - homeSeen, skipped, home)
    }

    /**
     * Replaces all of the owner's data with the copy read from [r], all or nothing: everything is
     * checked again while it goes in, and must match what the owner saw ([expected]); otherwise
     * nothing changes.
     */
    suspend fun restore(db: PersonalDb, r: Reader, settings: Set<String>, expected: Summary) {
        db.withTransaction {
            BackupData.clearAll(db, settings)
            val got = read(r) { BackupData.put(db, it, settings) }
            if (got != expected) throw BackupData.Incompatible()
        }
    }

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
