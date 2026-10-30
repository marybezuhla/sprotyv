package ua.sprotyv.personal.backup

import java.util.Base64

/**
 * The family plan as a QR code for a relative's phone: encrypted with a password that is said
 * aloud, never sent with the code. The short prefix only lets Sprotyv recognise its own code;
 * everything after it is random-looking without the password.
 */
object PlanCode {
    const val PREFIX = "SPRP1:"

    /** Large codes are harder to read from a screen; a plan longer than this asks for shorter notes. */
    const val MAX_CHARS = 1200

    class TooLong : Exception()

    fun isPlanCode(text: String): Boolean = text.startsWith(PREFIX)

    fun encode(plan: BackupData.SharedPlan, password: CharArray): String {
        val sealed = BackupCrypto.encrypt(BackupData.encodePlan(plan), password, BackupCrypto.Purpose.PLAN)
        val text = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed)
        if (text.length > MAX_CHARS) throw TooLong()
        return text
    }

    /** Throws [BackupCrypto.WrongPasswordOrDamaged] for a wrong password, [BackupData.Incompatible] for anything else. */
    fun decode(text: String, password: CharArray): BackupData.SharedPlan {
        if (!isPlanCode(text) || text.length > MAX_CHARS) throw BackupData.Incompatible()
        val sealed = try {
            Base64.getUrlDecoder().decode(text.substring(PREFIX.length))
        } catch (e: IllegalArgumentException) {
            throw BackupData.Incompatible()
        }
        val plain = try {
            BackupCrypto.decrypt(sealed, password, BackupCrypto.Purpose.PLAN)
        } catch (e: BackupCrypto.NotABackup) {
            throw BackupData.Incompatible()
        }
        return BackupData.decodePlan(plain)
    }
}
