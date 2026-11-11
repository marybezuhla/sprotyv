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
 * never a wipe: versions 2 and 3 only add tables (3: notes), version 4 adds one column with a default
 * (the pregnancy line of the medical card), version 5 adds the documents table, version 6 the people's own order (a column with a default), so Room's automatic migrations from
 * the committed layouts (personal/schemas) are exact.
 */
@Database(
    entities = [Setting::class, MedicalCard::class, Person::class, FamilyPlan::class, PlanRow::class, KitCheck::class, Note::class, Document::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class PersonalDb : RoomDatabase() {
    abstract fun settings(): SettingDao
    abstract fun medical(): MedicalDao
    abstract fun people(): PeopleDao
    abstract fun plan(): PlanDao
    abstract fun kit(): KitDao
    abstract fun notes(): NoteDao
    abstract fun documents(): DocumentDao

    // The key this copy was opened with, wiped when it closes (SQLCipher holds it only while open)
    @Volatile private var key: ByteArray? = null

    override fun close() {
        try {
            super.close()
        } finally {
            key?.fill(0)
            key = null
        }
    }

    companion object {
        const val FILE_NAME = "personal.db"

        /**
         * Opens the encrypted database. Call off the main thread.
         *
         * The passphrase is 32 random bytes, so it is given to SQLCipher as a raw 256-bit key
         * (x'…'): SQLCipher's password stretching (PBKDF2, 256 000 rounds) adds nothing to a key that
         * is already random, and only made "Mine" slow to open. A database made before is opened
         * the old way once and re-encrypted with the raw key (PRAGMA rekey, all or nothing); a mark
         * next to the file says which way it is. Should the mark and the file ever disagree (the
         * app stopped between the two), the other way is tried before anything is said to be wrong.
         */
        fun open(context: Context, passphrase: ByteArray): PersonalDb {
            System.loadLibrary("sqlcipher")
            val raw = rawKey(passphrase)
            // Kept by the open database (and wiped when it closes); if nothing opened, wiped now
            return try { openWith(context, passphrase, raw) } catch (t: Throwable) { raw.fill(0); throw t }
        }

        private fun openWith(context: Context, passphrase: ByteArray, raw: ByteArray): PersonalDb {
            val mark = java.io.File(context.getDatabasePath(FILE_NAME).path + RAW_MARK)
            if (mark.exists() || !exists(context)) {
                val db = try {
                    build(context, raw)
                } catch (e: net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
                    // Only "this key does not fit": the file may still be the old way (marked, but
                    // re-encrypting did not commit). Any other trouble is reported as it is
                    if (!exists(context)) throw e
                    return otherKey(e) { upgrade(context, passphrase, raw, mark) }
                }
                runCatching { mark.createNewFile() }
                return db
            }
            return try {
                upgrade(context, passphrase, raw, mark)
            } catch (e: net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
                // Already re-encrypted, but the mark was not written yet
                otherKey(e) { build(context, raw).also { runCatching { mark.createNewFile() } } }
            }
        }

        /** The other key, tried only after "the key does not fit"; if it does not fit either, the first answer stands. */
        private inline fun otherKey(first: Exception, block: () -> PersonalDb): PersonalDb = try {
            block()
        } catch (e: net.zetetic.database.sqlcipher.SQLiteNotADatabaseException) {
            first.addSuppressed(e)
            throw first
        }

        /** The old way once, re-encrypted with the raw key, the mark written, opened again the new way. */
        private fun upgrade(context: Context, passphrase: ByteArray, raw: ByteArray, mark: java.io.File): PersonalDb {
            val old = build(context, passphrase)
            try {
                // One call with the key as bytes: no copy of it as text, a full disk reported as such
                (old.openHelper.writableDatabase as net.zetetic.database.sqlcipher.SQLiteDatabase).changePassword(raw)
            } finally {
                old.close()
            }
            val db = build(context, raw)
            runCatching { mark.createNewFile() }
            return db
        }

        // No autoClose, createFrom* or setQueryCallback here: Room would wrap the helper and the rekey cast would fail
        private fun build(context: Context, key: ByteArray): PersonalDb {
            val db = Room.databaseBuilder(context.applicationContext, PersonalDb::class.java, FILE_NAME)
                // Third argument: write-ahead logging off. With TRUNCATE no second file with
                // recent pages stays next to the database.
                .openHelperFactory(SupportOpenHelperFactory(key, Hardening, false))
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
            // Open now, so a wrong key fails here and not later on a screen
            try {
                db.openHelper.writableDatabase
            } catch (e: Exception) {
                db.close()
                throw e
            }
            // Only once it opened: a key that did not fit may still be tried the other way
            db.key = key
            return db
        }

        /** x'<64 hex digits>': SQLCipher's form for a raw 256-bit key. */
        internal fun rawKey(passphrase: ByteArray): ByteArray {
            require(passphrase.size == 32)
            val hex = "0123456789abcdef"
            val out = ByteArray(2 + 64 + 1)
            out[0] = 'x'.code.toByte(); out[1] = '\''.code.toByte()
            passphrase.forEachIndexed { i, b -> val v = b.toInt() and 0xFF; out[2 + 2 * i] = hex[v shr 4].code.toByte(); out[3 + 2 * i] = hex[v and 0x0F].code.toByte() }
            out[66] = '\''.code.toByte()
            return out
        }

        /** Next to the database: it is encrypted with the raw key. */
        const val RAW_MARK = ".raw"

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
        try { connection.executeRaw(sql, null, null) } catch (e: RuntimeException) { /* see above */ }
    }
}
