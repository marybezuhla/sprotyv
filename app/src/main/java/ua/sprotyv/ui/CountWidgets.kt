package ua.sprotyv.ui

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R

/**
 * Count widgets: one count of "My shields" on the home screen, with "+1".
 *
 * The counts live encrypted in "Mine", which a widget cannot open. So, only for a count that has a
 * widget, the number is copied here in plain (the number alone, never anything else), and a "+1"
 * tapped on the home screen waits here until "Mine" next opens, where it is added to the encrypted
 * count. No "−1" on the home screen: a slip is put right in "My shields". Trip mode shows only a
 * neutral way in; the neutral icon takes the widget off the phone's list like the others.
 */
object CountWidgets {
    /** The counts a widget can show, with their names. */
    val COUNTERS = listOf(
        "blood" to R.string.count_blood, "donated" to R.string.count_donated, "shifts" to R.string.count_shifts,
        "helped" to R.string.count_helped, "taught" to R.string.count_taught, "courses" to R.string.count_courses,
        "animals" to R.string.count_animals, "thanks" to R.string.count_thanks,
    )
    private val KEYS = COUNTERS.map { it.first }.filter { it in Steps.COUNTS }.toSet()
    const val ACTION_PLUS = "ua.sprotyv.widget.COUNT_PLUS"
    private const val MAX = 10_000

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("widget_counts", Context.MODE_PRIVATE)

    class Chosen(val counter: String, val showName: Boolean)

    fun get(c: Context, widget: Int): Chosen? {
        val v = prefs(c).getString("w:$widget", null)?.split('|') ?: return null
        if (v.size != 2 || v[0] !in KEYS) return null
        return Chosen(v[0], v[1] == "1")
    }

    fun set(c: Context, widget: Int, counter: String, showName: Boolean) {
        if (counter !in KEYS) return
        prefs(c).edit().putString("w:$widget", "$counter|${if (showName) 1 else 0}").apply()
    }

    private fun used(c: Context): Set<String> =
        prefs(c).all.filterKeys { it.startsWith("w:") }.values.mapNotNull { (it as? String)?.substringBefore('|')?.takeIf { k -> k in KEYS } }.toSet()

    /** Removed widgets forget their choice; a count no widget shows is no longer copied. */
    fun keepOnly(c: Context, widgets: IntArray) {
        val keep = widgets.map { "w:$it" }.toSet()
        val e = prefs(c).edit()
        prefs(c).all.keys.filter { it.startsWith("w:") && it !in keep }.forEach { e.remove(it) }
        e.apply()
        val still = used(c)
        val e2 = prefs(c).edit()
        prefs(c).all.keys.filter { it.startsWith("n:") && it.removePrefix("n:") !in still }.forEach { e2.remove(it) }
        e2.apply()
    }

    /** The encrypted counts as now stored: copied only for the counts that have a widget. */
    fun mirror(c: Context, counts: Map<String, Int>) {
        // Nothing copied while the neutral icon is on or in trip mode, or with no widget placed
        if (Neutral.isOn(c)) return forgetCopies(c)
        if (HomePrefs.load(c).trip) return forgetNumbers(c)
        val still = used(c)
        if (still.isEmpty() && prefs(c).all.keys.none { it.startsWith("n:") }) return
        val e = prefs(c).edit()
        KEYS.forEach { k -> if (k in still) e.putInt("n:$k", (counts[k] ?: 0).coerceIn(0, MAX)) else e.remove("n:$k") }
        e.apply()
        Widgets.update(c)
    }

    /** "+1" from the home screen, kept until "Mine" opens. */
    fun plus(c: Context, counter: String) {
        if (counter !in KEYS) return
        synchronized(this) {
            val p = prefs(c)
            p.edit().putInt("p:$counter", (p.getInt("p:$counter", 0) + 1).coerceAtMost(MAX)).commit()
        }
    }

    /** What waits, read without taking it: it is taken ([settle]) only once it is in the encrypted counts. */
    fun peekPending(c: Context): Map<String, Int> = synchronized(this) {
        val p = prefs(c)
        KEYS.associateWith { p.getInt("p:$it", 0).coerceIn(0, MAX) }.filterValues { it > 0 }
    }

    /** Taps now stored encrypted: exactly those are taken away, so one made meanwhile stays. */
    fun settle(c: Context, stored: Map<String, Int>) {
        synchronized(this) {
            val p = prefs(c)
            val e = p.edit()
            stored.forEach { (k, n) -> val left = p.getInt("p:$k", 0) - n; if (left > 0) e.putInt("p:$k", left) else e.remove("p:$k") }
            e.commit()
        }
    }

