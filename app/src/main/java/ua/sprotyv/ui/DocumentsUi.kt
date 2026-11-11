package ua.sprotyv.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.home.Group
import ua.sprotyv.personal.DocInfo
import ua.sprotyv.personal.Document
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.docs.DocFiles
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Documents of the owner's own (a passport scan, test results), encrypted in "My data". Only the
 * most valuable, and few: [DocFiles.MAX_DOCS] files, each and in all within a size. Large photos
 * are made smaller (and lose their hidden details such as the place they were taken).
 */
object Docs {
    /** Longest side of a kept photo: text on a scanned page stays readable. */
    const val PHOTO_SIDE = 2400

    /** Imports run outside any screen: leaving or turning the phone never leaves a half-done one behind. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    val busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val notice = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    /** Brings one file in; [cleanup] (the camera's plain photo) runs whatever happens. */
    fun start(context: Context, uri: Uri, fallbackTitle: String, type: String? = null, cleanup: () -> Unit = {}) {
        if (busy.value) { cleanup(); return }
        busy.value = true
        notice.value = null
        val app = context.applicationContext
        scope.launch {
            try {
                notice.value = import(app, uri, fallbackTitle, type)
            } catch (e: Throwable) {
                notice.value = R.string.docs_failed
            } finally {
                cleanup()
                busy.value = false
            }
        }
    }

