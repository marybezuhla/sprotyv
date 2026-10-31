package ua.sprotyv.screens

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.sprotyv.content.Content
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Preset
import ua.sprotyv.ui.CardScreen
import ua.sprotyv.ui.DelegateScreen
import ua.sprotyv.ui.DirectoryScreen
import ua.sprotyv.ui.HandoverScreen
import ua.sprotyv.ui.Frame
import ua.sprotyv.ui.FullHome
import ua.sprotyv.ui.GuideSectionScreen
import ua.sprotyv.ui.HomePrefs
import ua.sprotyv.ui.KidHome
import ua.sprotyv.ui.Nav
import ua.sprotyv.ui.MozListScreen
import ua.sprotyv.ui.MozScreen
import ua.sprotyv.ui.PowerScreen
import ua.sprotyv.ui.QrTextScreen
import ua.sprotyv.ui.ResistanceScreen
import ua.sprotyv.ui.PracticeScreen
import ua.sprotyv.ui.Route
import ua.sprotyv.ui.SectionsList
import ua.sprotyv.ui.SimpleHome
import ua.sprotyv.ui.SituationScreen
import ua.sprotyv.ui.SprotyvTheme
import ua.sprotyv.ui.ThemeChoice
import java.io.File

/**
 * Draws the main screens on the computer, in light and dark, from the real built-in content.
 * Nothing personal is drawn: the owner's screens need the encrypted store and are left out.
 * A screen that crashes fails the build; the pictures go to build/screens for review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "uk-w400dp-h1800dp-xhdpi")
class ScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: Application
    private lateinit var content: Content
    private val nav = object : Nav {
        override fun go(route: Route) {}
        override fun back() {}
        override fun home() {}
        override fun replace(route: Route) {}
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        content = Content.get(app)
        HomePrefs.update(app) { it.copy(medic = false, service = false) }
        // The empty activity that hosts each screen (known only to this test)
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }

    private fun mode(m: Mode) = HomePrefs.update(app) { it.copy(mode = m, pins = Preset.GENERAL.pins) }

    private fun shot(name: String, dark: Boolean = false, body: @Composable () -> Unit) = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContent {
                SprotyvTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { body() }
                }
            }
        }
        repeat(8) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    @Test fun homeSimple() { mode(Mode.SIMPLE); shot("01-home-simple") { SimpleHome(content, Preset.GENERAL.pins, nav) {} } }
    @Test fun homeFull() { mode(Mode.FULL); shot("02-home-full") { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) } }
    @Test fun homeFullDark() { mode(Mode.FULL); shot("02-home-full-dark", dark = true) { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) } }
    @Test fun homeKid() { mode(Mode.KID); shot("03-home-kid") { KidHome(content, nav) {} } }

    @Test fun bleedingOverview() { mode(Mode.FULL); shot("04-card-bleeding") { CardScreen(content, "bleeding", nav) } }
    @Test fun bleedingOverviewDark() { mode(Mode.FULL); shot("04-card-bleeding-dark", dark = true) { CardScreen(content, "bleeding", nav) } }
    @Test fun bleedingStep() { mode(Mode.SIMPLE); shot("05-card-bleeding-step") { CardScreen(content, "bleeding", nav) } }
    @Test fun airAlert() { mode(Mode.FULL); shot("06-card-air-alert") { CardScreen(content, "air-alert", nav) } }
    @Test fun unconscious() { mode(Mode.FULL); shot("07-card-unconscious") { CardScreen(content, "unconscious", nav) } }
    @Test fun suspicious() { mode(Mode.FULL); shot("08-card-suspicious") { CardScreen(content, "suspicious", nav) } }
    @Test fun kidCard() { mode(Mode.KID); shot("09-card-kid-fire") { CardScreen(content, "kid-fire", nav) } }

    @Test fun practice() { mode(Mode.FULL); shot("10-practice") { PracticeScreen(content, "", nav) } }
    @Test fun sections() { mode(Mode.FULL); shot("11-sections") { Frame("Розділи", {}) { SectionsList(content, Preset.GENERAL.pins, nav) { _, _ -> } } } }
    @Test fun power() = shot("12-power") { PowerScreen(content, nav) }
    @Test fun powerDark() = shot("12-power-dark", dark = true) { PowerScreen(content, nav) }
    @Test fun situation() = shot("13-situation") { SituationScreen(content, nav) }
    @Test fun directory() = shot("14-directory") { DirectoryScreen(content, nav) }
    @Test fun guideTable() = shot("15-guide-2-5") { GuideSectionScreen(content, "2.5", nav) }
    @Test fun guidePower() = shot("16-guide-20-3") { GuideSectionScreen(content, "20.3", nav) }
    @Test fun mozList() { mode(Mode.FULL); shot("17-moz-list") { MozListScreen(content, nav) } }
    @Test fun mozSteps() { mode(Mode.FULL); shot("18-moz-bleeding-steps") { MozScreen(content, 4, nav) } }
    @Test fun mozFull() { HomePrefs.update(app) { it.copy(mode = Mode.FULL, medic = true) }; shot("19-moz-bleeding-full", dark = true) { MozScreen(content, 4, nav) } }
    @Test fun qrText() = shot("20-qr-text") { QrTextScreen(nav) }
    @Test fun delegate() = shot("22-delegate-bleeding") { DelegateScreen(content, 4, nav) }
    @Test fun handover() = shot("23-handover") { HandoverScreen(nav) }
    @Test fun resistance() { HomePrefs.update(app) { it.copy(mode = Mode.FULL, service = true) }; shot("21-resistance") { ResistanceScreen(content, nav) } }
}
