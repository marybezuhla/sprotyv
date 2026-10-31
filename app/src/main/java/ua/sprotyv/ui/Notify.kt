package ua.sprotyv.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ua.sprotyv.R
import ua.sprotyv.content.Blackout
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Notifications, each kind switched on by the owner (all off by default):
 * - the tourniquet: its time in the shade while it is on, and a reminder every hour;
 * - the wait after a strike: when the time is up;
 * - the shelter, no power, no heating: at the guide's time marks (the same as on their screens);
 * - supplies: when something runs out by the end of next month;
 * - reminders in notes: at the time the owner chose;
 * - the go-bag: a check every half a year.
 *
 * What is kept outside the encrypted data: only times (as for the counts themselves) and the
 * numbers of notes with a reminder, never their words. A reminder from notes, supplies or the
 * go-bag shows no words of the owner on the screen at all, and none is shown in trip mode. While
 * the neutral icon is on, nothing is shown: a notification would name Sprotyv.
 * Times are approximate (Android may delay them by a few minutes): no special permission for
 * exact alarms is asked for.
 */
object Notify {
    enum class Kind(val key: String) { TOURNIQUET("tourniquet"), WAIT("wait"), COUNTS("counts"), SUPPLIES("supplies"), NOTES("notes"), KIT("kit") }

    private const val URGENT = "urgent"
    private const val RUNNING = "running"
    private const val REMINDERS = "reminders"

    private const val ID_TOURNIQUET_ON = 1
    private const val ID_TOURNIQUET = 2
    private const val ID_WAIT = 3
    private const val ID_SHELTER = 4
    private const val ID_POWER = 5
    private const val ID_HEAT = 6
    private const val ID_SUPPLIES = 7
    private const val ID_KIT = 8
    private const val ID_NOTE = 9

    private const val HOUR = 3600_000L
    private const val DAY = 24 * HOUR
    private const val KIT_EVERY = 182 * DAY
    private val SHELTER_MARKS = listOf(1L)
    private val POWER_MARKS = listOf(Blackout.FRIDGE_HOURS.toLong(), Blackout.FREEZER_HALF_HOURS.toLong(), Blackout.FREEZER_FULL_HOURS.toLong())
    private val HEAT_MARKS = listOf(24L, 48L)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("notify", Context.MODE_PRIVATE)
    private fun other(c: Context, name: String) = c.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    // ---- what the owner switched on

    private val changed = MutableStateFlow(0)
    /** Changes whenever a switch or a note reminder changes, so screens can redraw. */
    val version: StateFlow<Int> = changed

    fun isOn(c: Context, kind: Kind): Boolean = prefs(c).getBoolean(kind.key, false)

    fun set(c: Context, kind: Kind, on: Boolean) {
        prefs(c).edit().putBoolean(kind.key, on).apply()
        if (kind == Kind.KIT && on) prefs(c).edit().putLong("kit_next", System.currentTimeMillis() + KIT_EVERY).apply()
        // Switched off: what was kept for it goes too
        if (kind == Kind.SUPPLIES && !on) prefs(c).edit().remove("supplies_for").remove("supplies_at").apply()
        if (kind == Kind.KIT && !on) prefs(c).edit().remove("kit_next").apply()
        if (kind == Kind.NOTES && !on) notes(c).edit().clear().apply()
        changed.value++
        sync(c)
    }

    /** Trip mode hides every notification, not only the owner's reminders (the owner's choice; off by default). */
    fun hideAllInTrip(c: Context): Boolean = prefs(c).getBoolean("trip_all", false)

    fun setHideAllInTrip(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("trip_all", on).apply()
        changed.value++
        sync(c)
    }

