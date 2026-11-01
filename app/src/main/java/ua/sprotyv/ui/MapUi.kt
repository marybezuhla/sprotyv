package ua.sprotyv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach
import java.io.FileInputStream

/**
 * The map file the owner chose (a Mapsforge ".map" opened with the system's file picker): only its
 * address is kept, on this phone. Sprotyv never downloads it; the browser does, from the address
 * shown, and the file is opened from where it was saved.
 */
object MapFiles {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)
    fun current(c: Context): Uri? = prefs(c).getString("uri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }?.takeIf { it.scheme == "content" }
    fun set(c: Context, uri: Uri) = prefs(c).edit().putString("uri", uri.toString()).apply()
    fun clear(c: Context) {
        current(c)?.let { runCatching { c.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        prefs(c).edit().remove("uri").apply()
    }

    /** Where map files are published (opened in the browser; nothing is fetched by Sprotyv). */
    const val DOWNLOADS = "https://download.mapsforge.org/maps/v5/europe/"
}

/**
 * "Map": a map that works with no network at all — streets, buildings, paths, drawn on the phone
 * from a file. The tiles drawn are kept only in memory, never on disk, so no trace of which places
 * were looked at is left behind.
 */
@Composable
fun MapScreen(nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    var uri by remember { mutableStateOf(MapFiles.current(context)) }
    var broken by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            MapFiles.set(context, picked)
            broken = false
            uri = picked
        }
    }
    Frame(stringResource(R.string.map_title), nav::back) {
        val u = uri
        if (u == null || broken) {
            if (broken) Text(stringResource(R.string.map_broken), style = ReadingText, color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.map_intro), style = ReadingText)
            Block(stringResource(R.string.map_how), Group.GUIDE) {
                listOf(R.string.map_how_1, R.string.map_how_2, R.string.map_how_3).forEachIndexed { i, s ->
                    Text("${i + 1}. " + stringResource(s), style = ReadingText)
                }
                OutlinedButton(onClick = { Reach.open(context, MapFiles.DOWNLOADS, toolbarColor = toolbar) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.map_where)) }
                Button(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.map_open_file)) }
            }
            Text(stringResource(R.string.map_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Frame
        }
        OfflineMap(u, Modifier.fillMaxWidth().height(520.dp)) { broken = true }
        Text(stringResource(R.string.map_osm), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.map_other_file)) }
    }
}

/** The map itself: Mapsforge drawing a ".map" file, memory-only tiles, closed when the screen goes. */
@Composable
private fun OfflineMap(uri: Uri, modifier: Modifier, onBroken: () -> Unit) {
    val context = LocalContext.current
    val holder = remember(uri) { MapHolder() }
    DisposableEffect(uri) { onDispose { holder.close() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidGraphicFactory.createInstance(ctx.applicationContext as android.app.Application)
            MapView(ctx).also { view ->
                view.isClickable = true
                view.mapScaleBar.isVisible = true
                view.setBuiltInZoomControls(true)
                if (!holder.open(context, uri, view)) onBroken()
            }
        },
    )
}

private class MapHolder {
    private var pfd: android.os.ParcelFileDescriptor? = null
    private var store: MapFile? = null
    private var view: MapView? = null

    fun open(context: Context, uri: Uri, mapView: MapView): Boolean = try {
        val fd = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r"))
        pfd = fd
        val file = MapFile(FileInputStream(fd.fileDescriptor))
        store = file
        // In memory only: no picture of the places looked at is written to the phone
        val cache = InMemoryTileCache(64)
        val layer = TileRendererLayer(cache, file, mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
        layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
        mapView.layerManager.layers.add(layer)
        val center = file.boundingBox().centerPoint
        mapView.setCenter(LatLong(center.latitude, center.longitude))
        mapView.setZoomLevel(12.toByte())
        view = mapView
        true
    } catch (e: Exception) {
        close()
        false
    }

    fun close() {
        runCatching { view?.destroyAll() }
        runCatching { store?.close() }
        runCatching { pfd?.close() }
        view = null; store = null; pfd = null
        runCatching { AndroidGraphicFactory.clearResourceMemoryCache() }
    }
}
