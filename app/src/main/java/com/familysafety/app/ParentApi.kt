package com.familysafety.app

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ParentApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("family_parent", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("family_parent_token_key", null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        gen.init(android.security.keystore.KeyGenParameterSpec.Builder(
            "family_parent_token_key",
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }

    private fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray())
        prefs.edit().putString("token_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun token(): String? = runCatching {
        val iv = Base64.decode(prefs.getString("token_iv", null), Base64.NO_WRAP)
        val data = Base64.decode(prefs.getString("token_data", null), Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            doFinal(data).toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                if (authenticated) token()?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("Server returned HTTP $code: $response")
                response
            } finally { connection.disconnect() }
        }

    suspend fun bootstrap(): String {
        token()?.let { return prefs.getString("device_id", "").orEmpty() }
        val fingerprint = prefs.getString("install_fingerprint", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_fingerprint", it).apply()
        }
        val response = JSONObject(request("/v1/parent/bootstrap", "POST",
            JSONObject().put("installFingerprint", fingerprint), authenticated = false))
        saveToken(response.getString("deviceToken"))
        prefs.edit().putString("device_id", response.getString("deviceId")).apply()
        return response.getString("deviceId")
    }

    suspend fun devices(): JSONArray = JSONArray(request("/v1/parent/devices"))

    suspend fun validatePairingOtp(otp: String): JSONObject =
        JSONObject(request("/v1/parent/pairing/validate", "POST", JSONObject().put("otp", otp)))

    suspend fun plans(pairingSessionId: String): JSONArray =
        JSONArray(request("/v1/parent/plans?pairingSessionId=$pairingSessionId"))

    suspend fun createPaymentOrder(planId: String, pairingSessionId: String): JSONObject =
        JSONObject(request("/v1/parent/payments/orders", "POST",
            JSONObject().put("planId", planId).put("pairingSessionId", pairingSessionId)))

    suspend fun liveTelemetry(kidsDeviceId: String): JSONObject =
        JSONObject(request("/v1/parent/telemetry?kidsDeviceId=$kidsDeviceId"))

    suspend fun permissionStatus(kidsDeviceId: String): JSONArray =
        JSONArray(request("/v1/parent/permissions?kidsDeviceId=$kidsDeviceId"))

    suspend fun history(kidsDeviceId: String): JSONArray =
        JSONArray(request("/v1/parent/history?kidsDeviceId=$kidsDeviceId"))
    suspend fun deleteHistory(recordId: String): String =
        request("/v1/parent/history/$recordId", "DELETE")
    suspend fun saveHistoryPreferences(enabled: Boolean, retentionHours: Int): JSONObject =
        JSONObject(request("/v1/parent/history/preferences", "PUT",
            JSONObject().put("savePdfBeforeDelete", enabled).put("retentionHours", retentionHours)))
    suspend fun historyPreferences(): JSONObject = JSONObject(request("/v1/parent/history/preferences"))
}
