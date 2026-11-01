package ua.sprotyv.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.reach.Reach
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * The maps: a light overview of all Ukraine built into the app (always there, no download), and
 * any number of detailed oblast maps the owner opened from files — kept as their addresses only,
 * on this phone. Sprotyv never downloads them; the browser does, from the address shown.
 */
object MapFiles {
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("map", Context.MODE_PRIVATE)

    fun all(c: Context): List<Uri> = migrate(c).getStringSet("uris", emptySet()).orEmpty()
        .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }.filter { it.scheme == "content" }.sortedBy { it.toString() }

    /** The one file the first map version kept becomes the first oblast in the list. */
    private fun migrate(c: Context) = prefs(c).also { p ->
        p.getString("uri", null)?.let { old -> p.edit().putStringSet("uris", p.getStringSet("uris", emptySet()).orEmpty() + old).remove("uri").apply() }
    }

    fun add(c: Context, uri: Uri) {
        runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs(c).edit().putStringSet("uris", (all(c).map { it.toString() } + uri.toString()).take(30).toSet()).apply()
    }

    fun remove(c: Context, uri: Uri) {
        runCatching { c.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs(c).edit().putStringSet("uris", all(c).filter { it != uri }.map { it.toString() }.toSet()).remove("sum:$uri").apply()
    }

    /** The official map files: the project's releases (opened in the browser; nothing is fetched by Sprotyv). */
    const val DOWNLOADS = "https://github.com/Marybezu/sprotyv/releases?q=maps"

    /** The overview built into the app, copied once out of the app's files so it can be read directly. */
    fun overview(c: Context): File? = try {
        val dir = File(c.noBackupFilesDir, "maps").apply { mkdirs() }
        val out = File(dir, "overview.map")
        c.assets.openFd("maps/overview.map").use { afd ->
            if (!out.exists() || out.length() != afd.length) {
                afd.createInputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        out
    } catch (e: Exception) {
        null
    }

    /** The oblasts and the SHA-256 of each official file, shipped with the app. */
    class Oblast(val id: String, val uk: String, val en: String)

    fun oblasts(c: Context): List<Oblast> = try {
        val a = org.json.JSONArray(c.assets.open("maps/oblasts.json").bufferedReader().use { it.readText() })
        (0 until minOf(a.length(), 40)).map { a.getJSONObject(it) }.map { Oblast(it.getString("id"), it.getString("uk"), it.getString("en")) }
    } catch (e: Exception) {
        emptyList()
    }

    fun sums(c: Context): Map<String, String> = try {
        c.assets.open("maps/SHA256SUMS").bufferedReader().readLines().mapNotNull { line ->
            line.trim().split(Regex("\\s+")).takeIf { it.size == 2 }?.let { (h, f) -> h.lowercase() to f.trimStart('*') }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    /** Which official file this is (by its SHA-256), or null for a file of unknown origin. */
    fun official(c: Context, uri: Uri, sums: Map<String, String>): String? = try {
        // Counted once per file (a big file takes a while); recounted if its size changes
        val size = c.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val known = prefs(c).getString("sum:$uri", null)?.split(" ")?.takeIf { it.size == 2 && it[0] == size.toString() }?.get(1)
        val hash = known ?: digest(c, uri).also { prefs(c).edit().putString("sum:$uri", "$size $it").apply() }
        sums[hash]
    } catch (e: Exception) {
        null
    }

    private fun digest(c: Context, uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        c.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun name(c: Context, uri: Uri): String = runCatching {
        c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
}

/**
 * "Map": always there — the overview of Ukraine is built in, like the Constitution — and as
 * detailed as the oblast files opened. Drawn on the phone with no network; the tiles are kept in
 * memory only, so no trace of the places looked at is left behind.
 */
@Composable
fun MapScreen(nav: Nav) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    var files by remember { mutableStateOf(MapFiles.all(context)) }
    var overview by remember { mutableStateOf<File?>(null) }
    var ready by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val sums = remember { MapFiles.sums(context) }
    val oblasts = remember { MapFiles.oblasts(context) }
    val english = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "en"
    fun MapFiles.Oblast.title() = if (english) en else uk
    LaunchedEffect(Unit) {
        overview = withContext(Dispatchers.IO) { MapFiles.overview(context) }
        ready = true
    }
    // Several oblasts at once: each file checked against the official sums before it is added
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        picked.forEach { uri ->
            val name = MapFiles.name(context, uri)
            if (!name.endsWith(".map", ignoreCase = true)) { note = context.getString(R.string.map_broken); return@forEach }
            MapFiles.add(context, uri)
        }
        files = MapFiles.all(context)
    }
    Frame(stringResource(R.string.map_title), nav::back) {
        if (!ready) return@Frame Loading()
        val all = listOfNotNull(overview?.let { Source.Built(it) }) + files.map { Source.Picked(it) }
        if (all.isNotEmpty()) {
            androidx.compose.runtime.key(all.joinToString { it.key }) { OfflineMap(all, Modifier.fillMaxWidth().height(480.dp)) { note = context.getString(R.string.map_broken) } }
            Text(stringResource(R.string.map_osm), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.map_intro), style = ReadingText)
        }
        note?.let { Text(it, style = ReadingText, color = MaterialTheme.colorScheme.error) }
        // The detailed oblasts opened, each with where it came from, and a way to take it off
        Block(stringResource(R.string.map_detailed), Group.GUIDE, padded = false, foldable = true, startOpen = files.isEmpty(),
            summary = if (files.isEmpty()) stringResource(R.string.map_detailed_none) else stringResource(R.string.map_detailed_count, files.size)) {
            files.forEachIndexed { i, uri ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                var origin by remember(uri) { mutableStateOf<String?>(null) }
                var checked by remember(uri) { mutableStateOf(false) }
                LaunchedEffect(uri) { origin = withContext(Dispatchers.IO) { MapFiles.official(context, uri, sums) }; checked = true }
                val fileName = MapFiles.name(context, uri)
                val oblast = oblasts.firstOrNull { "${it.id}.map" == (origin ?: fileName) }
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(oblast?.title() ?: fileName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            when { !checked -> stringResource(R.string.map_checking); origin != null -> stringResource(R.string.map_official); else -> stringResource(R.string.map_unknown) },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { MapFiles.remove(context, uri); files = MapFiles.all(context) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.map_remove)) }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(R.string.map_how_1, R.string.map_how_2, R.string.map_how_3).forEachIndexed { i, s -> Text("${i + 1}. " + stringResource(s), style = ReadingText) }
                OutlinedButton(onClick = { Reach.open(context, MapFiles.DOWNLOADS, toolbarColor = toolbar) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.map_where)) }
                Button(onClick = { note = null; pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.map_add)) }
            }
        }
        // Which file is which oblast: the names, as the files are called on the release page
        if (oblasts.isNotEmpty()) {
            Block(stringResource(R.string.map_files_title), Group.GUIDE, foldable = true) {
                oblasts.forEach { o -> Text("${o.title()} — ${o.id}.map", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Text(stringResource(R.string.map_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where a map comes from: built into the app, or a file the owner opened. */
private sealed class Source(val key: String) {
    class Built(val file: File) : Source("built")
    class Picked(val uri: Uri) : Source(uri.toString())
}

/** The map: all the sources drawn together (the overview below, the oblasts over it), memory-only tiles. */
@Composable
private fun OfflineMap(sources: List<Source>, modifier: Modifier, onBroken: () -> Unit) {
    val context = LocalContext.current
    val holder = remember { MapHolder() }
    DisposableEffect(Unit) { onDispose { holder.close() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AndroidGraphicFactory.createInstance(ctx.applicationContext as android.app.Application)
            MapView(ctx).also { view ->
                view.isClickable = true
                view.mapScaleBar.isVisible = true
                view.setBuiltInZoomControls(true)
                if (!holder.open(context, sources, view)) onBroken()
            }
        },
    )
}

private class MapHolder {
    private val fds = mutableListOf<android.os.ParcelFileDescriptor>()
    private var store: MultiMapDataStore? = null
    private var view: MapView? = null

    fun open(context: Context, sources: List<Source>, mapView: MapView): Boolean = try {
        val multi = MultiMapDataStore(MultiMapDataStore.DataPolicy.RETURN_ALL)
        var center: LatLong? = null
        var opened = 0
        sources.forEach { s ->
            // One broken file does not stop the others
            runCatching {
                val file = when (s) {
                    is Source.Built -> MapFile(s.file)
                    is Source.Picked -> {
                        val fd = requireNotNull(context.contentResolver.openFileDescriptor(s.uri, "r"))
                        fds += fd
                        MapFile(FileInputStream(fd.fileDescriptor))
                    }
                }
                multi.addMapDataStore(file, false, false)
                opened++
                // The view starts on the first detailed oblast, or on Ukraine
                if (s is Source.Picked && center == null) center = file.boundingBox().centerPoint
                if (s is Source.Built && center == null) center = file.boundingBox().centerPoint
            }
        }
        require(opened > 0)
        store = multi
        val layer = TileRendererLayer(InMemoryTileCache(64), multi, mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
        layer.setXmlRenderTheme(MapsforgeThemes.DEFAULT)
        mapView.layerManager.layers.add(layer)
        val c = center ?: multi.boundingBox().centerPoint
        mapView.setCenter(LatLong(c.latitude, c.longitude))
        mapView.setZoomLevel(if (sources.any { it is Source.Picked }) 11.toByte() else 6.toByte())
        view = mapView
        true
    } catch (e: Exception) {
        close()
        false
    }

    fun close() {
        runCatching { view?.destroyAll() }
        runCatching { store?.close() }
        fds.forEach { runCatching { it.close() } }
        fds.clear()
        view = null; store = null
        runCatching { AndroidGraphicFactory.clearResourceMemoryCache() }
        // The theme's symbols Mapsforge keeps as small pictures in the app's files: cleared too
        runCatching { AndroidGraphicFactory.clearResourceFileCache() }
    }
}
