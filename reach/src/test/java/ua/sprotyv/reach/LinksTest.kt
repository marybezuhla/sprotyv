package ua.sprotyv.reach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LinksTest {

    @Test fun cleansNumbers() {
        assertEquals("+380501234567", Links.clean(" +38 (050) 123-45-67 "))
        assertEquals("0501234567", Links.clean("050 123 45 67"))
    }

    @Test fun placeForMapApps() {
        assertEquals("geo:50.45010,30.52340?q=50.45010,30.52340", Links.geo(50.4501, 30.5234))
        assertThrows(IllegalArgumentException::class.java) { Links.geo(91.0, 0.0) }
    }

    @Test fun dialsEmergencyNumber() {
        assertEquals("tel:112", Links.tel("112"))
    }

    @Test fun oneSmsToSeveralPeople() {
        assertEquals("smsto:+380501234567;0671112233", Links.smsTo(listOf("+380 50 123 45 67", "067 111 22 33")))
    }

    @Test fun whatsappHasTextReady() {
        assertEquals("https://wa.me/380501234567?text=I%27m%20safe", Links.whatsapp("+380501234567", "I'm safe"))
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
        assertEquals("https://wa.me/380501234567?text=OK", Links.whatsapp("050 123 45 67", "OK"))
        assertEquals("https://wa.me/380501234567?text=OK", Links.whatsapp("+380501234567", "OK"))
    }
}
