package ua.sprotyv.personal.backup

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.KitCheck
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PersonalDb
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.personal.Setting
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Everything the owner keeps, as one JSON document, and back. Named fields only: the restore
 * takes nothing it does not know, checks every value, and replaces all data in one transaction,
 * so a broken file changes nothing. Kept in memory only, never written unencrypted.
 */
object BackupData {
    private const val FORMAT = 1
    private const val KIND_BACKUP = "backup"
    private const val KIND_PLAN = "plan"

    /** Generous limits: real data is far smaller; anything larger is not ours. */
    private const val MAX_TEXT = 20_000
    private const val MAX_ITEMS = 2_000

    /** Our files nest four levels at most; anything deeper is refused before parsing. */
    private const val MAX_DEPTH = 6
    private const val MAX_HOME = 40
    private const val MAX_HOME_TEXT = 2_000
    private val HOME_KEY = Regex("^[a-z_]{1,30}$")

    /** The file does not fit this version of Sprotyv (or is not a Sprotyv file at all). */
    class Incompatible : Exception()

    /** Made by a newer Sprotyv: update the app first. */
    class FromNewer : Exception()

    data class Snapshot(
        val created: Long,
        val medical: MedicalCard?,
        val people: List<Person>,
        val plan: FamilyPlan?,
        val rows: List<PlanRow>,
        val kit: List<KitCheck>,
        val settings: List<Setting>,
        /**
         * How the app looks and is set up (mode, tiles, theme and so on), as plain names and
         * values chosen by the app, so a new phone needs no setting up again. Empty in older copies.
         */
        val home: Map<String, String> = emptyMap(),
    ) {
        /** For the "what is in it" line before anything is replaced. */
        val records: Int get() = (if (medical != null) 1 else 0) + people.size + (if (plan != null) 1 else 0) + rows.size + kit.size + settings.size
    }

    data class SharedPlan(val plan: FamilyPlan, val rows: List<PlanRow>)

    /**
     * [settings]: the setting keys a backup carries (the "I am safe" text, the kit check date).
     * Others stay on this phone: a backup never brings or changes anything else.
     */
    suspend fun read(db: PersonalDb, settings: Set<String>): Snapshot = Snapshot(
        created = System.currentTimeMillis(),
        medical = db.medical().get(),
        people = db.people().all(),
        plan = db.plan().get(),
        rows = db.plan().allRows(),
        kit = db.kit().all(),
        settings = db.settings().all().filter { it.key in settings },
    )

    /** Replaces all of the owner's data with [s], all or nothing. Only the [settings] keys are touched. */
    suspend fun replaceAll(db: PersonalDb, s: Snapshot, settings: Set<String>) {
        db.withTransaction {
            db.medical().clear(); db.people().clear(); db.plan().clear(); db.plan().clearRows()
            db.kit().clear()
            settings.forEach { db.settings().delete(it) }
            s.medical?.let { db.medical().save(it) }
            s.people.forEach { db.people().save(it) }
            s.plan?.let { db.plan().save(it) }
            s.rows.forEach { db.plan().saveRow(it) }
            s.kit.forEach { db.kit().set(it) }
            s.settings.filter { it.key in settings }.forEach { db.settings().put(it) }
        }
    }

    /** Replaces only the family plan (from a relative's code), all or nothing. */
    suspend fun replacePlan(db: PersonalDb, p: SharedPlan) {
        db.withTransaction {
            db.plan().clear(); db.plan().clearRows()
            db.plan().save(p.plan.copy(id = 1, updatedAt = System.currentTimeMillis()))
            p.rows.forEach { db.plan().saveRow(it.copy(id = 0)) }
        }
    }

    // ---- JSON ----

