package ua.sprotyv.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person

class QrTest {

    private fun roundTrip(text: String): String? {
        val size = 400
        val m = Qr.encode(text, size)
        val pixels = IntArray(size * size) { i -> if (m[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Qr.decode(size, size, pixels)
    }

    @Test fun ukrainianTextSurvivesTheRoundTrip() {
        val text = "Група крові: A(II) Rh+\nАлергії: пеніцилін, горіхи\nКонтакт: Олена +380501234567"
        assertEquals(text, roundTrip(text))
    }

    @Test fun blankPictureHasNoCode() {
        assertNull(Qr.decode(50, 50, IntArray(2500) { 0xFFFFFFFF.toInt() }))
    }

    @Test fun refusesTextTooLongToScan() {
        try {
            Qr.encode("я".repeat(Qr.MAX_BYTES), 400)
            throw AssertionError("should refuse")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test fun showsWhereALinkLeads() {
        val link = Scanned.of("https://example.org/path?x=1") as Scanned.Link
        assertEquals("example.org", link.host)
        assertTrue(link.suspicious.isEmpty())
    }

    @Test fun flagsTrickLinks() {
        val hidden = Scanned.of("https://bank.example@evil.example/login") as Scanned.Link
        assertEquals("evil.example", hidden.host)
        assertTrue(Scanned.HIDDEN_ADDRESS in hidden.suspicious)
        assertTrue(Scanned.NOT_HTTPS in (Scanned.of("http://example.org") as Scanned.Link).suspicious)
        assertTrue(Scanned.LOOKALIKE_LETTERS in (Scanned.of("https://xn--80ak6aa92e.com") as Scanned.Link).suspicious)
        assertTrue(Scanned.BARE_NUMBERS in (Scanned.of("https://192.168.0.1/") as Scanned.Link).suspicious)
    }

    @Test fun backslashCannotHideTheRealHost() {
        val link = Scanned.of("https://evil.example\\@good.example/") as Scanned.Link
        assertEquals("evil.example", link.host)
    }

    @Test fun tabCannotJoinTwoAddresses() {
        assertTrue(Scanned.of("https://good.example\t.evil.example/") is Scanned.Plain)
        assertTrue(Scanned.of("https://good.example .evil.example/") is Scanned.Plain)
    }

    @Test fun contactComesBeforeLongMedicines() {
        val long = "дуже довгий опис ".repeat(20)
        val card = MedicalCard(bloodGroup = "A", allergies = long, medicines = long, conditions = long)
        val r = RescuerCode.text(card, listOf(Person(name = "Олена", phone = "+380501234567", ice = true)), labels)
        assertTrue(r.text, "Олена +380501234567" in r.text)
    }

    @Test fun plainTextAndPhones() {
        assertTrue(Scanned.of("Просто текст") is Scanned.Plain)
        assertEquals("+380501234567", (Scanned.of("tel:+380501234567") as Scanned.Phone).number)
    }

    private val labels = RescuerCode.Labels("МЕДКАРТКА", "Група крові", "Алергії", "Ліки", "Стани", "Контакт")

    @Test fun rescuerCodeHasOnlyTheEssentials() {
        val card = MedicalCard(name = "Тест Тестович", birthDate = "1 березня 1950", bloodGroup = "O(I) Rh−", allergies = "немає", notes = "особисте, не для коду")
        val r = RescuerCode.text(card, listOf(Person(name = "Олена", phone = "+380501234567", ice = true)), labels)
        val text = r.text
        assertTrue(!r.cut)
        assertTrue(text.startsWith("МЕДКАРТКА"))
        // Nothing that tells a stranger who the owner is
        assertTrue("Тест Тестович" !in text)
        assertTrue("1950" !in text)
        assertTrue("Група крові: O(I) Rh−" in text)
        assertTrue("Контакт: Олена +380501234567" in text)
        assertTrue("особисте" !in text)
        assertEquals(text, roundTrip(text))
    }

    @Test fun rescuerCodeAlwaysFits() {
        val long = "дуже довгий опис ".repeat(200)
        val card = MedicalCard(name = long, allergies = long, medicines = long, conditions = long)
        val r = RescuerCode.text(card, emptyList(), labels)
        val text = r.text
        assertTrue(r.cut)
        assertTrue(text.toByteArray(Charsets.UTF_8).size <= Qr.MAX_BYTES)
        assertEquals(text, roundTrip(text))
    }
}
