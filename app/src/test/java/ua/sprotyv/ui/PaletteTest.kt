package ua.sprotyv.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.sprotyv.home.Group

/**
 * Every pair of text and its background in the colour code reads at WCAG AA (4.5:1), in the light
 * and the dark theme: a colour that looks fine on one screen can fade on a cheap one in daylight.
 */
class PaletteTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun reads(what: String, text: Color, background: Color) {
        val c = contrast(text, background)
        assertTrue("$what: contrast %.2f is below 4.5".format(c), c >= 4.5)
    }

    @Test fun colourCodeReads() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) Dark else Light
            val tints = Group.entries.associate { it.name to Palette.of(it, dark) } + ("RESIST" to Palette.resist(dark))
            tints.forEach { (name, t) ->
                val theme = if (dark) "dark" else "light"
                // The group's own words on its fill (block headers, tiles, numbers of card steps)
                reads("$name ink on its fill, $theme", t.ink, t.container)
                // Ordinary text on the fill (a card's step panel, tile titles)
                reads("$name text on its fill, $theme", scheme.onSurface, t.container)
                // The group's words on a plain panel (titles inside blocks)
                reads("$name ink on the surface, $theme", t.ink, scheme.surface)
            }
        }
    }

    @Test fun themeTextReads() {
        for ((theme, s) in listOf("light" to Light, "dark" to Dark)) {
            reads("text on the background, $theme", s.onBackground, s.background)
            reads("text on panels, $theme", s.onSurface, s.surface)
            reads("quiet text on panels, $theme", s.onSurfaceVariant, s.surface)
            reads("quiet text on the background, $theme", s.onSurfaceVariant, s.background)
            reads("button text, $theme", s.onPrimary, s.primary)
            reads("links and buttons on panels, $theme", s.primary, s.surface)
        }
    }
}
