package ua.sprotyv.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RouteAndKeysTest {
    /** Every screen comes back as itself after Android rebuilds the app (the back stack is kept as words). */
    @Test fun everyRouteSavesAndRestores() {
        val routes = buildList<Route> {
            Section.entries.forEach { add(Route.Open(it)) }
            MinePart.entries.forEach { add(Route.Mine(it)) }
            addAll(listOf(
                Route.CardRoute("bleeding"), Route.ChapterRoute("ch7"), Route.GuideSection("7.2"), Route.Search, Route.About,
                Route.SuppliesRoute, Route.Scan, Route.Settings, Route.EditTiles, Route.Sections, Route.Presets, Route.Situation,
                Route.Power, Route.Directory, Route.QrText, Route.MozList, Route.Resistance, Route.NoteEdit(3), Route.Moz(4),
                Route.ConstitutionRoute("17"), Route.PdrRoute("12"), Route.HistoryRoute, Route.Story("s1"), Route.Delegate(4),
                Route.Handover, Route.PracticeRoute("bleeding"), Route.Comms, Route.Driving, Route.Weapons,
                Route.PathCheck("bleed"), Route.RoleRoute("medic"),
            ))
        }
        routes.forEach { r -> assertEquals("route ${r.save()}", r, Route.restore(r.save())) }
    }

    /**
     * Every setting key kept in the encrypted data is either carried by backups or named here as
     * one that stays on the phone on purpose; a new key cannot be forgotten silently.
     */
    @Test fun everySettingKeyIsBackedUpOrKeptOnPurpose() {
        val stays = setOf(KEY_BACKUP_AT) // when the last backup was made: about this phone
        val declared = File("src/main/java/ua/sprotyv/ui").walk().filter { it.extension == "kt" }
            .flatMap { f -> Regex("""const val (KEY_[A-Z_]+) = "([a-z_]+)"""").findAll(f.readText()).map { it.groupValues[2] } }
            .toSet()
        val known = BACKED_UP_SETTINGS + stays
        val missing = declared - known
        assertTrue("setting keys neither backed up nor kept on purpose: $missing", missing.isEmpty())
    }
}
