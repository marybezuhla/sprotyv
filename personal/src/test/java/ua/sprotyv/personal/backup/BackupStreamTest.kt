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
        people = listOf(Person(id = 3, name = "Контакт 1", phone = "+380000000101", ice = true)),
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

    @Test fun partsCutSwappedOrRepeatedAreRefused() {
        // Random bytes do not compress: more than three 1 MiB parts
        val data = ByteArray(3_500_000).also { java.util.Random(7).nextBytes(it) }
        val key = BackupStream.newKey(pw, fast)
        val out = ByteArrayOutputStream()
        BackupStream.writer(out, key).use { it.write(data) }
        val file = out.toByteArray()
        fun readAll(f: ByteArray): ByteArray {
            val input = ByteArrayInputStream(f)
            val k = BackupStream.keyFor(BackupStream.readHeader(input), pw, fast)
            return BackupStream.reader(input, k).readBytes()
        }
        assertTrue(readAll(file).contentEquals(data))
        val seg = 1 shl 20
        val h = BackupStream.HEADER_BYTES
        // Parts 2 and 3 start at h + 1 MiB and h + 2 MiB (the first part holds Tink's own header)
        val p1 = h + seg; val p2 = h + 2 * seg
        expect<BackupCrypto.WrongPasswordOrDamaged> { readAll(file.copyOf(p2)) }
        expect<BackupCrypto.WrongPasswordOrDamaged> { readAll(file.copyOf(p1)) }
        val swapped = file.copyOf().also { f ->
            val a = file.copyOfRange(p1, p1 + seg); val b = file.copyOfRange(p2, p2 + seg)
            b.copyInto(f, p1); a.copyInto(f, p2)
        }
        expect<BackupCrypto.WrongPasswordOrDamaged> { readAll(swapped) }
        val repeated = file.copyOf().also { f -> file.copyOfRange(p1, p1 + seg).copyInto(f, p2) }
        expect<BackupCrypto.WrongPasswordOrDamaged> { readAll(repeated) }
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

    // Documents: made-up bytes, as a file source would give them
    private val docBytes = mapOf("a" to ByteArray(700_000) { (it % 251).toByte() }, "b" to ByteArray(10) { 7 })
    private val docs = listOf(
        ua.sprotyv.personal.Document(id = 1, title = "Документ 1", mime = "application/pdf", size = 700_000, file = "a", key = ByteArray(32), addedAt = 5),
        ua.sprotyv.personal.Document(id = 2, title = "Фото 2", mime = "image/jpeg", size = 10, file = "b", key = ByteArray(32), addedAt = 6),
    )

    private fun sealWithDocs(list: List<ua.sprotyv.personal.Document> = docs, source: BackupLines.DocSource = BackupLines.DocSource { ByteArrayInputStream(docBytes.getValue(it.file)) }): ByteArray {
        val key = BackupStream.newKey(pw, fast)
        val out = ByteArrayOutputStream()
        BackupStream.writer(out, key).bufferedWriter().use { BackupLines.write(snapshot, it, list, source) }
        return out.toByteArray()
    }

    @Test fun documentsTravelWholeAndInOrder() {
        val got = mutableListOf<Any>()
        val s = open(sealWithDocs()) { got += it }
        assertEquals(2, s.docs)
        assertEquals(700_010L, s.docBytes)
        // Documents do not change the owner's record count
        assertEquals(snapshot.records, s.records)
        val starts = got.filterIsInstance<BackupLines.DocStart>()
        assertEquals(listOf("Документ 1", "Фото 2"), starts.map { it.title })
        val first = got.dropWhile { it !is BackupLines.DocStart }.drop(1).takeWhile { it !is BackupLines.DocEnd }.filterIsInstance<BackupLines.DocPart>()
        assertTrue(first.fold(ByteArray(0)) { a, p -> a + p.bytes }.contentEquals(docBytes.getValue("a")))
        assertEquals(1, got.count { it === BackupLines.DocsMarker })
        assertEquals(2, got.count { it === BackupLines.DocEnd })
    }

    @Test fun copyWithoutDocumentsSaysSo() {
        assertEquals(-1, open(seal(snapshot)).docs)
        assertEquals(0, open(sealWithDocs(emptyList())).docs)
    }

    @Test fun aDocumentThatChangedIsNotWritten() {
        // The row says 10 bytes, the file gives 9: the copy is not made at all
        val short = BackupLines.DocSource { ByteArrayInputStream(ByteArray(9)) }
        try {
            sealWithDocs(listOf(docs[1]), short)
            throw AssertionError("Wrote a copy with a document of the wrong size")
        } catch (e: java.io.IOException) {
        }
    }

    @Test fun missingOrExtraDocumentPartsAreRefused() {
        val text = String(open2(sealWithDocs()), Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotEmpty() }
        val partLine = lines.indexOfFirst { it.contains("\"docpart\"") }
        // Drop one part (and fix the count, so only the part check can refuse it)
        val dropped = lines.toMutableList().apply { removeAt(partLine) }
        expectIncompatible(dropped.fixEnd())
        // The same part twice
        val twice = lines.toMutableList().apply { add(partLine, lines[partLine]) }
        expectIncompatible(twice.fixEnd())
    }

    private fun List<String>.fixEnd(): String {
        val n = size - 2
        return (dropLast(1) + "{\"t\":\"end\",\"n\":$n}").joinToString("\n", postfix = "\n")
    }

    private fun open2(file: ByteArray): ByteArray {
        val input = ByteArrayInputStream(file)
        val key = BackupStream.keyFor(BackupStream.readHeader(input), pw, fast)
        return BackupStream.reader(input, key).readBytes()
    }

    private fun expectIncompatible(text: String) {
        try {
            runBlocking { BackupLines.read(text.reader()) { } }
        } catch (e: BackupData.Incompatible) {
            return
        }
        throw AssertionError("A copy with broken document parts was taken as whole")
    }
}
