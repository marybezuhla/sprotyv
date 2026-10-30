package ua.sprotyv.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.sprotyv.home.Mode
import ua.sprotyv.home.Pins

/**
 * How the home screen is set up: the mode, the chosen tiles and the settings lock.
 * Kept outside the encrypted data on purpose: the home screen, the cards and 112 must open at once,
 * even when the owner's data cannot. It holds no personal records, only which tiles are shown,
 * and is never in Android backups (see data_extraction_rules.xml). The README says so.
 */
object HomePrefs {
    data class State(
        val mode: Mode?,
        val pins: List<String>,
        val locked: Boolean,
        val theme: ThemeChoice = ThemeChoice.SYSTEM,
        val dynamic: Boolean = false,
    )

    private const val FILE = "home"
    private const val MODE = "mode"
    private const val PINS = "pins"
    private const val LOCK = "lock"
    private const val THEME = "theme"
    private const val DYNAMIC = "dynamic"

    private val flow = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = flow.asStateFlow()

    fun load(context: Context): State {
        flow.value?.let { return it }
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val s = State(
            mode = Mode.entries.firstOrNull { it.name == p.getString(MODE, null) },
            pins = Pins.parse(p.getString(PINS, null)),
            locked = p.getBoolean(LOCK, false),
            theme = ThemeChoice.entries.firstOrNull { it.name == p.getString(THEME, null) } ?: ThemeChoice.SYSTEM,
            dynamic = p.getBoolean(DYNAMIC, false),
        )
        flow.value = s
        return s
    }

    fun update(context: Context, change: (State) -> State) {
        val next = change(load(context))
        flow.value = next
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(MODE, next.mode?.name)
            .putString(PINS, Pins.write(next.pins))
            .putBoolean(LOCK, next.locked)
            .putString(THEME, next.theme.name)
            .putBoolean(DYNAMIC, next.dynamic)
            .apply()
    }
}
