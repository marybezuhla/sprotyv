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
        setContent {
            SprotyvTheme {
                App()
            }
        }
    }

    /** Leaving the screen closes the owner's data; it is opened again on entering the section. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) PersonalStore.close()
    }
}
