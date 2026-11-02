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
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Preset
import ua.sprotyv.personal.MedicalCard
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.backup.BackupData
import ua.sprotyv.ui.BACKED_UP_SETTINGS
import ua.sprotyv.ui.CardScreen
import ua.sprotyv.ui.Frame
import ua.sprotyv.ui.FullHome
import ua.sprotyv.ui.GuideSectionScreen
import ua.sprotyv.ui.HomePrefs
import ua.sprotyv.ui.MedicalScreen
import ua.sprotyv.ui.Nav
import ua.sprotyv.ui.NowScreen
import ua.sprotyv.ui.PlanScreen
import ua.sprotyv.ui.Route
import ua.sprotyv.ui.ShieldsScreen
import ua.sprotyv.ui.SprotyvTheme
import ua.sprotyv.ui.ThemeChoice
import java.io.File

/**
 * Pictures for app stores (F-Droid and others), in Ukrainian and English, at a phone's size.
 * The owner's screens show an invented person ([storeSnapshot]): made-up names, zero phone
 * numbers, no real place. Written to build/screens/store-<lang>/, then copied by hand into
 * fastlane/metadata/android/<lang>/images/phoneScreenshots after review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "uk-w411dp-h891dp-xxhdpi")
class StoreScreensTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val nav = object : Nav {
        override fun go(route: Route) {}
        override fun back() {}
        override fun home() {}
        override fun replace(route: Route) {}
    }
    private var db: ua.sprotyv.personal.PersonalDb? = null

    @After fun tearDown() {
        PersonalStore.close()
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(5_000) { PersonalStore.state.first { it == null } } }
        db?.close()
    }

    /** The app icon at 512 × 512 for store listings, drawn from the same adaptive icon as on phones. */
    @Test fun icon() {
        val app: Application = ApplicationProvider.getApplicationContext()
        val icon = app.getDrawable(R.mipmap.ic_launcher)!!
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(android.graphics.Canvas(bitmap))
        val out = File("build/screens/store-icon").apply { mkdirs() }
        File(out, "icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ukrainian() = all("uk", "store-uk")

    @Test fun english() = all("en", "store-en-US")

    private fun all(lang: String, dir: String) {
        RuntimeEnvironment.setQualifiers("$lang-w411dp-h891dp-xxhdpi")
        val app: Application = ApplicationProvider.getApplicationContext()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        val content = Content.get(app)
        val d = androidx.room.Room.inMemoryDatabaseBuilder(app, ua.sprotyv.personal.PersonalDb::class.java).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking { BackupData.replaceAll(d, storeSnapshot(content, lang == "en"), BACKED_UP_SETTINGS) }
        db = d
        PersonalStore.showForTest(d)
        HomePrefs.update(app) { it.copy(mode = Mode.FULL, pins = Preset.GENERAL.pins, medic = false, service = false, trip = false) }
        fun title(id: Int) = app.getString(id)
        shot(dir, "1") { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) }
        shot(dir, "2") { NowScreen(content, nav) }
        HomePrefs.update(app) { it.copy(mode = Mode.SIMPLE) }
        shot(dir, "3") { CardScreen(content, "bleeding", nav) }
        HomePrefs.update(app) { it.copy(mode = Mode.FULL) }
        shot(dir, "4") { GuideSectionScreen(content, "2.5", nav) }
        shot(dir, "5") { Frame(title(R.string.medical_card), {}) { MedicalScreen() } }
        shot(dir, "6", dark = true) { Frame(title(R.string.family_plan), {}) { PlanScreen() } }
        shot(dir, "7") { Frame(title(R.string.shields_title), {}) { ShieldsScreen(nav) } }
        shot(dir, "8", dark = true) { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) }
    }

    private fun shot(dir: String, name: String, dark: Boolean = false, body: @Composable () -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    SprotyvTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { body() }
                    }
                }
            }
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
            val out = File("build/screens/$dir").apply { mkdirs() }
            File(out, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** The demo data with an invented person in place of the placeholders. */
    private fun storeSnapshot(content: Content, en: Boolean): BackupData.Snapshot {
        val base = Demo.snapshot(content)
        val medical = (base.medical ?: MedicalCard()).copy(
            name = if (en) "Olena Melnyk" else "Олена Мельник",
            birthDate = "14.03.1987",
            bloodGroup = "A(II) Rh+",
            allergies = if (en) "Penicillin: rash and swelling" else "Пеніцилін: висип і набряк",
            medicines = if (en) "Inhaler for asthma, in the front pocket of the go-bag" else "Інгалятор від астми, у передній кишені валізи",
            conditions = if (en) "Asthma since childhood" else "Астма з дитинства",
            notes = if (en) "Contact lenses. Glasses in the go-bag." else "Контактні лінзи. Окуляри у тривожній валізі.",
        )
        val names = if (en) listOf("Mum", "Andrii", "Iryna", "Taras", "Neighbour Maria", "Work") else listOf("Мама", "Андрій", "Ірина", "Тарас", "Сусідка Марія", "Робота")
        val relations = if (en) listOf("Lives nearby", "Husband", "Sister", "Brother", "Has a car", "Office") else listOf("Живе поруч", "Чоловік", "Сестра", "Брат", "Має авто", "Офіс")
        val people = base.people.mapIndexed { i, p -> p.copy(name = names[i % names.size], relation = relations[i % relations.size]) }
        val plan = base.plan?.copy(
            point1 = if (en) "Basement of our building" else "Підвал нашого будинку",
            point2 = if (en) "Resilience point at the library" else "Пункт незламності біля бібліотеки",
            point3 = if (en) "Grandma's house outside the city" else "Бабусин дім за містом",
            goBetweenName = names[2],
            notes = if (en) "Documents in the blue folder. Whoever is home takes the cat." else "Документи в синій теці. Хто вдома — бере кота.",
        )
        val rows = if (en) listOf(
            "Olena" to "Documents, money, medicines", "Andrii" to "Water, power bank, torch", "Children" to "Own backpacks, phones",
            "No connection" to "Meet at point 1 at the agreed hour", "Evacuation announced" to "Go to the assembly point, send “I am safe”",
        ) else listOf(
            "Олена" to "Документи, гроші, ліки", "Андрій" to "Вода, павербанк, ліхтарик", "Діти" to "Свої рюкзаки, телефони",
            "Немає зв’язку" to "Зустрічаємося в місці 1 о домовленій годині", "Оголосили евакуацію" to "Ідемо до місця збору, пишемо «Я в безпеці»",
        )
        val planRows = base.rows.mapIndexed { i, r -> r.copy(first = rows[i % rows.size].first, second = rows[i % rows.size].second) }
        return base.copy(medical = medical, people = people, plan = plan, rows = planRows)
    }
}
