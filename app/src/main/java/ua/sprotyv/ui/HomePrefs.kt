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
        /** The call bar at the bottom; off, a phone button sits in the corner instead. */
        val callBar: Boolean = true,
        /** Screenshots of the owner's screens allowed (off by default; the owner's choice, kept until changed). */
        val screenshots: Boolean = false,
        /** The readiness shares last seen in "Mine" ("2/4,1/2,…"), shown on the full home screen; no records. */
        val readiness: String = "",
        val readinessHidden: Boolean = false,
        /** "My path" as last seen, per level ("4/4,2/3,0/4,0/3"): only counts, for the home screen and "Resistance"; no steps. */
        val path: String = "",
        /** The last level of "My path" whose completion was already said on the home screen (-1: none). */
        val pathCelebrated: Int = -1,
        /** "Daily hardening" today as last seen ("20393:1/3": the day, done, chosen): counts only, never which habits. */
        val gart: String = "",
        /** Trip mode: the owner's data and sensitive sources are off every screen (see [Trip]). */
        val trip: Boolean = false,
        /** The owner has medical training: first-aid procedures open as the full official text. */
        val medic: Boolean = false,
        /** Facts about military service shown in "Resistance" (adults who chose to see them). */
        val service: Boolean = false,
    )

    private const val FILE = "home"
    private const val MODE = "mode"
    private const val PINS = "pins"
    private const val LOCK = "lock"
    private const val THEME = "theme"
    private const val DYNAMIC = "dynamic"
    private const val CALL_BAR = "call_bar"
    private const val SCREENSHOTS = "screenshots"
    private const val READINESS = "readiness"
    private const val READINESS_HIDDEN = "readiness_hidden"
    private const val PATH = "path_share"
    private const val PATH_CELEBRATED = "path_celebrated"
    private const val GART = "gart_share"
    private const val TRIP = "trip"
    private const val MEDIC = "medic"
    private const val SERVICE = "service"

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
            callBar = p.getBoolean(CALL_BAR, true),
            screenshots = p.getBoolean(SCREENSHOTS, false),
            readiness = p.getString(READINESS, null).orEmpty().take(100),
            readinessHidden = p.getBoolean(READINESS_HIDDEN, false),
            path = p.getString(PATH, null).orEmpty().take(40),
            pathCelebrated = p.getInt(PATH_CELEBRATED, -1),
            gart = p.getString(GART, null).orEmpty().take(30),
            trip = p.getBoolean(TRIP, false),
            medic = p.getBoolean(MEDIC, false),
            service = p.getBoolean(SERVICE, false),
        )
        flow.value = s
        return s
    }

    /**
     * The look and set-up for a backup, so a new phone needs no setting up again. Not carried:
     * trip mode and screenshots (on purpose, each is switched on by hand) and the readiness shares
     * (worked out again).
     */
    fun export(context: Context): Map<String, String> {
        val s = load(context)
        return buildMap {
            s.mode?.let { put("mode", it.name) }
            put("pins", Pins.write(s.pins))
            put("lock", s.locked.toString())
            put("theme", s.theme.name)
            put("dynamic", s.dynamic.toString())
            put("call_bar", s.callBar.toString())
            put("readiness_hidden", s.readinessHidden.toString())
            put("medic", s.medic.toString())
            put("service", s.service.toString())
        }
    }

    /** Applies a backup's look and set-up; anything unknown or malformed is left as it is. */
    fun import(context: Context, home: Map<String, String>) {
        if (home.isEmpty()) return
        fun flag(k: String, now: Boolean) = home[k]?.toBooleanStrictOrNull() ?: now
        update(context) { s ->
            s.copy(
                mode = home["mode"]?.let { m -> Mode.entries.firstOrNull { it.name == m } } ?: s.mode,
                pins = home["pins"]?.let(Pins::parse) ?: s.pins,
                locked = flag("lock", s.locked),
                theme = home["theme"]?.let { t -> ThemeChoice.entries.firstOrNull { it.name == t } } ?: s.theme,
                dynamic = flag("dynamic", s.dynamic),
                callBar = flag("call_bar", s.callBar),
                readinessHidden = flag("readiness_hidden", s.readinessHidden),
                medic = flag("medic", s.medic),
                service = flag("service", s.service),
            )
        }
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
            .putBoolean(CALL_BAR, next.callBar)
            .putBoolean(SCREENSHOTS, next.screenshots)
            .putString(READINESS, next.readiness)
            .putBoolean(READINESS_HIDDEN, next.readinessHidden)
            .putString(PATH, next.path)
            .putInt(PATH_CELEBRATED, next.pathCelebrated)
            .putString(GART, next.gart)
            .putBoolean(TRIP, next.trip)
            .putBoolean(MEDIC, next.medic)
            .putBoolean(SERVICE, next.service)
            .apply()
    }
}
