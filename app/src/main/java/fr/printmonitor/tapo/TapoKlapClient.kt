package fr.printmonitor.tapo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class TapoException(message: String) : Exception(message)

/**
 * Client local pour prises Tapo (P100 / P105 / P110 / P115…) — protocole KLAP.
 *
 * IMPORTANT (firmwares 1.4.x et plus récents) : TP-Link a introduit le protocole TPAP, utilisé
 * tant que l'option « Compatibilité tierce » est désactivée. Pour que ce client fonctionne :
 *   Appli Tapo › Moi › Services tiers › Compatibilité tierce → ACTIVÉE.
 * Sinon le handshake échoue (HTTP 403).
 */
class TapoKlapClient(
    private val ip: String,
    email: String,
    password: String,
    private val http: OkHttpClient,
) {
    private val authHash = sha256(sha1(email.toByteArray()) + sha1(password.toByteArray()))
    private val mutex = Mutex()
    private var session: Session? = null

    private class Session(
        val key: ByteArray,
        val ivBase: ByteArray,
        var seq: Int,
        val sig: ByteArray,
        val cookie: String,
        val expiresAt: Long,
    )

    suspend fun getDeviceInfo(): JSONObject = request("get_device_info")
    suspend fun getEnergyUsage(): JSONObject = request("get_energy_usage")
    suspend fun setOn(on: Boolean) {
        request("set_device_info", JSONObject().put("device_on", on))
    }

    suspend fun request(method: String, params: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                doRequest(method, params)
            } catch (e: TapoException) {
                if (e.message?.startsWith("HTTP 40") == true) { // session expirée → nouveau handshake
                    session = null
                    doRequest(method, params)
                } else throw e
            }
        }
    }

    private fun doRequest(method: String, params: JSONObject?): JSONObject {
        val s = session?.takeIf { System.currentTimeMillis() < it.expiresAt } ?: handshake().also { session = it }
        s.seq += 1
        val seqBytes = ByteBuffer.allocate(4).putInt(s.seq).array()
        val iv = s.ivBase + seqBytes

        val payload = JSONObject().put("method", method)
        if (params != null) payload.put("params", params)
        val cipherText = aes(Cipher.ENCRYPT_MODE, s.key, iv, payload.toString().toByteArray())
        val signature = sha256(s.sig + seqBytes + cipherText)

        val req = Request.Builder()
            .url("http://$ip/app/request?seq=${s.seq}")
            .header("Cookie", s.cookie)
            .post((signature + cipherText).toRequestBody(OCTET))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw TapoException("HTTP ${resp.code}")
            val body = resp.body!!.bytes()
            if (body.size <= 32) throw TapoException("Réponse vide")
            val plain = aes(Cipher.DECRYPT_MODE, s.key, iv, body.copyOfRange(32, body.size))
            val json = JSONObject(String(plain))
            val code = json.optInt("error_code", 0)
            if (code != 0) throw TapoException("Erreur Tapo $code")
            return json.optJSONObject("result") ?: JSONObject()
        }
    }

    private fun handshake(): Session {
        val localSeed = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val (remoteSeed, cookie) = http.newCall(
            Request.Builder().url("http://$ip/app/handshake1").post(localSeed.toRequestBody(OCTET)).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw TapoException(
                "Handshake refusé (HTTP ${resp.code}). Activer « Compatibilité tierce » dans l'appli Tapo."
            )
            val body = resp.body!!.bytes()
            if (body.size < 48) throw TapoException("Handshake invalide")
            val remote = body.copyOfRange(0, 16)
            val serverHash = body.copyOfRange(16, 48)
            if (!serverHash.contentEquals(sha256(localSeed + remote + authHash))) {
                throw TapoException("Identifiants Tapo incorrects (e-mail / mot de passe du compte Tapo)")
            }
            val c = resp.headers("Set-Cookie").firstOrNull { it.startsWith("TP_SESSIONID") }
                ?.substringBefore(';') ?: throw TapoException("Cookie de session absent")
            remote to c
        }

        http.newCall(
            Request.Builder().url("http://$ip/app/handshake2")
                .header("Cookie", cookie)
                .post(sha256(remoteSeed + localSeed + authHash).toRequestBody(OCTET))
                .build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw TapoException("Handshake2 refusé (HTTP ${resp.code})")
        }

        val base = localSeed + remoteSeed + authHash
        val ivFull = sha256("iv".toByteArray() + base)
        return Session(
            key = sha256("lsk".toByteArray() + base).copyOf(16),
            ivBase = ivFull.copyOf(12),
            seq = ByteBuffer.wrap(ivFull, 28, 4).int,
            sig = sha256("ldk".toByteArray() + base).copyOf(28),
            cookie = cookie,
            expiresAt = System.currentTimeMillis() + 20 * 60 * 60 * 1000L, // TIMEOUT annoncé : 24 h
        )
    }

    private companion object {
        val OCTET = "application/octet-stream".toMediaType()
        fun sha1(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(b)
        fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
        fun aes(mode: Int, key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
            Cipher.getInstance("AES/CBC/PKCS5Padding").run {
                init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(data)
            }
    }
}
