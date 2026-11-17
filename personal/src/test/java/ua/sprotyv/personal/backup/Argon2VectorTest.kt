package ua.sprotyv.personal.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Argon2id test vector from RFC 9106, section 5.3. If an update of Bouncy Castle ever changed
 * what Argon2id gives, backups made before it would no longer open: this stops the build first.
 */
class Argon2VectorTest {
    @Test fun rfc9106Argon2id() {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(32)
                .withIterations(3)
                .withParallelism(4)
                .withSalt(ByteArray(16) { 0x02 })
                .withSecret(ByteArray(8) { 0x03 })
                .withAdditional(ByteArray(12) { 0x04 })
                .build(),
        )
        val tag = ByteArray(32)
        generator.generateBytes(ByteArray(32) { 0x01 }, tag)
        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            tag.joinToString("") { "%02x".format(it) },
        )
    }
}
