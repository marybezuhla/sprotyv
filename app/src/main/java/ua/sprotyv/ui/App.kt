package ua.sprotyv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.sprotyv.content.Content
import ua.sprotyv.personal.PersonalStore

/**
 * The whole app: a back stack of screens over the home screen. The guide, cards and calls never
 * wait for the owner's data, which is opened only in the owner's section.
 */
@Composable
fun App() {
    val context = LocalContext.current
    // The back stack as plain text, so it survives rotation and the app going to the background
    var saved by rememberSaveable { mutableStateOf(listOf<String>()) }
    val stack = saved.mapNotNull(Route::restore)
    val top = stack.lastOrNull()

    val content by produceState<Content?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Content.get(context) }.getOrElse { Content(null, null) } }
    }
    val personal by PersonalStore.state.collectAsState()

    val nav = object : Nav {
        override fun go(route: Route) { saved = saved + route.save() }
        // Built from what could be read, so an unreadable entry never makes Back look stuck
        override fun back() { saved = stack.dropLast(1).map(Route::save) }
        override fun home() { saved = emptyList() }
    }

    // On entering the owner's section and on every return to the screen (leaving it closed the data)
    LifecycleStartEffect(top) {
        if (top == Route.Open(Section.MINE)) PersonalStore.open(context)
        onStopOrDispose { }
    }

    BackHandler(enabled = top != null) { nav.back() }

    when (top) {
        null -> HomeScreen(onOpen = { nav.go(Route.Open(it)) })
        Route.Open(Section.MINE) -> PersonalGate(
            state = personal,
            onTryAgain = { PersonalStore.close(); PersonalStore.open(context) },
            onStartFresh = { PersonalStore.startFresh(context) },
            onBack = nav::back,
        )
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
    }
}

interface Nav {
    fun go(route: Route)
    fun back()
    fun home()
}
