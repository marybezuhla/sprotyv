package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import ua.sprotyv.R
import ua.sprotyv.personal.PersonalStore
import ua.sprotyv.personal.Setting
import ua.sprotyv.reach.Reach

/**
 * "My bookmarks": pages the owner keeps, newest first. A page comes from "Share → Sprotyv" in a
 * browser or is pasted here; the owner names it before it is kept. Opens in the browser, the same
 * way as every other page from Sprotyv.
 */
@Composable
fun BookmarksScreen() {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface.toArgb()
    val pages by remember {
        PersonalStore.observe { db -> combine(Bookmarks.KEYS.map { db.settings().observe(it) }) { it.toList() } }
    }.collectAsStateWithLifecycle(null)
    val list = Bookmarks.fromPages(pages ?: return Loading())
    val incoming by Incoming.state.collectAsStateWithLifecycle()
    var tooMany by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Bookmark?>(null) }

    // Each change is made on what is stored at the moment of writing, so two quick changes both count
    fun change(edit: (List<Bookmark>) -> List<Bookmark>) {
        PersonalStore.save("setting:bookmarks:" + java.util.UUID.randomUUID()) { db ->
            val now = Bookmarks.fromPages(Bookmarks.KEYS.map { db.settings().get(it) })
            val written = Bookmarks.toPages(edit(now)) ?: return@save
            Bookmarks.KEYS.forEachIndexed { i, k ->
                val v = written.getOrNull(i)
                if (v == null) db.settings().delete(k) else db.settings().put(Setting(k, v))
            }
        }
    }
    // False when it does not fit: the owner is told, and nothing is lost silently
    fun add(url: String, title: String): Boolean {
        val name = Bookmarks.cleanTitle(title).ifBlank { Bookmarks.host(url) }
        val next = { now: List<Bookmark> -> listOf(Bookmark(url, name, System.currentTimeMillis())) + now.filter { it.url != url } }
        if (Bookmarks.toPages(next(list)) == null) { tooMany = true; return false }
        change(next)
        return true
    }

    incoming?.let { shared -> SharedCard(shared, onKeep = { url, title -> if (add(url, title)) Incoming.clear() }, onDrop = Incoming::clear) }

    Text(stringResource(R.string.bookmarks_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PasteBlock { url -> add(url, "") }

    if (list.isEmpty()) {
        Text(stringResource(R.string.bookmarks_empty), style = MaterialTheme.typography.bodyLarge)
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            list.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { Reach.open(context, b.url, toolbarColor = toolbar) }
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(Bookmarks.host(b.url), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { removing = b }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.bookmarks_delete, b.title))
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.bookmarks_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    removing?.let { b ->
        AlertDialog(
            onDismissRequest = { removing = null },
            text = { Text(stringResource(R.string.bookmarks_delete_confirm, b.title)) },
            confirmButton = { TextButton(onClick = { change { now -> now.filter { it.url != b.url } }; removing = null }) { Text(stringResource(R.string.delete_row)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (tooMany) {
        AlertDialog(
            onDismissRequest = { tooMany = false },
            text = { Text(stringResource(R.string.bookmarks_too_many, Bookmarks.MAX)) },
            confirmButton = { TextButton(onClick = { tooMany = false }) { Text(stringResource(R.string.got_it)) } },
        )
    }
}

/** What arrived from another app: a page to name and keep, or a plain word on why it is not kept. */
@Composable
private fun SharedCard(shared: Incoming.Shared, onKeep: (String, String) -> Unit, onDrop: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val c = shared.check) {
                is Bookmarks.Check.Page -> {
                    var name by rememberSaveable(shared.id) { mutableStateOf(shared.title.ifBlank { Bookmarks.host(c.url) }) }
                    Text(stringResource(R.string.bookmarks_keep_title), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                    // The site first and large: a name suggested by another app cannot hide where it leads
                    Text(Bookmarks.host(c.url), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(c.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Field(stringResource(R.string.bookmarks_name), name, { name = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onKeep(c.url, name) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.save)) }
                        OutlinedButton(onClick = onDrop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.cancel)) }
                    }
                }
                else -> {
                    Text(
                        stringResource(
                            when (c) {
                                Bookmarks.Check.Telegram -> R.string.bookmarks_no_telegram
                                Bookmarks.Check.NotSecure -> R.string.bookmarks_not_secure
                                else -> R.string.bookmarks_no_page
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedButton(onClick = onDrop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.got_it)) }
                }
            }
        }
    }
}

/** Paste an address by hand: the same checks as a shared page. */
@Composable
private fun PasteBlock(onAdd: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<Int?>(null) }
    Field(stringResource(R.string.bookmarks_paste), text, { text = it; problem = null }, hint = "https://…")
    problem?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    OutlinedButton(enabled = text.isNotBlank(), onClick = {
        when (val c = Bookmarks.check(text)) {
            is Bookmarks.Check.Page -> if (onAdd(c.url)) text = ""
            Bookmarks.Check.Telegram -> problem = R.string.bookmarks_no_telegram
            Bookmarks.Check.NotSecure -> problem = R.string.bookmarks_not_secure
            Bookmarks.Check.NoPage -> problem = R.string.bookmarks_no_page
        }
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.bookmarks_add)) }
}
