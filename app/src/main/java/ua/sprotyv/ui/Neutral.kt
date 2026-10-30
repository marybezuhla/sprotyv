package ua.sprotyv.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import ua.sprotyv.R

/**
 * A neutral name and icon ("Notes") on the home screen instead of "Sprotyv", for a phone that may
 * be looked at by others. Kept only as which home-screen entry is on, nothing else is stored.
 * Not a hiding place: the app opens as itself, and the phone's settings still name it.
 */
object Neutral {
    private fun entry(context: Context, neutral: Boolean) =
        ComponentName(context, if (neutral) "ua.sprotyv.LauncherNeutral" else "ua.sprotyv.ui.MainActivity")

    fun isOn(context: Context): Boolean = try {
        context.packageManager.getComponentEnabledSetting(entry(context, true)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } catch (e: Exception) {
        false
    }

    /** The new entry goes on before the old one goes off, so there is never no way to open the app. */
    fun set(context: Context, neutral: Boolean) {
        val pm = context.packageManager
        val keep = PackageManager.DONT_KILL_APP
        pm.setComponentEnabledSetting(entry(context, neutral), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, keep)
        pm.setComponentEnabledSetting(entry(context, !neutral), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, keep)
    }

    /** The recent apps list shows the neutral name too. */
    fun applyToTask(activity: Activity) {
        val label = activity.getString(if (isOn(activity)) R.string.neutral_name else R.string.app_name)
        activity.setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(label).build())
    }
}
