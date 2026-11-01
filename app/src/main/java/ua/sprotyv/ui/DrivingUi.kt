package ua.sprotyv.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group

/**
 * "Driving": a skill everyone may need (to leave, to take a hurt person to help, to replace a driver
 * who cannot go on). What to do at a crash first, then road safety in numbers from WHO, then how to
 * get a licence, and the official places: the Rules of the Road and the exam questions are open on
 * official sites. Facts only from official sources, each block with its sources and date.
 */
@Composable
fun DrivingScreen(content: Content?, nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    Frame(stringResource(R.string.driving_title), nav::back) {
        Text(stringResource(R.string.driving_intro), style = ReadingText)
        Facts(R.string.driving_crash_title, Group.DANGER, listOf(R.string.driving_crash_1, R.string.driving_crash_2, R.string.driving_crash_3, R.string.driving_crash_4, R.string.driving_crash_5), R.string.driving_crash_source, startOpen = true)
        Facts(R.string.driving_safe_title, Group.FIRST_AID, listOf(R.string.driving_safe_1, R.string.driving_safe_2, R.string.driving_safe_3, R.string.driving_safe_4, R.string.driving_safe_5, R.string.driving_safe_6), R.string.driving_safe_source)
        Facts(R.string.driving_licence_title, Group.PREPARE, listOf(R.string.driving_licence_1, R.string.driving_licence_2, R.string.driving_licence_3, R.string.driving_licence_4, R.string.driving_licence_5, R.string.driving_licence_6, R.string.driving_licence_7), R.string.driving_licence_source)
        BlockRow(stringResource(R.string.chapter_evacuation_car), stringResource(R.string.chapter_evacuation_car_hint), first = true) { nav.go(Route.ChapterRoute("ch26")) }
        if (content == null) return@Frame Loading()
        val links = content.links.filter { it.id in DRIVING_LINKS }.sortedBy { DRIVING_LINKS.indexOf(it.id) }
        if (links.isNotEmpty()) Block(stringResource(R.string.driving_where), Group.PREPARE, padded = false, foldable = true, summary = stringResource(R.string.shelf_count, links.size)) {
            links.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
        }
    }
}

private val DRIVING_LINKS = listOf("pdr", "hsc-tests", "hsc-licence", "hsc-schools", "hsc-queue", "diia-licence")

@Composable
private fun Facts(title: Int, group: Group, facts: List<Int>, source: Int, startOpen: Boolean = false) {
    Block(stringResource(title), group, foldable = true, startOpen = startOpen) {
        facts.forEach {
            Row { Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp)); Text(stringResource(it), style = ReadingText, modifier = Modifier.weight(1f)) }
        }
        Text(stringResource(source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
