package ua.sprotyv.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import ua.sprotyv.R

/**
 * Widgets on the phone's own home screen. Nothing personal is on them: the home screen is seen by
 * anyone holding the phone, and the encrypted data is not opened for them. They are off the
 * phone's list while the neutral icon is on (they would name Sprotyv), and in trip mode the
 * running counts are not shown. No permissions, no network; a tap opens Sprotyv at the right place.
 */
object Widgets {
    private val PROVIDERS = listOf(
        NowWidget::class.java, RunningWidget::class.java, PathWidget::class.java, TileWidget::class.java,
        ServicesWidget::class.java, KidWidget::class.java, SafeWidget::class.java, KitWidget::class.java,
    )

    /** Draws every placed widget again (a count started or stopped, the path changed, trip mode). */
    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        // Neutral icon: nothing that could name Sprotyv, even if a launcher still holds a widget
        if (Neutral.isOn(app)) return blankAll(app, manager)
        try {
            ids(app, manager, NowWidget::class.java).forEach { manager.updateAppWidget(it, now(app)) }
            ids(app, manager, RunningWidget::class.java).forEach { manager.updateAppWidget(it, running(app)) }
            ids(app, manager, PathWidget::class.java).forEach { manager.updateAppWidget(it, path(app)) }
            ids(app, manager, TileWidget::class.java).forEach { manager.updateAppWidget(it, tile(app, it)) }
            ids(app, manager, ServicesWidget::class.java).forEach { manager.updateAppWidget(it, services(app)) }
            ids(app, manager, KidWidget::class.java).forEach { manager.updateAppWidget(it, kid(app)) }
            ids(app, manager, SafeWidget::class.java).forEach { manager.updateAppWidget(it, safe(app)) }
            ids(app, manager, KitWidget::class.java).forEach { manager.updateAppWidget(it, kit(app)) }
        } catch (e: Exception) {
            // A launcher that refuses an update keeps the last picture; the app itself is unaffected
        }
    }

    /** Every placed widget emptied: some launchers keep the last picture of a removed widget. */
    private fun blankAll(app: Context, manager: AppWidgetManager) {
        try {
            PROVIDERS.forEach { cls -> ids(app, manager, cls).forEach { manager.updateAppWidget(it, RemoteViews(app.packageName, R.layout.widget_blank)) } }
        } catch (e: Exception) { }
    }

    /** True when a "Running now" widget is on the home screen (its counts need redrawing on time). */
    fun hasRunning(context: Context): Boolean = try {
        (AppWidgetManager.getInstance(context.applicationContext)?.let { ids(context.applicationContext, it, RunningWidget::class.java) }?.size ?: 0) > 0
    } catch (e: Exception) { false }

    /** Off the phone's widget list while the neutral icon is on: they would show the name Sprotyv. */
    fun sync(context: Context) {
        val app = context.applicationContext
        // Emptied first, while they can still be reached
        if (Neutral.isOn(app)) AppWidgetManager.getInstance(app)?.let { blankAll(app, it) }
        val want = if (Neutral.isOn(app)) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        PROVIDERS.forEach { cls ->
            try {
                val name = ComponentName(app, cls)
                if (app.packageManager.getComponentEnabledSetting(name) != want) app.packageManager.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP)
            } catch (e: Exception) {
                // Widgets are a convenience; the app works without them
            }
        }
    }

    private fun ids(context: Context, manager: AppWidgetManager, cls: Class<*>): IntArray =
        manager.getAppWidgetIds(ComponentName(context, cls)) ?: IntArray(0)

    /**
     * Opens Sprotyv at a fixed place; the same words a notification uses ([Opening]). Request codes
     * from 1000 up belong to widgets; notifications use those below 1000 ([Notify]).
     */
    private fun open(context: Context, target: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, 1000 + code,
            Intent(context, Main::class.java).putExtra(Opening.EXTRA, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun now(context: Context) = RemoteViews(context.packageName, R.layout.widget_now).apply {
        setOnClickPendingIntent(R.id.w_root, open(context, "now", 1))
    }

    private class Count(val words: String, val since: Long, val down: Boolean, val target: String)

    /** The counts running now, from where their screens keep them (times only). */
    private fun counts(context: Context, now: Long): List<Count> {
        val out = ArrayList<Count>()
        fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs("tourniquet").getLong("at", 0L).takeIf { it > 0 && now - it in 0 until 24 * 3600_000L }?.let { at ->
            val clock = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            out += Count(context.getString(R.string.widget_tourniquet, clock), at, false, "bleeding")
        }
        prefs("wait").let { p ->
            val t = p.getLong("since", 0L)
            val total = p.getInt("total", 40).coerceIn(1, 180)
            val end = t + total * 60_000L
            if (t > 0 && now - t in 0 until 3 * 3600_000L) {
                if (end > now) out += Count(context.getString(R.string.widget_wait), end, true, "now")
                else out += Count(context.getString(R.string.active_wait_done, total), t, false, "now")
            }
        }
        Since.SHELTER.get(context, now).takeIf { it != 0L }?.let { out += Count(context.getString(R.string.widget_shelter), it, false, "alarm") }
        Since.POWER.get(context, now).takeIf { it != 0L }?.let { out += Count(context.getString(R.string.widget_power), it, false, "power") }
        Since.HEAT.get(context, now).takeIf { it != 0L }?.let { out += Count(context.getString(R.string.widget_heat), it, false, "power") }
        return out
    }

    private fun running(context: Context): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_running)
        val now = System.currentTimeMillis()
        // Trip mode: no counts on the home screen, only the way in
        val list = if (HomePrefs.load(context).trip) emptyList() else counts(context, now).take(3)
        val rows = listOf(R.id.w_row1 to (R.id.w_label1 to R.id.w_clock1), R.id.w_row2 to (R.id.w_label2 to R.id.w_clock2), R.id.w_row3 to (R.id.w_label3 to R.id.w_clock3))
        rows.forEachIndexed { i, (row, ids) ->
            val c = list.getOrNull(i)
            if (c == null) { v.setViewVisibility(row, View.GONE); return@forEachIndexed }
            v.setViewVisibility(row, View.VISIBLE)
            v.setTextViewText(ids.first, c.words)
            // The phone's own clock counts on the widget: no app work every minute
            val base = SystemClock.elapsedRealtime() + (c.since - now)
            v.setChronometer(ids.second, base, null, true)
            v.setChronometerCountDown(ids.second, c.down)
            v.setOnClickPendingIntent(row, open(context, c.target, 10 + i))
        }
        v.setViewVisibility(R.id.w_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        v.setOnClickPendingIntent(R.id.w_root, open(context, "now", 2))
        return v
    }

    private fun path(context: Context): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_path)
        val prefs = HomePrefs.load(context)
        val levels = pathLevels(prefs.path)
        val names = listOf(R.string.path_level_personal, R.string.path_level_family, R.string.path_level_civic, R.string.path_level_defence, R.string.path_level_medic)
        val current = levels.indexOfFirst { it.first < it.second }
        val text = when {
            // Trip mode: a neutral line, never a word that trip mode is on
            prefs.trip -> context.getString(R.string.widget_path_start)
            levels.isEmpty() -> context.getString(R.string.widget_path_start)
            current < 0 -> context.getString(R.string.path_home_all)
            else -> context.getString(R.string.widget_path_level, context.getString(names.getOrElse(current) { names.last() }), levels[current].first, levels[current].second)
        }
        v.setTextViewText(R.id.w_text, text)
        v.setOnClickPendingIntent(R.id.w_root, open(context, if (prefs.trip) "now" else "path", 3))
        return v
    }
}

