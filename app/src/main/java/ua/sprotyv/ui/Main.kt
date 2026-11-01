package ua.sprotyv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import ua.sprotyv.personal.PersonalStore

/**
 * The one activity. Opened through two home-screen entries (aliases in the manifest): the usual
 * "ua.sprotyv.ui.MainActivity", so existing home-screen icons keep working, and the neutral one.
 */
class Main : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        Neutral.applyToTask(this)
        enableEdgeToEdge()
        // A QR photo left from an earlier run goes. Not when Android restores the app, because a
        // photo just taken may be waiting to be read
        if (savedInstanceState == null) clearScans(this)
        HomePrefs.load(this)
        // What an earlier version kept about map files opened goes, whether or not the map is opened
        if (savedInstanceState == null) Overview.forgetOpenedFiles(this)
        // A page shared from a browser; only on a fresh start, never again when Android restores the app
        if (savedInstanceState == null) Incoming.take(intent)
        // A notification's tap; and the notifications' alarms planned again from what is on
        if (savedInstanceState == null) Opening.take(intent)
        Widgets.sync(this)
        Notify.sync(this)
        // Screenshots: the owner's own choice, kept until changed (a strip on every "Mine" screen says so)
        setContent {
            val prefs = HomePrefs.state.collectAsState().value ?: HomePrefs.load(this)
            SprotyvTheme(prefs.theme, prefs.dynamic) {
                App()
            }
        }
    }

    private val closeLater = android.os.Handler(android.os.Looper.getMainLooper())
    private val closeNow = Runnable {
        Away.back()
        PersonalStore.close()
        Guard.close()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        Incoming.take(intent)
        Opening.take(intent)
    }

    override fun onResume() {
        super.onResume()
        ShareIn.sync(this)
        closeLater.removeCallbacks(closeNow)
        Away.back()
    }

    /** Leaving the screen closes the owner's data and the settings; both open again when asked for. */
    override fun onStop() {
        super.onStop()
        // Gone into our own window (a file picker, the camera): if the owner does not come back
        // from it within three minutes, the data closes anyway
        if (!isChangingConfigurations && Away.expected) closeLater.postDelayed(closeNow, 3 * 60_000L)
        if (!isChangingConfigurations && !Away.expected) {
            PersonalStore.close()
            // Settings lock again; not while Android's own PIN window is what covers the app
            Guard.close()
        }
    }
}
