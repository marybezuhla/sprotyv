package ua.sprotyv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.sprotyv.R
import ua.sprotyv.content.Service
import ua.sprotyv.reach.Reach

/** Home: six large sections and the "Call" bar, which is also on every other screen. */
@Composable
fun HomeScreen(onOpen: (Section) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        // Two columns on a phone; one column when the text is made large, so nothing is cut off
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(Section.entries) { s -> SectionTile(s, onClick = { onOpen(s) }) }
        }
        CallBar()
    }
}

@Composable
private fun SectionTile(section: Section, onClick: () -> Unit) {
    val colors = if (section.marked) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    }
    Card(
        onClick = onClick,
        colors = colors,
        border = if (section.marked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            // The icon only supports the words; the title alone says what the section is
            Icon(section.icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(section.hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Call 112" at once; the other services one tap further. On every screen. */
@Composable
fun CallBar() {
    val context = LocalContext.current
    var others by rememberSaveable { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { Reach.dial(context, Service.UNIFIED.number) },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.call_112), fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { others = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.call_others))
            }
        }
    }
    if (others) {
        AlertDialog(
            onDismissRequest = { others = false },
            confirmButton = { TextButton(onClick = { others = false }) { Text(stringResource(R.string.back)) } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Service.entries.forEach { service ->
                        OutlinedButton(
                            onClick = { others = false; Reach.dial(context, service.number) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) { Text(stringResource(service.label())) }
                    }
                }
            },
        )
    }
}

private fun Service.label(): Int = when (this) {
    Service.UNIFIED -> R.string.service_unified
    Service.RESCUE -> R.string.service_rescue
    Service.POLICE -> R.string.service_police
    Service.AMBULANCE -> R.string.service_ambulance
    Service.GAS -> R.string.service_gas
}
