package ua.sprotyv.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KitTest {
    private val guide by lazy { GuideReader.parse(File("src/main/assets/guide.json").readText()) }

    @Test fun kitComesFromTheGuide() {
        val groups = Kit.groups(guide)
        assertTrue(groups.size >= 5)
        assertTrue(groups.first().title.startsWith("Документи"))
        val keys = groups.flatMap { g -> g.items.map { it.key } }
        assertEquals("item keys are unique", keys.size, keys.toSet().size)
    }

    @Test fun keyIgnoresBoldMarks() {
        assertEquals(Kit.key("**Турнікет**"), Kit.key("Турнікет"))
    }

    @Test fun waterIsFourLitresPerPersonPerDay() {
        assertEquals(12, Supplies.waterLitres(1, 3))
        assertEquals(168, Supplies.waterLitres(3, 14))
    }

    @Test fun scalesRangesAndSingleAmounts() {
        assertEquals("8–10 кг", Supplies.scale("4–5 кг", 2.0))
        assertEquals("1 кг", Supplies.scale("0,5 кг", 2.0))
        assertEquals("0,3 л", Supplies.scale("1 л", 0.25))
        assertEquals("за звичкою", Supplies.scale("за звичкою", 3.0))
    }

    @Test fun foodTableIsForOnePersonForTwoWeeks() {
        val oneTwoWeeks = Supplies.food(guide, 1, 14)
        assertTrue(oneTwoWeeks.isNotEmpty())
        val grain = oneTwoWeeks.first { it.what.startsWith("Крупи") }
        assertEquals("4–5 кг", grain.amount)
        val twoPeople = Supplies.food(guide, 2, 14).first { it.what.startsWith("Крупи") }
        assertEquals("8–10 кг", twoPeople.amount)
    }

    @Test fun piecesAreWholeWithTheRightWord() {
        // 10–14 cans for two weeks, one person, three days: 2,1–3 → 3
        assertEquals("3 банки", Supplies.scale("10–14 банок", 3 / 14.0))
        assertEquals("1 упаковка", Supplies.scale("1 упаковка", 3 / 14.0))
        assertEquals("5 упаковок", Supplies.scale("1 упаковка", 5.0))
        assertEquals("21–30 банок", Supplies.scale("7–10 банок", 3.0))
    }
}
