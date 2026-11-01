package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Link
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach

private const val FOCUS_PAGE = "https://play.google.com/store/apps/details?id=org.mozilla.focus"

/** Opens a source: a phone number in the dialler, an address in its own app or a Custom Tab. */
fun openLink(context: Context, link: Link, toolbar: Int? = null) {
    if (link.url.isEmpty() && link.phone.isNotEmpty()) Reach.dial(context, link.phone)
    else Reach.open(context, link.url, link.app, toolbar)
}

/** The colour of a kind of source, by what it is for. */
internal fun linkGroupColour(g: String): Group = when (g) {
    "threats" -> Group.DANGER
    "evac", "idp" -> Group.LEAVE
    "help", "mind", "health", "care" -> Group.FIRST_AID
    "search", "kids" -> Group.FAMILY
    Link.POWER -> Group.HOME_LIFE
    else -> Group.GUIDE
}

private val Link.groupTitle: Int get() = when (group) {
    "threats" -> R.string.situation_threats
    "front" -> R.string.situation_front
    "help" -> R.string.situation_help
    "evac" -> R.string.situation_evac
    "search" -> R.string.situation_search
    "mind" -> R.string.situation_mind
    "rights" -> R.string.situation_rights
    "idp" -> R.string.dir_idp
    "money" -> R.string.dir_money
    "docs" -> R.string.dir_docs
    "kids" -> R.string.dir_kids
    "veterans" -> R.string.dir_veterans
    "health" -> R.string.dir_health
    "care" -> R.string.dir_care
    Link.POWER -> R.string.power_title
    Link.MAPS -> R.string.situation_maps
    else -> R.string.situation_facts
}

internal val Link.kindTitle: Int get() = when (kind) {
    Link.NGO -> R.string.kind_ngo
    Link.OFFICIAL -> R.string.kind_official
    Link.VOLUNTEER -> R.string.kind_volunteer
    else -> R.string.kind_independent
}

/**
 * "Situation": where to look, and how to read it. Sprotyv collects nothing itself: it leads to the
 * best sources, says who runs each one and how far to trust it, and opens them outside the app.
 * The rules for reading a threat map come first, offline, because the map never replaces the alert.
 */
