package ua.sprotyv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ua.sprotyv.personal.PersonalStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A QR photo left from an earlier run goes. Not when Android restores the app, because a
        // photo just taken may be waiting to be read
        if (savedInstanceState == null) clearScans(this)
        setContent {
            SprotyvTheme {
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
