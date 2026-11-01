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
    if (prefs?.let { gartToday(it.gart) } != null) GartStrip(nav)
    else HubCard(stringResource(R.string.gart_title), stringResource(R.string.gart_hint), Group.PREPARE.tint()) { nav.go(Route.Mine(MinePart.GART)) }
    // What has really been done, as shields: inside "Mine"; here just the way in
    HubCard(stringResource(R.string.shields_title), stringResource(R.string.shields_hint), resist()) { nav.go(Route.Mine(MinePart.SHIELDS)) }
    // "Who I am now": which roles, only inside "Mine"; here just the way in
    HubCard(stringResource(R.string.roles_title), stringResource(R.string.roles_hint), resist()) { nav.go(Route.Mine(MinePart.ROLES)) }
    val share = prefs?.path?.let(::pathShare)
    HubCard(
        stringResource(R.string.path_title),
        share?.let { (d, t) -> stringResource(R.string.path_summary, d, t) } ?: stringResource(R.string.path_open_hint),
        resist(),
        leading = share?.let { (d, t) -> { ReadyRing(d.toFloat() / t, "$d/$t") } },
    ) { nav.go(Route.Mine(MinePart.PATH)) }
    HubCard(stringResource(R.string.resist_title), stringResource(R.string.resist_hint), resist()) { nav.go(Route.Resistance) }
    HubCard(stringResource(R.string.np_live_on_title), stringResource(R.string.np_live_on_hint), Group.LEAVE.tint()) { nav.go(Route.Mine(MinePart.NEW_PLACE)) }
    HubCard(stringResource(R.string.practice_title), stringResource(R.string.practice_hint), Group.PREPARE.tint()) { nav.go(Route.PracticeRoute("")) }
    Text(stringResource(R.string.way_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
