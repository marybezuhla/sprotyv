package ua.sprotyv.personal

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.system.Os
import android.system.OsConstants
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the database passphrase safe.
 *
 * The passphrase is 32 random bytes, stored only wrapped with an AES-256-GCM key in the phone's
 * keystore (StrongBox when available, otherwise the phone's secure hardware; [Unwrapped.Ok.softwareKey]
 * says when a phone has neither). Unlike a reviewed app the key needs no fingerprint or PIN per use, so
 * Sprotyv opens at once in an emergency. Limits, stated plainly in the README too:
 * - anyone holding the phone unlocked can open the data (including a phone that someone forces
 *   the owner to unlock); the optional app lock in stage 3 adds a second wrap, as in a reviewed app;
 * - "only while unlocked" protects unwrapping the key, not data already open; Sprotyv closes the
 *   database when it leaves the screen;
 * - a phone with no screen lock, or kept unlocked by Smart Lock, gives no lock protection at all.
 *
 * Nothing here ever deletes a key or a file: a key judged lost is set aside, never erased, so a
 * wrong judgement can still be undone.
 *
 * File format 1: version, alias, IV, wrapped passphrase. Version and alias are bound to the
 * ciphertext as associated data, so any change to the header is detected.
 */
class KeyVault(context: Context) {

    private val file = File(context.noBackupFilesDir, WRAPPED_FILE)
    private val tmp = File(context.noBackupFilesDir, "$WRAPPED_FILE.tmp")

    private class Wrapped(val alias: String, val iv: ByteArray, val data: ByteArray)

    sealed interface Unwrapped {
        class Ok(val passphrase: ByteArray, val softwareKey: Boolean) : Unwrapped

        /** Clear evidence the data cannot be opened any more: the key is gone or does not fit. */
        data object Lost : Unwrapped

        /** Could not tell (the keystore did not answer): nothing is judged, try again later. */
        data object Retry : Unwrapped
    }

    /** True when a wrapped passphrase exists, i.e. the app was set up before. */
    fun isSetUp(): Boolean = file.exists()

    /**
     * First start only: a fresh hardware key under a new random alias and a new random passphrase,
     * stored wrapped. Refuses if a key file already exists, so stored data is never replaced.
     */
    @Synchronized
    fun create(): Unwrapped.Ok {
        check(!file.exists()) { "Already set up" }
        tmp.delete()
        val alias = KEY_PREFIX + randomHex(8)
        val key = createKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(header(alias))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        writeWrapped(Wrapped(alias, cipher.iv, cipher.doFinal(passphrase)))
        return Unwrapped.Ok(passphrase, isSoftware(key))
    }