// ---- more widgets

/** The parts of Sprotyv the owner put on the home screen, by widget: the tile's id, its name, its colour. */
object TileWidgets {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("widget_tiles", Context.MODE_PRIVATE)
    private val ID = Regex("^(card|chapter|tool|link):[A-Za-z0-9_-]{1,40}$")

    class Chosen(val id: String, val title: String, val tone: String)

    fun get(c: Context, widget: Int): Chosen? {
        val v = prefs(c).getString(widget.toString(), null)?.split('\u0001') ?: return null
        if (v.size != 3 || !ID.matches(v[0])) return null
        return Chosen(v[0], v[1].take(80), v[2])
    }

    fun set(c: Context, widget: Int, id: String, title: String, tone: String) {
        if (!ID.matches(id)) return
        prefs(c).edit().putString(widget.toString(), listOf(id, title.take(80).replace('\u0001', ' '), tone).joinToString("\u0001")).apply()
    }

    fun remove(c: Context, widgets: IntArray) = prefs(c).edit().apply { widgets.forEach { remove(it.toString()) } }.apply()
}

private fun Widgets.layoutFor(tone: String) = when (tone) {
    "urgent" -> R.layout.widget_tile_urgent
    "green" -> R.layout.widget_tile_green
    "blue" -> R.layout.widget_tile_blue
    "khaki" -> R.layout.widget_tile_khaki
    else -> R.layout.widget_tile_plain
}

