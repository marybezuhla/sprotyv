package ua.sprotyv.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CardsTest {

    private val one = """
        [{"id":"sample","title":"Sample","source":"Test source","checkedOn":"2026-10-29",
          "steps":[{"action":"Do the first thing","why":"Because"}]}]
    """.trimIndent()

    @Test fun readsACard() {
        val cards = CardsReader.parse(one)
        assertEquals(1, cards.size)
        assertEquals("Do the first thing", cards[0].steps[0].action)
        // Not reviewed until a specialist says so
        assertEquals("", cards[0].reviewedBy)
    }

    @Test fun emptyListIsFine() {
        assertEquals(0, CardsReader.parse("[]").size)
    }

    @Test fun refusesACardWithoutSource() {
        assertThrows(IllegalArgumentException::class.java) {
            CardsReader.parse(one.replace("Test source", " "))
        }
    }

    @Test fun refusesDuplicateIds() {
        val two = "[" + one.trim().removePrefix("[").removeSuffix("]") + "," +
            one.trim().removePrefix("[").removeSuffix("]") + "]"
        assertThrows(IllegalArgumentException::class.java) { CardsReader.parse(two) }
    }

    @Test fun refusesAStepTooLongForStress() {
        val long = "x".repeat(Step.MAX_ACTION + 1)
        assertThrows(IllegalArgumentException::class.java) {
            CardsReader.parse(one.replace("Do the first thing", long))
        }
    }

    @Test fun emergencyNumbersAreShortAndUnique() {
        val numbers = Service.entries.map { it.number }
        assertEquals(numbers.size, numbers.toSet().size)
        numbers.forEach { assert(it.matches(Regex("\\d{3}"))) }
    }
}
