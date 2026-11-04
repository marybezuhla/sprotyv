package ua.sprotyv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VcfTest {
    @Test fun writesAndReadsBack() {
        val list = listOf(Vcf.Contact("Контакт 1", "+380000000101", "Сестра; живе поруч"), Vcf.Contact("Contact, 2", "0000000102"))
        assertEquals(list, Vcf.read(Vcf.write(list)))
    }

    @Test fun readsAndroidQuotedPrintable() {
        // As Android's contacts app exports a Cyrillic name (vCard 2.1)
        val text = "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:;=D0=9A=D0=BE=D0=BD=D1=82=D0=B0=D0=BA=D1=82;;;\r\n" +
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=D0=9A=D0=BE=D0=BD=D1=82=D0=B0=D0=BA=D1=82\r\nTEL;CELL:+380 00 000-01-03\r\nEND:VCARD\r\n"
        assertEquals(listOf(Vcf.Contact("Контакт", "+380000000103")), Vcf.read(text))
    }

    @Test fun nameFromPartsWithoutAFullName_folded_andCardsWithoutNumberSkipped() {
        val text = "BEGIN:VCARD\nVERSION:3.0\nN:Прізвище;Ім'я;;;\nTEL;TYPE=CELL:\n +380000000104\nEND:VCARD\n" +
            "BEGIN:VCARD\nVERSION:4.0\nFN:Без номера\nEND:VCARD\n" +
            "BEGIN:VCARD\nVERSION:4.0\nFN:Через URI\nTEL;VALUE=uri:tel:+380000000105\nEND:VCARD\n"
        assertEquals(listOf(Vcf.Contact("Ім'я Прізвище", "+380000000104"), Vcf.Contact("Через URI", "+380000000105")), Vcf.read(text))
    }

    @Test fun namesCannotHideOrTurnText() {
        val c = Vcf.read("BEGIN:VCARD\nFN:Ma\u202Erko\u200B\\nX\nTEL:+\u0661380 00 000 01 01\nNOTE:a\\\\nb\nEND:VCARD\n").single()
        assertEquals("Marko X", c.name)
        assertEquals("+380000000101", c.phone)
        assertEquals("a\\nb", c.note)
    }

    @Test fun readsOlderCharsets() {
        // "Олена" in windows-1251, quoted-printable, as older phones wrote it
        val c = Vcf.read("BEGIN:VCARD\r\nVERSION:2.1\r\nFN;CHARSET=WINDOWS-1251;ENCODING=QUOTED-PRINTABLE:=CE=EB=E5=ED=E0\r\nTEL:0000000101\r\nEND:VCARD\r\n").single()
        assertEquals("Олена", c.name)
    }

    @Test fun longFoldedFileStaysQuick() {
        val text = "BEGIN:VCARD\nFN:A" + "\n x".repeat(200_000) + "\nTEL:0000000101\nEND:VCARD\n"
        val start = System.nanoTime()
        assertEquals(1, Vcf.read(text).size)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 3000)
    }
}
