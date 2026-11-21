package com.familysafety.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val historyRandom = SecureRandom()
private const val HISTORY_MAX_BYTES = 1_048_576

@Serializable
data class HistoryIngestRequest(
    val recordType: String,
    val occurredAt: String,
    val payloadJson: String
)

@Serializable
data class HistoryRecordResponse(
    val id: String,
    val kidsDeviceId: String,
    val recordType: String,
    val occurredAt: String,
    val viewedAt: String,
    val deleteAt: String,
    val payloadJson: String
)

@Serializable
data class HistoryPreferenceResponse(val savePdfBeforeDelete: Boolean, val retentionHours: Int = 168)

@Serializable
data class HistoryPreferenceRequest(val savePdfBeforeDelete: Boolean, val retentionHours: Int = 168)

@Serializable
data class HistoryDeleteResponse(val status: String, val deletedId: String)

private fun historyKey(): ByteArray {
    val raw = familyEnv("HISTORY_ENCRYPTION_KEY_BASE64")
    require(raw.isNotBlank()) { "HISTORY_ENCRYPTION_KEY_BASE64 is not configured" }
    val key = Base64.getDecoder().decode(raw)
    require(key.size == 32) { "HISTORY_ENCRYPTION_KEY_BASE64 must decode to 32 bytes" }
    return key
}

private fun encryptHistory(id: UUID, plain: ByteArray): Pair<ByteArray, ByteArray> {
    val iv = ByteArray(12).also(historyRandom::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(historyKey(), "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(id.toString().toByteArray())
    return iv to cipher.doFinal(plain)
}

private fun decryptHistory(id: UUID, iv: ByteArray, encrypted: ByteArray): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(historyKey(), "AES"), GCMParameterSpec(128, iv))
    cipher.updateAAD(id.toString().toByteArray())
    return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
}

