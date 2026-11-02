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
 * says when a phone has neither). By default the key needs no fingerprint or PIN per use, so
 * Sprotyv opens at once in an emergency. The owner may lock "Mine" ([Mode.locked]): the passphrase
 * is then wrapped with a key that works only right after a fingerprint or the phone's PIN, once per
 * use; with [Mode.pinOnly] the hardware refuses a fingerprint for it at all.
 * Limits, stated plainly in the README too:
 * - without the lock, anyone holding the phone unlocked can open the data (including a phone that
 *   someone forces the owner to unlock);
 * - "only while unlocked" protects unwrapping the key, not data already open; Sprotyv closes the
 *   database when it leaves the screen;
 * - a phone with no screen lock, or kept unlocked by Smart Lock, gives no lock protection at all.
 *
 * Nothing here deletes a file, and a key judged lost is set aside, never erased, so a wrong
 * judgement can still be undone. The one key ever deleted is the old one after the lock was
 * changed and the new wrapping written ([finishRewrap]).
 *
 * File format 2: version, flags (bit 0 locked, bit 1 PIN only), alias, IV, wrapped passphrase.
 * Format 1 (older installs) has no flags. The header is bound to the ciphertext as associated
 * data, so any change to it, the flags included, is detected.
 */
class KeyVault(context: Context) {

    private val file = File(context.noBackupFilesDir, WRAPPED_FILE)
    private val tmp = File(context.noBackupFilesDir, "$WRAPPED_FILE.tmp")

    private class Wrapped(val version: Int, val mode: Mode, val alias: String, val iv: ByteArray, val data: ByteArray)

    /** How the passphrase is wrapped: [locked] needs the owner per use; [pinOnly] refuses a fingerprint. */
    data class Mode(val locked: Boolean, val pinOnly: Boolean) {
        val flags: Int get() = (if (locked) 1 else 0) or (if (pinOnly) 2 else 0)

        companion object {
            val OPEN = Mode(locked = false, pinOnly = false)
            fun of(flags: Int): Mode = Mode(flags and 1 != 0, flags and 2 != 0)
        }
    }

    sealed interface Unwrapped {
        class Ok(val passphrase: ByteArray, val softwareKey: Boolean) : Unwrapped

        /** Clear evidence the data cannot be opened any more: the key is gone or does not fit. */
        data object Lost : Unwrapped

        /** Could not tell (the keystore did not answer): nothing is judged, try again later. */
        data object Retry : Unwrapped

        /** "Mine" is locked: [cipherForUnlock], confirmed by the owner, then [finishUnlock]. */
        data class NeedsOwner(val pinOnly: Boolean) : Unwrapped
    }

