package ua.sprotyv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
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
        if (savedInstanceState == null) { clearScans(this); Docs.clearShared(this) }
        HomePrefs.load(this)
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
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                    App()
                    // "Added to the home screen" and the like: above the bottom tabs, never under the keyboard
                    NoticeHost(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
                        .navigationBarsPadding().imePadding().padding(bottom = 72.dp))
                }
            }
        }
    }

    private val closeLater = android.os.Handler(android.os.Looper.getMainLooper())
    private val closeNow = Runnable {
        Away.back()
        // A photo the camera was making for Documents is not kept plain once the data closes
        java.io.File(cacheDir, "scan/doc.jpg").delete()
        forgetTyped()
        Notices.finishPending()
        PersonalStore.close()
        Guard.close()
    }

    /** Typed or scanned text waiting in memory goes with the data: drafts, the three inboxes, the last search. */
    private fun forgetTyped() {
        EditGuard.drafts.clear()
        SearchMemory.query = ""
        QrInbox.text = ""
        NoteInbox.title = ""
        NoteInbox.text = ""
        PlanInbox.text = null
        // A backup waiting for a yes or no, with its password and decrypted data, is dropped; one
        // being written goes on (it holds its own copy) and one being read is thrown away when done
        BackupJobs.forget()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        Incoming.take(intent)
        Opening.take(intent)
    }

    override fun onResume() {
        super.onResume()
        // A document handed to another app is not kept plain for long
        Docs.clearSharedSoon(this)
        ShareIn.sync(this)
        closeLater.removeCallbacks(closeNow)
        stopWatchingScreen()
        Away.back()
    }

    /**
     * While the owner is in our own window (a picker, the camera) the data may stay open a while;
     * with the lock on "My data", the phone's screen going off closes it at once, so a phone left
     * there and unlocked again by someone else never shows it without the fingerprint or PIN.
     */
    private var screenOff: android.content.BroadcastReceiver? = null

    private fun watchScreen() {
        if (screenOff != null || ua.sprotyv.personal.PersonalStore.lockMode(this)?.locked != true) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                closeLater.removeCallbacks(closeNow)
                closeNow.run()
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, android.content.Context.RECEIVER_NOT_EXPORTED) else registerReceiver(r, filter)
            screenOff = r
        } catch (e: Exception) {
            // Without it the data still closes by the timer above (onStop), only not the moment the screen goes off
        }
    }

    private fun stopWatchingScreen() {
        screenOff?.let { try { unregisterReceiver(it) } catch (e: Exception) { /* already gone */ } }
        screenOff = null
    }

    override fun onDestroy() {
        stopWatchingScreen()
        super.onDestroy()
    }

    /** Leaving the screen closes the owner's data and the settings; both open again when asked for. */
    override fun onStop() {
        super.onStop()
        // Gone into our own window (a file picker, the camera): if the owner does not come back
        // from it within three minutes, the data closes anyway
        // (two minutes: enough to pick a file or frame a photo; someone else picking up the phone
        // meanwhile has little time to come back to it past the lock, and screen-off closes at once)
        if (!isChangingConfigurations && Away.expected) { closeLater.postDelayed(closeNow, 2 * 60_000L); watchScreen() }
        if (!isChangingConfigurations && !Away.expected) {
            // Drafts are kept in memory only for a turn of the screen, never while the app is away
            forgetTyped()
            PersonalStore.close()
            // Settings lock again; not while Android's own PIN window is what covers the app
            Guard.close()
            // A document handed to another app does not stay plain in the cache: ten minutes, so a
            // mail or messenger that reads it only on Send still has it
            Docs.clearSharedSoon(this, 10 * 60_000L)
        }
    }
}
