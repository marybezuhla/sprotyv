package ua.sprotyv.personal.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun writtenEncryptedAndReadBack() {
        val files = DocFiles(tmp.newFolder("docs"))
        val plain = ByteArray(300_000) { (it * 7).toByte() }
        val w = files.write(plain.inputStream())
        assertEquals(plain.size.toLong(), w.size)
        val onDisk = java.io.File(files.dir, w.file).readBytes()
        // Nothing of the plain bytes on the storage
        assertFalse(String(onDisk, Charsets.ISO_8859_1).contains(String(plain.copyOfRange(1000, 1064), Charsets.ISO_8859_1)))
        assertTrue(files.open(w.file, w.key).readBytes().contentEquals(plain))
    }

    @Test fun anotherKeyOrAChangedFileOpensNothing() {
        val files = DocFiles(tmp.newFolder("docs"))
        val w = files.write(ByteArray(1000) { 1 }.inputStream())
        assertTrue(runCatching { files.open(w.file, ByteArray(32)).readBytes() }.isFailure)
        val f = java.io.File(files.dir, w.file)
        val b = f.readBytes(); b[b.size / 2] = (b[b.size / 2] + 1).toByte(); f.writeBytes(b)
        assertTrue(runCatching { files.open(w.file, w.key).readBytes() }.isFailure)
    }

    @Test fun tooLargeLeavesNothing() {
        val files = DocFiles(tmp.newFolder("docs"))
        assertTrue(runCatching { files.write(ByteArray(2000).inputStream(), limit = 1000) }.exceptionOrNull() is DocFiles.TooLarge)
        assertEquals(0, files.dir.listFiles()!!.size)
    }

    @Test fun sweepKeepsOnlyNamedFiles() {
        val files = DocFiles(tmp.newFolder("docs"))
        val a = files.write(ByteArray(10).inputStream())
        val b = files.write(ByteArray(10).inputStream())
        files.sweep(setOf(a.file), graceMs = 0)
        assertTrue(java.io.File(files.dir, a.file).exists())
        assertFalse(java.io.File(files.dir, b.file).exists())
    }

    @Test fun sweepSparesFreshFiles() {
        val files = DocFiles(tmp.newFolder("docs"))
        val a = files.write(ByteArray(10).inputStream())
        // Just written, no row yet (an import in progress): kept
        files.sweep(emptySet())
        assertTrue(java.io.File(files.dir, a.file).exists())
    }
}
