package ua.sprotyv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import ua.sprotyv.personal.PersonalStore

/**
 * The whole app. The guide, cards and calls never wait for the owner's data: it is opened only
 * when the owner's section is entered, and nothing on the other screens depends on it.
 */
@Composable
fun App() {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf<Section?>(null) }
    val personal by PersonalStore.state.collectAsState()

    // On entering the section and on every return to the screen (leaving it closed the data)
    LifecycleStartEffect(section) {
        if (section == Section.MINE) PersonalStore.open(context)
        onStopOrDispose { }
    }

    BackHandler(enabled = section != null) { section = null }

    when (val s = section) {
        null -> HomeScreen(onOpen = { section = it })
        Section.MINE -> PersonalGate(
            state = personal,
            onTryAgain = { PersonalStore.close(); PersonalStore.open(context) },
            onStartFresh = { PersonalStore.startFresh(context) },
            onBack = { section = null },
        )
        else -> SectionScreen(section = s, onBack = { section = null })
    }
}
