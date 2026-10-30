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
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
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

/** "Call 112" at once; the other services one tap further. On every screen, unless turned off. */
@Composable
fun CallBar(edge: Boolean = true) {
    val shown = HomePrefs.state.collectAsState().value?.callBar ?: true
    var others by rememberSaveable { mutableStateOf(false) }
    if (!shown) {
        // No bar: the edge still keeps clear of the system bar
        if (edge) Spacer(Modifier.navigationBarsPadding())
        return
    }
    val context = LocalContext.current
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            // At the screen's edge it keeps clear of the system bar; above the tabs it does not need to
            (if (edge) Modifier.navigationBarsPadding() else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(stringResource(R.string.call_112), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { others = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.call_others), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    if (others) ServicesDialog { others = false }
}

/**
 * When the bar is turned off: a phone button in the corner of every screen opens the same list,
 * so 112 is never more than two taps away.
 */
@Composable
fun CornerCall() {
    val shown = HomePrefs.state.collectAsState().value?.callBar ?: true
    if (shown) return
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.call_services), tint = DangerRed)
    }
    if (open) ServicesDialog { open = false }
}

/** The services as one even list: the number large on the left, the name beside it. */
@Composable
private fun ServicesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.call_services)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.back)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Service.entries.forEach { service ->
                    OutlinedButton(
                        onClick = { onClose(); Reach.dial(context, service.number) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                service.number,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (service == Service.UNIFIED) DangerRed else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(stringResource(service.name()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    )
}

private fun Service.name(): Int = when (this) {
    Service.UNIFIED -> R.string.service_name_unified
    Service.RESCUE -> R.string.service_name_rescue
    Service.POLICE -> R.string.service_name_police
    Service.AMBULANCE -> R.string.service_name_ambulance
    Service.GAS -> R.string.service_name_gas
}