    /**
     * Every choice and copied number gone (the neutral icon, the last widget removed). Taps that
     * still wait are kept: they are only numbers, and they go into "Mine" when it next opens.
     */
    fun forgetCopies(c: Context) {
        val p = prefs(c)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("w:") || it.startsWith("n:") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun forgetNumbers(c: Context) {
        val p = prefs(c)
        val gone = p.all.keys.filter { it.startsWith("n:") }
        if (gone.isNotEmpty()) p.edit().apply { gone.forEach { remove(it) } }.apply()
    }

    /** The number shown: the copy plus what waits. Null before "Mine" first gave a number. */
    private fun shown(c: Context, counter: String): Int? {
        val p = prefs(c)
        val pending = p.getInt("p:$counter", 0)
        val base = if (p.contains("n:$counter")) p.getInt("n:$counter", 0) else null
        return if (base == null && pending == 0) null else ((base ?: 0) + pending).coerceAtMost(MAX)
    }

    fun draw(c: Context, widget: Int): RemoteViews {
        val v = RemoteViews(c.packageName, R.layout.widget_count)
        val chosen = get(c, widget)
        val trip = HomePrefs.load(c).trip
        // Trip mode: the copied numbers are not kept either; "Mine" gives them back afterwards
        if (trip) forgetNumbers(c)
        if (trip || chosen == null) {
            // Trip mode: a neutral way in, no number, no name; not chosen yet: the way to choose
            v.setTextViewText(R.id.w_number, "")
            v.setTextViewText(R.id.w_title, c.getString(if (trip) R.string.widget_neutral else R.string.widget_tile_choose))
            v.setViewVisibility(R.id.w_plus, View.GONE)
            v.setOnClickPendingIntent(R.id.w_root, Widgets.openFor(c, "now", 300 + (widget % 500)))
            return v
        }
        val name = c.getString(COUNTERS.first { it.first == chosen.counter }.second)
        v.setTextViewText(R.id.w_number, shown(c, chosen.counter)?.toString() ?: "·")
        v.setTextViewText(R.id.w_title, if (chosen.showName) name else "")
        v.setViewVisibility(R.id.w_title, if (chosen.showName) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.w_plus, View.VISIBLE)
        v.setContentDescription(R.id.w_plus, "+1")
        v.setOnClickPendingIntent(R.id.w_plus, PendingIntent.getBroadcast(
            c, 3000 + (widget % 5000),
            Intent(c, CountPlus::class.java).setAction(ACTION_PLUS).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget)
                .setData(android.net.Uri.parse("sprotyv-widget:count/$widget")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ))
        v.setOnClickPendingIntent(R.id.w_root, Widgets.openFor(c, "shields", 300 + (widget % 500), widget))
        return v
    }
}

/** One count of "My shields" with "+1". */
class CountWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.update(context)
    override fun onDeleted(context: Context, ids: IntArray) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val left = (manager.getAppWidgetIds(ComponentName(context, CountWidget::class.java)) ?: IntArray(0)).filter { it !in ids }.toIntArray()
        CountWidgets.keepOnly(context, left)
    }

    /** The last one removed: nothing copied stays. */
    override fun onDisabled(context: Context) = CountWidgets.forgetCopies(context)

}

/**
 * "+1" tapped on a count widget. Not exported: only Sprotyv's own pending intent reaches it (the
 * widget receivers need the system's widget permission, which the app itself does not hold).
 */
class CountPlus : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CountWidgets.ACTION_PLUS) return
        val widget = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(context) ?: return
        if (manager.getAppWidgetInfo(widget)?.provider != ComponentName(context, CountWidget::class.java)) return
        if (HomePrefs.load(context).trip || Neutral.isOn(context)) return
        val chosen = CountWidgets.get(context, widget) ?: return
        CountWidgets.plus(context, chosen.counter)
        // "Mine" open right now: straight into the encrypted counts
        Steps.flushCounts(context)
        Widgets.update(context)
    }
}

/** Choosing the count a widget shows, and whether its name is on the home screen. */
class CountWidgetSetup : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widget = try {
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        } catch (e: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        val manager = AppWidgetManager.getInstance(this)
        val ours = widget != AppWidgetManager.INVALID_APPWIDGET_ID &&
            manager?.getAppWidgetInfo(widget)?.provider == ComponentName(this, CountWidget::class.java)
        if (!ours || Neutral.isOn(this)) { finish(); return }
        enableEdgeToEdge()
        setContent {
            val prefs = HomePrefs.state.collectAsState().value ?: HomePrefs.load(this)
            SprotyvTheme(prefs.theme, prefs.dynamic) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showName by remember { mutableStateOf(CountWidgets.get(this, widget)?.showName ?: false) }
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.widget_count_pick), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.widget_count_pick_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Toggle(stringResource(R.string.widget_count_show_name), showName) { showName = it }
                        androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                            CountWidgets.COUNTERS.forEachIndexed { i, (key, title) ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                                        CountWidgets.set(this@CountWidgetSetup, widget, key, showName)
                                        Widgets.update(this@CountWidgetSetup)
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget))
                                        finish()
                                    }.padding(horizontal = 16.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
