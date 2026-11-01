package ua.sprotyv.personal.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Note
import ua.sprotyv.personal.Person
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupStreamTest {
    private val fast = BackupCrypto.KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private val pw = "тест-пароль-для-копії".toCharArray()

    private val snapshot = BackupData.Snapshot(
        created = 1_700_000_000_000,
        medical = MedicalCard(bloodGroup = "A(II) Rh+", notes = "рядок\nдругий"),
        people = listOf(Person(id = 3, name = "Олена", phone = "+380501234567", ice = true)),
        plan = null, rows = emptyList(), kit = emptyList(), settings = emptyList(),
        home = mapOf("mode" to "FULL"),
        notes = (1L..300L).map { Note(id = it, title = "Нотатка $it", text = "текст ".repeat(400), pinned = false, updatedAt = it) },
    )

    private fun seal(text: String, password: CharArray = pw): ByteArray {
        val key = BackupStream.newKey(password, fast)
        val out = ByteArrayOutputStream()
        BackupStream.writer(out, key).bufferedWriter().use { it.write(text) }
        return out.toByteArray()
    }

    private fun seal(s: BackupData.Snapshot): ByteArray {
        val key = BackupStream.newKey(pw, fast)
        val out = ByteArrayOutputStream()
        BackupStream.writer(out, key).bufferedWriter().use { BackupLines.write(s, it) }
        return out.toByteArray()
    }

    private fun open(file: ByteArray, password: CharArray = pw, each: (Any) -> Unit = {}): BackupLines.Summary = runBlocking {
        val input = ByteArrayInputStream(file)
        val key = BackupStream.keyFor(BackupStream.readHeader(input), password, fast)
        BackupStream.reader(input, key).bufferedReader().use { r -> BackupLines.read(r) { each(it) } }
    }

    @Test fun roundTripRecordByRecord() {
        val got = mutableListOf<Any>()
        val s = open(seal(snapshot)) { got += it }
        assertEquals(snapshot.created, s.created)
        assertEquals(1, s.people)
        assertEquals(snapshot.records, s.records)
        assertEquals(0, s.skipped)
        assertEquals(snapshot.home, s.home)
        assertEquals(listOf<Any>(snapshot.medical!!) + snapshot.people + snapshot.notes, got)
    }

    @Test fun compressedAndLooksRandom() {
        val file = seal(snapshot)
        // 300 notes of repeated words: compression makes the file much smaller than the text
        assertTrue(file.size < 40_000)
        assertTrue(BackupStream.isStream(file))
    }

    @Test fun wrongPasswordAnyChangeOrCutIsRefused() {
        val file = seal(snapshot)
        expect<BackupCrypto.WrongPasswordOrDamaged> { open(file, "інший-пароль-зовсім".toCharArray()) }
        for (i in BackupStream.HEADER_BYTES until file.size step 97) {
            val bad = file.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            expectAny { open(bad) }
        }
        // Cut short anywhere: never taken as a whole copy
        for (cut in listOf(file.size - 1, file.size - 20, file.size / 2, BackupStream.HEADER_BYTES + 40)) {
            expectAny { open(file.copyOf(cut)) }
        }
        // The header is bound in too: another salt gives another key
        val salted = file.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        expectAny { open(salted) }
    }

    @Test fun kindsFromANewerVersionAreSkippedNotRefused() {
        val text = """{"app":"sprotyv","kind":"backup","format":2,"created":5}
{"t":"person","v":{"id":1,"name":"А","phone":"","relation":"","safeList":false,"ice":false}}
{"t":"meal","v":{"what":"борщ"}}
{"t":"end","n":2}
"""
        val s = open(seal(text))
        assertEquals(1, s.records)
        assertEquals(1, s.skipped)
    }

    @Test fun oddFilesAreRefused() {
        val head = """{"app":"sprotyv","kind":"backup","format":2,"created":5}"""
        // No end line, a wrong count, lines after the end, a newer format
        expect<BackupData.Incompatible> { open(seal("$head\n")) }
        expect<BackupData.Incompatible> { open(seal("$head\n{\"t\":\"end\",\"n\":3}\n")) }
        expect<BackupData.Incompatible> { open(seal("$head\n{\"t\":\"end\",\"n\":0}\n{\"t\":\"end\",\"n\":0}\n")) }
        expect<BackupData.FromNewer> { open(seal("""{"app":"sprotyv","kind":"backup","format":3,"created":5}""" + "\n")) }
        // The same person twice, and a line far too long
        val p = """{"t":"person","v":{"id":1,"name":"А","phone":"","relation":"","safeList":false,"ice":false}}"""
        expect<BackupData.Incompatible> { open(seal("$head\n$p\n$p\n{\"t\":\"end\",\"n\":2}\n")) }
        expect<BackupData.Incompatible> { open(seal("$head\n" + "x".repeat(600_000) + "\n")) }
        // An older one-piece copy is not mistaken for a streamed one
        val old = BackupCrypto.encrypt(BackupData.encode(snapshot), pw, BackupCrypto.Purpose.BACKUP, fast)
        assertTrue(!BackupStream.isStream(old))
    }

    private inline fun <reified E : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            assertTrue("Expected ${E::class.simpleName}, got $e", e is E)
            return
        }
        throw AssertionError("Expected ${E::class.simpleName}")
    }

    private inline fun expectAny(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            return
        }
        throw AssertionError("A damaged file was taken as whole")
    }
}
