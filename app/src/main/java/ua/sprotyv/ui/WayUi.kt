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
    // "Who I am now": which roles, only inside "Mine"; here just the way in
    Block(stringResource(R.string.roles_title), Group.PREPARE, tint = resist(), padded = false) {
        BlockRow(stringResource(R.string.roles_title), stringResource(R.string.roles_hint), first = true) { nav.go(Route.Mine(MinePart.ROLES)) }
    }
    Block(stringResource(R.string.gart_title), Group.PREPARE, padded = false) {
        BlockRow(stringResource(R.string.gart_title), stringResource(R.string.gart_hint), first = true) { nav.go(Route.Mine(MinePart.GART)) }
    }
    val share = prefs?.path?.let(::pathShare)
    Block(
        stringResource(R.string.path_title), Group.PREPARE, tint = resist(), padded = false,
        summary = share?.let { (d, t) -> stringResource(R.string.path_summary, d, t) },
        leading = share?.let { (d, t) -> { ReadyRing(d.toFloat() / t, "$d/$t") } },
    ) {
        BlockRow(stringResource(R.string.path_week_open), stringResource(R.string.path_open_hint), first = true) { nav.go(Route.Mine(MinePart.PATH)) }
        BlockRow(stringResource(R.string.resist_title), stringResource(R.string.resist_hint)) { nav.go(Route.Resistance) }
    }
    Block(stringResource(R.string.np_live_on_title), Group.LEAVE, padded = false) {
        BlockRow(stringResource(R.string.np_title), stringResource(R.string.np_live_on_hint), first = true) { nav.go(Route.Mine(MinePart.NEW_PLACE)) }
    }
    Block(stringResource(R.string.practice_title), Group.PREPARE, padded = false) {
        BlockRow(stringResource(R.string.practice_title), stringResource(R.string.practice_hint), first = true) { nav.go(Route.PracticeRoute("")) }
    }
    Text(stringResource(R.string.way_private), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
