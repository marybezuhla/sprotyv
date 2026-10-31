package ua.sprotyv.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.sprotyv.R
import ua.sprotyv.content.Content
import ua.sprotyv.home.Group
import ua.sprotyv.home.Tiles
import ua.sprotyv.home.Tool

/**
 * Choosing what a "part of Sprotyv" widget opens, when it is placed on the home screen: the same
 * parts as the home screen inside the app, by group. Only for Sprotyv's own widgets (another app
 * cannot point it at anything else), and not while the neutral icon is on.
 */
class TileWidgetSetup : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        // A broken request from elsewhere simply closes this screen
        val widget = try {
            intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        } catch (e: Exception) {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
        val manager = AppWidgetManager.getInstance(this)
        // Only a widget of our own "part of Sprotyv" kind
        val ours = widget != AppWidgetManager.INVALID_APPWIDGET_ID &&
            manager?.getAppWidgetInfo(widget)?.provider == ComponentName(this, TileWidget::class.java)
        if (!ours || Neutral.isOn(this)) { finish(); return }
        enableEdgeToEdge()
        setContent {
            val prefs = HomePrefs.state.collectAsState().value ?: HomePrefs.load(this)
            SprotyvTheme(prefs.theme, prefs.dynamic) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Picker { id, title, tone ->
                        TileWidgets.set(this, widget, id, title, tone)
                        Widgets.update(this)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget))
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun Picker(onPick: (String, String, String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val content by produceState<Content?>(null) { value = withContext(Dispatchers.IO) { runCatching { Content.get(context) }.getOrNull() } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.widget_tile_pick), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.widget_tile_pick_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val c = content ?: return@Column Loading()
        // The same groups as "Sections", the fixed ways in first and resistance after them
        val groups = listOf(Tiles.FIXED + Tiles.tool(Tool.RESIST)) + Group.entries.map { it.tiles }
        groups.forEach { ids ->
            val infos = ids.distinct().mapNotNull { tileInfo(it, c) }
            if (infos.isEmpty()) return@forEach
            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                infos.forEachIndexed { i, info ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val tone = when {
                        info.id == Tiles.tool(Tool.RESIST) || info.id == Tiles.tool(Tool.CONSTITUTION) || info.id == Tiles.tool(Tool.HISTORY) -> "khaki"
                        info.id == Tiles.tool(Tool.NOW) -> "urgent"
                        else -> when (info.tone) {
                            Group.DANGER -> "urgent"
                            Group.FIRST_AID, Group.LEAVE -> "green"
                            Group.PREPARE, Group.MINE, Group.FAMILY -> "blue"
                            else -> "plain"
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(info.id, info.title, tone) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(info.icon, info.badge(), 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
