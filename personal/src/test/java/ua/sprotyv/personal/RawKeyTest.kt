package ua.sprotyv.personal

import org.junit.Assert.assertEquals
import org.junit.Test

class RawKeyTest {
    @Test fun rawKeyIsSqlCiphersHexForm() {
        val key = ByteArray(32) { it.toByte() }
        val raw = String(PersonalDb.rawKey(key), Charsets.US_ASCII)
        assertEquals("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'", raw)
        assertEquals("x'" + "ff".repeat(32) + "'", String(PersonalDb.rawKey(ByteArray(32) { -1 }), Charsets.US_ASCII))
    }
}
