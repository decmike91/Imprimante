package fr.printmonitor.printers

import fr.printmonitor.data.PrintStatus
import fr.printmonitor.data.PrinterState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

interface PrinterClient {
    fun start()
    fun stop()
    fun pause()
    fun resume()
    fun cancel()
}

/**
 * Connexion WebSocket persistante avec reconnexion automatique (backoff 2 s → 30 s)
 * et keep-alive applicatif toutes les 10 s.
 */
abstract class WsPrinterClient(
    protected val target: MutableStateFlow<PrinterState>,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) : PrinterClient {

    protected abstract val url: String
    protected abstract fun onOpened()
    protected abstract fun onText(text: String)
    protected open fun keepAlive() {}

    @Volatile private var ws: WebSocket? = null
    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val closed = CompletableDeferred<Unit>()
                val opened = AtomicBoolean(false)
                val socket = http.newWebSocket(
                    Request.Builder().url(url).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            opened.set(true)
                            target.update { if (it.status == PrintStatus.OFFLINE) it.copy(status = PrintStatus.IDLE, message = null) else it }
                            onOpened()
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try { onText(text) } catch (_: Exception) { /* trame inattendue : ignorée */ }
                        }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(1000, null)
                        }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            closed.complete(Unit)
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            target.update { it.copy(message = t.message ?: "Connexion impossible") }
                            closed.complete(Unit)
                        }
                    })
                ws = socket
                val ka = launch {
                    while (isActive) { delay(10_000); keepAlive() }
                }
                try {
                    closed.await()
                } finally {
                    ka.cancel()
                    socket.cancel()
                    ws = null
                }
                target.update { it.copy(status = PrintStatus.OFFLINE) }
                if (opened.get()) backoff = 2_000L
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        ws?.close(1000, null)
        ws = null
    }

    protected fun send(text: String): Boolean = ws?.send(text) ?: false
}

internal fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

internal fun JSONObject.int(key: String): Int? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }?.toInt() else null

internal fun JSONObject.lng(key: String): Long? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() }?.toLong() else null
