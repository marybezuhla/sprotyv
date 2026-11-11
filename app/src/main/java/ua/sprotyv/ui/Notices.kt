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
import kotlinx.coroutines.flow.getAndUpdate
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
    /** [onGone]: runs once if the line goes without its action (timed out, replaced, or the app closed). */
    class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null, val onGone: (() -> Unit)? = null) {
        val id = System.nanoTime()
        private var settled = false

        /** Runs [onAction] or [onGone], never both and never twice. */
        fun settle(acted: Boolean) {
            synchronized(this) { if (settled) return; settled = true }
            if (acted) onAction?.invoke() else onGone?.invoke()
        }
    }

    val current = MutableStateFlow<Notice?>(null)

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val next = Notice(text, action, onAction)
        // A deletion still waiting for "Undo" is finished, never left half done
        current.getAndUpdate { next }?.settle(acted = false)
    }

    /**
     * The owner's data closes (the app left the screen, "Mine" was left, everything is erased): a
     * deletion still offering "Undo" is finished now, so no name stays on screen past the lock and
     * no document key stays in memory.
     */
    fun finishPending() {
        current.getAndUpdate { null }?.settle(acted = false)
    }

    /**
     * Something was deleted: says so with "Undo". [undo] puts it back; [gone] finishes the deletion
     * (a document's file, say) once the line has gone without "Undo". A line still waiting when a
     * new one comes is finished at once, so nothing is left half deleted.
     */
    fun deleted(context: Context, text: String, undo: () -> Unit, gone: (() -> Unit)? = null) {
        val next = Notice(text, context.getString(R.string.undo), undo, gone)
        current.getAndUpdate { next }?.settle(acted = false)
    }

    /** Puts a tile on the home screen and says so; when the home screen is full, says why nothing happened. */
    fun pin(screen: Context, content: Content?, id: String) {
        val context = screen.applicationContext
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
    fun unpin(screen: Context, content: Content?, id: String) {
        val context = screen.applicationContext
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

/** A number's short name for a tile: the grid operator alone for power lines, the title otherwise. */
fun phoneShort(p: ua.sprotyv.content.Phone): String = if (p.category == "power") p.title.substringAfter(": ") else p.title

/** Where the notices show, over every screen. */
@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val notice by Notices.current.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current
    // With TalkBack, finding the button takes longer than any timer: the line stays until it is used or closed
    val talkBack = remember { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    LaunchedEffect(notice?.id) {
        val n = notice ?: return@LaunchedEffect
        host.currentSnackbarData?.dismiss()
        var acted = false
        try {
            val duration = when {
                n.action == null -> SnackbarDuration.Short
                talkBack -> SnackbarDuration.Indefinite
                else -> SnackbarDuration.Long
            }
            val result = host.showSnackbar(n.text, actionLabel = n.action, withDismissAction = talkBack && n.action != null, duration = duration)
            acted = result == SnackbarResult.ActionPerformed
        } finally {
            // Turning the phone rebuilds the screen: the same line is shown again, nothing is finished.
            // Otherwise (time out, closed, a new line, the app gone) a waiting deletion is finished
            if (activity?.isChangingConfigurations != true) {
                n.settle(acted)
                if (Notices.current.value === n) Notices.current.value = null
            }
        }
    }
    SnackbarHost(host, modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
