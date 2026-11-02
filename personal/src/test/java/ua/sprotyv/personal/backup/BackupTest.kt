package ua.sprotyv.personal.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val B = BackupCrypto.Purpose.BACKUP
    private val pw = "тест-пароль-для-копії".toCharArray()

    private val snapshot = BackupData.Snapshot(
        created = 1_700_000_000_000,
        medical = MedicalCard(bloodGroup = "A(II) Rh+", allergies = "немає", notes = "рядок\nдругий \"лапки\""),
        people = listOf(Person(id = 3, name = "Контакт 1", phone = "+380000000101", ice = true)),
        plan = FamilyPlan(point1 = "Біля школи", goBetweenPhone = "+380000000102"),
        rows = listOf(PlanRow(id = 5, kind = PlanRow.KIND_IF_THEN, first = "Немає зв'язку", second = "Йдемо до точки 1", sort = 1)),
        kit = listOf(KitCheck("abc", true, 42)),
        settings = listOf(Setting("safe_message", "Я в безпеці")),
        home = mapOf("mode" to "FULL", "pins" to "card:bleeding\ntool:KIT"),
        notes = listOf(ua.sprotyv.personal.Note(id = 2, title = "Адреса", text = "вул. Вигадана, 1\nкв. 2", pinned = true, updatedAt = 9)),
    )

    @Test fun encryptsAndOpensWithThePassword() {
        val plain = "Привіт, ґанок".toByteArray()
        val sealed = BackupCrypto.encrypt(plain, pw, B, fast)
        assertEquals(plain.size + BackupCrypto.OVERHEAD, sealed.size)
        assertArrayEquals(plain, BackupCrypto.decrypt(sealed, pw, B, listOf(fast)))
    }

    @Test fun wrongPasswordAndAnyChangeAreRefused() {
        val sealed = BackupCrypto.encrypt("дані".toByteArray(), pw, B, fast)
        expect<BackupCrypto.WrongPasswordOrDamaged> { BackupCrypto.decrypt(sealed, "інший-пароль-зовсім".toCharArray(), B, listOf(fast)) }
        for (i in sealed.indices step 7) {
            val bad = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            expect<BackupCrypto.WrongPasswordOrDamaged> { BackupCrypto.decrypt(bad, pw, B, listOf(fast)) }
        }
        expect<BackupCrypto.NotABackup> { BackupCrypto.decrypt(ByteArray(10), pw, B, listOf(fast)) }
    }

    @Test fun sameFileTwiceLooksDifferent() {
        val a = BackupCrypto.encrypt("x".toByteArray(), pw, B, fast)
        val b = BackupCrypto.encrypt("x".toByteArray(), pw, B, fast)
        assertTrue(!a.contentEquals(b))
    }

    @Test fun composedAndDecomposedLettersGiveTheSameKey() {
        // "й" typed as one letter or as "и" + combining breve
        val sealed = BackupCrypto.encrypt("x".toByteArray(), "мій-довгий-пароль-й".toCharArray(), B, fast)
        assertArrayEquals("x".toByteArray(), BackupCrypto.decrypt(sealed, "мій-довгий-пароль-й".toCharArray(), B, listOf(fast)))
    }

    @Test fun snapshotRoundTrip() {
        val back = BackupData.decode(BackupData.encode(snapshot))
        assertEquals(snapshot, back)
        assertEquals(7, back.records)
    }

    @Test fun copiesFromBeforeNotesStillOpen() {
        val older = org.json.JSONObject(String(BackupData.encode(snapshot))).apply { remove("notes") }.toString()
        assertEquals(emptyList<ua.sprotyv.personal.Note>(), BackupData.decode(older.toByteArray()).notes)
        // The same note twice is not a file Sprotyv wrote
        val twice = org.json.JSONObject(String(BackupData.encode(snapshot))).apply {
            getJSONArray("notes").put(getJSONArray("notes").getJSONObject(0))
        }.toString()
        expect<BackupData.Incompatible> { BackupData.decode(twice.toByteArray()) }
    }

    @Test fun homeSetUpTravelsAndOlderCopiesStillOpen() {
        val older = org.json.JSONObject(String(BackupData.encode(snapshot))).apply { remove("home") }.toString()
        assertEquals(emptyMap<String, String>(), BackupData.decode(older.toByteArray()).home)
        // Odd entries are skipped, the rest of the copy still opens
        val odd = org.json.JSONObject(String(BackupData.encode(snapshot))).apply {
            getJSONObject("home").put("Mode!", "x").put("long", "x".repeat(5000)).put("number", 5)
        }.toString()
        assertEquals(snapshot.home, BackupData.decode(odd.toByteArray()).home)
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
        val words = (1..2000).map { n -> "w" + n.toString().map { d -> 'a' + (d - '0') }.joinToString("") }
        val password = BackupCrypto.suggestPassword(words, 4).toCharArray()
        val code = PlanCode.encode(shared, password)
        assertTrue(code.startsWith(PlanCode.PREFIX))
        assertTrue(code.length <= PlanCode.MAX_CHARS)
        assertEquals(shared, PlanCode.decode(code, password))
        expect<BackupCrypto.WrongPasswordOrDamaged> { PlanCode.decode(code, "w1-w2-w3-w4".toCharArray()) }
        expect<BackupData.Incompatible> { PlanCode.decode(PlanCode.PREFIX + "!!!", password) }
    }

    @Test fun backupNeverOpensAsPlanCode() {
        val sealed = BackupCrypto.encrypt("x".toByteArray(), pw, B, fast)
        expect<BackupCrypto.WrongPasswordOrDamaged> { BackupCrypto.decrypt(sealed, pw, BackupCrypto.Purpose.PLAN, listOf(fast)) }
    }

    @Test fun deepNestingIsRefusedBeforeParsing() {
        val deep = "{\"app\":\"sprotyv\",\"kind\":\"backup\",\"format\":1,\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}"
        expect<BackupData.Incompatible> { BackupData.decode(deep.toByteArray()) }
        assertTrue(BackupData.shallow(BackupData.encode(snapshot)))
        // Brackets inside text do not count
        assertTrue(BackupData.shallow("{\"a\":\"[[[[[[[[[[\\\"[[[[\"}".toByteArray()))
        // Millions of empty objects: refused before parsing, so memory stays small
        assertFalse(BackupData.shallow(("[" + "{},".repeat(300_000) + "{}]").toByteArray()))
        // Comments and single-quoted strings could hide a closing bracket from the count: refused
        assertFalse(BackupData.shallow("{\"a\":[/*]]]*/[[[]]]}".toByteArray()))
        assertFalse(BackupData.shallow("{'a':']]]'}".toByteArray()))
        assertFalse(BackupData.shallow("{\"a\":1}# ]]]".toByteArray()))
        // More closing than opening brackets, or unfinished text
        assertFalse(BackupData.shallow("]]{\"a\":[[[]]]}".toByteArray()))
        assertFalse(BackupData.shallow("{\"a\":\"b}".toByteArray()))
    }

    @Test fun duplicatesAndWrongShapesAreRefused() {
        val two = snapshot.copy(people = snapshot.people + snapshot.people)
        expect<BackupData.Incompatible> { BackupData.decode(BackupData.encode(two)) }
        val medicalAsText = String(BackupData.encode(snapshot)).replaceFirst(Regex("\"medical\":\\{[^}]*\\}"), "\"medical\":\"x\"")
        expect<BackupData.Incompatible> { BackupData.decode(medicalAsText.toByteArray()) }
    }

    @Test fun wordsAreReadHoweverTyped() {
        assertEquals("cat-dog-sun-tree", String(BackupCrypto.asWords(" Cat dog-SUN  tree ".toCharArray())!!))
        assertEquals(null, BackupCrypto.asWords("my-own-pass1-word".toCharArray()))
        val list = (1..2000).map { "w" + "abcdefghij"[it % 10] + it.toString().map { d -> 'a' + (d - '0') }.joinToString("") } + listOf("t-shirt", "yo-yo")
        repeat(50) { assertTrue(!BackupCrypto.suggestPassword(list, 4).contains("shirt")) }
    }

    @Test fun weakPasswordsAreRefused() {
        assertTrue(BackupCrypto.isTooSimple("kyiv2024kyiv2024".toCharArray()))
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
