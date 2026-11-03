package ua.sprotyv.personal

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A database in layout 3, as phones have it before version 0.3.0, opened by today's app: Room's
 * automatic migrations add the pregnancy line (4) and the documents table (5) and keep everything else. (Without encryption here:
 * the layout change is the same inside SQLCipher.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun layout3ToTodayKeepsTheMedicalCard() {
        val schema = JSONObject(File("../personal/schemas/ua.sprotyv.personal.PersonalDb/3.json").readText()).getJSONObject("database")
        val file = app.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO medical (id, name, birthDate, bloodGroup, allergies, medicines, conditions, notes, updatedAt) VALUES (1, 'Ім''я', '01.01.1990', 'A(II) Rh+', 'пилок', '', '', '', 5)")
            db.version = 3
        }
        val room = Room.databaseBuilder(app, PersonalDb::class.java, file.absolutePath).allowMainThreadQueries().build()
        try {
            val card = runBlocking { room.medical().get() }!!
            assertEquals("Ім'я", card.name)
            assertEquals("A(II) Rh+", card.bloodGroup)
            assertEquals("пилок", card.allergies)
            assertEquals("", card.pregnancy)
            assertEquals(6, room.openHelper.readableDatabase.version)
            // Layout 5 adds the documents table, empty
            assertEquals(0, runBlocking { room.documents().count() })
        } finally {
            room.close()
            file.delete()
        }
    }

    /** The oldest layout (1, before 0.2) to today, step by step through every automatic migration. */
    @Test fun layout1ToTodayKeepsSettings() {
        val schema = JSONObject(File("../personal/schemas/ua.sprotyv.personal.PersonalDb/1.json").readText()).getJSONObject("database")
        val file = app.getDatabasePath("migration-test-1.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO settings (`key`, value) VALUES ('safe_message', 'Я в безпеці')")
            db.version = 1
        }
        val room = Room.databaseBuilder(app, PersonalDb::class.java, file.absolutePath).allowMainThreadQueries().build()
        try {
            assertEquals("Я в безпеці", runBlocking { room.settings().get("safe_message") })
            assertEquals(0, runBlocking { room.notes().all().size })
            assertEquals(0, runBlocking { room.documents().count() })
            assertEquals(6, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
            file.delete()
        }
    }
}
