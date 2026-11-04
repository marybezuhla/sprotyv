package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Pins
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

/**
 * A short line at the bottom of the screen that says what just happened, with a way to undo it.
 * Quiet: no sound, no system pop-up; TalkBack reads it aloud.
 */
object Notices {
    class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null) {
        val id = System.nanoTime()
    }

    val current = MutableStateFlow<Notice?>(null)

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        current.value = Notice(text, action, onAction)
    }

    /** Puts a tile on the home screen and says so; when the home screen is full, says why nothing happened. */
    fun pin(context: Context, content: Content?, id: String) {
        val now = HomePrefs.load(context).pins
        if (id in now || id in Tiles.FIXED) return
        if (now.size >= Tiles.MAX_PINS) return show(context.getString(R.string.pins_full_short, Tiles.MAX_PINS))
        HomePrefs.update(context) { it.copy(pins = Pins.pin(it.pins, id)) }
        val name = tileName(context, content, id)
        show(
            if (name != null) context.getString(R.string.pin_added, name) else context.getString(R.string.pin_added_plain),
            context.getString(R.string.pin_undo),
        ) { HomePrefs.update(context) { it.copy(pins = Pins.unpin(it.pins, id)) } }
    }

    /** Takes a tile off and says so, with a way to put it back in the same place. */
    fun unpin(context: Context, content: Content?, id: String) {
        val before = HomePrefs.load(context).pins
        val at = before.indexOf(id)
        if (at < 0) return
        HomePrefs.update(context) { it.copy(pins = Pins.unpin(it.pins, id)) }
        val name = tileName(context, content, id)
        show(
            if (name != null) context.getString(R.string.pin_removed, name) else context.getString(R.string.pin_removed_plain),
            context.getString(R.string.pin_restore),
        ) {
            HomePrefs.update(context) { s ->
                if (id in s.pins || s.pins.size >= Tiles.MAX_PINS) s
                else s.copy(pins = s.pins.toMutableList().apply { add(at.coerceAtMost(size), id) })
            }
        }
    }

    /** A tile's name for the line, outside a screen (no Compose here). */
    private fun tileName(context: Context, content: Content?, id: String): String? {
        val kind = id.substringBefore(':')
        val value = id.substringAfter(':', "")
        return when (kind) {
            "card" -> content?.card(value)?.title
            "chapter" -> content?.guide?.chapters?.firstOrNull { it.id == value }?.title?.substringBefore(':')?.trim()
            "link" -> content?.links?.firstOrNull { it.id == value }?.title
            "tool" -> Tool.entries.firstOrNull { it.name == value }?.let { context.getString(it.title) }
            "phone" -> content?.phones?.firstOrNull { it.id == value }?.let { ua.sprotyv.content.PhonesReader.shown(it.number) + " " + phoneShort(it) }
            else -> null
        }?.take(80)
    }
}

/** A number's short name for a tile: the operator alone for the grid ("Житомиробленерго"), the title otherwise. */
fun phoneShort(p: ua.sprotyv.content.Phone): String = if (p.category == "power") p.title.substringAfter(": ") else p.title

/** Where the notices show, over every screen. */
@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val notice by Notices.current.collectAsState()
    LaunchedEffect(notice?.id) {
        val n = notice ?: return@LaunchedEffect
        host.currentSnackbarData?.dismiss()
        val result = host.showSnackbar(n.text, actionLabel = n.action, withDismissAction = false, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) n.onAction?.invoke()
        if (Notices.current.value === n) Notices.current.value = null
    }
    SnackbarHost(host, modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