/** A part chosen by the owner: its name and a mark by its colour; a tap opens it. */
internal fun Widgets.tile(c: Context, widget: Int): RemoteViews {
    val chosen = TileWidgets.get(c, widget)
    val v = RemoteViews(c.packageName, layoutFor(chosen?.tone ?: "plain"))
    v.setTextViewText(R.id.w_mark, when (chosen?.tone) { "urgent" -> "⚠"; "green" -> "✚"; "blue" -> "▣"; "khaki" -> "⛉"; else -> "📖" })
    v.setTextViewText(R.id.w_title, chosen?.title ?: c.getString(R.string.widget_tile_choose))
    v.setOnClickPendingIntent(R.id.w_root, openFor(c, if (chosen != null) "tile/" + chosen.id else "now", 100 + (widget % 500)))
    return v
}

internal fun Widgets.services(c: Context): RemoteViews {
    val v = RemoteViews(c.packageName, R.layout.widget_services)
    listOf(R.id.w_s1 to "112", R.id.w_s2 to "101", R.id.w_s3 to "102", R.id.w_s4 to "103", R.id.w_s5 to "104").forEachIndexed { i, (id, number) ->
        // The dialler with the number filled in; never a call by itself
        v.setOnClickPendingIntent(id, PendingIntent.getActivity(
            c, 1600 + i, Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
    }
    return v
}

internal fun Widgets.kid(c: Context): RemoteViews {
    val v = RemoteViews(c.packageName, R.layout.widget_kid)
    v.setOnClickPendingIntent(R.id.w_k1, openFor(c, "tile/card:kid-siren", 41))
    v.setOnClickPendingIntent(R.id.w_k2, openFor(c, "tile/card:kid-fire", 42))
    v.setOnClickPendingIntent(R.id.w_k3, openFor(c, "tile/card:kid-lost", 43))
    v.setOnClickPendingIntent(R.id.w_k4, openFor(c, if (HomePrefs.load(c).trip) "now" else "kidcall", 44))
    return v
}

internal fun Widgets.safe(c: Context): RemoteViews {
    val v = RemoteViews(c.packageName, R.layout.widget_tile_green)
    v.setTextViewText(R.id.w_mark, "➤")
    v.setTextViewText(R.id.w_title, c.getString(R.string.tile_safe))
    v.setOnClickPendingIntent(R.id.w_root, openFor(c, "safe", 45))
    return v
}

/** The go-bag in counts, from what "Mine" last showed; none in trip mode. */
internal fun Widgets.kit(c: Context): RemoteViews {
    val v = RemoteViews(c.packageName, R.layout.widget_kit)
    val prefs = HomePrefs.load(c)
    val share = prefs.readiness.split(",").getOrNull(3)?.split("/")?.takeIf { it.size == 2 }?.let { (d, t) -> d.toIntOrNull()?.let { dd -> t.toIntOrNull()?.takeIf { it > 0 }?.let { dd to it } } }
    if (prefs.trip || share == null) {
        v.setTextViewText(R.id.w_text, c.getString(R.string.widget_kit_open))
        v.setViewVisibility(R.id.w_bar, View.GONE)
    } else {
        v.setTextViewText(R.id.w_text, c.getString(R.string.kit_packed, share.first, share.second))
        v.setViewVisibility(R.id.w_bar, View.VISIBLE)
        v.setProgressBar(R.id.w_bar, 100, share.first * 100 / share.second, false)
    }
    v.setOnClickPendingIntent(R.id.w_root, openFor(c, if (prefs.trip) "now" else "kit", 46))
    return v
}

/** Widgets ask the same as notifications: a fixed word to [Opening]; request codes from 1000 up. */
internal fun Widgets.openFor(c: Context, target: String, code: Int): PendingIntent =
    PendingIntent.getActivity(
        c, 1000 + code,
        Intent(c, Main::class.java).putExtra(Opening.EXTRA, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/** Any part of Sprotyv, chosen when the widget is placed. */
class TileWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
    override fun onDeleted(context: Context, ids: IntArray) = TileWidgets.remove(context, ids)
}

/** Emergency numbers: the dialler, never a call. */
class ServicesWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** A child's widget: four big pictures. */
class KidWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** "I am safe": the way to the message, never the message. */
class SafeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** The go-bag in counts. */
class KitWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** "What to do now": a big orange way in. */
class NowWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** "Running now": the live counts, or a calm "nothing is running". */
class RunningWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}

/** "This week's step": the level of My path, in counts only. */
class PathWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
}
