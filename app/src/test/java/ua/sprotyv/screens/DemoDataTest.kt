package ua.sprotyv.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ua.sprotyv.content.Content
import ua.sprotyv.content.Kit
import ua.sprotyv.content.MySupplies
import ua.sprotyv.content.Supplies
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Preset
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.KitCheck
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Note
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.personal.Setting
import ua.sprotyv.personal.backup.BackupCrypto
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.ui.Bookmark
import ua.sprotyv.ui.Bookmarks
import ua.sprotyv.ui.KEY_KIT_REVIEWED
import ua.sprotyv.ui.KEY_PATH
import ua.sprotyv.ui.KEY_SAFE_MESSAGE
import ua.sprotyv.ui.KEY_SUPPLIES
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The backup with every kind of data in it (placeholder data, see [Demo]) is encrypted, opens
 * again with its password and restores into a real database, all of it. Nothing is written out.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DemoDataTest {
    private val zone = ZoneId.of("Europe/Kyiv")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun backupRoundTrip() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val content = Content.get(app)
        val snap = Demo.snapshot(content)
        val people = snap.people
        val notes = snap.notes
        val plain = BackupData.encode(snap)
        val file = BackupCrypto.encrypt(plain, PASSWORD.toCharArray(), BackupCrypto.Purpose.BACKUP)
        // The file opens again with the same password, and reads back the same
        val back = BackupData.decode(BackupCrypto.decrypt(file, PASSWORD.toCharArray(), BackupCrypto.Purpose.BACKUP))
        assertEquals(snap.records, back.records)

        // And it restores into a real database as the app does it, all of it
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(ctx, ua.sprotyv.personal.PersonalDb::class.java).allowMainThreadQueries().build()
        try {
            kotlinx.coroutines.runBlocking {
                // Words typed with spaces and capitals open it too, as on the restore screen
                val typed = BackupCrypto.asWords("Test Backup Pass Word".toCharArray())!!
                val restored = BackupData.decode(BackupCrypto.decrypt(file, typed, BackupCrypto.Purpose.BACKUP))
                BackupData.replaceAll(db, restored, ua.sprotyv.ui.BACKED_UP_SETTINGS)
                assertEquals(people.size, db.people().all().size)
                assertEquals(notes.size, db.notes().all().size)
            }
        } finally {
            db.close()
        }
    }

    companion object {
        /** Test data only: the password is public on purpose. */
        const val PASSWORD = "test-backup-pass-word"
    }
}
