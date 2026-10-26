package fr.printmonitor.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import fr.printmonitor.MonitorHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream

/**
 * Affiche un flux MJPEG (multipart/x-mixed-replace). Les images JPEG sont repérées par leurs
 * marqueurs FFD8…FFD9, ce qui fonctionne quel que soit le « boundary » utilisé par la caméra.
 * Le flux est fermé dès que la vue quitte l'écran (important pour l'Elegoo : 1 seul flux).
 */
@Composable
fun MjpegView(url: String, modifier: Modifier = Modifier) {
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    readStream(url) { frame = it; error = null }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Caméra indisponible : ${e.message}"
                }
                delay(3_000)
            }
        }
    }

    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val f = frame
        if (f != null) {
            Image(f, contentDescription = "Caméra", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(
                error ?: "Connexion à la caméra…",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private suspend fun readStream(url: String, onFrame: (ImageBitmap) -> Unit) {
    val ctx = currentCoroutineContext()
    MonitorHub.streamHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
        if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
        val input = resp.body!!.byteStream().buffered(64 * 1024)
        val buf = ByteArrayOutputStream(256 * 1024)
        var inFrame = false
        var prev = -1
        var lastEmit = 0L
        while (true) {
            val b = input.read()
            if (b < 0) break
            if (!inFrame) {
                if (prev == 0xFF && b == 0xD8) {
                    inFrame = true
                    buf.reset(); buf.write(0xFF); buf.write(0xD8)
                }
            } else {
                buf.write(b)
                if (prev == 0xFF && b == 0xD9) {
                    inFrame = false
                    ctx.ensureActive()
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 80) { // ~12 i/s max pour ménager la batterie
                        lastEmit = now
                        val bytes = buf.toByteArray()
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { onFrame(it.asImageBitmap()) }
                    }
                }
            }
            prev = b
        }
    }
}
