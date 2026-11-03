package ua.sprotyv.personal

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The owner's medical card: one record. Everything is free text the owner types; nothing is
 * checked against medical rules, and nothing leaves the phone unless the owner prints or shares it.
 */
@Entity(tableName = "medical")
data class MedicalCard(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val birthDate: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val medicines: String = "",
    val conditions: String = "",
    val notes: String = "",
    /** Pregnancy and its term, in the owner's words: shown first to rescuers. Added in layout 4. */
    @androidx.room.ColumnInfo(defaultValue = "") val pregnancy: String = "",
    val updatedAt: Long = 0,
)

/** Someone the owner trusts: for "I am safe" and as an emergency contact (ICE). */
@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val relation: String = "",
    /** Gets the "I am safe" message. */
    val safeList: Boolean = true,
    /** Shown on the medical card as the contact in an emergency. */
    val ice: Boolean = false,
    /** The owner's own order, most important first (lower first); then by name. */
    @androidx.room.ColumnInfo(defaultValue = "0") val sort: Int = 0,
)

/** The family plan: one record with the meeting points and the go-between. */
@Entity(tableName = "plan")
data class FamilyPlan(
    @PrimaryKey val id: Int = 1,
    /** Near home, for leaving the building. */
    val point1: String = "",
    /** Within the district, if home is not safe. */
    val point2: String = "",
    /** Outside the town, if the town must be left. */
    val point3: String = "",
    /** A relative elsewhere everyone calls when they cannot reach each other. */
    val goBetweenName: String = "",
    val goBetweenPhone: String = "",
    val notes: String = "",
    val updatedAt: Long = 0,
)

/** A line of the plan: a role ("who — does what") or an "if — then" rule. */
@Entity(tableName = "plan_rows")
data class PlanRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [KIND_ROLE] or [KIND_IF_THEN]. */
    val kind: String,
    val first: String,
    val second: String,
    val sort: Int = 0,
) {
    companion object {
        const val KIND_ROLE = "role"
        const val KIND_IF_THEN = "ifthen"
    }
}

/** A ticked item of the emergency kit, keyed by the item's text in the guide. */
@Entity(tableName = "kit")
data class KitCheck(
    @PrimaryKey val key: String,
    val checked: Boolean,
    val at: Long,
)

@Dao
interface MedicalDao {
    @Query("SELECT * FROM medical WHERE id = 1")
    fun observe(): Flow<MedicalCard?>

    @Query("SELECT * FROM medical WHERE id = 1")
    suspend fun get(): MedicalCard?

    @Upsert
    suspend fun save(card: MedicalCard)

    @Query("DELETE FROM medical")
    suspend fun clear()
}

@Dao
interface PeopleDao {
    @Query("SELECT * FROM people ORDER BY sort, name COLLATE NOCASE")
    fun observe(): Flow<List<Person>>

    @Query("SELECT COUNT(*) FROM people")
    suspend fun count(): Int

    @Query("UPDATE people SET sort = :sort WHERE id = :id")
    suspend fun setSort(id: Long, sort: Int)

    @Upsert
    suspend fun save(person: Person): Long

    @Delete
    suspend fun delete(person: Person)

    @Query("SELECT * FROM people ORDER BY id")
    suspend fun all(): List<Person>

    @Query("DELETE FROM people")
    suspend fun clear()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan WHERE id = 1")
    suspend fun get(): FamilyPlan?

    @Query("SELECT * FROM plan WHERE id = 1")
    fun observe(): Flow<FamilyPlan?>

    @Upsert
    suspend fun save(plan: FamilyPlan)

    @Query("SELECT * FROM plan_rows WHERE kind = :kind ORDER BY sort, id")
    fun observeRows(kind: String): Flow<List<PlanRow>>

    @Upsert
    suspend fun saveRow(row: PlanRow): Long

    @Delete
    suspend fun deleteRow(row: PlanRow)

    @Query("SELECT * FROM plan_rows ORDER BY kind, sort, id")
    suspend fun allRows(): List<PlanRow>

    @Query("DELETE FROM plan")
    suspend fun clear()

    @Query("DELETE FROM plan_rows")
    suspend fun clearRows()
}

@Dao
interface KitDao {
    @Query("SELECT * FROM kit")
    fun observe(): Flow<List<KitCheck>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(check: KitCheck)

    @Query("DELETE FROM kit")
    suspend fun clear()

    @Query("SELECT * FROM kit")
    suspend fun all(): List<KitCheck>
}

/** A note of the owner's own: free text, kept encrypted with the rest of "Mine". */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val text: String = "",
    /** Kept at the top of the list. */
    val pinned: Boolean = false,
    val updatedAt: Long = 0,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC, id DESC")
    fun observe(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Upsert
    suspend fun save(note: Note): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM notes ORDER BY id")
    suspend fun all(): List<Note>

    @Query("DELETE FROM notes")
    suspend fun clear()
}

/**
 * A document of the owner's own (a passport scan, test results): the file is encrypted on its own
 * with [key], which lives only here, in the encrypted database. So the lock on "My data" covers
 * the files too, and a file without its row opens nothing.
 */
@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** "image/jpeg" or "application/pdf". */
    val mime: String,
    /** Bytes before encryption. */
    val size: Long,
    /** The file's random name in the documents folder ([ua.sprotyv.personal.docs.DocFiles]). */
    val file: String,
    val key: ByteArray,
    val addedAt: Long,
) {
    // Compared by what it is, not by the key array's identity
    override fun equals(other: Any?): Boolean = other is Document && id == other.id && title == other.title && mime == other.mime &&
        size == other.size && file == other.file && key.contentEquals(other.key) && addedAt == other.addedAt
    override fun hashCode(): Int = file.hashCode()
}

/** A document as the list shows it: never its key. */
data class DocInfo(val id: Long, val title: String, val mime: String, val size: Long, val addedAt: Long)

@Dao
interface DocumentDao {
    @Query("SELECT id, title, mime, size, addedAt FROM documents ORDER BY addedAt DESC, id DESC")
    fun observe(): Flow<List<DocInfo>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): Document?

    @Query("SELECT * FROM documents ORDER BY id")
    suspend fun all(): List<Document>

    @Query("SELECT file FROM documents")
    suspend fun files(): List<String>

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(size), 0) FROM documents")
    suspend fun bytes(): Long

    @androidx.room.Insert
    suspend fun add(d: Document): Long

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM documents")
    suspend fun clear()
}
