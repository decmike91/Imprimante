package fr.printmonitor.data

import android.content.Context

enum class PrinterType { ELEGOO_CC, CREALITY_K1 }

enum class PrinterId(val type: PrinterType, val defaultName: String, val key: String) {
    ELEGOO(PrinterType.ELEGOO_CC, "Centauri Carbon 1", "elegoo"),
    ELEGOO2(PrinterType.ELEGOO_CC, "Centauri Carbon 2", "elegoo2"),
    K1(PrinterType.CREALITY_K1, "Creality K1", "k1"),
}

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
    /** Page caméra de secours (WebRTC Creality, port 8000) si le MJPEG ne répond pas */
    val cameraFallbackUrl: String? = null,
    val message: String? = null,
    /** null = état inconnu */
    val lightOn: Boolean? = null,
    /** true = fin de filament détectée ; null = capteur non exposé par l'imprimante */
    val filamentRunout: Boolean? = null,
)

data class PlugState(
    val configured: Boolean = false,
    val reachable: Boolean = false,
    val on: Boolean? = null,
    val powerW: Double? = null,
    val todayWh: Double? = null,
    val error: String? = null,
)

data class PrinterConfig(
    /** Nom affiché (vide = nom par défaut) */
    val name: String = "",
    val ip: String = "",
    /** Vide = URL caméra par défaut du modèle */
    val cameraUrl: String = "",
    val plugIp: String = "",
    val autoOff: Boolean = false,
)

data class AppConfig(
    val printers: Map<PrinterId, PrinterConfig> = emptyMap(),
    val tapoEmail: String = "",
    val tapoPassword: String = "",
    val backgroundMonitoring: Boolean = true,
) {
    fun printer(id: PrinterId) = printers[id] ?: PrinterConfig()
    fun displayName(id: PrinterId) = printer(id).name.ifBlank { id.defaultName }
    fun with(id: PrinterId, pc: PrinterConfig) = copy(printers = printers + (id to pc))
    val isEmpty get() = PrinterId.entries.all { printer(it).ip.isBlank() }
}

object ConfigStore {
    private const val PREFS = "printmonitor"

    fun load(ctx: Context): AppConfig {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fun str(k: String, legacy: String? = null) =
            p.getString(k, null) ?: legacy?.let { p.getString(it, null) } ?: ""
        val printers = PrinterId.entries.associateWith { id ->
            val k = id.key
            val legacySuffix = k.replaceFirstChar { it.uppercase() } // ex. plugElegooIp, autoOffK1 (v1.0)
            PrinterConfig(
                name = str("${k}Name"),
                ip = str("${k}Ip"),
                cameraUrl = str("${k}CameraUrl"),
                plugIp = str("${k}PlugIp", "plug${legacySuffix}Ip"),
                autoOff = p.getBoolean("${k}AutoOff", p.getBoolean("autoOff$legacySuffix", false)),
            )
        }
        return AppConfig(
            printers = printers,
            tapoEmail = str("tapoEmail"),
            tapoPassword = str("tapoPassword"),
            backgroundMonitoring = p.getBoolean("backgroundMonitoring", true),
        )
    }

    fun save(ctx: Context, c: AppConfig) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        PrinterId.entries.forEach { id ->
            val pc = c.printer(id)
            val k = id.key
            e.putString("${k}Name", pc.name.trim())
                .putString("${k}Ip", pc.ip.trim())
                .putString("${k}CameraUrl", pc.cameraUrl.trim())
                .putString("${k}PlugIp", pc.plugIp.trim())
                .putBoolean("${k}AutoOff", pc.autoOff)
        }
        e.putString("tapoEmail", c.tapoEmail.trim())
            .putString("tapoPassword", c.tapoPassword)
            .putBoolean("backgroundMonitoring", c.backgroundMonitoring)
            .apply()
    }
}
