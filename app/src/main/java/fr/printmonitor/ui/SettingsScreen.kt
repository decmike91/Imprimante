package fr.printmonitor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.printmonitor.MonitorHub
import fr.printmonitor.MonitorService
import fr.printmonitor.data.ConfigStore
import fr.printmonitor.data.PrinterConfig
import fr.printmonitor.data.PrinterId
import fr.printmonitor.data.PrinterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var c by remember { mutableStateOf(MonitorHub.config.value) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Réglages") },
            actions = { TextButton(onClick = onDone) { Text("Fermer") } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Laisser l'adresse IP vide pour masquer une imprimante.",
                style = MaterialTheme.typography.bodySmall,
            )
            PrinterId.entries.forEach { id ->
                val pc = c.printer(id)
                fun set(n: PrinterConfig) { c = c.with(id, n) }
                val model = if (id.type == PrinterType.ELEGOO_CC) "Elegoo Centauri Carbon" else "Creality K1"
                val camDefault = if (id.type == PrinterType.ELEGOO_CC) "http://IP:3031/video" else "8080, sinon page http://IP:8000/"

                Section("$model — ${c.displayName(id)}")
                OutlinedTextField(
                    pc.name, { set(pc.copy(name = it)) },
                    label = { Text("Nom affiché") }, placeholder = { Text(id.defaultName) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Ip("Adresse IP de l'imprimante", pc.ip) { set(pc.copy(ip = it)) }
                OutlinedTextField(
                    pc.cameraUrl, { set(pc.copy(cameraUrl = it)) },
                    label = { Text("URL caméra (vide = $camDefault)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Ip("Adresse IP de la prise Tapo", pc.plugIp) { set(pc.copy(plugIp = it)) }
                Toggle("Couper la prise après impression (buse < 50 °C)", pc.autoOff) { set(pc.copy(autoOff = it)) }
            }

            Section("Compte Tapo")
            Text(
                "Identifiants de l'appli Tapo. Activez aussi « Compatibilité tierce » dans l'appli Tapo (Moi › Services tiers).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                c.tapoEmail, { c = c.copy(tapoEmail = it) }, label = { Text("E-mail") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                c.tapoPassword, { c = c.copy(tapoPassword = it) }, label = { Text("Mot de passe") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Section("Notifications")
            Toggle("Surveillance en arrière-plan (alertes écran éteint)", c.backgroundMonitoring) {
                c = c.copy(backgroundMonitoring = it)
            }

            Button(
                onClick = {
                    ConfigStore.save(ctx, c)
                    MonitorHub.apply(ConfigStore.load(ctx))
                    MonitorService.update(ctx, c.backgroundMonitoring && !c.isEmpty)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enregistrer") }
        }
    }
}

@Composable private fun Section(title: String) =
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

@Composable private fun Ip(label: String, value: String, onChange: (String) -> Unit) =
    OutlinedTextField(
        value, onChange, label = { Text(label) }, placeholder = { Text("192.168.1.50") },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )

@Composable private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onChange)
    }