    fun encode(s: Snapshot): ByteArray = JSONObject()
        .put("format", FORMAT).put("app", "sprotyv").put("kind", KIND_BACKUP).put("created", s.created)
        .put("medical", s.medical?.let(::medical) ?: JSONObject.NULL)
        .put("people", JSONArray().apply { s.people.forEach { put(person(it)) } })
        .put("plan", s.plan?.let(::plan) ?: JSONObject.NULL)
        .put("rows", JSONArray().apply { s.rows.forEach { put(row(it)) } })
        .put("kit", JSONArray().apply { s.kit.forEach { put(JSONObject().put("key", it.key).put("checked", it.checked).put("at", it.at)) } })
        .put("settings", JSONArray().apply { s.settings.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) } })
        .put("home", JSONObject().apply { s.home.toSortedMap().forEach { (k, v) -> put(k, v) } })
        .toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Snapshot {
        val root = root(bytes, KIND_BACKUP)
        return guard {
            Snapshot(
                created = root.getLong("created"),
                medical = objectOrNull(root, "medical")?.let { m ->
                    MedicalCard(
                        name = text(m, "name"), birthDate = text(m, "birthDate"), bloodGroup = text(m, "bloodGroup"),
                        allergies = text(m, "allergies"), medicines = text(m, "medicines"), conditions = text(m, "conditions"),
                        notes = text(m, "notes"), updatedAt = m.optLong("updatedAt", 0),
                    )
                },
                people = items(root.getJSONArray("people")).map { p ->
                    Person(
                        id = p.getLong("id").also { require(it > 0) }, name = text(p, "name"), phone = text(p, "phone"),
                        relation = text(p, "relation"), safeList = p.getBoolean("safeList"), ice = p.getBoolean("ice"),
                    )
                },
                plan = objectOrNull(root, "plan")?.let(::planOf),
                rows = items(root.getJSONArray("rows")).map(::rowOf),
                kit = items(root.getJSONArray("kit")).map { KitCheck(text(it, "key").also { k -> require(k.isNotEmpty()) }, it.getBoolean("checked"), it.getLong("at")) },
                settings = items(root.getJSONArray("settings")).map { Setting(text(it, "key").also { k -> require(k.isNotEmpty()) }, text(it, "value")) },
                home = root.optJSONObject("home")?.let(::homeOf) ?: emptyMap(),
            ).also { s ->
                // The same person, row, tick or setting twice: not a file Sprotyv wrote
                require(s.people.map { it.id }.toSet().size == s.people.size)
                require(s.rows.map { it.id }.filter { it > 0 }.let { it.toSet().size == it.size })
                require(s.kit.map { it.key }.toSet().size == s.kit.size)
                require(s.settings.map { it.key }.toSet().size == s.settings.size)
            }
        }
    }

    /** The look and set-up: a few short names and values; anything else refuses the file. */
    private fun homeOf(o: JSONObject): Map<String, String> {
        require(o.length() <= MAX_HOME)
        return o.keys().asSequence().associateWith { k ->
            require(HOME_KEY.matches(k))
            val v = o.get(k)
            require(v is String && v.length <= MAX_HOME_TEXT)
            v
        }
    }

    /** A field that must be an object or null; anything else refuses the file instead of being dropped. */
    private fun objectOrNull(o: JSONObject, name: String): JSONObject? {
        val v = o.get(name)
        if (v == JSONObject.NULL) return null
        require(v is JSONObject)
        return v
    }

    /** The family plan alone, compressed so the code stays small enough to scan. */
    fun encodePlan(p: SharedPlan): ByteArray = deflate(
        JSONObject().put("format", FORMAT).put("app", "sprotyv").put("kind", KIND_PLAN)
            .put("plan", plan(p.plan)).put("rows", JSONArray().apply { p.rows.forEach { put(row(it)) } })
            .toString().toByteArray(Charsets.UTF_8),
    )

    fun decodePlan(bytes: ByteArray): SharedPlan {
        val root = root(inflate(bytes), KIND_PLAN)
        return guard { SharedPlan(planOf(root.getJSONObject("plan")), items(root.getJSONArray("rows")).map(::rowOf)) }
    }

    private fun root(bytes: ByteArray, kind: String): JSONObject {
        // org.json parses by recursion: a crafted file nested thousands deep would crash the app
        if (!shallow(bytes)) throw Incompatible()
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw Incompatible()
        }
        if (root.optString("app") != "sprotyv" || root.optString("kind") != kind) throw Incompatible()
        val format = root.optInt("format", -1)
        if (format > FORMAT) throw FromNewer()
        if (format != FORMAT) throw Incompatible()
        return root
    }

    /** Counts brackets outside strings; true when nesting stays within [MAX_DEPTH]. */
    internal fun shallow(bytes: ByteArray): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > MAX_DEPTH) return false
                '}', ']' -> depth--
            }
        }
        return true
    }

    private inline fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: Incompatible) {
        throw e
    } catch (e: Exception) {
        // A missing field, a wrong type or a value out of bounds: the whole file is refused
        throw Incompatible()
    }

    private fun items(a: JSONArray): List<JSONObject> {
        require(a.length() <= MAX_ITEMS)
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun text(o: JSONObject, name: String): String {
        val v = o.get(name)
        require(v is String && v.length <= MAX_TEXT)
        return v
    }

    private fun medical(m: MedicalCard) = JSONObject()
        .put("name", m.name).put("birthDate", m.birthDate).put("bloodGroup", m.bloodGroup).put("allergies", m.allergies)
        .put("medicines", m.medicines).put("conditions", m.conditions).put("notes", m.notes).put("updatedAt", m.updatedAt)

    private fun person(p: Person) = JSONObject()
        .put("id", p.id).put("name", p.name).put("phone", p.phone).put("relation", p.relation)
        .put("safeList", p.safeList).put("ice", p.ice)

    private fun plan(p: FamilyPlan) = JSONObject()
        .put("point1", p.point1).put("point2", p.point2).put("point3", p.point3)
        .put("goBetweenName", p.goBetweenName).put("goBetweenPhone", p.goBetweenPhone).put("notes", p.notes)
        .put("updatedAt", p.updatedAt)

    private fun planOf(o: JSONObject) = FamilyPlan(
        point1 = text(o, "point1"), point2 = text(o, "point2"), point3 = text(o, "point3"),
        goBetweenName = text(o, "goBetweenName"), goBetweenPhone = text(o, "goBetweenPhone"), notes = text(o, "notes"),
        updatedAt = o.optLong("updatedAt", 0),
    )

    private fun row(r: PlanRow) = JSONObject().put("id", r.id).put("kind", r.kind).put("first", r.first).put("second", r.second).put("sort", r.sort)

    private fun rowOf(o: JSONObject): PlanRow {
        val kind = text(o, "kind")
        require(kind == PlanRow.KIND_ROLE || kind == PlanRow.KIND_IF_THEN)
        return PlanRow(id = o.optLong("id", 0).coerceAtLeast(0), kind = kind, first = text(o, "first"), second = text(o, "second"), sort = o.optInt("sort", 0))
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            d.setInput(bytes); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            return out.toByteArray()
        } finally {
            d.end()
        }
    }

    /** Stops at 256 KB: a tiny code can never unpack into something huge. */
    private fun inflate(bytes: ByteArray): ByteArray {
        val i = Inflater(true)
        try {
            i.setInput(bytes)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!i.finished()) {
                val n = i.inflate(buf)
                if (n == 0 && (i.needsInput() || i.needsDictionary())) throw Incompatible()
                out.write(buf, 0, n)
                if (out.size() > 256 * 1024) throw Incompatible()
            }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw Incompatible()
        } finally {
            i.end()
        }
    }
}
