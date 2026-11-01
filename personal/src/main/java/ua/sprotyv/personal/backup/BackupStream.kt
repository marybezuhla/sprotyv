package ua.sprotyv.personal.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The backup file, written and read in parts, so memory never depends on its size: a copy may grow
 * to hundreds of megabytes and still open on a small phone.
 *
 * Layout: "SPRB" | version 2 | salt (16) | streaming ciphertext of a gzip stream.
 * The password goes through Argon2id with the same fixed settings as before ([BackupCrypto.DEFAULT]).
 * That key feeds Google Tink's AES-256-GCM-HKDF streaming encryption (1 MiB segments): Tink takes a
 * fresh key for each file from its own random salt and authenticates every segment, their order
 * and the end of the file, so a cut, reordered or changed file never reads as whole. The header
 * and the purpose are bound in as associated data. Compressed before encryption: only the size of
 * the file says anything, as with any file.
 */
object BackupStream {
    private val MAGIC = byteArrayOf(0x53, 0x50, 0x52, 0x42)
    private const val VERSION: Byte = 2
    private const val SALT_BYTES = 16
    const val HEADER_BYTES = 4 + 1 + SALT_BYTES
    private const val SEGMENT_BYTES = 1 shl 20
    private val LABEL = "sprotyv-backup-v2".toByteArray(Charsets.US_ASCII)

    /** The file, at most: far above any real copy, a bound on what a crafted one can make the phone read. */
    const val MAX_FILE_BYTES = 1L shl 30
    /** Unpacked, at most: a small crafted file can never unpack into something endless. */
    const val MAX_PLAIN_BYTES = 256L shl 20

    /** The key for one file, held only while it is written or until a restore is confirmed or dropped. */
    class Key internal constructor(internal val header: ByteArray, internal val ikm: ByteArray) {
        /** Our copy is cleared; Tink keeps its own until the memory is reused, which the JVM cannot prevent. */
        fun wipe() = ikm.fill(0)
        internal val aead by lazy { AesGcmHkdfStreaming(ikm, "HmacSha256", 32, SEGMENT_BYTES, 0) }
        /** True when [h] is the header this key was made for. */
        fun matches(h: ByteArray): Boolean = h.contentEquals(header)
    }

    /** True when the first bytes are those of a streamed copy; older copies are one piece ([BackupCrypto]). */
    fun isStream(head: ByteArray): Boolean = head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC)

    fun newKey(password: CharArray, params: BackupCrypto.KdfParams = BackupCrypto.DEFAULT): Key {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        return Key(MAGIC + VERSION + salt, BackupCrypto.deriveKey(password, salt, params))
    }

    /** The key a file's header and a password give. Whether it is right shows when the first part opens. */
    fun keyFor(header: ByteArray, password: CharArray, params: BackupCrypto.KdfParams = BackupCrypto.DEFAULT): Key {
        if (header.size != HEADER_BYTES || !isStream(header)) throw BackupCrypto.NotABackup()
        if (header[4] > VERSION) throw BackupData.FromNewer()
        if (header[4] != VERSION) throw BackupCrypto.NotABackup()
        return Key(header.copyOf(), BackupCrypto.deriveKey(password, header.copyOfRange(5, HEADER_BYTES), params))
    }

    /** Reads exactly the header from [input]; a shorter file is not ours. */
    fun readHeader(input: InputStream): ByteArray {
        val h = ByteArray(HEADER_BYTES)
        var got = 0
        while (got < h.size) {
            val n = input.read(h, got, h.size - got)
            if (n < 0) throw BackupCrypto.NotABackup()
            got += n
        }
        return h
    }

    private fun aad(key: Key) = key.header + LABEL

    /** Writes the header to [out]; what is then written to the result is compressed and encrypted. Closing it finishes the file. */
    fun writer(out: OutputStream, key: Key): OutputStream {
        out.write(key.header)
        return GZIPOutputStream(key.aead.newEncryptingStream(out, aad(key)), 64 * 1024)
    }

    /**
     * [input] stands right after the header, which must be [key]'s. Reading gives the data back;
     * a wrong password or any change in the file shows as [BackupCrypto.WrongPasswordOrDamaged].
     */
    fun reader(input: InputStream, key: Key): InputStream {
        val decrypted = key.aead.newDecryptingStream(Bounded(input, MAX_FILE_BYTES - HEADER_BYTES), aad(key))
        return Failing(Bounded(GZIPInputStream(Failing(decrypted), 64 * 1024), MAX_PLAIN_BYTES))
    }

    /** Refuses to read past [limit] bytes: a file that size is not ours. */
    private class Bounded(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0 && ++count > limit) throw BackupCrypto.NotABackup() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { count += it; if (count > limit) throw BackupCrypto.NotABackup() } }
        override fun skip(n: Long): Long = throw java.io.IOException("No skipping")
    }

    /** Tink and gzip say "wrong" as an IOException; the screen says it in one way, never with details. */
    private class Failing(input: InputStream) : FilterInputStream(input) {
        override fun read(): Int = guard { super.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = guard { super.read(b, off, len) }
        private inline fun guard(block: () -> Int): Int = try {
            block()
        } catch (e: BackupCrypto.NotABackup) {
            throw e
        } catch (e: java.io.IOException) {
            throw BackupCrypto.WrongPasswordOrDamaged()
        }
    }
}
