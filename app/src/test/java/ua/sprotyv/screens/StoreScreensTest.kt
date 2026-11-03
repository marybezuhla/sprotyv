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
import ua.sprotyv.ui.KitScreen
import ua.sprotyv.ui.Route
import ua.sprotyv.ui.SearchMemory
import ua.sprotyv.ui.SearchScreen
import ua.sprotyv.ui.ShieldsScreen
import ua.sprotyv.ui.SprotyvTheme
import ua.sprotyv.ui.ThemeChoice
import ua.sprotyv.personal.Note
import ua.sprotyv.ui.GartScreen
import ua.sprotyv.ui.KEY_SAFE_MESSAGE
import ua.sprotyv.ui.KidHome
import ua.sprotyv.ui.DocumentsScreen
import ua.sprotyv.ui.MozListScreen
import ua.sprotyv.ui.NotesScreen
import ua.sprotyv.ui.PathScreen
import ua.sprotyv.ui.PdrScreen
import ua.sprotyv.ui.PeopleScreen
import ua.sprotyv.ui.PowerScreen
import ua.sprotyv.ui.PracticeScreen
import ua.sprotyv.ui.ResistanceScreen
import ua.sprotyv.ui.RolesScreen
import ua.sprotyv.ui.SafeScreen
import ua.sprotyv.ui.SectionsList
import ua.sprotyv.ui.SimpleHome
import ua.sprotyv.ui.SituationScreen
import ua.sprotyv.ui.SuppliesScreen
import ua.sprotyv.ui.WeaponsScreen
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
        // Three invented documents: small files encrypted as the app does, with their rows
        val files = ua.sprotyv.personal.docs.DocFiles.of(app)
        kotlinx.coroutines.runBlocking {
            (if (lang == "en") listOf("Passport" to 2_400_000L, "Blood test, October" to 380_000L, "Birth certificate" to 1_900_000L)
            else listOf("Паспорт" to 2_400_000L, "Аналіз крові, жовтень" to 380_000L, "Свідоцтво про народження" to 1_900_000L)).forEachIndexed { i, (t, size) ->
                val w = files.write(ByteArray(size.toInt()).inputStream())
                d.documents().add(ua.sprotyv.personal.Document(title = t, mime = if (i == 1) "application/pdf" else "image/jpeg", size = w.size, file = w.file, key = w.key, addedAt = 1_760_000_000_000L + i))
            }
        }
        db = d
        PersonalStore.showForTest(d)
        HomePrefs.update(app) { it.copy(mode = Mode.FULL, pins = Preset.GENERAL.pins, trip = false) }
        fun title(id: Int) = app.getString(id)
        shot(dir, "1") { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) }
        shot(dir, "2") { NowScreen(content, nav) }
        HomePrefs.update(app) { it.copy(mode = Mode.SIMPLE) }
        shot(dir, "3") { CardScreen(content, "bleeding", nav) }
        HomePrefs.update(app) { it.copy(mode = Mode.FULL) }
        shot(dir, "4") { GuideSectionScreen(content, "2.5", nav) }
        shot(dir, "5") { Frame(title(R.string.medical_card), {}) { MedicalScreen() } }
        shot(dir, "6", dark = true) { Frame(title(R.string.kit), {}) { KitScreen(content, nav) } }
        shot(dir, "7") { Frame(title(R.string.shields_title), {}) { ShieldsScreen(nav) } }
        // Search, as someone in a hurry types it
        SearchMemory.query = if (lang == "en") "tourniquet" else "турнікет"
        shot(dir, "8", dark = true) { SearchScreen(content, nav) }
        SearchMemory.query = ""

        // More pictures for presentations and other stores (not in the F-Droid listing)
        val more = "$dir-more"
        HomePrefs.update(app) { it.copy(mode = Mode.SIMPLE) }
        shot(more, "09-simple-home") { SimpleHome(content, Preset.GENERAL.pins, nav) {} }
        HomePrefs.update(app) { it.copy(mode = Mode.KID) }
        shot(more, "10-kid-home") { KidHome(content, nav) {} }
        HomePrefs.update(app) { it.copy(mode = Mode.FULL) }
        shot(more, "11-people") { Frame(title(R.string.people), {}) { PeopleScreen() } }
        shot(more, "12-safe") { Frame(title(R.string.tile_safe), {}) { SafeScreen(nav) } }
        shot(more, "13-notes", dark = true) { Frame(title(R.string.notes_title_screen), {}) { NotesScreen(nav) } }
        shot(more, "14-supplies") { Frame(title(R.string.supplies), {}) { SuppliesScreen(content, nav) } }
        shot(more, "15-resilience", dark = true) { Frame(title(R.string.gart_title), {}) { GartScreen(nav) } }
        shot(more, "16-path") { Frame(title(R.string.path_title), {}) { PathScreen(nav) } }
        shot(more, "17-roles", dark = true) { Frame(title(R.string.roles_title), {}) { RolesScreen(nav) } }
        shot(more, "18-resistance") { ResistanceScreen(content, nav) }
        shot(more, "19-situation") { SituationScreen(content, nav) }
        shot(more, "20-power", dark = true) { PowerScreen(content, nav) }
        shot(more, "21-road-rules") { PdrScreen(content, nav) }
        shot(more, "22-weapons-law", dark = true) { WeaponsScreen(nav) }
        shot(more, "23-moz") { MozListScreen(content, nav) }
        shot(more, "24-practice", dark = true) { PracticeScreen(content, "", nav) }
        shot(more, "25-card-overview", dark = true) { CardScreen(content, "bleeding", nav) }
        shot(more, "26-sections") { Frame(title(R.string.tab_know), {}) { SectionsList(content, Preset.GENERAL.pins, nav) { _, _ -> } } }
        shot(more, "28-documents") { Frame(title(R.string.docs_title), {}) { DocumentsScreen() } }
        RuntimeEnvironment.setFontScale(1.3f)
        try { shot(more, "27-large-text") { FullHome(content, Preset.GENERAL.pins, nav, {}, {}, {}) } } finally { RuntimeEnvironment.setFontScale(1f) }
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
        // Notes and the "I am safe" message in the shown language, invented
        val noteTexts = if (en) listOf(
            "Documents" to "Passports and birth certificates: blue folder, top shelf. Copies on the memory stick in the go-bag.",
            "Water" to "Change the bottles in the hallway every 6 months. Next time: May.",
            "Neighbours" to "Maria (flat 12) has a generator. Taras (flat 7) is a nurse.",
            "Shelter" to "Basement entrance from the yard. Key with the house manager.",
            "Medicines" to "Inhaler, painkillers, plasters. Check the dates in spring.",
            "Car" to "Keep the tank at least half full. Spare canister in the garage.",
        ) else listOf(
            "Документи" to "Паспорти й свідоцтва: синя тека, верхня полиця. Копії на флешці в тривожній валізі.",
            "Вода" to "Міняти пляшки в коридорі кожні 6 місяців. Наступного разу — у травні.",
            "Сусіди" to "Марія (кв. 12) має генератор. Тарас (кв. 7) — медбрат.",
            "Укриття" to "Вхід у підвал з подвір'я. Ключ у старшої по будинку.",
            "Ліки" to "Інгалятор, знеболювальне, пластирі. Перевірити терміни навесні.",
            "Авто" to "Тримати бак щонайменше наполовину повним. Запасна каністра в гаражі.",
        )
        val notes = noteTexts.mapIndexed { i, (t, x) -> Note(id = i + 1L, text = t + "\n" + x, pinned = i < 2, updatedAt = base.created - i * 86_400_000L * 9) }
        val safe = if (en) "I am safe. I am in the shelter, all is well. I will call when I can." else "Я в безпеці. Я в укритті, усе гаразд. Подзвоню, коли зможу."
        val settings = base.settings.map { if (it.key == KEY_SAFE_MESSAGE) it.copy(value = safe) else it }
        return base.copy(medical = medical, people = people, plan = plan, rows = planRows, notes = notes, settings = settings)
    }
}
