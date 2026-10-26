package fr.printmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.printmonitor.MonitorHub
import fr.printmonitor.data.PrintStatus
import fr.printmonitor.data.PrinterId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dashboard(onSettings: () -> Unit) {
    val cfg by MonitorHub.config.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PrintMonitor") },
                actions = { TextButton(onClick = onSettings) { Text("Réglages") } },
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrinterId.entries.filter { cfg.printer(it).ip.isNotBlank() }.forEach { id ->
                item(key = id.name) { PrinterCard(id) }
            }
            if (cfg.isEmpty) item { Text("Aucune imprimante configurée. Ouvrez les réglages.") }
        }
    }
}

private fun statusColor(s: PrintStatus) = when (s) {
    PrintStatus.PRINTING -> Color(0xFF1E88E5)
    PrintStatus.FINISHED -> Color(0xFF43A047)
    PrintStatus.PAUSED -> Color(0xFFFB8C00)
    PrintStatus.ERROR, PrintStatus.STOPPED -> Color(0xFFE53935)
    PrintStatus.OFFLINE -> Color.Gray
    PrintStatus.IDLE -> Color(0xFF757575)
}

private fun t(v: Double?) = v?.let { "%.0f".format(it) } ?: "–"
private fun dur(sec: Long?): String {
    if (sec == null) return "–"
    val h = sec / 3600; val m = (sec % 3600) / 60
    return if (h > 0) "${h} h ${"%02d".format(m)}" else "$m min"
}

@Composable
private fun PrinterCard(id: PrinterId) {
    val s by MonitorHub.printer(id).collectAsStateWithLifecycle()
    val plug by MonitorHub.plug(id).collectAsStateWithLifecycle()
    var camOn by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(s.status.label, color = statusColor(s.status), fontWeight = FontWeight.Bold)
            }

            val cam = s.cameraUrl
            if (cam != null && s.status != PrintStatus.OFFLINE && camOn) MjpegView(cam)
            if (cam != null && s.status != PrintStatus.OFFLINE) {
                TextButton(onClick = { camOn = !camOn }) { Text(if (camOn) "Masquer la caméra" else "Afficher la caméra") }
            }

            Text(
                "Buse ${t(s.nozzle)}/${t(s.nozzleTarget)} °C · Plateau ${t(s.bed)}/${t(s.bedTarget)} °C" +
                    (s.chamber?.let { " · Caisson ${t(it)} °C" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (s.status == PrintStatus.PRINTING || s.status == PrintStatus.PAUSED) {
                s.fileName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                val p = (s.progress ?: 0).coerceIn(0, 100)
                LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                val end = s.remainingSec?.let {
                    SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(System.currentTimeMillis() + it * 1000))
                }
                Text(
                    "$p %" + (s.layer?.let { " · couche $it/${s.totalLayers ?: "?"}" } ?: "") +
                        " · écoulé ${dur(s.elapsedSec)} · reste ${dur(s.remainingSec)}" +
                        (end?.let { " · fin ≈ $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.status == PrintStatus.PRINTING)
                        Button(onClick = { MonitorHub.pause(id) }) { Text("Pause") }
                    else
                        Button(onClick = { MonitorHub.resume(id) }) { Text("Reprendre") }
                    OutlinedButton(onClick = {
                        confirm = "Arrêter définitivement l'impression en cours ?" to { MonitorHub.cancel(id) }
                    }) { Text("Arrêter") }
                }
            }

            s.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            if (plug.configured) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Prise Tapo", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when {
                                !plug.reachable -> plug.error ?: "Injoignable"
                                else -> (plug.powerW?.let { "%.1f W".format(it) } ?: "") +
                                    (plug.todayWh?.let { " · aujourd'hui ${"%.2f".format(it / 1000)} kWh" } ?: "")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = plug.on == true,
                        enabled = plug.reachable,
                        onCheckedChange = { wantOn ->
                            if (wantOn) MonitorHub.setPlug(id, true)
                            else {
                                val hot = (s.nozzle ?: 0.0) >= 50.0
                                val busy = s.status == PrintStatus.PRINTING || s.status == PrintStatus.PAUSED
                                val msg = when {
                                    busy -> "⚠️ Une impression est en cours : couper la prise la fera échouer. Couper quand même ?"
                                    hot -> "⚠️ La buse est encore chaude (${t(s.nozzle)} °C). Couper maintenant arrête la ventilation de la tête et peut boucher la buse. Couper quand même ?"
                                    else -> "Couper l'alimentation de l'imprimante ?"
                                }
                                confirm = msg to { MonitorHub.setPlug(id, false) }
                            }
                        },
                    )
                }
            }
        }
    }

    confirm?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } },
        )
    }
}
