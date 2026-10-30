package ua.sprotyv.personal.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password encryption for the backup file and the family plan code, a well-reviewed scheme for
 * backups. Nothing readable inside: to anyone without the password it looks like random bytes.
 *
 * Layout: salt (16) | nonce (12) | AES-256-GCM ciphertext with 16-byte tag.
 * The key comes from the password through Argon2id with fixed settings ([PROFILES]); salt and
 * nonce are authenticated as associated data, so any change is detected. If the settings ever
 * change, a new profile is added and old files still open.
 */
object BackupCrypto {

    data class KdfParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int)

    /** 64 MiB, 3 passes: a few seconds on a phone, very costly for an attacker. */
    val DEFAULT = KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)

    /** Settings tried when opening, newest first. Only fixed values: nothing is read from the file. */
    internal val PROFILES = listOf(DEFAULT)

    const val MIN_PASSWORD_LENGTH = 16
    /** Far above any real backup (no photos in it yet); keeps memory use safe on small phones. */
    const val MAX_FILE_BYTES = 4L * 1024 * 1024

    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    const val OVERHEAD = SALT_BYTES + NONCE_BYTES + TAG_BYTES

    /** The password or the file is wrong; deliberately the same for both. */
    class WrongPasswordOrDamaged : Exception()

    /** Too small or too large to be one of ours. */
    class NotABackup : Exception()

    /**
     * What a file is for, bound into the encryption: a backup never opens as a plan code and the
     * other way round, even with the same password.
     */
    enum class Purpose(val label: String) { BACKUP("sprotyv-backup-v1"), PLAN("sprotyv-plan-v1") }

    /**
     * The password as characters, which the caller clears afterwards. Clearing is best effort:
     * the JVM and the cipher keep their own copies, so this is no promise that nothing remains.
     */
    fun encrypt(plain: ByteArray, password: CharArray, purpose: Purpose, params: KdfParams = DEFAULT): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val header = salt + nonce
        val key = deriveKey(password, salt, params)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            cipher.updateAAD(purpose.label.toByteArray(Charsets.US_ASCII))
            return header + cipher.doFinal(plain)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(file: ByteArray, password: CharArray, purpose: Purpose, profiles: List<KdfParams> = PROFILES): ByteArray {
        val headerSize = SALT_BYTES + NONCE_BYTES
        if (file.size < headerSize + TAG_BYTES || file.size > MAX_FILE_BYTES) throw NotABackup()
        val salt = file.copyOfRange(0, SALT_BYTES)
        val nonce = file.copyOfRange(SALT_BYTES, headerSize)
        for (params in profiles) {
            val key = deriveKey(password, salt, params)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
                cipher.updateAAD(file, 0, headerSize)
                cipher.updateAAD(purpose.label.toByteArray(Charsets.US_ASCII))
                return cipher.doFinal(file, headerSize, file.size - headerSize)
            } catch (e: AEADBadTagException) {
                // Try the next profile
            } finally {
                key.fill(0)
            }
        }
        throw WrongPasswordOrDamaged()
    }

    /**
     * The password as UTF-8 in NFC form, so the same password typed on another keyboard gives the
     * same key. Already in NFC (nearly always), it is encoded straight from the characters.
     */
    internal fun utf8Nfc(password: CharArray): ByteArray {
        val seq = java.nio.CharBuffer.wrap(password)
        if (!java.text.Normalizer.isNormalized(seq, java.text.Normalizer.Form.NFC)) {
            return java.text.Normalizer.normalize(seq, java.text.Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        }
        val bb = Charsets.UTF_8.newEncoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
            .encode(java.nio.CharBuffer.wrap(password))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    internal fun deriveKey(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val passwordBytes = utf8Nfc(password)
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .build(),
        )
        val key = ByteArray(32)
        generator.generateBytes(passwordBytes, key)
        passwordBytes.fill(0)
        return key
    }

    /**
     * Random words from the EFF list, every word equally likely: six for a backup (about 77 bits),
     * four for the family plan code, which is said aloud (about 52 bits).
     */
    fun suggestPassword(words: List<String>, count: Int): String {
        // Words with a dash ("t-shirt", "yo-yo") are easy to mishear or type without it
        val plain = words.filter { w -> w.isNotEmpty() && w.all { it in 'a'..'z' } }
        require(plain.size >= 1024 && count >= 4)
        val random = SecureRandom()
        return (1..count).joinToString("-") { plain[random.nextInt(plain.size)] }
    }

    /**
     * The same words however they were typed: spaces or dashes between them, capitals or not.
     * Null when the text is not just words, so an own password is never changed.
     */
    fun asWords(typed: CharArray): CharArray? {
        val text = String(typed).trim().lowercase()
        val parts = text.split(Regex("[\\s\\-]+")).filter { it.isNotEmpty() }
        if (parts.size < 4 || parts.any { p -> !p.all { it in 'a'..'z' } }) return null
        return parts.joinToString("-").toCharArray()
    }

    /**
     * Refuses passwords that are obviously weak: too short, too few different characters, only
     * digits, or built from common words and keyboard rows. A suggested password is always fine.
     */
    fun isTooSimple(password: CharArray): Boolean {
        if (password.size < MIN_PASSWORD_LENGTH) return true
        if (password.toSet().size < 6) return true
        if (password.all { it.isDigit() } && password.size < 24) return true
        // One part said again and again ("kyiv2024kyiv2024") is as weak as the part
        val n = password.size
        if ((1..n / 2).any { k -> n % k == 0 && (k until n).all { password[it] == password[it - k] } }) return true
        val lower = String(password).lowercase()
        val withoutCommon = COMMON_PARTS.fold(lower) { acc, part -> acc.replace(part, "") }
        return withoutCommon.filterNot { it.isDigit() }.length < 8
    }

    private val COMMON_PARTS = listOf(
        "password", "passw0rd", "qwertyuiop", "asdfghjkl", "zxcvbnm", "йцукенгшщзхї", "фівапролджє",
        "ячсмитьбю", "1234567890", "0987654321", "admin", "welcome", "letmein", "iloveyou",
        "пароль", "sprotyv", "спротив",
    )
}