    /** The stored passphrase, or a clear verdict why not. Never throws for keystore trouble. */
    fun unwrap(): Unwrapped {
        val w = try {
            readWrapped()
        } catch (e: EOFException) {
            // Truncated key file
            return Unwrapped.Lost
        } catch (e: IOException) {
            // A read error may pass: nothing is judged
            return Unwrapped.Retry
        } catch (e: IllegalArgumentException) {
            // Damaged key file
            return Unwrapped.Lost
        }
        val key = try {
            keyStore().getKey(w.alias, null) as? SecretKey
        } catch (e: Exception) {
            return Unwrapped.Retry
        }
        if (key == null) return if (keyIsGone(w.alias)) Unwrapped.Lost else Unwrapped.Retry
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, w.iv))
                updateAAD(header(w.alias))
            }
            val passphrase = cipher.doFinal(w.data)
            if (passphrase.size != PASSPHRASE_BYTES) Unwrapped.Lost else Unwrapped.Ok(passphrase, isSoftware(key))
        } catch (e: KeyPermanentlyInvalidatedException) {
            Unwrapped.Lost
        } catch (e: AEADBadTagException) {
            Unwrapped.Lost
        } catch (e: Exception) {
            Unwrapped.Retry
        }
    }

    /**
     * Sets the key file aside under a new name (never deletes it, never deletes the keystore
     * entry), so a fresh start can begin. Only after [unwrap] said [Unwrapped.Lost].
     */
    @Synchronized
    fun setAside(stamp: Long) {
        tmp.delete()
        if (file.exists() && !file.renameTo(File(file.parentFile, "$WRAPPED_FILE.lost-$stamp"))) {
            error("Could not set the key file aside")
        }
    }

    /**
     * True only when the keystore answers normally twice, a moment apart, and still has no such key.
     * A keystore that throws or is busy gives false: no data is ever judged lost on a hiccup.
     */
    private fun keyIsGone(alias: String): Boolean {
        repeat(2) { attempt ->
            try {
                val ks = keyStore()
                // The keystore must answer a plain question before its "no" counts
                ks.aliases()
                if (ks.getKey(alias, null) != null) return false
            } catch (e: Exception) {
                return false
            }
            if (attempt == 0) Thread.sleep(400)
        }
        return true
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun isSoftware(key: SecretKey): Boolean = try {
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        info.securityLevel == KeyProperties.SECURITY_LEVEL_SOFTWARE
    } catch (e: Exception) {
        // Could not tell: warn rather than claim secure hardware
        true
    }

    private fun header(alias: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeByte(FORMAT_VERSION)
            writeUTF(alias)
            flush()
        }
        return bytes.toByteArray()
    }

    private fun writeWrapped(w: Wrapped) {
        try {
            FileOutputStream(tmp).use { stream ->
                val out = DataOutputStream(stream)
                out.writeByte(FORMAT_VERSION)
                out.writeUTF(w.alias)
                out.writeByte(w.iv.size)
                out.write(w.iv)
                out.writeInt(w.data.size)
                out.write(w.data)
                out.flush()
                // Really on the disk before it takes the real name
                stream.fd.sync()
            }
            if (!tmp.renameTo(file)) error("Could not save the wrapped key")
            syncDirectory(file.parentFile!!)
        } finally {
            tmp.delete()
        }
    }

    private fun readWrapped(): Wrapped =
        DataInputStream(file.inputStream()).use { input ->
            require(input.readUnsignedByte() == FORMAT_VERSION) { "Unknown key file format" }
            val alias = input.readUTF()
            require(alias.matches(ALIAS)) { "Damaged key file" }
            val ivSize = input.readUnsignedByte()
            require(ivSize == GCM_IV_BYTES) { "Damaged key file" }
            val iv = ByteArray(ivSize).also { input.readFully(it) }
            val size = input.readInt()
            require(size == PASSPHRASE_BYTES + GCM_TAG_BITS / 8) { "Damaged key file" }
            val data = ByteArray(size).also { input.readFully(it) }
            // Nothing may follow the wrapped passphrase
            require(input.read() == -1) { "Damaged key file" }
            Wrapped(alias, iv, data)
        }

    private fun syncDirectory(dir: File) {
        try {
            val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(fd) } finally { Os.close(fd) }
        } catch (e: Exception) {
            // Best effort; the file itself is already synced
        }
    }

    private fun createKey(alias: String): SecretKey = try {
        generate(alias, strongBox = true)
    } catch (e: StrongBoxUnavailableException) {
        generate(alias, strongBox = false)
    } catch (e: Exception) {
        // Some phones report StrongBox but fail inside it: the regular secure hardware is used instead
        generate(alias, strongBox = false)
    }

    private fun generate(alias: String, strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Only while the phone is unlocked; no fingerprint or PIN per use (see the class comment)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_PREFIX = "sprotyv_db_key_"
        private val ALIAS = Regex("sprotyv_db_key_[0-9a-f]{16}")
        private const val WRAPPED_FILE = "db_key.bin"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val PASSPHRASE_BYTES = 32
        private const val FORMAT_VERSION = 1
    }
}
