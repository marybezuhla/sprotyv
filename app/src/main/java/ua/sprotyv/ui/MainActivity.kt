package ua.sprotyv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import ua.sprotyv.personal.PersonalStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A QR photo left from an earlier run goes. Not when Android restores the app, because a
        // photo just taken may be waiting to be read
        if (savedInstanceState == null) clearScans(this)
        HomePrefs.load(this)
        // Screenshots of "Mine" are for showing the app to testers: off again at every fresh start
        if (savedInstanceState == null && HomePrefs.load(this).screenshots) HomePrefs.update(this) { it.copy(screenshots = false) }
        setContent {
            val prefs = HomePrefs.state.collectAsState().value ?: HomePrefs.load(this)
            SprotyvTheme(prefs.theme, prefs.dynamic) {
                App()
            }
        }
    }

    /** Leaving the screen closes the owner's data and the settings; both open again when asked for. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            PersonalStore.close()
            // Settings lock again; not while Android's own PIN window is what covers the app
            Guard.close()
        }
    }
}
