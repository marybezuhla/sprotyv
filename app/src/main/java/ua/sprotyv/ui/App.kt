package ua.sprotyv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Pins
import ua.sprotyv.personal.PersonalStore

/** The tabs of full mode. */
enum class Tab(val title: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    SECTIONS(R.string.tab_sections, Icons.AutoMirrored.Filled.List),
    GUIDE(R.string.tile_guide, Icons.Filled.Info),
    MINE(R.string.tile_mine, Icons.Filled.Lock),
}

/**
 * The whole app: a back stack of screens over the home screen (simple mode) or over four tabs
 * (full mode). The guide, cards and calls never wait for the owner's data, which is opened only on
 * the owner's screens.
 */
@Composable
fun App() {
    val context = LocalContext.current
    val activity = context as Activity
    // Small and read once; the home screen needs it before anything is drawn
    remember { HomePrefs.load(context) }
    val prefs = HomePrefs.state.collectAsState().value ?: HomePrefs.load(context)
    val unlocked by Guard.unlocked.collectAsState()

    // The back stack as plain text, so it survives rotation and the app going to the background
    var saved by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab = Tab.entries.firstOrNull { it.name == tabName } ?: Tab.HOME
    // Settings screens stay only while settings are open; after leaving the app they are gone
    val settingsOpen = !prefs.locked || unlocked || !Guard.available(context)
    val trip = prefs.trip
    val stack = saved.mapNotNull(Route::restore).filter { (settingsOpen || !it.settings) && !(trip && Trip.hides(it)) }
    // Drop them from the saved stack too, so they do not come back with the next PIN
    LaunchedEffect(settingsOpen, trip) {
        if (!settingsOpen) saved = saved.filter { s -> Route.restore(s)?.settings != true }
        if (trip) {
            saved = saved.filter { s -> Route.restore(s)?.let(Trip::hides) != true }
            if (tabName == Tab.MINE.name) tabName = Tab.HOME.name
        }
    }
    val top = stack.lastOrNull()
    val full = prefs.mode == Mode.FULL

    val content by produceState<Content?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Content.get(context) }.getOrElse { Content(null, null) } }
    }
    val personal by PersonalStore.state.collectAsState()

    val nav = object : Nav {
        override fun go(route: Route) {
            // An outside source opens outside Sprotyv; nothing is added to the back stack
            if (route is Route.External) {
                content?.links?.firstOrNull { it.id == route.id }?.takeIf { !(trip && Trip.hides(it)) }?.let { openLink(context, it) }
                return
            }
            // Trip mode: what it hides does not open, whatever leads there
            if (trip && Trip.hides(route)) return
            saved = stack.map(Route::save) + route.save()
        }
        // Built from what could be read, so an unreadable entry never makes Back look stuck
        override fun back() { saved = stack.dropLast(1).map(Route::save) }
        override fun home() { saved = emptyList() }
        override fun replace(route: Route) {
            if (trip && Trip.hides(route)) return
            saved = stack.dropLast(1).map(Route::save) + route.save()
        }
    }

    // What runs after the PIN (or at once when settings are not locked)
    fun perform(action: String) {
        when {
            action == ACTION_SETTINGS -> nav.go(Route.Settings)
            action == ACTION_EDIT -> nav.go(Route.EditTiles)
            action == ACTION_TRIP_OFF -> HomePrefs.update(context) { it.copy(trip = false) }
            action.startsWith(ACTION_PIN) -> HomePrefs.update(context) { it.copy(pins = Pins.pin(it.pins, action.removePrefix(ACTION_PIN))) }
            action.startsWith(ACTION_UNPIN) -> HomePrefs.update(context) { it.copy(pins = Pins.unpin(it.pins, action.removePrefix(ACTION_UNPIN))) }
        }
    }
    val prompt = stringResource(R.string.lock_prompt)
    fun guarded(action: String) = Guard.ask(activity, prefs.locked, prompt, action, ::perform)
    val pending by Guard.pending.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { Guard.done(); perform(it) }
    }

    // On entering the owner's screens and on every return to the screen (leaving it closed the data)
    val personalNow = !trip && (top?.personal ?: (full && tab == Tab.MINE))
    LifecycleStartEffect(personalNow) {
        if (personalNow) PersonalStore.open(context) else PersonalStore.close()
        onStopOrDispose { }
    }

    // "Share → Sprotyv" follows the mode, trip mode and the neutral icon
    LaunchedEffect(prefs.mode, trip) { ShareIn.sync(context) }
    // A page shared from a browser opens the bookmarks, where the owner names and keeps it.
    // Not in trip or kid mode, and not before set-up: then it is simply dropped
    val incoming by Incoming.state.collectAsState()
    LaunchedEffect(incoming?.id) {
        if (incoming == null) return@LaunchedEffect
        if (trip || (prefs.mode != Mode.FULL && prefs.mode != Mode.SIMPLE)) Incoming.clear()
        else if (top != Route.Mine(MinePart.BOOKMARKS)) nav.go(Route.Mine(MinePart.BOOKMARKS))
    }

    BackHandler(enabled = top != null) { nav.back() }
    // Back from another tab first returns to "Home", then leaves the app
    BackHandler(enabled = top == null && full && tab != Tab.HOME) { tabName = Tab.HOME.name }

    if (top != null) return Screen(top, content, prefs, personal, nav, ::guarded)

    when (prefs.mode) {
        null -> WelcomeScreen(nav)
        Mode.SIMPLE -> SimpleHome(content, prefs.pins, nav, onSettings = { guarded(ACTION_SETTINGS) })
        Mode.KID -> KidHome(content, nav, onGrownUp = { guarded(ACTION_SETTINGS) })
        Mode.FULL -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalInTab provides true) {
                    when (tab) {
                        Tab.HOME -> FullHome(
                            content, prefs.pins, nav,
                            onSettings = { guarded(ACTION_SETTINGS) },
                            onEdit = { guarded(ACTION_EDIT) },
                            onAdd = { tabName = Tab.SECTIONS.name },
                        )
                        Tab.SECTIONS -> Frame(stringResource(R.string.tab_sections), {}) {
                            SectionsList(content, prefs.pins, nav) { id, on -> guarded((if (on) ACTION_PIN else ACTION_UNPIN) + id) }
                        }
                        Tab.GUIDE -> GuideScreen(content, nav)
                        Tab.MINE -> if (!trip) Personal(R.string.tile_mine, personal, nav) { MineHub(nav, content) }
                    }
                }
            }
            CallBar(edge = false)
            NavigationBar {
                Tab.entries.filter { !(trip && it == Tab.MINE) }.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tabName = t.name },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.title)) },
                        alwaysShowLabel = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun Screen(
    top: Route,
    content: Content?,
    prefs: HomePrefs.State,
    personal: ua.sprotyv.personal.Opened?,
    nav: Nav,
    guarded: (String) -> Unit,
) {
    when (top) {
        Route.Open(Section.MINE) -> Personal(Section.MINE.title, personal, nav) { MineHub(nav, content) }
        Route.Open(Section.SAFE) -> Personal(Section.SAFE.title, personal, nav) { SafeScreen(nav) }
        is Route.Mine -> when (top.part) {
            MinePart.MEDICAL -> Personal(R.string.medical_card, personal, nav) { MedicalScreen() }
            MinePart.PEOPLE -> Personal(R.string.people, personal, nav) { PeopleScreen() }
            MinePart.PLAN -> Personal(R.string.family_plan, personal, nav) { PlanScreen() }
            MinePart.KIT -> Personal(R.string.kit, personal, nav) { KitScreen(content, nav) }
            MinePart.PRINT -> Personal(R.string.print_title, personal, nav) { PrintScreen() }
            MinePart.RESCUER -> Personal(R.string.rescuer_screen, personal, nav) { RescuerQrScreen(nav) }
            MinePart.BACKUP -> Personal(R.string.backup_title, personal, nav) { BackupScreen() }
            MinePart.PLAN_QR -> Personal(R.string.plan_code_title, personal, nav) { PlanCodeScreen() }
            MinePart.PLAN_IMPORT -> Personal(R.string.plan_code_import_title, personal, nav) { PlanImportScreen(nav) }
            MinePart.KID_CALL -> Personal(R.string.kid_call_family, personal, nav) { KidCallScreen() }
            MinePart.BOOKMARKS -> Personal(R.string.bookmarks_title, personal, nav) { BookmarksScreen() }
        }
        Route.Scan -> ScanScreen(nav)
        Route.SuppliesRoute -> Personal(R.string.supplies, personal, nav) { SuppliesScreen(content, nav) }
        is Route.Open -> when (top.section) {
            Section.NOW -> NowScreen(content, nav)
            Section.GUIDE -> GuideScreen(content, nav)
            Section.ALARM -> AlarmScreen(content, nav)
            Section.HOME_LIFE -> HomeLifeScreen(content, nav)
            else -> SectionScreen(section = top.section, onBack = nav::back)
        }
        is Route.CardRoute -> CardScreen(content, top.id, nav)
        is Route.ChapterRoute -> ChapterScreen(content, top.id, nav)
        is Route.GuideSection -> GuideSectionScreen(content, top.num, nav)
        Route.Search -> SearchScreen(content, nav)
        Route.About -> AboutScreen(content, nav)
        Route.Settings -> SettingsScreen(prefs, nav)
        Route.EditTiles -> EditTilesScreen(content, prefs.mode, prefs.pins, nav)
        Route.Presets -> PresetsScreen(nav)
        Route.Situation -> SituationScreen(content, nav)
        Route.Power -> PowerScreen(content, nav)
        Route.Directory -> DirectoryScreen(content, nav)
        Route.QrText -> QrTextScreen(nav)
        is Route.PracticeRoute -> PracticeScreen(content, top.card, nav)
        is Route.External -> {}
        Route.Sections -> Frame(stringResource(R.string.add_tile), nav::back) {
            SectionsList(content, prefs.pins, nav) { id, on -> guarded((if (on) ACTION_PIN else ACTION_UNPIN) + id) }
        }
    }
}

private const val ACTION_SETTINGS = "settings"
private const val ACTION_EDIT = "edit"
const val ACTION_TRIP_OFF = "trip_off"
private const val ACTION_PIN = "pin:"
private const val ACTION_UNPIN = "unpin:"

interface Nav {
    fun go(route: Route)
    fun back()
    fun home()
    /** Swaps the current screen for [route], so Back does not return to it. */
    fun replace(route: Route)
}
