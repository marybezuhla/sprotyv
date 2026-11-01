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
import ua.sprotyv.ui.WayHub
import java.io.File
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.ui.BACKED_UP_SETTINGS
import ua.sprotyv.ui.GartScreen
import ua.sprotyv.ui.KitScreen
import ua.sprotyv.ui.MedicalScreen
import ua.sprotyv.ui.MineHub
import ua.sprotyv.ui.NewPlaceScreen
import ua.sprotyv.ui.NotesScreen
import ua.sprotyv.ui.PathScreen
import ua.sprotyv.ui.PeopleScreen
import ua.sprotyv.ui.PlanScreen
import ua.sprotyv.ui.ROLES
import ua.sprotyv.ui.RoleScreen
import ua.sprotyv.ui.RolesScreen
import ua.sprotyv.ui.SafeScreen
import ua.sprotyv.ui.SuppliesScreen
import ua.sprotyv.ui.ShieldsScreen

/**
 * The owner's screens, drawn with the made-up demo person ([Demo]) in an in-memory database:
 * no real data is ever involved. For checking type, alignment and colour by eye.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "uk-w400dp-h1800dp-xhdpi")
class PersonalScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: Application
    private lateinit var content: Content
    private var db: ua.sprotyv.personal.PersonalDb? = null
    private val nav = object : Nav {
        override fun go(route: Route) {}
        override fun back() {}
        override fun home() {}
        override fun replace(route: Route) {}
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        content = Content.get(app)
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        val snap = Demo.snapshot(content)
        val d = androidx.room.Room.inMemoryDatabaseBuilder(app, ua.sprotyv.personal.PersonalDb::class.java).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking { BackupData.replaceAll(d, snap, BACKED_UP_SETTINGS) }
        db = d
        PersonalStore.showForTest(d)
        HomePrefs.update(app) { it.copy(mode = Mode.FULL, pins = Preset.GENERAL.pins, medic = false, trip = false) }
    }

    @org.junit.After fun tearDown() { db?.close() }

    private fun shot(name: String, dark: Boolean = false, big: Boolean = false, body: @Composable () -> Unit) {
        if (big) org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        SprotyvTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { body() }
                        }
                    }
                }
                // The database answers on its own threads: give it time, then draw
                repeat(24) {
                    shadowOf(Looper.getMainLooper()).idle()
                    compose.waitForIdle()
                    Thread.sleep(50)
                }
                var bitmap: Bitmap? = null
                scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
                }
                val dir = File("build/screens").apply { mkdirs() }
                File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            if (big) org.robolectric.RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test fun mine() = shot("40-mine-dark", dark = true) { Frame("Моє", {}) { MineHub(nav, content) } }
    @Test fun mineLight() = shot("40-mine") { Frame("Моє", {}) { MineHub(nav, content) } }
    @Test fun gart() = shot("41-gart-dark", dark = true) { Frame("Щоденний гарт", {}) { GartScreen(nav) } }
    @Test fun gartBig() = shot("41-gart-big", big = true) { Frame("Щоденний гарт", {}) { GartScreen(nav) } }
    @Test fun path() = shot("42-path-dark", dark = true) { Frame("Мій шлях готовності", {}) { PathScreen(nav) } }
    @Test fun roles() = shot("43-roles-dark", dark = true) { Frame("Хто я зараз", {}) { RolesScreen(nav) } }
    @Test fun roleDftg() = shot("44-role-dftg-dark", dark = true) { Frame("ДФТГ", {}) { RoleScreen(ROLES.getValue("dftg"), content, nav) } }
    @Test fun roleDobro() = shot("44-role-dobro") { Frame("Доброволець / доброволиця", {}) { RoleScreen(ROLES.getValue("dobro"), content, nav) } }
    @Test fun medical() = shot("45-medical-dark", dark = true) { Frame("Медкартка", {}) { MedicalScreen() } }
    @Test fun people() = shot("46-people") { Frame("Довірені люди", {}) { PeopleScreen() } }
    @Test fun plan() = shot("47-plan-dark", dark = true) { Frame("План родини", {}) { PlanScreen() } }
    @Test fun kit() = shot("48-kit-dark", dark = true) { Frame("Тривожна валіза", {}) { KitScreen(content, nav) } }
    @Test fun supplies() = shot("49-supplies") { Frame("Запаси вдома", {}) { SuppliesScreen(content, nav) } }
    @Test fun notes() = shot("50-notes-dark", dark = true) { Frame("Нотатки", {}) { NotesScreen(nav) } }
    @Test fun newPlace() = shot("51-new-place-dark", dark = true) { Frame("Нове місце", {}) { NewPlaceScreen(content, nav) } }
    @Test fun safe() = shot("52-safe") { Frame("Я в безпеці", {}) { SafeScreen(nav) } }
    @Test fun shields() = shot("53-shields-dark", dark = true) { Frame("Мої щити", {}) { ShieldsScreen(nav) } }
    @Test fun shieldsLight() = shot("53-shields") { Frame("Мої щити", {}) { ShieldsScreen(nav) } }
}
