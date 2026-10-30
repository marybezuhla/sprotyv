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
    private val adultCards by lazy { CardsReader.parse(File(assets, "cards.json").readText()) }
    private val kidCards by lazy { CardsReader.parse(File(assets, "kid_cards.json").readText()) }
    private val cards by lazy { adultCards + kidCards }

    @Test fun childCardsAreShortAndApart() {
        assertTrue("kid cards", kidCards.size >= 6)
        kidCards.forEach { card ->
            assertTrue(card.id, card.id.startsWith("kid-"))
            assertTrue("${card.id} too many steps", card.steps.size <= 5)
            card.steps.forEach {
                val words = Regex("[\\p{L}\\p{N}'’]+").findAll(it.action).count()
                assertTrue("${card.id}: $words words", words <= 15)
            }
        }
        assertTrue(adultCards.none { it.id.startsWith("kid-") })
        assertEquals(cards.size, cards.map { it.id }.toSet().size)
    }

    @Test fun everyCardHasPractice() {
        val practice = PracticeReader.parse(File(assets, "practice.json").readText(), cards)
        cards.forEach { c -> assertTrue("no practice for ${c.id}", practice.any { it.card == c.id }) }
        practice.forEach { p -> assertEquals(p.id, 1, p.options.count { it.right }) }
    }

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
        val nums = guide.chapters.flatMap { c -> c.sections.map { it.num } }
        assertTrue("every section has a number", nums.none { it.isEmpty() })
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

    @Test fun stepFiguresPointToRealPictures() {
        val withFigures = cards.flatMap { it.steps }.filter { it.figure.isNotEmpty() }
        assertTrue(withFigures.size >= 7)
        withFigures.forEach { st ->
            val section = guide.section(st.figure)?.second
            assertTrue("no section ${st.figure}", section != null)
            assertTrue("no picture in ${st.figure}", section!!.blocks.any { it is Block.Figure })
        }
    }

    @Test fun situationLinksAreSafeToOpen() {
        val links = LinksReader.parse(File(assets, "links.json").readText())
        assertTrue(links.size >= 10)
        Link.GROUPS.forEach { g -> assertTrue(g, links.any { it.group == g }) }
        // Official sources are present in every group but the front line, which has none
        assertTrue(links.count { it.kind == Link.OFFICIAL } >= 5)
        assertTrue(links.all { it.url.isEmpty() || it.url.startsWith("https://") })
        try {
            LinksReader.parse("""[{"id":"x","group":"threats","kind":"official","title":"t","note":"n","url":"http://a.b","checkedOn":"d"}]""")
            throw AssertionError("plain http must be refused")
        } catch (e: IllegalArgumentException) {
        }
        // A user part would show one host and open another
        try {
            LinksReader.parse("""[{"id":"x","group":"threats","kind":"official","title":"t","note":"n","url":"https://map.example@evil.example","checkedOn":"2026-10-30"}]""")
            throw AssertionError("user part must be refused")
        } catch (e: IllegalArgumentException) {
        }
        assertEquals("deepstatemap.live", LinksReader.host("https://deepstatemap.live"))
    }

    @Test fun stepPictogramsExist() {
        val drawn = File("../app/src/main/res/drawable").list()!!.filter { it.startsWith("pict_") }.map { it.removePrefix("pict_").removeSuffix(".xml") }.toSet()
        val used = cards.flatMap { it.steps }.map { it.icon }.filter { it.isNotEmpty() }
        assertTrue(used.size >= 20)
        used.forEach { assertTrue("no pictogram $it", it in drawn) }
    }

    @Test fun cardsAreUnreviewedUntilSomeoneReviews() {
        // Nothing may claim a review that did not happen: the field is filled by hand only after one
        cards.forEach { assertEquals("${it.id} claims a review", "", it.reviewedBy) }
    }

    @Test fun cardStepsFitAStressedReader() {
        cards.forEach { card ->
            assertTrue("${card.id} too many steps", card.steps.size <= 8)
            card.steps.forEach {
                assertTrue("${card.id}: ${it.action.take(40)}", it.action.length <= Step.MAX_ACTION)
                // 27/9/3: about 27 words a step for a reader under stress
                val words = Regex("[\\p{L}\\p{N}'’]+").findAll(it.action).count()
                assertTrue("${card.id}: $words words in ${it.action.take(40)}", words <= 27)
            }
        }
    }

    @Test fun guideKeepsItsDisclaimer() {
        assertTrue(guide.disclaimer.isNotEmpty())
    }

    @Test fun boldKeepsWordsApart() {
        // "**Інсульт — тест «ОРМЧ»:** **О**бличчя" must keep both bold runs as written
        val stroke = guide.section("7.11")!!.second.blocks.filterIsInstance<Block.Items>().flatMap { it.items }
            .first { it.text.contains("ОРМЧ") }.text
        assertTrue(stroke, stroke.contains("«ОРМЧ»:** **О**бличчя"))
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
