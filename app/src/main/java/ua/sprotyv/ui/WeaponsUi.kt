package ua.sprotyv.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.sprotyv.R
import ua.sprotyv.home.Group

/**
 * "Weapons and the law": what to do with a found weapon first, then what the law says about
 * declaring it, one's own weapon, liability and lawful training. Law and safety only, never how to
 * handle or make anything. Every fact from the law's current text or an official body, with the date.
 */
@Composable
fun WeaponsScreen(nav: Nav) {
    Frame(stringResource(R.string.weapons_title), nav::back) {
        Text(stringResource(R.string.weapons_intro), style = ReadingText)
        Facts(R.string.weapons_found_title, Group.DANGER, listOf(R.string.weapons_found_1, R.string.weapons_found_2, R.string.weapons_found_3, R.string.weapons_found_4), R.string.weapons_found_source, startOpen = true)
        BlockRow(stringResource(R.string.weapons_suspicious), stringResource(R.string.weapons_suspicious_hint), first = true) { nav.go(Route.CardRoute("suspicious")) }
        Facts(R.string.weapons_declare_title, Group.PREPARE, listOf(R.string.weapons_declare_1, R.string.weapons_declare_2, R.string.weapons_declare_3, R.string.weapons_declare_4, R.string.weapons_declare_5), R.string.weapons_declare_source)
        Facts(R.string.weapons_own_title, Group.PREPARE, listOf(R.string.weapons_own_1, R.string.weapons_own_2, R.string.weapons_own_3, R.string.weapons_own_4, R.string.weapons_own_5), R.string.weapons_own_source)
        Facts(R.string.weapons_law_title, Group.PREPARE, listOf(R.string.weapons_law_1, R.string.weapons_law_2, R.string.weapons_law_3, R.string.weapons_law_4), R.string.weapons_law_source)
        Facts(R.string.weapons_learn_title, Group.PREPARE, listOf(R.string.weapons_learn_1, R.string.weapons_learn_2, R.string.weapons_learn_3), R.string.weapons_learn_source)
        BlockRow(stringResource(R.string.role_dftg), stringResource(R.string.role_dftg_hint), first = true) { nav.go(Route.RoleRoute("dftg")) }
    }
}
