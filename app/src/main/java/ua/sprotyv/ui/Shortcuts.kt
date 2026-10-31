package ua.sprotyv.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import ua.sprotyv.R

/**
 * A long press on Sprotyv's icon: three ways in. Made by the app (not fixed in the manifest) so
 * that with the neutral icon they are taken away, and any pinned to the home screen are switched off.
 */
object Shortcuts {
    private val IDS = listOf("now", "safe", "tourniquet")

    fun sync(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(ShortcutManager::class.java) ?: return
        try {
            if (Neutral.isOn(app)) {
                manager.removeAllDynamicShortcuts()
                manager.disableShortcuts(IDS, app.getString(R.string.neutral_name))
                return
            }
            fun one(id: String, short: Int, long: Int, open: String) = ShortcutInfo.Builder(app, id)
                .setShortLabel(app.getString(short)).setLongLabel(app.getString(long))
                .setIcon(Icon.createWithResource(app, R.mipmap.ic_launcher))
                .setIntent(Intent(app, Main::class.java).setAction(Intent.ACTION_VIEW).putExtra(Opening.EXTRA, open))
                .build()
            manager.enableShortcuts(IDS)
            manager.dynamicShortcuts = listOf(
                one("now", R.string.shortcut_now, R.string.tile_now, "now"),
                one("safe", R.string.shortcut_safe, R.string.tile_safe, "safe"),
                one("tourniquet", R.string.shortcut_tourniquet, R.string.shortcut_tourniquet_long, "bleeding"),
            )
        } catch (e: Exception) {
            // A launcher without shortcuts: nothing to show
        }
    }
}
