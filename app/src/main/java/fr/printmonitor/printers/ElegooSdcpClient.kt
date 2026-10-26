package fr.printmonitor.printers

import fr.printmonitor.data.PrintStatus
import fr.printmonitor.data.PrinterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID

/**
 * Elegoo Centauri Carbon (CC1) — protocole SDCP v3 en local, sans cloud.
 *  - WebSocket : ws://IP:3030/websocket (5 connexions max côté imprimante)
 *  - Caméra    : MJPEG http://IP:3031/video (UN seul flux simultané : fermer la vue caméra
 *                d'Elegoo Slicer / de l'appli Elegoo si l'image ne vient pas)
 *
 * Note : la Centauri Carbon 2 (CC2) utilise MQTT (port 1883) et n'est pas couverte ici.
 */
class ElegooSdcpClient(
    private val ip: String,
    private val cameraOverride: String,
    target: MutableStateFlow<PrinterState>,
    http: OkHttpClient,
    scope: CoroutineScope,
) : WsPrinterClient(target, http, scope) {

    override val url get() = "ws://$ip:3030/websocket"

    @Volatile private var mainboardId = ""

    override fun onOpened() {
        target.update { it.copy(cameraUrl = cameraOverride.ifBlank { "http://$ip:3031/video" }) }
        sendCmd(CMD_ATTRIBUTES)
        sendCmd(CMD_STATUS)
        sendCmd(CMD_VIDEO, JSONObject().put("Enable", 1))
    }

    override fun keepAlive() {
        send("ping")
        sendCmd(CMD_STATUS)
    }

    override fun pause() { sendCmd(CMD_PAUSE) }
    override fun resume() { sendCmd(CMD_RESUME) }
    override fun cancel() { sendCmd(CMD_STOP) }

    /** Cmd 403 avec {"LightStatus":{"SecondLight":0|1}} — éclairage LED du caisson. */
    override fun setLight(on: Boolean) {
        sendCmd(CMD_PARAMS, JSONObject().put("LightStatus", JSONObject().put("SecondLight", if (on) 1 else 0)))
        target.update { it.copy(lightOn = on) } // affichage immédiat, confirmé au prochain statut
        sendCmd(CMD_STATUS)
    }

    private fun sendCmd(cmd: Int, data: JSONObject = JSONObject()) {
        val inner = JSONObject()
            .put("Cmd", cmd)
            .put("Data", data)
            .put("RequestID", UUID.randomUUID().toString().replace("-", ""))
            .put("MainboardID", mainboardId)
            .put("TimeStamp", System.currentTimeMillis() / 1000)
            .put("From", 1)
        val msg = JSONObject()
            .put("Id", "")
            .put("Data", inner)
            .put("Topic", "sdcp/request/$mainboardId")
        send(msg.toString())
    }

    override fun onText(text: String) {
        if (!text.trimStart().startsWith("{")) return // "pong"
        val o = JSONObject(text)

        o.optString("MainboardID").takeIf { it.isNotEmpty() }?.let { mainboardId = it }
        o.optJSONObject("Data")?.optString("MainboardID")?.takeIf { it.isNotEmpty() }?.let { mainboardId = it }
        o.optJSONObject("Attributes")?.optString("MainboardID")?.takeIf { it.isNotEmpty() }?.let { mainboardId = it }

        val topic = o.optString("Topic")
        if (topic.startsWith("sdcp/error")) {
            val code = o.optJSONObject("Data")?.optJSONObject("Data")?.optInt("ErrorCode")
            target.update { it.copy(status = PrintStatus.ERROR, message = "Erreur imprimante (code $code)") }
            return
        }

        val st = o.optJSONObject("Status") ?: return
        val cur = st.optJSONArray("CurrentStatus")?.let { a -> (0 until a.length()).map { a.optInt(it) } }
            ?: listOf(st.optInt("CurrentStatus", 0))
        val pi = st.optJSONObject("PrintInfo")
        val piStatus = pi?.optInt("Status", -1) ?: -1

        // CurrentStatus : 0 = inactive, 1 = impression ; PrintInfo.Status : 5/6 pause, 7/8 arrêt, 9 terminé
        val status = when {
            piStatus == 5 || piStatus == 6 -> PrintStatus.PAUSED
            1 in cur && piStatus != 9 && piStatus != 8 -> PrintStatus.PRINTING
            piStatus == 9 -> PrintStatus.FINISHED
            piStatus == 7 || piStatus == 8 -> PrintStatus.STOPPED
            else -> PrintStatus.IDLE
        }

        target.update { s ->
            val cur0 = pi?.lng("CurrentTicks")
            val tot = pi?.lng("TotalTicks")
            s.copy(
                status = status,
                nozzle = st.dbl("TempOfNozzle") ?: s.nozzle,
                nozzleTarget = st.dbl("TempTargetNozzle") ?: s.nozzleTarget,
                bed = st.dbl("TempOfHotbed") ?: s.bed,
                bedTarget = st.dbl("TempTargetHotbed") ?: s.bedTarget,
                chamber = st.dbl("TempOfBox") ?: s.chamber,
                lightOn = st.optJSONObject("LightStatus")?.int("SecondLight")?.let { it == 1 } ?: s.lightOn,
                // La CC1 n'expose pas l'état du capteur : une fin de filament se traduit par une pause.
                filamentRunout = null,
                progress = pi?.int("Progress") ?: s.progress,
                fileName = pi?.optString("Filename")?.takeIf { it.isNotBlank() } ?: s.fileName,
                layer = pi?.int("CurrentLayer") ?: s.layer,
                totalLayers = pi?.int("TotalLayer") ?: s.totalLayers,
                elapsedSec = cur0 ?: s.elapsedSec,
                remainingSec = if (cur0 != null && tot != null && tot >= cur0) tot - cur0 else s.remainingSec,
                message = if (status == PrintStatus.ERROR) s.message else null,
            )
        }
    }

    private companion object {
        const val CMD_STATUS = 0
        const val CMD_ATTRIBUTES = 1
        const val CMD_PAUSE = 129
        const val CMD_STOP = 130
        const val CMD_RESUME = 131
        const val CMD_VIDEO = 386
        const val CMD_PARAMS = 403
    }
}
