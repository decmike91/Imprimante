package fr.printmonitor.printers

import fr.printmonitor.data.PrintStatus
import fr.printmonitor.data.PrinterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creality K1 / K1C / K1 Max — firmware d'origine, sans root.
 *  - WebSocket : ws://IP:9999 (trames JSON partielles : seuls les champs modifiés sont envoyés)
 *  - Caméra    : MJPEG http://IP:8080/?action=stream par défaut (URL modifiable dans les réglages)
 *
 * Attention : sur certains firmwares récents (ex. 1.3.5.22) le flux MJPEG du port 8080 a disparu.
 * Voir le guide (section dépannage) ; renseigner alors une autre URL caméra.
 */
class CrealityK1Client(
    private val ip: String,
    private val cameraOverride: String,
    target: MutableStateFlow<PrinterState>,
    http: OkHttpClient,
    scope: CoroutineScope,
) : WsPrinterClient(target, http, scope) {

    override val url get() = "ws://$ip:9999"

    override fun onOpened() {
        // Firmwares récents (ex. 1.3.5.22) : plus de MJPEG sur 8080, caméra en WebRTC via http://IP:8000/
        target.update {
            it.copy(
                cameraUrl = cameraOverride.ifBlank { "http://$ip:8080/?action=stream" },
                cameraFallbackUrl = if (cameraOverride.isBlank()) "http://$ip:8000/" else null,
            )
        }
        send(JSONObject().put("method", "get").put("params", JSONObject().put("ReqPrinterPara", 1)).toString())
    }

    override fun keepAlive() {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        send(JSONObject().put("ModeCode", "heart_beat").put("msg", ts).toString())
    }

    override fun pause() = set("pause", 1)
    override fun resume() = set("pause", 0)
    override fun cancel() = set("stop", 1)
    override fun setLight(on: Boolean) {
        set("lightSw", if (on) 1 else 0)
        target.update { it.copy(lightOn = on) }
    }

    private fun set(key: String, value: Int) {
        send(JSONObject().put("method", "set").put("params", JSONObject().put(key, value)).toString())
    }

    override fun onText(text: String) {
        val t = text.trim()
        if (!t.startsWith("{")) return // "ok" en réponse au heart_beat
        val o = JSONObject(t)
        if (o.optString("ModeCode") == "heart_beat") { send("ok"); return }

        target.update { s ->
            // state : 0 inactive, 1 impression, 2 terminée, 3 échec, 4 annulée, 5 pause
            val status = when (o.int("state")) {
                null -> s.status
                1 -> PrintStatus.PRINTING
                2 -> PrintStatus.FINISHED
                3 -> PrintStatus.ERROR
                4 -> PrintStatus.STOPPED
                5 -> PrintStatus.PAUSED
                else -> PrintStatus.IDLE
            }
            val errMsg = o.optJSONObject("err")?.let { e ->
                val code = e.optInt("errcode", 0)
                if (code != 0) "Erreur imprimante (code $code)" else null
            }
            s.copy(
                status = status,
                nozzle = o.dbl("nozzleTemp") ?: s.nozzle,
                nozzleTarget = o.dbl("targetNozzleTemp") ?: s.nozzleTarget,
                bed = o.dbl("bedTemp0") ?: s.bed,
                bedTarget = o.dbl("targetBedTemp0") ?: s.bedTarget,
                chamber = o.dbl("boxTemp") ?: s.chamber,
                lightOn = o.int("lightSw")?.let { it == 1 } ?: s.lightOn,
                // materialStatus : 0 = filament présent, 1 = fin de filament détectée
                filamentRunout = o.int("materialStatus")?.let { it == 1 } ?: s.filamentRunout,
                progress = o.int("printProgress") ?: s.progress,
                fileName = o.optString("printFileName").takeIf { it.isNotBlank() }?.substringAfterLast('/') ?: s.fileName,
                layer = o.int("layer") ?: s.layer,
                totalLayers = o.int("TotalLayer") ?: s.totalLayers,
                elapsedSec = o.lng("printJobTime") ?: s.elapsedSec,
                remainingSec = o.lng("printLeftTime") ?: s.remainingSec,
                message = errMsg ?: if (o.has("err")) null else s.message,
            )
        }
    }
}
