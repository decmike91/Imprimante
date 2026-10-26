package fr.printmonitor.data

import android.content.Context

enum class PrinterId { ELEGOO, K1 }

enum class PrintStatus(val label: String) {
    OFFLINE("Hors ligne"),
    IDLE("Inactive"),
    PRINTING("Impression"),
    PAUSED("En pause"),
    FINISHED("Terminée"),
    STOPPED("Arrêtée"),
    ERROR("Erreur"),
}

data class PrinterState(
    val name: String,
    val status: PrintStatus = PrintStatus.OFFLINE,
    val nozzle: Double? = null,
    val nozzleTarget: Double? = null,
    val bed: Double? = null,
    val bedTarget: Double? = null,
    val chamber: Double? = null,
    val progress: Int? = null,
    val fileName: String? = null,
    val layer: Int? = null,
    val totalLayers: Int? = null,
    val elapsedSec: Long? = null,
    val remainingSec: Long? = null,
    val cameraUrl: String? = null,
    val message: String? = null,
)

data class PlugState(
    val configured: Boolean = false,
    val reachable: Boolean = false,
    val on: Boolean? = null,
    val powerW: Double? = null,
    val todayWh: Double? = null,
    val error: String? = null,
)

data class AppConfig(
    val elegooIp: String = "",
    val k1Ip: String = "",
    /** Laisser vide pour http://<ip K1>:8080/?action=stream */
    val k1CameraUrl: String = "",
    val plugElegooIp: String = "",
    val plugK1Ip: String = "",
    val tapoEmail: String = "",
    val tapoPassword: String = "",
    val autoOffElegoo: Boolean = false,
    val autoOffK1: Boolean = false,
    val backgroundMonitoring: Boolean = true,
) {
    val isEmpty get() = elegooIp.isBlank() && k1Ip.isBlank()
}

object ConfigStore {
    private const val PREFS = "printmonitor"

    fun load(ctx: Context): AppConfig {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppConfig(
            elegooIp = p.getString("elegooIp", "")!!,
            k1Ip = p.getString("k1Ip", "")!!,
            k1CameraUrl = p.getString("k1CameraUrl", "")!!,
            plugElegooIp = p.getString("plugElegooIp", "")!!,
            plugK1Ip = p.getString("plugK1Ip", "")!!,
            tapoEmail = p.getString("tapoEmail", "")!!,
            tapoPassword = p.getString("tapoPassword", "")!!,
            autoOffElegoo = p.getBoolean("autoOffElegoo", false),
            autoOffK1 = p.getBoolean("autoOffK1", false),
            backgroundMonitoring = p.getBoolean("backgroundMonitoring", true),
        )
    }

    fun save(ctx: Context, c: AppConfig) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("elegooIp", c.elegooIp.trim())
            .putString("k1Ip", c.k1Ip.trim())
            .putString("k1CameraUrl", c.k1CameraUrl.trim())
            .putString("plugElegooIp", c.plugElegooIp.trim())
            .putString("plugK1Ip", c.plugK1Ip.trim())
            .putString("tapoEmail", c.tapoEmail.trim())
            .putString("tapoPassword", c.tapoPassword)
            .putBoolean("autoOffElegoo", c.autoOffElegoo)
            .putBoolean("autoOffK1", c.autoOffK1)
            .putBoolean("backgroundMonitoring", c.backgroundMonitoring)
            .apply()
    }
}
