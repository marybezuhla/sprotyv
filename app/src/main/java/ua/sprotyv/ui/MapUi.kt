package ua.sprotyv.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.content.Link
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach
import java.io.File

/** The overview of all Ukraine, built into the app like the Constitution: no download, no files to open. */
object Overview {
    /**
     * Copied out of the app's own files so the map reader can read it directly: again after every
     * install or update of the app (a new map may have the same size), through a temporary file so
     * a copy cut short is never read, and one copy at a time.
     */
    @Synchronized
    fun file(c: Context): File? = try {
        val dir = File(c.noBackupFilesDir, "maps").apply { mkdirs() }
        val out = File(dir, "overview.map")
        val mark = File(dir, "overview.installed")
        @Suppress("DEPRECATION")
        val installed = c.packageManager.getPackageInfo(c.packageName, 0).lastUpdateTime.toString()
        c.assets.openFd("maps/overview.map").use { afd ->
            val current = out.exists() && out.length() == afd.length && runCatching { mark.readText() }.getOrNull() == installed
            if (!current) {
                val tmp = File(dir, "overview.map.tmp")
                afd.createInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                check(tmp.length() == afd.length && tmp.renameTo(out))
                mark.writeText(installed)
            }
        }
        out
    } catch (e: Exception) {
        null
    }

    /**
     * An earlier version let the owner open oblast map files and kept their addresses: those, and
     * only those, are let go, and the settings file that held them is deleted. Once, at start.
     */
    fun forgetOpenedFiles(c: Context) {
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

/**
 * "Map": the overview of Ukraine, always there. Drawn on the phone with no network; the tiles are
 * kept in memory only, so no trace of the places looked at is left behind. A long press picks a
 * point, which opens in a map app with streets (the person picks the app; the point is not kept).
 */
@Composable
fun MapScreen(content: Content?, nav: Nav) {
    // The places looked at stay off screenshots and the recent-apps picture
    SecureScreen()
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    var overview by remember { mutableStateOf<File?>(null) }
    var ready by remember { mutableStateOf(false) }
    var broken by remember { mutableStateOf(false) }
    var point by remember { mutableStateOf<LatLong?>(null) }
    LaunchedEffect(Unit) {
        overview = withContext(Dispatchers.IO) { Overview.file(context) }
        ready = true
    }
    Frame(stringResource(R.string.map_title), nav::back) {
        if (!ready) return@Frame Loading()
        val file = overview
        if (file != null && !broken) {
            OfflineMap(file, Modifier.fillMaxWidth().height(480.dp), onPoint = { point = it }) { broken = true }
            val p = point
            if (p == null) {
                Text(stringResource(R.string.map_press), style = ReadingText)
            } else {
                val chooser = stringResource(R.string.map_open_in)
                Button(onClick = { Reach.place(context, p.latitude, p.longitude, chooser) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.map_open_in))
                }
            }
            Text(stringResource(R.string.map_osm), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.map_missing), style = ReadingText)
        }
        // Streets, paths and routes with no internet: open apps, each marked who runs it
        val apps = content?.links.orEmpty().filter { it.group == Link.MAPS }
        if (apps.isNotEmpty()) {
            Block(stringResource(R.string.situation_maps), Group.GUIDE, foldable = true, summary = stringResource(R.string.shelf_count, apps.size)) {
                Text(stringResource(R.string.map_apps_intro), style = ReadingText)
                apps.forEach { link -> LinkRow(link) { openLink(context, link, toolbar) } }
            }
        }
        Text(stringResource(R.string.map_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The map itself: Mapsforge drawing the overview, memory-only tiles, closed when the screen goes. */
@Composable
private fun OfflineMap(file: File, modifier: Modifier, onPoint: (LatLong) -> Unit, onBroken: () -> Unit) {
    val holder = remember { MapHolder() }
    val mark = MaterialTheme.colorScheme.primary.toArgb()
    DisposableEffect(Unit) { onDispose { holder.close() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidGraphicFactory.createInstance(ctx.applicationContext as android.app.Application)
            MapView(ctx).also { view ->
                view.isClickable = true
                view.mapScaleBar.isVisible = true
                view.setBuiltInZoomControls(true)
                if (!holder.open(file, view, mark, onPoint)) onBroken()
            }
        },
    )
}

private class MapHolder {
    private var store: MapFile? = null
    private var view: MapView? = null

    fun open(file: File, mapView: MapView, markColour: Int, onPoint: (LatLong) -> Unit): Boolean = try {
        val map = MapFile(file)
        store = map
        // One mark for the point picked: a filled circle with a white edge, moved on each long press
        val g = AndroidGraphicFactory.INSTANCE
        val fill = g.createPaint().apply { setColor(markColour); setStyle(Style.FILL) }
        val edge = g.createPaint().apply { setColor(android.graphics.Color.WHITE); setStyle(Style.STROKE); setStrokeWidth(4f) }
        val pin = FixedPixelCircle(null, 12f, fill, edge).apply { isVisible = false }
        val layer = object : TileRendererLayer(InMemoryTileCache(64), map, mapView.model.mapViewPosition, g) {
            override fun onLongPress(tapLatLong: LatLong, layerXY: Point, tapXY: Point): Boolean {
                pin.setLatLong(tapLatLong)
                pin.isVisible = true
                pin.requestRedraw()
                onPoint(tapLatLong)
                return true
            }
        }
        layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
        mapView.layerManager.layers.add(layer)
        mapView.layerManager.layers.add(pin)
        val c = map.boundingBox().centerPoint
        mapView.setCenter(LatLong(c.latitude, c.longitude))
        mapView.setZoomLevel(6.toByte())
        // The overview has no detail past this: zooming further would only show emptiness
        mapView.setZoomLevelMax(11.toByte())
        view = mapView
        true
    } catch (e: Exception) {
        close()
        false
    }

    fun close() {
        runCatching { view?.destroyAll() }
        runCatching { store?.close() }
        view = null; store = null
        runCatching { AndroidGraphicFactory.clearResourceMemoryCache() }
        // The theme's symbols Mapsforge keeps as small pictures in the app's files: cleared too
        runCatching { AndroidGraphicFactory.clearResourceFileCache() }
    }
}
