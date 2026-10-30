package ua.sprotyv.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real content files that ship in the app, not samples. */
class ContentTest {

    // Unit tests run with the module folder as the working directory
    private val assets = File("src/main/assets")
    private val guide by lazy { GuideReader.parse(File(assets, "guide.json").readText()) }
    private val cards by lazy { CardsReader.parse(File(assets, "cards.json").readText()) }

    @Test fun guideIsComplete() {
        assertTrue("chapters", guide.chapters.size >= 30)
        assertEquals("CC BY-SA 4.0", guide.license)
        assertTrue(guide.source.startsWith("https://github.com/snegireff/"))
        guide.chapters.forEach { c ->
            assertTrue("title of ${c.id}", c.title.isNotBlank())
            c.sections.forEach { s -> assertTrue("blocks of ${s.num}", s.blocks.isNotEmpty()) }
        }
    }

    @Test fun sectionNumbersAreUnique() {
        val nums = guide.chapters.flatMap { c -> c.sections.map { it.num } }.filter { it.isNotEmpty() }
        assertEquals(nums.size, nums.toSet().size)
    }

    @Test fun everyFigureHasItsPicture() {
        val names = mutableListOf<String>()
        fun walk(blocks: List<Block>) {
            blocks.forEach { b ->
                when (b) {
                    is Block.Figure -> names += b.pictures
                    is Block.Callout -> walk(b.blocks)
                    else -> {}
                }
            }
        }
        guide.chapters.forEach { c -> walk(c.intro); c.sections.forEach { walk(it.blocks) } }
        assertTrue(names.isNotEmpty())
        names.forEach { assertTrue("missing picture $it", File(assets, "figures/$it.png").isFile) }
    }

    @Test fun boldMarksArePaired() {
        fun check(t: String) = assertEquals("odd ** in: ${t.take(60)}", 0, Regex("\\*\\*").findAll(t).count() % 2)
        fun walk(blocks: List<Block>) {
            blocks.forEach { b ->
                when (b) {
                    is Block.Paragraph -> check(b.text)
                    is Block.Heading -> check(b.text)
                    is Block.Items -> b.items.forEach { i -> check(i.text); i.sub.forEach { check(it.text) } }
                    is Block.Table -> b.rows.forEach { r -> r.cells.forEach(::check) }
                    is Block.Figure -> check(b.caption)
                    is Block.Callout -> walk(b.blocks)
                }
            }
        }
        guide.chapters.forEach { c -> walk(c.intro); c.sections.forEach { walk(it.blocks) } }
    }

    @Test fun cardsPointToRealSections() {
        assertTrue("cards", cards.size >= 12)
        cards.forEach { card ->
            assertTrue("${card.id} has no guide section", card.guide.isNotEmpty())
            card.guide.forEach { num ->
                val whole = guide.chapters.any { it.label.endsWith(" $num") }
                assertTrue("${card.id} points to missing $num", guide.section(num) != null || whole)
            }
        }
    }

    @Test fun cardsAreUnreviewedUntilSomeoneReviews() {
        // Nothing may claim a review that did not happen: the field is filled by hand only after one
        cards.forEach { assertEquals("${it.id} claims a review", "", it.reviewedBy) }
    }

    @Test fun cardStepsFitAStressedReader() {
        cards.forEach { card ->
            assertTrue("${card.id} too many steps", card.steps.size <= 7)
            card.steps.forEach { assertTrue("${card.id}: ${it.action.take(40)}", it.action.length <= Step.MAX_ACTION) }
        }
    }

    @Test fun searchFindsTourniquet() {
        val hits = GuideSearch.search(guide, "турнікет")
        assertTrue(hits.any { it.section.num == "7.2" })
    }

    @Test fun searchIgnoresApostropheAndCase() {
        val a = GuideSearch.search(guide, "Зв'язок").map { it.section.num }
        val b = GuideSearch.search(guide, "зв’язок").map { it.section.num }
        assertTrue(a.isNotEmpty())
        assertEquals(a, b)
    }

    @Test fun searchNeedsAllWords() {
        val hits = GuideSearch.search(guide, "турнікет зебра")
        assertTrue(hits.isEmpty())
    }
}