    /** Brings a picked file or photo in: checked, encrypted, then named in the database. Returns a notice or null. */
    suspend fun import(context: Context, uri: Uri, fallbackTitle: String, typeHint: String? = null): Int? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val files = DocFiles.of(app)
        val type = typeHint ?: app.contentResolver.getType(uri).orEmpty()
        val title = (displayName(app, uri)?.substringBeforeLast('.')?.trim()?.take(200)?.ifBlank { null } ?: fallbackTitle)
        val mime = when {
            type == "application/pdf" -> "application/pdf"
            type.startsWith("image/") -> "image/jpeg"
            else -> return@withContext R.string.docs_not_supported
        }
        val w: DocFiles.Written? = try {
            if (mime == "application/pdf") {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    val head = readUpTo(input, 5)
                    if (String(head, Charsets.US_ASCII) != "%PDF-") return@withContext R.string.docs_not_supported
                    files.write(java.io.SequenceInputStream(head.inputStream(), input))
                }
            } else {
                val jpeg = shrink(app, uri) ?: return@withContext R.string.docs_not_supported
                try { files.write(jpeg.inputStream()) } finally { jpeg.fill(0) }
            }
        } catch (e: DocFiles.TooLarge) {
            return@withContext R.string.docs_too_large
        } catch (e: OutOfMemoryError) {
            return@withContext R.string.docs_failed
        } catch (e: Exception) {
            return@withContext R.string.docs_failed
        }
        if (w == null) return@withContext R.string.docs_failed
        var notice: Int = R.string.docs_failed
        try {
            // Only while the data is open: an import never waits for it (the owner left, trip mode)
            notice = kotlinx.coroutines.withTimeoutOrNull(5_000) { PersonalStore.read { db ->
                val docs = db.documents()
                when {
                    docs.count() >= DocFiles.MAX_DOCS -> R.string.docs_full_count
                    docs.bytes() + w.size > DocFiles.MAX_TOTAL_BYTES -> R.string.docs_full_space
                    else -> {
                        docs.add(Document(title = title, mime = mime, size = w.size, file = w.file, key = w.key, addedAt = System.currentTimeMillis()))
                        0
                    }
                }
            } } ?: R.string.docs_failed
        } finally {
            // A file that did not get its row goes at once; the key never stays in memory
            w.key.fill(0)
            if (notice != 0) files.delete(w.file)
            files.done(w.file)
        }
        if (notice == 0) null else notice
    }

    /** A photo as a JPEG at most [PHOTO_SIDE] on its longest side, turned upright; only the picture is kept. */
    private fun shrink(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_SIDE) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val turn = context.contentResolver.openInputStream(uri)?.use { input ->
            when (android.media.ExifInterface(input).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, PHOTO_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val m = android.graphics.Matrix().apply { postScale(scale, scale); postRotate(turn) }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (upright !== decoded) decoded.recycle()
        val out = ByteArrayOutputStream()
        upright.compress(Bitmap.CompressFormat.JPEG, 85, out)
        upright.recycle()
        return out.toByteArray()
    }

    private fun readUpTo(input: java.io.InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = input.read(b, got, n - got)
            if (r < 0) break
            got += r
        }
        return b.copyOf(got)
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** The plain bytes of a document, read from its encrypted file; null if it cannot be read. */
    suspend fun bytes(context: Context, id: Long): Pair<Document, ByteArray>? = withContext(Dispatchers.IO) {
        val doc = PersonalStore.read { it.documents().get(id) } ?: return@withContext null
        try {
            val out = ByteArray(doc.size.toInt().coerceIn(0, DocFiles.MAX_FILE_BYTES.toInt()))
            DocFiles.of(context).open(doc.file, doc.key).use { input ->
                var got = 0
                while (got < out.size) {
                    val n = input.read(out, got, out.size - got)
                    if (n < 0) break
                    got += n
                }
                // Exactly the size the row says, and the end of the file checked (a cut file fails here)
                if (got != out.size || input.read() != -1) { out.fill(0); return@withContext null }
            }
            doc to out
        } catch (e: Throwable) {
            null
        } finally {
            doc.key.fill(0)
        }
    }

    /**
     * The row goes at once; the encrypted file stays until "Undo" has passed, then goes too. If the
     * app is closed in between, the file has no row and the next sweep removes it.
     */
    fun delete(context: Context, id: Long) {
        val app = context.applicationContext
        PersonalStore.save("doc:delete:$id") { db ->
            val doc = db.documents().get(id) ?: return@save
            val files = DocFiles.of(app)
            files.hold(doc.file)
            db.documents().delete(id)
            Notices.deleted(app, app.getString(R.string.deleted_named, doc.title), undo = {
                PersonalStore.save("doc:restore:$id") { it.documents().add(doc); files.done(doc.file) }
            }, gone = {
                // Wiped before the file goes, even if deleting it fails
                doc.key.fill(0)
                files.delete(doc.file)
                files.done(doc.file)
            })
        }
    }

    fun rename(id: Long, title: String) {
        val t = title.trim().take(200)
        if (t.isEmpty()) return
        PersonalStore.save("doc:rename:$id") { it.documents().rename(id, t) }
    }

    /** Copies handed to another app are plain: they are removed on the next return (after a minute) or start. */
    private fun shareDir(context: Context) = File(context.applicationContext.cacheDir, "share")

    fun clearShared(context: Context, olderThanMs: Long = 0) {
        val now = System.currentTimeMillis()
        shareDir(context).listFiles()?.forEach { if (now - it.lastModified() >= olderThanMs) it.deleteRecursively() }
    }

    private val clearLater = android.os.Handler(android.os.Looper.getMainLooper())

    /** Back in the app: the plain copy handed out goes a minute later (the other app had time to take it). */
    fun clearSharedSoon(context: Context, afterMs: Long = 60_000) {
        val app = context.applicationContext
        if (shareDir(app).listFiles().isNullOrEmpty()) return
        clearLater.removeCallbacksAndMessages(null)
        // Only copies at least that old: one shared again meanwhile keeps its own time
        clearLater.postDelayed({ Thread { clearShared(app, afterMs) }.start() }, afterMs)
    }

    fun share(context: Context, doc: Document, bytes: ByteArray) {
        val app = context.applicationContext
        clearLater.removeCallbacksAndMessages(null)
        // Its own copy: the viewer wipes its bytes when it closes, which may be while this is written
        val copy = bytes.copyOf()
        // Written away from the screen's thread; the chooser opens from the screen itself
        scope.launch {
            val dir = shareDir(app).apply { mkdirs() }
            clearShared(app)
            val ext = if (doc.mime == "application/pdf") "pdf" else "jpg"
            val name = doc.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60).ifBlank { "document" } + "." + ext
            // Each share in its own unguessable folder: an app that kept the address of an earlier
            // share never reaches a later document of the same name
            val own = File(dir, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
            val file = File(own, name)
            try { file.writeBytes(copy) } catch (e: Exception) { file.delete(); return@launch } finally { copy.fill(0) }
            withContext(Dispatchers.Main) {
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND).setType(doc.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Not "our own window": another app is in front, so the data closes as on any leaving
                try {
                    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (e: Exception) {
                    file.delete()
                }
            }
        }
    }

}

private fun sizeText(context: Context, bytes: Long): String = android.text.format.Formatter.formatShortFileSize(context, bytes)

@Composable
fun DocumentsScreen() {
    val context = LocalContext.current
    val docs by remember { PersonalStore.observe { it.documents().observe() } }.collectAsStateWithLifecycle(null)
    val notice by Docs.notice.collectAsStateWithLifecycle()
    val busy by Docs.busy.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<DocInfo?>(null) }
    // A copy given to another app earlier is not kept; files left by an interrupted restore go
    LaunchedEffect(Unit) {
        // An old message is not shown again on a later visit
        if (!Docs.busy.value) Docs.notice.value = null
        withContext(Dispatchers.IO) {
            Docs.clearShared(context, 60_000)
            PersonalStore.read { db -> DocFiles.of(context).sweep(db.documents().files().toSet(), graceMs = 60 * 60_000L) }
        }
    }
    val photoTitle = stringResource(R.string.docs_photo_title, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")))
    fun bring(uri: Uri, type: String? = null, cleanup: () -> Unit = {}) = Docs.start(context, uri, photoTitle, type, cleanup)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Away.back()
        if (uri != null) bring(uri)
    }
    val photo = remember { File(File(context.cacheDir, "scan").apply { mkdirs() }, "doc.jpg") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        Away.back()
        if (ok) bring(Uri.fromFile(photo), "image/jpeg") { photo.delete() } else photo.delete()
    }

    Text(stringResource(R.string.docs_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val list = docs ?: return Loading()
    if (list.isEmpty()) {
        Text(stringResource(R.string.docs_none), style = MaterialTheme.typography.bodyLarge)
    } else {
        Block(stringResource(R.string.docs_title), Group.MINE, padded = false) {
            list.forEachIndexed { i, d ->
                val kind = stringResource(if (d.mime == "application/pdf") R.string.docs_kind_pdf else R.string.docs_kind_photo)
                BlockRow(d.title, "$kind · ${sizeText(context, d.size)}", first = i == 0) { open = d }
            }
        }
    }
    // The space, plainly: a thin bar in the colour of "My data", words for the numbers
    val used = list.sumOf { it.size }
    Block(stringResource(R.string.docs_space_title), Group.MINE) {
        LinearProgressIndicator(
            progress = { (used.toFloat() / DocFiles.MAX_TOTAL_BYTES).coerceIn(0f, 1f) },
            color = Group.MINE.tint().ink,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        Text(
            stringResource(R.string.docs_space, sizeText(context, used), sizeText(context, DocFiles.MAX_TOTAL_BYTES), list.size, DocFiles.MAX_DOCS),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (list.size >= DocFiles.MAX_DOCS - 2 || used >= DocFiles.MAX_TOTAL_BYTES * 9 / 10) {
            Text(stringResource(R.string.docs_space_low), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Block(stringResource(R.string.docs_add_title), Group.MINE) {
        Text(stringResource(R.string.docs_add_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { Away.forOwnWindow(); try { pick.launch(arrayOf("application/pdf", "image/*")) } catch (e: android.content.ActivityNotFoundException) { Away.back() } },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.docs_add_file)) }
        OutlinedButton(
            onClick = {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
                Away.forOwnWindow()
                try { camera.launch(uri) } catch (e: android.content.ActivityNotFoundException) { Away.back() }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.docs_add_photo)) }
        if (busy) Text(stringResource(R.string.docs_busy), style = MaterialTheme.typography.bodyMedium)
        notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
    }
    open?.let { d -> DocViewer(d) { open = null } }
}

/** One document, full screen, read into memory only; never written out plain unless the owner shares it. */
@Composable
private fun DocViewer(info: DocInfo, onClose: () -> Unit) {
    val context = LocalContext.current
    // Loading, then the bytes, or a plain "could not open"
    var failed by remember(info.id) { mutableStateOf(false) }
    val loaded by produceState<Pair<Document, ByteArray>?>(null, info.id) {
        // Finished even if the viewer closes meanwhile, so the bytes are always wiped below
        val got = withContext(kotlinx.coroutines.NonCancellable) { Docs.bytes(context, info.id) }
        if (got == null) failed = true
        value = got
        // Closed before it arrived: wiped at once
        awaitDispose { got?.second?.fill(0) }
    }
    var asking by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf(info.title) }
    val screenshots = HomePrefs.state.collectAsStateWithLifecycle().value?.screenshots == true
    // Its own window: kept off screenshots and away from screen-reading apps like the screen beneath
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = if (screenshots) androidx.compose.ui.window.SecureFlagPolicy.Inherit else androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
        SensitiveWindow()
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(8.dp))
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                }
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    val l = loaded
                    when {
                        l == null && failed -> Text(stringResource(R.string.docs_failed), modifier = Modifier.padding(16.dp))
                        l == null -> Loading()
                        l.first.mime == "application/pdf" -> PdfPages(l.second)
                        else -> PhotoView(l.second)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { asking = "rename" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.docs_rename)) }
                    TextButton(onClick = { asking = "share" }, enabled = loaded != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.docs_share)) }
                    TextButton(onClick = { asking = "delete" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.docs_delete)) }
                }
            }
        }
    }
    when (asking) {
        "share" -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.docs_share_title)) },
            text = { Text(stringResource(R.string.docs_share_warning)) },
            confirmButton = { TextButton(onClick = { asking = null; loaded?.let { (d, b) -> Docs.share(context, d, b) } }) { Text(stringResource(R.string.docs_share)) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.docs_delete_title)) },
            text = { Text(stringResource(R.string.docs_delete_text)) },
            confirmButton = { TextButton(onClick = { asking = null; Docs.delete(context, info.id); onClose() }) { Text(stringResource(R.string.docs_delete)) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
        )
        "rename" -> AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.docs_rename)) },
            text = { OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { asking = null; Docs.rename(info.id, title) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A picture that can be pinched larger, to read small print; moved only within its own edges. */
@Composable
private fun Zoomable(bitmap: androidx.compose.ui.graphics.ImageBitmap, description: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        val maxX = size.width * (scale - 1) / 2
        val maxY = size.height * (scale - 1) / 2
        dx = (dx + pan.x).coerceIn(-maxX, maxX)
        dy = (dy + pan.y).coerceIn(-maxY, maxY)
    }
    Image(
        bitmap = bitmap, contentDescription = description,
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it }.transformable(state).graphicsLayer(scaleX = scale, scaleY = scale, translationX = dx, translationY = dy),
    )
}

