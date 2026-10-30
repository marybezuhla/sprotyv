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
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<Person>>

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
