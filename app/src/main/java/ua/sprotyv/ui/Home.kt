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

/** "Call 112" at once; the other services one tap further. On every screen. */
@Composable
fun CallBar(edge: Boolean = true) {
    val context = LocalContext.current
    var others by rememberSaveable { mutableStateOf(false) }
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