/** A photo, decoded away from the screen's thread. */
@Composable
private fun PhotoView(bytes: ByteArray) {
    var failed by remember(bytes) { mutableStateOf(false) }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, bytes) {
        val decoded = withContext(Dispatchers.Default) { try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } catch (e: OutOfMemoryError) { null } }
        if (decoded == null) failed = true
        value = decoded
    }
    val b = bitmap ?: return if (failed) Text(stringResource(R.string.docs_failed), modifier = Modifier.padding(16.dp)) else Loading()
    Zoomable(b, null)
}

/**
 * A PDF drawn page by page from memory: the bytes go into an anonymous in-memory file (never on
 * the storage) that Android's own PDF reader opens. A tap on a page opens it larger, to zoom in.
 */
@Composable
private fun PdfPages(bytes: ByteArray) {
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    var failedToOpen by remember(bytes) { mutableStateOf(false) }
    val doc by produceState<PdfDoc?>(null, bytes) {
        // Finished even if the viewer closes meanwhile, so it is always closed below
        val opened = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { PdfDoc.open(bytes) }
        if (opened == null) failedToOpen = true
        value = opened
        awaitDispose { opened?.close() }
    }
    val d = doc ?: return if (failedToOpen) Text(stringResource(R.string.docs_failed), modifier = Modifier.padding(16.dp)) else Loading()
    var large by remember { mutableStateOf<Int?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(d.pages) { i ->
            val page by produceState<Bitmap?>(null, i) { value = withContext(Dispatchers.Default) { d.render(i, widthPx.coerceAtMost(1600)) } }
            val label = stringResource(R.string.docs_page, i + 1, d.pages)
            page?.let {
                Image(it.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.docs_zoom)) { large = i })
            } ?: Loading()
        }
    }
    large?.let { i ->
        val screenshots = HomePrefs.state.collectAsStateWithLifecycle().value?.screenshots == true
        Dialog(onDismissRequest = { large = null }, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = if (screenshots) androidx.compose.ui.window.SecureFlagPolicy.Inherit else androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
            SensitiveWindow()
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                    TextButton(onClick = { large = null }, modifier = Modifier.align(Alignment.End).padding(8.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                    val big by produceState<Bitmap?>(null, i) { value = withContext(Dispatchers.Default) { d.render(i, (widthPx * 2).coerceAtMost(2800)) } }
                    Column(Modifier.weight(1f)) {
                        big?.let { Zoomable(it.asImageBitmap(), stringResource(R.string.docs_page, i + 1, d.pages)) } ?: Loading()
                    }
                }
            }
        }
    }
}

