package ua.sprotyv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.Person

class TilesTest {
    private val cards = setOf(
        "first-60", "air-alert", "ballistic", "drone", "bleeding", "unconscious", "burn", "home-fire",
        "suspicious", "chemical", "radiation", "rubble", "blackout", "leave-home",
    )
    private val chapters = (1..30).map { "ch$it" }.toSet() + setOf("planA", "abroad", "sources")

    @Test fun presetsAndGroupsOnlyNameRealTiles() {
        (Preset.entries.flatMap { it.pins } + Group.entries.flatMap { it.tiles }).forEach {
            assertTrue(it, Tiles.isValid(it, cards, chapters))
        }
    }

    @Test fun everyPresetFitsAndHasNoRepeats() {
        Preset.entries.forEach {
            assertEquals(it.name, it.pins.distinct(), it.pins)
            assertTrue(it.name, it.pins.size in Tiles.SIMPLE_CHOSEN..Tiles.MAX_PINS)
        }
    }

    @Test fun everyCardCanBePinned() {
        val inGroups = Group.entries.flatMap { it.tiles }.toSet()
        cards.forEach { assertTrue(it, Tiles.card(it) in inGroups) }
    }

    @Test fun unknownOrFixedIdsAreDropped() {
        assertFalse(Tiles.isValid("card:nope", cards, chapters))
        assertFalse(Tiles.isValid("tool:NOPE", cards, chapters))
        assertFalse(Tiles.isValid("tool:NOW", cards, chapters))
        assertFalse(Tiles.isValid("open:x", cards, chapters))
        assertEquals(listOf("card:a"), Pins.parse("tool:NOW\ncard:a\ncard:a\n\n"))
    }

    @Test fun movingStaysInPlaceAtTheEdges() {
        val p = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), Pins.move(p, "b", -1))
        assertEquals(p, Pins.move(p, "a", -1))
        assertEquals(p, Pins.move(p, "c", 1))
        assertEquals(p, Pins.move(p, "x", 1))
    }

    @Test fun pinningAddsAtTheEndOnceAndStopsAtTheLimit() {
        assertEquals(listOf("a", "b"), Pins.pin(listOf("a"), "b"))
        assertEquals(listOf("a"), Pins.pin(listOf("a"), "a"))
        val full = (1..Tiles.MAX_PINS).map { "card:$it" }
        assertEquals(full, Pins.pin(full, "card:x"))
        assertEquals(full, Pins.parse(Pins.write(full + "card:x")))
    }

    @Test fun simpleModeShowsSixAtMost() {
        val s = Pins.simple(Preset.FRONTLINE.pins)
        assertEquals(6, s.size)
        assertEquals(Tiles.FIXED, s.take(2))
    }

    @Test fun restListsUnnamedChapters() {
        val rest = Group.rest(listOf("ch1", "ch7", "sources"))
        assertEquals(listOf("chapter:ch1", "chapter:sources"), rest)
    }

    @Test fun readinessCountsWithoutGrading() {
        assertEquals(Progress(0, 4), Readiness.medical(null))
        assertEquals(Progress(2, 4), Readiness.medical(MedicalCard(bloodGroup = "A", allergies = "немає")))
        val p = Readiness.people(listOf(Person(name = "О", phone = "1", safeList = true, ice = false)))
        assertEquals(Progress(1, 2), p)
        assertFalse(p.complete)
        assertTrue(Readiness.kit(5, 5).complete)
        assertEquals(0f, Readiness.kit(0, 0).fraction)
    }
}
