package ua.sprotyv.reach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinksTest {

    @Test fun cleansNumbers() {
        assertEquals("+380000000101", Links.clean(" +38 (000) 000-01-01 "))
        assertEquals("0000000101", Links.clean("000 000 01 01"))
    }

    @Test fun dialsEmergencyNumber() {
        assertEquals("tel:112", Links.tel("112"))
    }

    @Test fun oneSmsToSeveralPeople() {
        assertEquals("smsto:+380000000101;0000000102", Links.smsTo(listOf("+380 00 000 01 01", "000 000 01 02")))
    }

    @Test fun whatsappHasTextReady() {
        assertEquals("https://wa.me/380000000101?text=I%27m%20safe", Links.whatsapp("+380000000101", "I'm safe"))
    }

    @Test fun encodesUkrainianText() {
        // "Я" is D0 AF in UTF-8
        assertEquals("%D0%AF%20OK", Links.encode("Я OK"))
    }

    @Test fun refusesSmsWithNoRealNumber() {
        assertThrows(IllegalArgumentException::class.java) { Links.smsTo(listOf(" ", "+", "-")) }
    }

    @Test fun refusesWhatsappWithoutNumber() {
        assertThrows(IllegalArgumentException::class.java) { Links.whatsapp("+", "text") }
    }

    @Test fun whatsappTurnsLocalNumberInternational() {
        assertEquals("https://wa.me/380000000101?text=OK", Links.whatsapp("000 000 01 01", "OK"))
        assertEquals("https://wa.me/380000000101?text=OK", Links.whatsapp("+380000000101", "OK"))
    }
}