    /** The current mode, or null if the key file cannot be read. */
    fun mode(): Mode? = try { if (file.exists()) readWrapped().mode else null } catch (e: Exception) { null }

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
        val key = createKey(alias, Mode.OPEN)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(header(FORMAT_VERSION, Mode.OPEN, alias))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        writeWrapped(Wrapped(FORMAT_VERSION, Mode.OPEN, alias, cipher.iv, cipher.doFinal(passphrase)))
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
        // Locked: nothing is tried without the owner (an attempt would only fail)
        if (w.mode.locked) return Unwrapped.NeedsOwner(w.mode.pinOnly)
        val key = try {
            keyStore().getKey(w.alias, null) as? SecretKey
        } catch (e: Exception) {
            return Unwrapped.Retry
        }
        if (key == null) return if (keyIsGone(w.alias)) Unwrapped.Lost else Unwrapped.Retry
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, w.iv))
                updateAAD(header(w.version, w.mode, w.alias))
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
     * A cipher to unwrap a locked passphrase, for the owner to confirm (a fingerprint or the PIN).
     * Null when the state is unclear (nothing is judged); [Unwrapped.Lost] cases are told by
     * [finishUnlock] or by the key being gone.
     */
    fun cipherForUnlock(): Unlocking? = try {
        val w = readWrapped()
        val key = keyStore().getKey(w.alias, null) as? SecretKey
        key?.let {
            // The header is bound only after the owner confirmed (see [Unlocking])
            Unlocking(
                Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, it, GCMParameterSpec(GCM_TAG_BITS, w.iv)) },
                w.data,
                header(w.version, w.mode, w.alias),
            )
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        null
    } catch (e: Exception) {
        null
    }

    /**
     * A cipher for the owner to confirm, the wrapped passphrase it was made for and the header it
     * must be bound to. A key that needs the owner refuses every step of its operation before the
     * confirmation, the header too; the refusal only surfaces at the end, as an
     * IllegalBlockSizeException. So the header is given to the cipher after the confirmation.
     */
    class Unlocking(val cipher: Cipher, internal val data: ByteArray, internal val aad: ByteArray)

    /** True when a locked key is clearly gone or invalidated (the screen lock was removed, say). */
    fun lockedKeyLost(): Boolean = try {
        val w = readWrapped()
        val key = keyStore().getKey(w.alias, null) as? SecretKey
        if (key == null) keyIsGone(w.alias)
        else try {
            Cipher.getInstance(TRANSFORMATION).init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, w.iv))
            false
        } catch (e: KeyPermanentlyInvalidatedException) {
            true
        } catch (e: Exception) {
            false
        }
    } catch (e: Exception) {
        false
    }

    /** After the owner confirmed [cipherForUnlock]: the passphrase, or a clear verdict. */
    fun finishUnlock(confirmed: Cipher, unlocking: Unlocking): Unwrapped = try {
        // The data and header the cipher was made for, never a file read again later
        confirmed.updateAAD(unlocking.aad)
        val passphrase = confirmed.doFinal(unlocking.data)
        if (passphrase.size != PASSPHRASE_BYTES) Unwrapped.Lost else Unwrapped.Ok(passphrase, softwareKey = false)
    } catch (e: AEADBadTagException) {
        Unwrapped.Lost
    } catch (e: Exception) {
        Unwrapped.Retry
    }

    /**
     * Step 1 of changing the mode: a new key for [mode] under a new alias and a cipher to wrap
     * with it. A locked mode's cipher must be confirmed by the owner before [finishRewrap], which
     * also binds the header (a key that needs the owner refuses it before the confirmation).
     * Nothing stored changes until then.
     */
    fun cipherForRewrap(mode: Mode, strongBox: Boolean = true): Pair<Cipher, String> {
        fun attempt(strongBox: Boolean?): Pair<Cipher, String> {
            val alias = KEY_PREFIX + randomHex(8)
            val key = if (strongBox == null) createKey(alias, mode) else generate(alias, mode, strongBox)
            return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) } to alias
        }
        // After a StrongBox key failed at the very end, the next try goes straight to the regular hardware
        if (!strongBox) return attempt(strongBox = false)
        return try {
            attempt(null)
        } catch (e: Exception) {
            // Some phones make a StrongBox key that needs the owner's confirmation and then refuse to
            // use it: the phone's regular secure hardware is used instead. A failed key holds nothing.
            lastError = e
            try {
                attempt(strongBox = false)
            } catch (e2: Exception) {
                // Both reasons travel together, so the first one is not lost
                e2.addSuppressed(e)
                throw e2
            }
        }
    }

    /** The last keystore failure seen while changing the lock, for the owner's feedback (no secrets in it). */
    @Volatile var lastError: Throwable? = null

    /**
     * Step 2: the same passphrase is stored wrapped with the new key, atomically. Then the old key
     * entry is deleted: the file it wrapped is gone, so it has nothing left to open, and a copy of
     * that file left on the phone's storage must not open without the new protection. This is the
     * one place a key is deleted, and only once the new wrapping is written.
     */
    @Synchronized
    fun finishRewrap(confirmed: Cipher, alias: String, mode: Mode, passphrase: ByteArray, oldAlias: String?) {
        require(passphrase.size == PASSPHRASE_BYTES)
        val store = keyStore()
        require(store.containsAlias(alias)) { "New key missing" }
        // The key file must still be the one this change started from (a fresh start in between, say)
        require(currentAlias() == oldAlias) { "Key file changed meanwhile" }
        confirmed.updateAAD(header(FORMAT_VERSION, mode, alias))
        val data = confirmed.doFinal(passphrase)
        val durable = writeWrapped(Wrapped(FORMAT_VERSION, mode, alias, confirmed.iv, data))
        // Only once the new file is surely on the disk, and only a different, Sprotyv-made entry;
        // otherwise the old key stays and simply opens nothing current
        if (durable && oldAlias != null && oldAlias != alias && ALIAS.matches(oldAlias)) {
            try { store.deleteEntry(oldAlias) } catch (e: Exception) { lastError = e }
        }
    }

    /** The alias named in the key file now, or null if there is none or it cannot be read. */
    fun currentAlias(): String? = try { readWrapped().alias } catch (e: Exception) { null }

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

    private fun header(version: Int, mode: Mode, alias: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeByte(version)
            if (version >= 2) writeByte(mode.flags)
            writeUTF(alias)
            flush()
        }
        return bytes.toByteArray()
    }

    private fun writeWrapped(w: Wrapped): Boolean {
        try {
            FileOutputStream(tmp).use { stream ->
                val out = DataOutputStream(stream)
                out.writeByte(w.version)
                if (w.version >= 2) out.writeByte(w.mode.flags)
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
            return syncDirectory(file.parentFile!!)
        } finally {
            tmp.delete()
        }
    }

    private fun readWrapped(): Wrapped =
        DataInputStream(file.inputStream()).use { input ->
            val version = input.readUnsignedByte()
            require(version == 1 || version == FORMAT_VERSION) { "Unknown key file format" }
            val flags = if (version >= 2) input.readUnsignedByte() else 0
            require(flags and 3.inv() == 0) { "Damaged key file" }
            val mode = Mode.of(flags)
            require(mode.locked || !mode.pinOnly) { "Damaged key file" }
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
            Wrapped(version, mode, alias, iv, data)
        }

    /** True when the folder's new entry is surely on the disk. Best effort: the file itself is already synced. */
    private fun syncDirectory(dir: File): Boolean = try {
        val fd = Os.open(dir.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(fd) } finally { Os.close(fd) }
        true
    } catch (e: Exception) {
        false
    }

    private fun createKey(alias: String, mode: Mode): SecretKey = try {
        generate(alias, mode, strongBox = true)
    } catch (e: StrongBoxUnavailableException) {
        generate(alias, mode, strongBox = false)
    } catch (e: Exception) {
        // Some phones report StrongBox but fail inside it: the regular secure hardware is used instead
        generate(alias, mode, strongBox = false)
    }

    private fun generate(alias: String, mode: Mode, strongBox: Boolean): SecretKey {
        val b = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // Only while the phone is unlocked (see the class comment)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
        if (mode.locked) {
            b.setUserAuthenticationRequired(true)
                // 0: the owner confirms every single use of the key
                // "PIN only": the hardware itself refuses a fingerprint for this key
                .setUserAuthenticationParameters(
                    0,
                    if (mode.pinOnly) KeyProperties.AUTH_DEVICE_CREDENTIAL
                    else KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                )
                // A new fingerprint must not destroy the data; the PIN still protects it
                .setInvalidatedByBiometricEnrollment(false)
        }
        val spec = b.build()
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
        private const val FORMAT_VERSION = 2
    }
}
