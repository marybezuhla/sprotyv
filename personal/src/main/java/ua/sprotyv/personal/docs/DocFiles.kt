package ua.sprotyv.personal.docs

import android.content.Context
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * The owner's documents on this phone, each file encrypted on its own: Google Tink's AES-256-GCM-HKDF
 * streaming encryption with a random key per file, kept only in the encrypted database. The file's
 * name is random and bound in, so a file renamed or swapped never opens as another. Plain bytes
 * never touch the storage: they go into the encryption as they come and are read back the same way.
 *
 * The folder is outside anything Android copies or shares (no-backup files, private to the app).
 */
class DocFiles(val dir: File) {

    /** A file just written: its name and key go into the database; until then it is an orphan [sweep] removes. */
    class Written(val file: String, val key: ByteArray, val size: Long)

    class TooLarge : RuntimeException()

    /** Writes one file as the bytes come; [finish] when all are in, [abort] to drop it. */
    inner class Writer internal constructor(private val limit: Long) {
        val file: String = randomName().also { synchronized(writing) { writing += it } }
        private val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        private val target = File(dir.apply { mkdirs() }, file)
        private var count = 0L
        private val raw = FileOutputStream(target)
        private val out: OutputStream = aead(key).newEncryptingStream(raw, aad(file))

        fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
            count += len
            if (count > limit) { abort(); throw TooLarge() }
            out.write(b, off, len)
        }

        fun finish(): Written {
            out.close()
            return Written(file, key, count)
        }

        fun abort() {
            try { out.close() } catch (e: Exception) { }
            key.fill(0)
            target.delete()
            done(file)
        }
    }

    fun writer(limit: Long = MAX_FILE_BYTES): Writer = Writer(limit)

    /** Writes all of [input] (at most [limit] bytes) as a new encrypted file. */
    fun write(input: InputStream, limit: Long = MAX_FILE_BYTES): Written {
        val w = writer(limit)
        try {
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                w.write(buf, 0, n)
            }
            return w.finish()
        } catch (e: Throwable) {
            w.abort()
            throw e
        }
    }

    /** The plain bytes of a file, read as they are decrypted; any change to the file fails the read. */
    fun open(file: String, key: ByteArray): InputStream {
        require(NAME.matches(file))
        return aead(key).newDecryptingStream(File(dir, file).inputStream(), aad(file))
    }

    fun delete(file: String) {
        if (NAME.matches(file)) File(dir, file).delete()
    }

    /**
     * Removes files the database does not name (a restore that did not finish, a deleted row), but
     * none newer than [graceMs]: an import may have written its file and not yet its row.
     */
    fun sweep(keep: Set<String>, graceMs: Long = 10 * 60_000L) {
        val now = System.currentTimeMillis()
        val busy = synchronized(writing) { writing.toSet() }
        dir.listFiles()?.forEach { f -> if (f.name !in keep && f.name !in busy && now - f.lastModified() >= graceMs) f.delete() }
    }

    /** A deleted document's file, kept while "Undo" is offered: sweeps leave it until [done]. */
    fun hold(file: String) { synchronized(writing) { writing += file } }

    /** A written file is named in the database now (or was dropped): sweeps may take it into account again. */
    fun done(file: String) { synchronized(writing) { writing -= file } }

    companion object {
        /** Files being written or waiting for their row: never swept, whatever their age. */
        private val writing = HashSet<String>()

        /** No more than this many documents, each and in all at most so large: only the most valuable. */
        const val MAX_DOCS = 20
        // Decimal megabytes, as the phone itself shows sizes
        const val MAX_FILE_BYTES = 10_000_000L
        const val MAX_TOTAL_BYTES = 100_000_000L

        private const val KEY_BYTES = 32
        private const val SEGMENT_BYTES = 64 * 1024
        private val NAME = Regex("^[0-9a-f]{32}$")

        fun of(context: Context): DocFiles = DocFiles(File(context.applicationContext.noBackupFilesDir, "documents"))

        private fun aead(key: ByteArray) = AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT_BYTES, 0)
        private fun aad(file: String) = "sprotyv-doc-v1:$file".toByteArray(Charsets.US_ASCII)

        private fun randomName(): String {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}
