package ua.sprotyv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import ua.sprotyv.R
import ua.sprotyv.home.Group

/**
 * "My way": how to hold on and grow in a long war, in one place — daily habits, the path of
 * resistance, a new place after leaving, practice. Each opens in "Mine", where the ticks are kept
 * encrypted; this page shows only counts already kept outside (the path). Hidden in trip mode.
 */
@Composable
fun WayHub(nav: Nav) {
    val prefs = HomePrefs.state.collectAsState().value
    Text(stringResource(R.string.way_intro), style = MaterialTheme.typography.bodyLarge)
    // One card per way in: no block repeating its own title above a single row
    // "Who I am now" first: how many roles are chosen (never which: those stay inside "Mine")
    val n = prefs?.rolesCount ?: 0
    HubCard(stringResource(R.string.way_roles_title), if (n > 0) stringResource(R.string.roles_chosen, n) else stringResource(R.string.way_roles_none), resist(), leading = { ThemeIcon(R.drawable.ocha_users, resist()) }) { nav.go(Route.Mine(MinePart.ROLES)) }
    if (prefs?.let { gartToday(it.gart) } != null) GartStrip(nav)
    else HubCard(stringResource(R.string.gart_title), stringResource(R.string.gart_hint), Group.PREPARE.tint(), leading = { ThemeIcon(R.drawable.ocha_preparedness, Group.PREPARE.tint()) }) { nav.go(Route.Mine(MinePart.GART)) }
    // What has really been done, as shields: inside "Mine"; here just the way in
    HubCard(stringResource(R.string.shields_title), stringResource(R.string.shields_hint), resist(), leading = { ThemeIcon(R.drawable.ocha_security, resist()) }) { nav.go(Route.Mine(MinePart.SHIELDS)) }
    val share = prefs?.path?.let(::pathShare)
    HubCard(
        stringResource(R.string.path_title),
        share?.let { (d, t) -> stringResource(R.string.path_summary, d, t) } ?: stringResource(R.string.path_open_hint),
        resist(),
        leading = share?.let { (d, t) -> { ReadyRing(d.toFloat() / t, "$d/$t") } } ?: { ThemeIcon(R.drawable.tab_way, resist()) },
    ) { nav.go(Route.Mine(MinePart.PATH)) }
    HubCard(stringResource(R.string.resist_title), stringResource(R.string.resist_hint), resist(), leading = { ThemeIcon(R.drawable.ocha_assembly_point, resist()) }) { nav.go(Route.Resistance) }
    HubCard(stringResource(R.string.np_live_on_title), stringResource(R.string.np_live_on_hint), Group.LEAVE.tint(), leading = { ThemeIcon(R.drawable.ocha_internally_displaced, Group.LEAVE.tint()) }) { nav.go(Route.Mine(MinePart.NEW_PLACE)) }
    HubCard(stringResource(R.string.practice_title), stringResource(R.string.practice_hint), Group.PREPARE.tint(), leading = { ThemeIcon(R.drawable.ocha_training, Group.PREPARE.tint()) }) { nav.go(Route.PracticeRoute("")) }
    Text(stringResource(R.string.way_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