private const val MAX_PIXELS = 16_000_000L

/** memfd_create's own close-on-exec flag (Linux MFD_CLOEXEC; not O_CLOEXEC). */
private const val MFD_CLOEXEC = 1

/** An open PDF: pages drawn one at a time, never while it closes. */
private class PdfDoc private constructor(private val renderer: PdfRenderer) {
    val pages: Int = renderer.pageCount
    private var closed = false

    fun render(i: Int, width: Int): Bitmap? = synchronized(this) {
        if (closed) return null
        try {
            renderer.openPage(i).use { p ->
                // At most 16 million pixels (a long page is drawn narrower), far below what Android can draw
                var w = width
                var h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                if (w.toLong() * h > MAX_PIXELS) {
                    val k = kotlin.math.sqrt(MAX_PIXELS.toDouble() / (w.toLong() * h))
                    w = (w * k).toInt().coerceAtLeast(1); h = (h * k).toInt().coerceAtLeast(1)
                }
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun close() = synchronized(this) {
        closed = true
        try { renderer.close() } catch (e: Exception) { /* closing only; the pages are already drawn */ }
    }

    companion object {
        fun open(bytes: ByteArray): PdfDoc? = try {
            // An anonymous file in memory, not inherited by anything this app starts
            val fd = Os.memfd_create("doc", MFD_CLOEXEC)
            try {
                java.io.FileOutputStream(fd).write(bytes)
                Os.lseek(fd, 0, OsConstants.SEEK_SET)
                PdfDoc(PdfRenderer(ParcelFileDescriptor.dup(fd)))
            } finally {
                Os.close(fd)
            }
        } catch (e: Throwable) {
            null
        }
    }
}

/** Marks the window this is drawn in (a dialog's own) as not for screen-reading apps, as the data screens are. */
@Composable
fun SensitiveWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        DisposableEffect(view) {
            val root = view.rootView
            root.setAccessibilityDataSensitive(android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES)
            onDispose { }
        }
    }
}