@Composable
fun SituationScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.situation_title), nav::back) {
        Block(stringResource(R.string.situation_rules_title), ua.sprotyv.home.Group.GUIDE) {
            listOf(R.string.situation_rule_1, R.string.situation_rule_2, R.string.situation_rule_3).forEachIndexed { i, r ->
                Row {
                    Text("${i + 1}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(24.dp))
                    Text(stringResource(r), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.situation_rules_source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The map of Ukraine is always inside, with no internet (not in trip mode)
        if (HomePrefs.state.collectAsState().value?.trip != true) Block(stringResource(R.string.map_title), Group.GUIDE, padded = false) {
            BlockRow(stringResource(R.string.map_title), stringResource(R.string.map_hint), first = true) { nav.go(Route.MapRoute) }
        }
        if (content == null) return@Frame Loading()
        if (content.linksBroken) return@Frame Broken()
        // Power cuts have their own screen: one row per oblast would crowd this one
        Block(stringResource(R.string.power_title), Group.HOME_LIFE, padded = false) {
            BlockRow(stringResource(R.string.power_title), stringResource(R.string.power_hint), first = true) { nav.go(Route.Power) }
        }
        val trip = HomePrefs.state.collectAsState().value?.trip == true
        content.links.filter { it.group != Link.POWER && it.group != Link.ROLES && it.group !in Link.DIRECTORY && it.group !in Link.RESIST && !(trip && Trip.hides(it)) }.groupBy { it.group }.forEach { (g, links) ->
            // Each kind of source in its colour, folded: open the one needed now
            Block(stringResource(links.first().groupTitle), linkGroupColour(g), foldable = true, summary = stringResource(R.string.shelf_count, links.size)) {
                links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
            }
        }
        // Said plainly: whether this phone's browser keeps a trace of the pages
        // Worked out again on every return, e.g. after installing Firefox Focus
        var trace by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Reach.trace(context)) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
            trace = Reach.trace(context)
            onPauseOrDispose { }
        }
        Text(
            stringResource(
                when (trace) {
                    Reach.Trace.FOCUS -> R.string.situation_focus
                    Reach.Trace.FOCUS_NOT_FROM_PLAY -> R.string.situation_focus_other
                    Reach.Trace.CHROME_TAB -> R.string.situation_no_trace
                    Reach.Trace.BROWSER -> R.string.situation_trace
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trace != Reach.Trace.FOCUS && trace != Reach.Trace.FOCUS_NOT_FROM_PLAY && Reach.hasPlay(context)) {
            // Any phone, any main browser: Firefox Focus erases pages on closing
            androidx.compose.material3.TextButton(
                onClick = { Reach.open(context, FOCUS_PAGE, toolbarColor = toolbar) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.situation_get_focus)) }
        }
        Text(stringResource(R.string.situation_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun flagTitle(flag: String): Int = when (flag) {
    Link.UNKNOWN_AUTHOR -> R.string.flag_unknown_author
    Link.ADS -> R.string.flag_ads
    Link.BETA -> R.string.flag_beta
    Link.PAID -> R.string.flag_paid
    Link.PAUSED -> R.string.flag_paused
    else -> R.string.flag_collects_data
}

@Composable
internal fun LinkRow(link: Link, onClick: () -> Unit) {
    val context = LocalContext.current
    val official = link.kind == Link.OFFICIAL
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Who runs it, in words, on its own line above the name, so the name keeps the full width.
            // A quiet label, not a colour: colours in Sprotyv say what a thing is for
            Surface(
                color = if (official) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    (if (official) "✓ " else "") + stringResource(link.kindTitle), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(link.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(link.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Warnings in words: who runs it, ads, beta, data collection
            if (link.flags.isNotEmpty()) {
                Text(
                    link.flags.joinToString(" · ") { "⚠ " + context.getString(flagTitle(it)) },
                    style = MaterialTheme.typography.bodyMedium,
                    // A caution, not danger now: red is kept for danger
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (link.phone.isNotEmpty() && link.url.isEmpty()) stringResource(R.string.call_number, link.phone) else ua.sprotyv.content.LinksReader.host(link.url),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Power cuts: the national picture, then each oblast's own grid operator, where a person finds
 * their queue. Official sites only; bots are not listed, because look-alike "schedule bots" are common.
 */
@Composable
fun PowerScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.power_title), nav::back) {
        BlackoutBlock()
        HeatingBlock(nav)
        ChargeBlock()
        if (content == null) return@Frame Loading()
        if (content.linksBroken) return@Frame Broken()
        val schedules = content.links.filter { it.group == Link.POWER }
        Block(stringResource(R.string.power_schedules), Group.HOME_LIFE, foldable = true, summary = stringResource(R.string.shelf_count, schedules.size)) {
            Text(stringResource(R.string.power_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            schedules.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
        }
        Text(stringResource(R.string.power_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Where to turn": help for everyday life around the war (displaced people, money, documents,
 * children, veterans, health, care). One tile deeper than "Situation" on purpose: it is for
 * calmer moments. Every entry says who runs it and when it was checked.
 */
@Composable
fun DirectoryScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.dir_title), nav::back) {
        Text(stringResource(R.string.dir_intro), style = MaterialTheme.typography.bodyLarge)
        if (content == null) return@Frame Loading()
        if (content.linksBroken) return@Frame Broken()
        val trip = HomePrefs.state.collectAsState().value?.trip == true
        val entries = content.links.filter { it.group in Link.DIRECTORY && !(trip && Trip.hides(it)) }
        if (entries.isEmpty()) Text(stringResource(R.string.dir_empty), style = MaterialTheme.typography.bodyLarge)
        Link.DIRECTORY.forEach { g ->
            val links = entries.filter { it.group == g }
            if (links.isEmpty()) return@forEach
            Block(stringResource(links.first().groupTitle), linkGroupColour(g), foldable = true, summary = stringResource(R.string.shelf_count, links.size)) {
                links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
            }
        }
        Text(stringResource(R.string.dir_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
