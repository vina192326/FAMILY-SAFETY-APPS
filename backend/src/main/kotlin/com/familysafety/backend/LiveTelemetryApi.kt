package com.familysafety.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable data class KidsTelemetryRequest(val capturedAt: String, val payloadJson: String)
private val telemetryRandom = SecureRandom()
private val allowedTelemetryFields = setOf("latitude", "longitude", "accuracyMeters", "batteryPercent", "charging", "networkType", "foregroundAppLabel", "screenState", "locationPermission", "usageAccessPermission")
private fun telemetryEncrypt(id: UUID, raw: ByteArray): Pair<ByteArray, ByteArray> {
    val key = Base64.getDecoder().decode(familyEnv("HISTORY_ENCRYPTION_KEY_BASE64"))
    require(key.size == 32)
    val iv = ByteArray(12).also(telemetryRandom::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(id.toString().toByteArray())
    return iv to cipher.doFinal(raw)
}
private fun telemetryDecrypt(id: UUID, iv: ByteArray, raw: ByteArray): String {
    val key = Base64.getDecoder().decode(familyEnv("HISTORY_ENCRYPTION_KEY_BASE64"))
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(id.toString().toByteArray())
    return cipher.doFinal(raw).toString(Charsets.UTF_8)
}
fun Route.liveTelemetryRoutes() {
    route("/v1") {
        post("/kids/telemetry") {
            val kidsId = call.deviceId("KIDS") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = runCatching { call.receive<KidsTelemetryRequest>() }.getOrNull()
                ?: return@post call.familyFail("TELEMETRY_INVALID")
            if (req.payloadJson.toByteArray().size !in 1..65536) return@post call.familyFail("TELEMETRY_SIZE_INVALID", HttpStatusCode.PayloadTooLarge)
            val payload = runCatching { Json.parseToJsonElement(req.payloadJson).jsonObject }.getOrNull()
                ?: return@post call.familyFail("TELEMETRY_JSON_INVALID")
            if (payload.keys.any { it !in allowedTelemetryFields }) return@post call.familyFail("TELEMETRY_FIELD_NOT_ALLOWED")
            val battery = runCatching { payload["batteryPercent"]?.jsonPrimitive?.intOrNull }.getOrNull()
            if (battery != null && battery !in 0..100) return@post call.familyFail("BATTERY_VALUE_INVALID")
            val latitude = runCatching { payload["latitude"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
            val longitude = runCatching { payload["longitude"]?.jsonPrimitive?.doubleOrNull }.getOrNull()
            if (latitude != null && latitude !in -90.0..90.0) return@post call.familyFail("LATITUDE_INVALID")
            if (longitude != null && longitude !in -180.0..180.0) return@post call.familyFail("LONGITUDE_INVALID")
            val captured = runCatching { java.time.Instant.parse(req.capturedAt) }.getOrNull()
                ?: return@post call.familyFail("TELEMETRY_TIMESTAMP_INVALID")
            val parentId = familyDb.connection.use { c ->
                c.prepareStatement("""SELECT parent_device_id FROM device_bindings b JOIN permits p ON p.id=b.permit_id
                    WHERE b.kids_device_id=? AND b.revoked_at IS NULL AND p.revoked_at IS NULL AND p.expires_at>now()""").use { p ->
                    p.setObject(1,kidsId); p.executeQuery().use { r -> if(r.next()) r.getObject(1,UUID::class.java) else null }
                }
            } ?: return@post call.familyFail("NO_VALID_BINDING", HttpStatusCode.Forbidden)
            val encrypted = runCatching { telemetryEncrypt(kidsId, req.payloadJson.toByteArray()) }.getOrElse {
                return@post call.familyFail("TELEMETRY_ENCRYPTION_UNAVAILABLE", HttpStatusCode.ServiceUnavailable)
            }
            familyDb.connection.use { c ->
                c.prepareStatement("""INSERT INTO kids_live_telemetry(kids_device_id,parent_device_id,captured_at,payload_iv,payload_ciphertext)
                    VALUES(?,?,?,?,?) ON CONFLICT(kids_device_id) DO UPDATE SET parent_device_id=EXCLUDED.parent_device_id,
                    captured_at=EXCLUDED.captured_at,payload_iv=EXCLUDED.payload_iv,payload_ciphertext=EXCLUDED.payload_ciphertext,updated_at=now()
                    WHERE kids_live_telemetry.captured_at<=EXCLUDED.captured_at""").use { p ->
                    p.setObject(1,kidsId); p.setObject(2,parentId); p.setObject(3,captured)
                    p.setBytes(4,encrypted.first); p.setBytes(5,encrypted.second); p.executeUpdate()
                }
            }
            call.respond(mapOf("status" to "RECORDED"))
        }
        get("/parent/telemetry") {
            val parentId = call.deviceId("PARENT") ?: return@get call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val kidsId = call.request.queryParameters["kidsDeviceId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.familyFail("KIDS_DEVICE_ID_REQUIRED")
            val row = familyDb.connection.use { c ->
                c.prepareStatement("""SELECT t.captured_at,t.payload_iv,t.payload_ciphertext FROM kids_live_telemetry t
                    JOIN device_bindings b ON b.kids_device_id=t.kids_device_id AND b.parent_device_id=t.parent_device_id
                    JOIN permits p ON p.id=b.permit_id
                    WHERE t.kids_device_id=? AND t.parent_device_id=? AND b.revoked_at IS NULL AND p.revoked_at IS NULL AND p.expires_at>now()""").use { p ->
                    p.setObject(1,kidsId); p.setObject(2,parentId); p.executeQuery().use { r ->
                        if(r.next()) Triple(r.getObject(1).toString(),r.getBytes(2),r.getBytes(3)) else null
                    }
                }
            } ?: return@get call.familyFail("TELEMETRY_NOT_AVAILABLE", HttpStatusCode.NotFound)
            val payload = runCatching { telemetryDecrypt(kidsId,row.second,row.third) }.getOrElse {
                return@get call.familyFail("TELEMETRY_DECRYPT_FAILED", HttpStatusCode.InternalServerError)
            }
            call.respond(mapOf("kidsDeviceId" to kidsId.toString(),"capturedAt" to row.first,"payloadJson" to payload))
        }
    }
}
