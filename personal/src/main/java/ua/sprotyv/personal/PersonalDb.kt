package ua.sprotyv.personal

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** A setting or small fact the owner chose (region, language, simple mode). */
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: Setting)

    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): kotlinx.coroutines.flow.Flow<String?>

    @Query("SELECT * FROM settings")
    suspend fun all(): List<Setting>

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun delete(key: String)
}

/**
 * The owner's own data, always encrypted with SQLCipher. Every layout change gets a migration,
 * never a wipe: versions 2 and 3 only add tables (3: notes), so Room's automatic migrations from
 * the committed layouts (personal/schemas) are exact.
 */
@Database(
    entities = [Setting::class, MedicalCard::class, Person::class, FamilyPlan::class, PlanRow::class, KitCheck::class, Note::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class PersonalDb : RoomDatabase() {
    abstract fun settings(): SettingDao
    abstract fun medical(): MedicalDao
    abstract fun people(): PeopleDao
    abstract fun plan(): PlanDao
    abstract fun kit(): KitDao
    abstract fun notes(): NoteDao

    companion object {
        const val FILE_NAME = "personal.db"

        /** Opens the encrypted database. Call off the main thread. */
        fun open(context: Context, passphrase: ByteArray): PersonalDb {
            System.loadLibrary("sqlcipher")
            val db = Room.databaseBuilder(context.applicationContext, PersonalDb::class.java, FILE_NAME)
                // Third argument: write-ahead logging off. With TRUNCATE no second file with
                // recent pages stays next to the database.
                .openHelperFactory(SupportOpenHelperFactory(passphrase, Hardening, false))
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
            // Open now, so a wrong key fails here and not later on a screen
            db.openHelper.writableDatabase
            return db
        }

        fun exists(context: Context): Boolean = context.getDatabasePath(FILE_NAME).exists()
    }
}

/**
 * Before the key, decrypted pages and keys in freed memory are wiped; after it,
 * temporary data stays in memory and deleted records are overwritten.
 */
internal object Hardening : net.zetetic.database.sqlcipher.SQLiteDatabaseHook {
    override fun preKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) {
        apply(connection, "PRAGMA cipher_memory_security = ON")
    }

    override fun postKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) {
        apply(connection, "PRAGMA temp_store = MEMORY")
        apply(connection, "PRAGMA secure_delete = ON")
    }

    // A setting that cannot be applied never keeps Sprotyv from opening
    private fun apply(connection: net.zetetic.database.sqlcipher.SQLiteConnection, sql: String) {
        try { connection.executeRaw(sql, null, null) } catch (e: RuntimeException) { }
    }
}
