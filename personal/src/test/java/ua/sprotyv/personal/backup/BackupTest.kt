package ua.sprotyv.personal.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.sprotyv.personal.FamilyPlan
import ua.sprotyv.personal.KitCheck
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person
import ua.sprotyv.personal.PlanRow
import ua.sprotyv.personal.Setting

class BackupTest {
    // Small settings keep the tests quick; the app always uses BackupCrypto.DEFAULT
    private val fast = BackupCrypto.KdfParams(memoryKiB = 1024, iterations = 1, parallelism = 1)
    private val pw = "тест-пароль-для-копії".toCharArray()

    private val snapshot = BackupData.Snapshot(
        created = 1_700_000_000_000,
        medical = MedicalCard(bloodGroup = "A(II) Rh+", allergies = "немає", notes = "рядок\nдругий \"лапки\""),
        people = listOf(Person(id = 3, name = "Олена", phone = "+380501234567", ice = true)),
        plan = FamilyPlan(point1 = "Біля школи", goBetweenPhone = "+380671112233"),
        rows = listOf(PlanRow(id = 5, kind = PlanRow.KIND_IF_THEN, first = "Немає зв'язку", second = "Йдемо до точки 1", sort = 1)),
        kit = listOf(KitCheck("abc", true, 42)),
        settings = listOf(Setting("safe_message", "Я в безпеці")),
    )

    @Test fun encryptsAndOpensWithThePassword() {
        val plain = "Привіт, ґанок".toByteArray()
        val sealed = BackupCrypto.encrypt(plain, pw, fast)
        assertEquals(plain.size + BackupCrypto.OVERHEAD, sealed.size)
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, pw, listOf(fast)))
    }

    @Test fun wrongPasswordAndAnyChangeAreRefused() {
        val sealed = BackupCrypto.encrypt("дані".toByteArray(), pw, fast)
        expect<BackupCrypto.WrongPasswordOrDamaged> { BackupCrypto.decrypt(sealed, "інший-пароль-зовсім".toCharArray(), listOf(fast)) }
        for (i in sealed.indices step 7) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            expect<BackupCrypto.WrongPasswordOrDamaged> { BackupCrypto.decrypt(bad, pw, listOf(fast)) }
        }
        expect<BackupCrypto.NotABackup> { BackupCrypto.decrypt(ByteArray(10), pw, listOf(fast)) }
    }

    @Test fun sameFileTwiceLooksDifferent() {
        val a = BackupCrypto.encrypt("x".toByteArray(), pw, fast)
        val b = BackupCrypto.encrypt("x".toByteArray(), pw, fast)
        assertTrue(!a.contentEquals(b))
    }

    @Test fun composedAndDecomposedLettersGiveTheSameKey() {
        // "й" typed as one letter or as "и" + combining breve
        val sealed = BackupCrypto.encrypt("x".toByteArray(), "мій-довгий-пароль-й".toCharArray(), fast)
        assertArrayEquals("x".toByteArray(), BackupCrypto.decrypt(sealed, "мій-довгий-пароль-й".toCharArray(), listOf(fast)))
    }

    @Test fun snapshotRoundTrip() {
        val back = BackupData.decode(BackupData.encode(snapshot))
        assertEquals(snapshot, back)
        assertEquals(6, back.records)
    }

    @Test fun foreignOrBrokenFilesAreRefused() {
        expect<BackupData.Incompatible> { BackupData.decode("not json".toByteArray()) }
        expect<BackupData.Incompatible> { BackupData.decode("""{"app":"other","kind":"backup","format":1}""".toByteArray()) }
        expect<BackupData.FromNewer> { BackupData.decode("""{"app":"sprotyv","kind":"backup","format":9}""".toByteArray()) }
        val noPhone = String(BackupData.encode(snapshot)).replace("\"phone\"", "\"fone\"")
        expect<BackupData.Incompatible> { BackupData.decode(noPhone.toByteArray()) }
        val badKind = String(BackupData.encode(snapshot)).replace(PlanRow.KIND_IF_THEN, "other")
        expect<BackupData.Incompatible> { BackupData.decode(badKind.toByteArray()) }
    }

    @Test fun planCodeIsNotABackupAndBack() {
        val shared = BackupData.SharedPlan(snapshot.plan!!, snapshot.rows)
        val plain = BackupData.encodePlan(shared)
        assertEquals(shared, BackupData.decodePlan(plain))
        expect<BackupData.Incompatible> { BackupData.decode(plain) }
        expect<BackupData.Incompatible> { BackupData.decodePlan(BackupData.encode(snapshot)) }
    }

    @Test fun planCodeFitsAndOpens() {
        val shared = BackupData.SharedPlan(snapshot.plan!!, snapshot.rows)
        val words = (1..2000).map { "w$it" }
        val password = BackupCrypto.suggestPassword(words, 4).toCharArray()
        val code = PlanCode.encode(shared, password)
        assertTrue(code.startsWith(PlanCode.PREFIX))
        assertTrue(code.length <= PlanCode.MAX_CHARS)
        assertEquals(shared, PlanCode.decode(code, password))
        expect<BackupCrypto.WrongPasswordOrDamaged> { PlanCode.decode(code, "w1-w2-w3-w4".toCharArray()) }
        expect<BackupData.Incompatible> { PlanCode.decode(PlanCode.PREFIX + "!!!", password) }
    }

    @Test fun weakPasswordsAreRefused() {
        assertTrue(BackupCrypto.isTooSimple("short".toCharArray()))
        assertTrue(BackupCrypto.isTooSimple("password12345678".toCharArray()))
        assertTrue(BackupCrypto.isTooSimple("1111111111111111".toCharArray()))
        assertTrue(!BackupCrypto.isTooSimple("смерека-ліхтар-вишня-7".toCharArray()))
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("expected ${T::class.simpleName}, got $e")
        }
        throw AssertionError("expected ${T::class.simpleName}")
    }
}
