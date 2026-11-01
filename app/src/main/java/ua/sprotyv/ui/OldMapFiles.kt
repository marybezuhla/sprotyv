package ua.sprotyv.ui

import android.content.Context
import java.io.File

/**
 * Earlier versions had an offline map: a copy of it in the app's files, and the addresses of map
 * files the owner opened. The map is gone (map apps do it better): whatever it left is let go, once.
 */
object OldMapFiles {
    fun forget(c: Context) {
        runCatching { File(c.noBackupFilesDir, "maps").takeIf { it.exists() }?.deleteRecursively() }
        if (!File(c.applicationInfo.dataDir, "shared_prefs/map.xml").exists()) return
        runCatching {
            val p = c.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)
            val kept = p.getStringSet("uris", emptySet()).orEmpty() + listOfNotNull(p.getString("uri", null))
            kept.forEach { u ->
                runCatching { c.contentResolver.releasePersistableUriPermission(android.net.Uri.parse(u), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }
        runCatching { c.applicationContext.deleteSharedPreferences("map") }
    }
}
