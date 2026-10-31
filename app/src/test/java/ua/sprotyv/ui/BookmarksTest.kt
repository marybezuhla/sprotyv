package ua.sprotyv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarksTest {
    private fun page(s: String) = (Bookmarks.check(s) as Bookmarks.Check.Page).url

    @Test fun keepsAnHttpsPageFromSharedText() {
        assertEquals("https://example.org/a?b=1", page("Read this https://example.org/a?b=1."))
        assertEquals("https://example.org/", page("HTTPS://example.org/"))
    }

    @Test fun refusesTelegramInAnyForm() {
        listOf("https://t.me/x", "https://www.t.me/x", "http://telegram.me/x", "tg://resolve?domain=x", "https://telegra.ph/a", "https://web.telegram.org/")
            .forEach { assertEquals(it, Bookmarks.Check.Telegram, Bookmarks.check(it)) }
    }

    @Test fun refusesWhatIsNotASecurePage() {
        assertEquals(Bookmarks.Check.NotSecure, Bookmarks.check("http://example.org/"))
        assertEquals(Bookmarks.Check.NoPage, Bookmarks.check("javascript:alert(1)"))
        assertEquals(Bookmarks.Check.NoPage, Bookmarks.check("https://bank.example@other.example/"))
        assertEquals(Bookmarks.Check.NoPage, Bookmarks.check("no address here"))
        assertEquals(Bookmarks.Check.NoPage, Bookmarks.check("https://example.org/" + "a".repeat(3000)))
    }

    @Test fun namesLoseDirectionAndControlCharacters() {
        assertEquals("abc", Bookmarks.cleanTitle("a‮b\u0000c"))
    }

    @Test fun pagesRoundTripAndStayWithinLimits() {
        val list = (1..Bookmarks.MAX).map { Bookmark("https://example.org/" + "p".repeat(500) + it, "Сторінка $it", it.toLong()) }
        val pages = Bookmarks.toPages(list)!!
        assertTrue(pages.size <= Bookmarks.KEYS.size)
        assertTrue(pages.all { it.length <= 16_000 })
        assertEquals(list, Bookmarks.fromPages(pages))
    }

    @Test fun storedTelegramOrBrokenEntriesAreDropped() {
        val stored = """[{"u":"https://t.me/x","t":"x","a":1},{"u":"https://example.org/","t":"ok","a":2},{"u":"http://example.org/","t":"no","a":3}]"""
        assertEquals(listOf(Bookmark("https://example.org/", "ok", 2)), Bookmarks.fromPages(listOf(stored, "not json", null)))
        assertNull(Bookmarks.toPages((0..Bookmarks.MAX).map { Bookmark("https://example.org/$it", "", 0) }))
    }
}