    /** Android lets this app show notifications (the owner may have said no). */
    fun allowedBySystem(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    /** Whether this kind may be shown now: on, not hidden by the neutral icon, not hidden by trip mode. */
    private fun shows(c: Context, kind: Kind): Boolean {
        if (!isOn(c, kind) || Neutral.isOn(c)) return false
        val personal = kind == Kind.SUPPLIES || kind == Kind.NOTES || kind == Kind.KIT
        val trip = HomePrefs.load(c).trip
        return !(trip && (personal || hideAllInTrip(c)))
    }

    // ---- note reminders: only the note's number and a time

    private const val NOTES_FILE = "notify_notes"
    private fun notes(c: Context) = c.applicationContext.getSharedPreferences(NOTES_FILE, Context.MODE_PRIVATE)

    fun noteReminder(c: Context, id: Long): Long? = notes(c).getLong(id.toString(), 0L).takeIf { it > 0 }

    fun setNoteReminder(c: Context, id: Long, at: Long?) {
        notes(c).edit().apply { if (at == null) remove(id.toString()) else putLong(id.toString(), at) }.apply()
        changed.value++
        sync(c)
    }

    // ---- supplies: the first day of the month before the soonest date, at ten

    /** Called with the supplies as they are now; keeps only when to remind. */
    fun supplies(c: Context, soonest: YearMonth?) {
        val p = prefs(c)
        // Nothing is kept unless the owner switched these reminders on
        if (!isOn(c, Kind.SUPPLIES)) return
        val key = soonest?.toString().orEmpty()
        if (key == p.getString("supplies_for", "")) return
        val at = soonest?.minusMonths(1)?.atDay(1)?.atTime(10, 0)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        p.edit().putString("supplies_for", key).apply { if (at == null) remove("supplies_at") else putLong("supplies_at", maxOf(at, System.currentTimeMillis() + 60_000)) }.apply()
        sync(c)
    }

    // ---- the running counts, read from where their screens keep them

    private fun tourniquetAt(c: Context, now: Long): Long? =
        other(c, "tourniquet").getLong("at", 0L).takeIf { it > 0 && now - it in 0 until DAY }

    private fun waitEnd(c: Context, now: Long): Pair<Long, Int>? {
        val p = other(c, "wait")
        val t = p.getLong("since", 0L)
        if (t <= 0 || now - t !in 0 until 3 * HOUR) return null
        val total = p.getInt("total", 40).coerceIn(1, 180)
        return (t + total * 60_000L) to total
    }

    private fun nextMark(since: Long, marks: List<Long>, now: Long): Long? = marks.map { since + it * HOUR }.firstOrNull { it > now }

    /** The mark just reached (up to six hours late, e.g. after the phone was off), so a late alarm still says the right thing. */
    private fun reached(since: Long, marks: List<Long>, now: Long): Long? =
        marks.lastOrNull { since + it * HOUR <= now + 60_000 }?.takeIf { now - (since + it * HOUR) < 6 * HOUR }

    // ---- planning: worked out again from scratch every time, so nothing old lingers

    /** Plans every alarm from what is on now; cancels whatever is no longer needed. */
    fun sync(c: Context) {
        val app = c.applicationContext
        channels(app)
        val now = System.currentTimeMillis()
        // Hidden at once: everything under the neutral icon, the owner's reminders in trip mode
        if (Neutral.isOn(app) || (HomePrefs.load(app).trip && hideAllInTrip(app))) NotificationManagerCompat.from(app).cancelAll()
        else if (HomePrefs.load(app).trip) {
            cancel(app, ID_SUPPLIES); cancel(app, ID_KIT)
            app.getSystemService(NotificationManager::class.java)?.activeNotifications?.forEach { if (it.id >= ID_NOTE && it.id % 10 == ID_NOTE) cancel(app, it.id) }
        }

        val t = tourniquetAt(app, now)
        if (t != null && shows(app, Kind.TOURNIQUET)) {
            postTourniquetOn(app, t)
            alarm(app, "tourniquet", t + ((now - t) / HOUR + 1) * HOUR)
        } else {
            cancel(app, ID_TOURNIQUET_ON); cancel(app, ID_TOURNIQUET); unalarm(app, "tourniquet")
        }

        val w = waitEnd(app, now)
        if (w != null && w.first > now && shows(app, Kind.WAIT)) alarm(app, "wait", w.first) else unalarm(app, "wait")
        if (w == null || !shows(app, Kind.WAIT)) cancel(app, ID_WAIT)

        val counts = shows(app, Kind.COUNTS)
        plan(app, "shelter", Since.SHELTER.get(app, now).takeIf { counts && it != 0L }?.let { nextMark(it, SHELTER_MARKS, now) })
        plan(app, "power", Since.POWER.get(app, now).takeIf { counts && it != 0L }?.let { nextMark(it, POWER_MARKS, now) })
        plan(app, "heat", Since.HEAT.get(app, now).takeIf { counts && it != 0L }?.let { nextMark(it, HEAT_MARKS, now) })
        if (!counts || Since.SHELTER.get(app, now) == 0L) cancel(app, ID_SHELTER)
        if (!counts || Since.POWER.get(app, now) == 0L) cancel(app, ID_POWER)
        if (!counts || Since.HEAT.get(app, now) == 0L) cancel(app, ID_HEAT)

        val p = prefs(app)
        plan(app, "supplies", p.getLong("supplies_at", 0L).takeIf { it > 0 && shows(app, Kind.SUPPLIES) })

        if (isOn(app, Kind.KIT)) {
            var next = p.getLong("kit_next", 0L)
            if (next <= 0) { next = now + KIT_EVERY; p.edit().putLong("kit_next", next).apply() }
            plan(app, "kit", next.takeIf { shows(app, Kind.KIT) })
        } else {
            unalarm(app, "kit")
        }

        val notesOn = shows(app, Kind.NOTES)
        notes(app).all.forEach { (k, v) ->
            val id = k.toLongOrNull()
            val at = v as? Long
            // Anything odd, or missed by more than a day, or with reminders off: not kept
            if (id == null || at == null || at <= now - DAY || !isOn(app, Kind.NOTES)) { notes(app).edit().remove(k).apply(); unalarm(app, "note/$k"); return@forEach }
            // Missed while the phone was off: said soon after, if not older than a day
            val due = if (at <= now && now - at < DAY) now + 30_000 else at
            plan(app, "note/$id", due.takeIf { notesOn && it > now })
        }
    }

    private fun plan(c: Context, name: String, at: Long?) = if (at != null) alarm(c, name, at) else unalarm(c, name)

    private fun pending(c: Context, name: String, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            c, 0,
            Intent(c, Alarms::class.java).setData(Uri.parse("sprotyv-alarm:$name")),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun alarm(c: Context, name: String, at: Long) {
        val pi = pending(c, name) ?: return
        // Approximate on purpose: no exact-alarm permission; Android may move it a few minutes
        c.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun unalarm(c: Context, name: String) {
        val pi = pending(c, name, PendingIntent.FLAG_NO_CREATE) ?: return
        c.getSystemService(AlarmManager::class.java)?.cancel(pi)
        pi.cancel()
    }

    // ---- an alarm went off

    internal fun fired(c: Context, name: String) {
        val app = c.applicationContext
        val now = System.currentTimeMillis()
        when {
            name == "tourniquet" -> tourniquetAt(app, now)?.takeIf { shows(app, Kind.TOURNIQUET) }?.let { t ->
                val hours = ((now - t + 5 * 60_000) / HOUR).toInt().coerceAtLeast(1)
                post(app, ID_TOURNIQUET, URGENT, "bleeding", app.getString(R.string.notify_tourniquet_title, hours), app.getString(R.string.notify_tourniquet_text, clock(t)))
            }
            name == "wait" -> waitEnd(app, now)?.takeIf { shows(app, Kind.WAIT) && it.first <= now + 60_000 }?.let { (_, total) ->
                post(app, ID_WAIT, URGENT, "now", app.getString(R.string.active_wait_done, total), app.getString(R.string.notify_wait_text))
            }
            name == "shelter" -> Since.SHELTER.get(app, now).takeIf { it != 0L && shows(app, Kind.COUNTS) }?.let { s ->
                reached(s, SHELTER_MARKS, now)?.let {
                    post(app, ID_SHELTER, REMINDERS, "alarm", app.getString(R.string.notify_shelter_title), app.getString(R.string.shelter_air) + " " + app.getString(R.string.shelter_warm))
                }
            }
            name == "power" -> Since.POWER.get(app, now).takeIf { it != 0L && shows(app, Kind.COUNTS) }?.let { s ->
                reached(s, POWER_MARKS, now)?.let { h ->
                    val text = if (h == Blackout.FRIDGE_HOURS.toLong()) R.string.blackout_fridge_over else R.string.blackout_freezer_over
                    post(app, ID_POWER, REMINDERS, "power", app.getString(R.string.notify_power_title, h.toInt()), app.getString(text))
                }
            }
            name == "heat" -> Since.HEAT.get(app, now).takeIf { it != 0L && shows(app, Kind.COUNTS) }?.let { s ->
                reached(s, HEAT_MARKS, now)?.let { h ->
                    val text = if (h == 24L) R.string.heat_pipes_do else R.string.heat_leave_do
                    post(app, ID_HEAT, REMINDERS, "power", app.getString(R.string.notify_heat_title, (h / 24).toInt()), app.getString(text))
                }
            }
            name == "supplies" -> {
                prefs(app).edit().remove("supplies_at").apply()
                if (shows(app, Kind.SUPPLIES)) post(app, ID_SUPPLIES, REMINDERS, "supplies", app.getString(R.string.notify_supplies_title), app.getString(R.string.notify_supplies_text), private = true)
            }
            name == "kit" -> {
                prefs(app).edit().putLong("kit_next", now + KIT_EVERY).apply()
                if (shows(app, Kind.KIT)) post(app, ID_KIT, REMINDERS, "kit", app.getString(R.string.notify_kit_title), app.getString(R.string.notify_kit_text), private = true)
            }
            name.startsWith("note/") -> name.removePrefix("note/").toLongOrNull()?.let { id ->
                notes(app).edit().remove(id.toString()).apply()
                changed.value++
                if (shows(app, Kind.NOTES)) post(app, ID_NOTE + (id % 100_000).toInt() * 10, REMINDERS, "note/$id", app.getString(R.string.notify_note_title), app.getString(R.string.notify_note_text), private = true)
            }
        }
        sync(app)
    }

    // ---- showing

    private fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(URGENT, c.getString(R.string.notify_channel_urgent), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(RUNNING, c.getString(R.string.notify_channel_running), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(REMINDERS, c.getString(R.string.notify_channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun clock(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    /** What a tap opens: a fixed word for each kind, read back by [Opening]. */
    private fun open(c: Context, target: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            c, code,
            Intent(c, Main::class.java).putExtra(Opening.EXTRA, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun post(c: Context, id: Int, channel: String, target: String, title: String, text: String, private: Boolean = false) {
        if (!allowedBySystem(c)) return
        val b = NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(c, target, id))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (private) {
            // On the locked screen: only that there is a reminder
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(NotificationCompat.Builder(c, channel).setSmallIcon(R.drawable.ic_notify).setContentTitle(c.getString(R.string.notify_private)).build())
        } else {
            b.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
        try { NotificationManagerCompat.from(c).notify(id, b.build()) } catch (e: SecurityException) { }
    }

    /** The tourniquet's time, in the shade for as long as it is on, with a clock counting up. */
    private fun postTourniquetOn(c: Context, t: Long) {
        if (!allowedBySystem(c)) return
        val n = NotificationCompat.Builder(c, RUNNING)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(c.getString(R.string.tourniquet_mark, clock(t)))
            .setContentText(c.getString(R.string.notify_tourniquet_on))
            .setWhen(t).setShowWhen(true).setUsesChronometer(true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(c, "bleeding", ID_TOURNIQUET_ON))
            .build()
        try { NotificationManagerCompat.from(c).notify(ID_TOURNIQUET_ON, n) } catch (e: SecurityException) { }
    }

    private fun cancel(c: Context, id: Int) = NotificationManagerCompat.from(c).cancel(id)
}

/** Alarms going off, and the phone starting or the app being updated (alarms are lost then). */
class Alarms : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Notify.sync(context)
            else -> {
                val data = intent.data ?: return
                if (data.scheme != "sprotyv-alarm") return
                Notify.fired(context, data.schemeSpecificPart.orEmpty().take(40))
            }
        }
    }
}

/** A notification's tap: which screen to open. In memory only; only fixed words are accepted. */
object Opening {
    const val EXTRA = "ua.sprotyv.open"
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target

    fun take(intent: Intent?) {
        // Only the app's own notifications address Main directly; the home-screen and share
        // entries (open to other apps) never carry a screen to open
        if (intent?.component?.className != Main::class.java.name) return
        val t = try { intent.getStringExtra(EXTRA) } catch (e: Exception) { null } ?: return
        intent.removeExtra(EXTRA)
        if (t in FIXED || NOTE.matches(t)) _target.value = t
    }

    fun clear() { _target.value = null }

    /** The screen for a word. */
    fun route(t: String): Route? = when (t) {
        "bleeding" -> Route.CardRoute("bleeding")
        "now" -> Route.Open(Section.NOW)
        "alarm" -> Route.Open(Section.ALARM)
        "power" -> Route.Power
        "supplies" -> Route.SuppliesRoute
        "kit" -> Route.Mine(MinePart.KIT)
        else -> t.removePrefix("note/").toLongOrNull()?.let { Route.NoteEdit(it) }
    }

    private val FIXED = setOf("bleeding", "now", "alarm", "power", "supplies", "kit")
    private val NOTE = Regex("^note/[0-9]{1,18}$")
}
