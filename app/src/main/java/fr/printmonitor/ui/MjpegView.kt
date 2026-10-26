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
/**
 * Caméra : flux MJPEG, ou page WebRTC (Creality, port 8000) affichée dans une WebView.
 * Si le MJPEG échoue sans jamais avoir affiché d'image et qu'une URL de secours existe,
 * on bascule automatiquement sur la page de secours.
 */
@Composable
fun CameraView(url: String, fallbackUrl: String?, modifier: Modifier = Modifier) {
    var useFallback by remember(url, fallbackUrl) { mutableStateOf(false) }
    val pageUrl = when {
        isWebPage(url) -> url
        useFallback && fallbackUrl != null -> fallbackUrl
        else -> null
    }
    if (pageUrl != null) {
        WebCameraView(pageUrl, modifier)
    } else {
        MjpegView(url, modifier, onFirstFailure = if (fallbackUrl != null) ({ useFallback = true }) else null)
    }
}

/** Une URL qui n'a pas l'air d'un flux MJPEG est traitée comme une page web (ex. http://IP:8000/). */
private fun isWebPage(url: String): Boolean {
    val u = url.lowercase()
    return !(u.contains("action=stream") || u.contains("/video") || u.contains("mjpg") || u.contains("mjpeg") || u.contains("stream"))
}

@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCameraView(url: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(url)
                }
            },
            update = { if (it.url != url && it.originalUrl != url) it.loadUrl(url) },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}

@Composable
fun MjpegView(url: String, modifier: Modifier = Modifier, onFirstFailure: (() -> Unit)? = null) {
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
                    if (frame == null && onFirstFailure != null) {
                        withContext(Dispatchers.Main) { onFirstFailure() }
                        return@withContext
                    }
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
