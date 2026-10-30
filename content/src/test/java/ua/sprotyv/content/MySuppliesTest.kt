package ua.sprotyv.content

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class MySuppliesTest {
    @Test fun roundTrip() {
        val s = MySupplies(2, 14, 30, "2027-01", setOf("ab12"), mapOf("ab12" to "2026-12"))
        assertEquals(s, MySupplies.fromJson(s.toJson()))
    }

    @Test fun waterDaysForTheHousehold() {
        assertEquals(3, MySupplies(people = 2, water = 24).waterDays)
        assertEquals(0, MySupplies(people = 3, water = 10).waterDays)
        assertEquals(112, MySupplies(people = 2, days = 14).waterGoal)
    }

    @Test fun damagedValuesFallBackQuietly() {
        assertEquals(MySupplies(), MySupplies.fromJson("not json"))
        val s = MySupplies.fromJson("""{"people":99,"days":5,"water":-3,"waterUntil":"soon","have":["ok1","BAD KEY"],"until":{"ok1":"2027-13"}}""")
        assertEquals(20, s.people)
        assertEquals(3, s.days)
        assertEquals(0, s.water)
        assertEquals("", s.waterUntil)
        assertEquals(setOf("ok1"), s.have)
        assertEquals(emptyMap<String, String>(), s.until)
    }

    @Test fun expiringOnlyWhatIsAtHome() {
        val s = MySupplies(have = setOf("a1"), until = mapOf("a1" to "2026-11", "b2" to "2026-11", "c3" to "2027-06"), waterUntil = "2026-12")
        assertEquals(listOf("a1", MySupplies.WATER), s.expiring(YearMonth.of(2026, 11)).map { it.first })
    }
}