fun Route.historyApiRoutes() {
    route("/v1") {
        post("/kids/history") {
            val kidsId = call.deviceId("KIDS")
                ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = runCatching { call.receive<HistoryIngestRequest>() }.getOrNull()
                ?: return@post call.familyFail("INVALID_HISTORY_REQUEST")
            if (req.recordType !in setOf("LOCATION", "APP_USAGE", "TELEMETRY", "MEDIA"))
                return@post call.familyFail("HISTORY_TYPE_INVALID")
            if (req.payloadJson.toByteArray().size !in 1..HISTORY_MAX_BYTES)
                return@post call.familyFail("HISTORY_PAYLOAD_SIZE_INVALID", HttpStatusCode.PayloadTooLarge)
            val occurred = runCatching { Instant.parse(req.occurredAt) }.getOrNull()
                ?: return@post call.familyFail("HISTORY_TIMESTAMP_INVALID")
            val parentId = familyDb.connection.use { c ->
                c.prepareStatement(
                    """SELECT b.parent_device_id FROM device_bindings b
                       JOIN permits p ON p.id=b.permit_id
                       WHERE b.kids_device_id=? AND b.revoked_at IS NULL
                         AND p.revoked_at IS NULL AND p.expires_at>now()"""
                ).use { p ->
                    p.setObject(1, kidsId)
                    p.executeQuery().use { r -> if (r.next()) r.getObject(1, UUID::class.java) else null }
                }
            } ?: return@post call.familyFail("NO_VALID_BINDING", HttpStatusCode.Forbidden)
            val retentionHours = familyDb.connection.use { c ->
                c.prepareStatement("SELECT offline_retention_hours FROM parent_history_preferences WHERE parent_device_id=?").use { p ->
                    p.setObject(1, parentId); p.executeQuery().use { r -> if (r.next()) r.getInt(1) else 168 }
                }
            }
            val id = UUID.randomUUID()
            val encrypted = runCatching { encryptHistory(id, req.payloadJson.toByteArray()) }.getOrElse {
                return@post call.familyFail("HISTORY_ENCRYPTION_UNAVAILABLE", HttpStatusCode.ServiceUnavailable)
            }
            familyDb.connection.use { c ->
                c.prepareStatement(
                    """INSERT INTO history_records
                       (id,kids_device_id,parent_device_id,record_type,occurred_at,delete_at,payload_iv,payload_ciphertext)
                       VALUES(?,?,?,?,?,now()+(? * interval '1 hour'),?,?)"""
                ).use { p ->
                    p.setObject(1, id); p.setObject(2, kidsId); p.setObject(3, parentId)
                    p.setString(4, req.recordType); p.setObject(5, OffsetDateTime.ofInstant(occurred, ZoneOffset.UTC))
                    p.setInt(6, retentionHours); p.setBytes(7, encrypted.first); p.setBytes(8, encrypted.second); p.executeUpdate()
                }
            }
            call.respond(HttpStatusCode.Accepted, mapOf("status" to "STORED", "recordId" to id.toString(), "offlineRetentionHours" to retentionHours))
        }

        get("/parent/history") {
            val parentId = call.deviceId("PARENT")
                ?: return@get call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val kidsId = call.request.queryParameters["kidsDeviceId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.familyFail("KIDS_DEVICE_ID_REQUIRED")
            val records = runCatching {
                familyDb.connection.use { c ->
                    c.autoCommit = false
                    try {
                        val ownsDevice = c.prepareStatement(
                            """SELECT 1 FROM device_bindings b JOIN permits p ON p.id=b.permit_id
                               WHERE b.kids_device_id=? AND b.parent_device_id=? AND b.revoked_at IS NULL
                                 AND p.revoked_at IS NULL"""
                        ).use { p ->
                            p.setObject(1, kidsId); p.setObject(2, parentId)
                            p.executeQuery().use { it.next() }
                        }
                        if (!ownsDevice) { c.rollback(); return@use null }
                        c.prepareStatement(
                            """UPDATE history_records SET viewed_at=COALESCE(viewed_at,now()),
                               delete_at=CASE WHEN viewed_at IS NULL THEN now()+interval '24 hours' ELSE delete_at END
                               WHERE parent_device_id=? AND kids_device_id=? AND deleted_at IS NULL"""
                        ).use { p -> p.setObject(1, parentId); p.setObject(2, kidsId); p.executeUpdate() }
                        val result = c.prepareStatement(
                            """SELECT id,record_type,occurred_at,viewed_at,delete_at,payload_iv,payload_ciphertext
                               FROM history_records WHERE parent_device_id=? AND kids_device_id=? AND deleted_at IS NULL
                               ORDER BY occurred_at DESC"""
                        ).use { p ->
                            p.setObject(1, parentId); p.setObject(2, kidsId)
                            p.executeQuery().use { r ->
                                buildList {
                                    while (r.next()) {
                                        val id = r.getObject(1, UUID::class.java)
                                        add(HistoryRecordResponse(
                                            id.toString(), kidsId.toString(), r.getString(2),
                                            r.getObject(3).toString(), r.getObject(4).toString(), r.getObject(5).toString(),
                                            decryptHistory(id, r.getBytes(6), r.getBytes(7))
                                        ))
                                    }
                                }
                            }
                        }
                        c.commit()
                        result
                    } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
                }
            }.getOrElse {
                return@get call.familyFail("HISTORY_READ_FAILED", HttpStatusCode.InternalServerError)
            } ?: return@get call.familyFail("DEVICE_NOT_LINKED", HttpStatusCode.Forbidden)
            call.respond(records)
        }

        delete("/parent/history/{recordId}") {
            val parentId = call.deviceId("PARENT")
                ?: return@delete call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val id = call.parameters["recordId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.familyFail("HISTORY_ID_INVALID")
            val deleted = familyDb.connection.use { c ->
                c.autoCommit = false
                try {
                    val exists = c.prepareStatement(
                        "SELECT 1 FROM history_records WHERE id=? AND parent_device_id=? AND deleted_at IS NULL FOR UPDATE"
                    ).use { p ->
                        p.setObject(1, id); p.setObject(2, parentId); p.executeQuery().use { it.next() }
                    }
                    if (exists) {
                        c.prepareStatement(
                            "INSERT INTO history_deletion_audit(record_id,parent_device_id,reason) VALUES(?,?,'PARENT_DELETED')"
                        ).use { p -> p.setObject(1, id); p.setObject(2, parentId); p.executeUpdate() }
                        c.prepareStatement("DELETE FROM history_records WHERE id=? AND parent_device_id=?").use { p ->
                            p.setObject(1, id); p.setObject(2, parentId); p.executeUpdate()
                        }
                    }
                    c.commit()
                    exists
                } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
            }
            if (!deleted) call.familyFail("HISTORY_NOT_FOUND", HttpStatusCode.NotFound)
            else call.respond(HistoryDeleteResponse("DELETED", id.toString()))
        }

        get("/parent/history/preferences") {
            val parentId = call.deviceId("PARENT")
                ?: return@get call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val preference = familyDb.connection.use { c ->
                c.prepareStatement("SELECT save_pdf_before_delete,offline_retention_hours FROM parent_history_preferences WHERE parent_device_id=?").use { p ->
                    p.setObject(1, parentId)
                    p.executeQuery().use { r -> if (r.next()) r.getBoolean(1) to r.getInt(2) else false to 168 }
                }
            }
            call.respond(HistoryPreferenceResponse(preference.first, preference.second))
        }

        put("/parent/history/preferences") {
            val parentId = call.deviceId("PARENT")
                ?: return@put call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = runCatching { call.receive<HistoryPreferenceRequest>() }.getOrNull()
                ?: return@put call.familyFail("INVALID_PREFERENCE")
            if (req.retentionHours !in setOf(24, 168)) return@put call.familyFail("RETENTION_MUST_BE_24_OR_168_HOURS")
            familyDb.connection.use { c ->
                c.prepareStatement(
                    """INSERT INTO parent_history_preferences(parent_device_id,save_pdf_before_delete,offline_retention_hours)
                       VALUES(?,?,?) ON CONFLICT(parent_device_id) DO UPDATE
                       SET save_pdf_before_delete=EXCLUDED.save_pdf_before_delete,offline_retention_hours=EXCLUDED.offline_retention_hours,updated_at=now()"""
                ).use { p -> p.setObject(1, parentId); p.setBoolean(2, req.savePdfBeforeDelete); p.setInt(3, req.retentionHours); p.executeUpdate() }
            }
            call.respond(HistoryPreferenceResponse(req.savePdfBeforeDelete, req.retentionHours))
        }
    }
}

/** Server-authoritative deletion. Unviewed records expire after 7 days; first history read starts a non-resetting 24h timer. */
fun purgeExpiredHistoryRecords() {
    familyDb.connection.use { c ->
        c.autoCommit = false
        try {
            c.prepareStatement(
                """WITH expired AS (
                     DELETE FROM history_records WHERE delete_at<=now()
                     RETURNING id,parent_device_id
                   )
                   INSERT INTO history_deletion_audit(record_id,parent_device_id,reason)
                   SELECT id,parent_device_id,CASE WHEN true THEN 'AUTO_EXPIRED' END FROM expired"""
            ).use { it.executeUpdate() }
            c.commit()
        } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
    }
}
