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
    private val PROVIDERS = listOf(NowWidget::class.java, RunningWidget::class.java, PathWidget::class.java)

    /** Draws every placed widget again (a count started or stopped, the path changed, trip mode). */
    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        try {
            ids(app, manager, NowWidget::class.java).forEach { manager.updateAppWidget(it, now(app)) }
            ids(app, manager, RunningWidget::class.java).forEach { manager.updateAppWidget(it, running(app)) }
            ids(app, manager, PathWidget::class.java).forEach { manager.updateAppWidget(it, path(app)) }
        } catch (e: Exception) {
            // A launcher that refuses an update keeps the last picture; the app itself is unaffected
        }
    }

    /** Off the phone's widget list while the neutral icon is on: they would show the name Sprotyv. */
    fun sync(context: Context) {
        val app = context.applicationContext
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

    /** Opens Sprotyv at a fixed place; the same words a notification uses ([Opening]). */
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
            prefs.trip -> context.getString(R.string.widget_path_trip)
            levels.isEmpty() -> context.getString(R.string.widget_path_start)
            current < 0 -> context.getString(R.string.path_home_all)
            else -> context.getString(R.string.widget_path_level, context.getString(names.getOrElse(current) { names.last() }), levels[current].first, levels[current].second)
        }
        v.setTextViewText(R.id.w_text, text)
        v.setOnClickPendingIntent(R.id.w_root, open(context, if (prefs.trip) "now" else "path", 3))
        return v
    }
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
