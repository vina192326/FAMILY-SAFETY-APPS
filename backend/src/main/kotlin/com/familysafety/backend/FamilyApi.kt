package com.familysafety.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpHeaders
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val familyRandom = SecureRandom()
private val familyJson = Json { ignoreUnknownKeys = true }
private val familyHttp = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build()
private val familyDb by lazy {
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/familysafety"
        username = System.getenv("DATABASE_USER") ?: "familysafety"
        password = System.getenv("DATABASE_PASSWORD") ?: "CHANGE_ME"
        maximumPoolSize = 8
    })
}
private fun familyEnv(k: String) = System.getenv(k).orEmpty()
private fun familySha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
private fun familyToken() = ByteArray(32).also(familyRandom::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
private fun familyOtp() = (100000 + familyRandom.nextInt(900000)).toString()
private fun familyHashToken(s: String) = familySha(s + familyEnv("TOKEN_PEPPER"))
private fun familyHmac(secret: String, value: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
private fun familyBasicAuth() = Base64.getEncoder().encodeToString("${familyEnv("RAZORPAY_KEY_ID")}:${familyEnv("RAZORPAY_KEY_SECRET")}".toByteArray())

@Serializable data class DeviceBootstrapRequest(val installFingerprint: String)
@Serializable data class DeviceBootstrapResponse(val deviceId: String, val deviceToken: String, val pairingOtp: String? = null, val otpExpiresAt: String? = null)
@Serializable data class OtpResponse(val pairingOtp: String, val expiresAt: String)
@Serializable data class PairingOtpRequest(val otp: String)
@Serializable data class PairingSessionResponse(val pairingSessionId: String, val expiresAt: String, val kidsDeviceId: String)
@Serializable data class FamilyPlan(val id: String, val name: String, val amountPaise: Long, val currency: String, val durationValue: Int, val durationUnit: String, val historyDays: Int, val capabilities: List<String>)
@Serializable data class CreateOrderRequest(val planId: String, val pairingSessionId: String)
@Serializable data class CreateOrderResponse(val orderId: String, val gatewayOrderId: String, val amountPaise: Long, val currency: String, val keyId: String)
@Serializable data class VerifyPaymentRequest(val orderId: String, val gatewayPaymentId: String, val gatewaySignature: String)
@Serializable data class VerifyPaymentResponse(val status: String, val permitCode: String, val expiresAt: String, val kidsDeviceId: String)
@Serializable data class FamilyError(val error: String)

private fun io.ktor.server.application.ApplicationCall.deviceId(role: String): UUID? {
    val raw = request.headers[HttpHeaders.Authorization]?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim() ?: return null
    return familyDb.connection.use { c ->
        c.prepareStatement("SELECT id FROM app_devices WHERE token_hash=? AND role=?").use { p ->
            p.setString(1, familyHashToken(raw)); p.setString(2, role)
            p.executeQuery().use { r -> if (r.next()) r.getObject(1, UUID::class.java) else null }
        }
    }
}
private suspend fun io.ktor.server.application.ApplicationCall.familyFail(message: String, status: HttpStatusCode = HttpStatusCode.BadRequest) {
    respond(status, FamilyError(message))
}

fun Route.familyApiRoutes() {
    route("/v1") {
        post("/kids/bootstrap") { bootstrapFamilyDevice("KIDS") }
        post("/parent/bootstrap") { bootstrapFamilyDevice("PARENT") }

        post("/kids/pairing-otp/rotate") {
            val kidsId = call.deviceId("KIDS") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val otp = familyOtp()
            val expiry = Instant.now().plusSeconds(600)
            familyDb.connection.use { c ->
                c.prepareStatement("INSERT INTO pairing_otps(kids_device_id,otp_hash,expires_at) VALUES(?,?,?)").use { p ->
                    p.setObject(1, kidsId); p.setString(2, familySha(otp + familyEnv("TOKEN_PEPPER")))
                    p.setObject(3, OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)); p.executeUpdate()
                }
            }
            call.respond(OtpResponse(otp, expiry.toString()))
        }

        post("/parent/pairing/validate") {
            val parentId = call.deviceId("PARENT") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = runCatching { call.receive<PairingOtpRequest>() }.getOrNull()
                ?: return@post call.familyFail("OTP_INVALID", HttpStatusCode.Forbidden)
            if (!req.otp.matches(Regex("[0-9]{6}"))) return@post call.familyFail("OTP_INVALID", HttpStatusCode.Forbidden)

            val result = familyDb.connection.use { c ->
                c.autoCommit = false
                try {
                    val otpId: UUID?
                    val kidsId: UUID?
                    c.prepareStatement("SELECT id,kids_device_id FROM pairing_otps WHERE otp_hash=? AND consumed_at IS NULL AND expires_at>now() ORDER BY created_at DESC LIMIT 1 FOR UPDATE").use { p ->
                        p.setString(1, familySha(req.otp + familyEnv("TOKEN_PEPPER")))
                        p.executeQuery().use { r ->
                            if (r.next()) { otpId = r.getObject(1, UUID::class.java); kidsId = r.getObject(2, UUID::class.java) }
                            else { otpId = null; kidsId = null }
                        }
                    }
                    if (otpId == null || kidsId == null) { c.rollback(); null }
                    else {
                        c.prepareStatement("UPDATE pairing_otps SET consumed_at=now() WHERE id=?").use { p -> p.setObject(1, otpId); p.executeUpdate() }
                        val session = UUID.randomUUID()
                        val expiry = Instant.now().plusSeconds(900)
                        c.prepareStatement("INSERT INTO pairing_sessions(id,parent_device_id,kids_device_id,expires_at) VALUES(?,?,?,?)").use { p ->
                            p.setObject(1, session); p.setObject(2, parentId); p.setObject(3, kidsId)
                            p.setObject(4, OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)); p.executeUpdate()
                        }
                        c.commit()
                        PairingSessionResponse(session.toString(), expiry.toString(), kidsId.toString())
                    }
                } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
            }
            if (result == null) call.familyFail("OTP_INVALID_OR_EXPIRED", HttpStatusCode.Forbidden) else call.respond(result)
        }

        get("/parent/plans") {
            val parentId = call.deviceId("PARENT") ?: return@get call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val sessionId = call.request.queryParameters["pairingSessionId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.familyFail("PAIRING_SESSION_REQUIRED")
            val valid = familyDb.connection.use { c ->
                c.prepareStatement("SELECT 1 FROM pairing_sessions WHERE id=? AND parent_device_id=? AND expires_at>now()").use { p ->
                    p.setObject(1, sessionId); p.setObject(2, parentId); p.executeQuery().use { it.next() }
                }
            }
            if (!valid) return@get call.familyFail("PAIRING_SESSION_INVALID", HttpStatusCode.Forbidden)
            val plans = familyDb.connection.use { c ->
                c.prepareStatement("SELECT id,name,amount_paise,currency,duration_value,duration_unit,history_days,capabilities::text FROM subscription_plans WHERE active=true ORDER BY amount_paise").use { p ->
                    p.executeQuery().use { r ->
                        buildList {
                            while (r.next()) add(FamilyPlan(r.getObject(1).toString(), r.getString(2), r.getLong(3), r.getString(4), r.getInt(5), r.getString(6), r.getInt(7), familyJson.decodeFromString(r.getString(8))))
                        }
                    }
                }
            }
            call.respond(plans)
        }

        post("/parent/payments/orders") {
            val parentId = call.deviceId("PARENT") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = call.receive<CreateOrderRequest>()
            val sessionId = runCatching { UUID.fromString(req.pairingSessionId) }.getOrNull() ?: return@post call.familyFail("PAIRING_SESSION_INVALID")
            val planId = runCatching { UUID.fromString(req.planId) }.getOrNull() ?: return@post call.familyFail("PLAN_UNAVAILABLE")
            val target = familyDb.connection.use { c ->
                c.prepareStatement("SELECT s.kids_device_id,p.amount_paise,p.currency FROM pairing_sessions s JOIN subscription_plans p ON p.id=? WHERE s.id=? AND s.parent_device_id=? AND s.expires_at>now() AND p.active=true").use { p ->
                    p.setObject(1, planId); p.setObject(2, sessionId); p.setObject(3, parentId)
                    p.executeQuery().use { r -> if (r.next()) Triple(r.getObject(1, UUID::class.java), r.getLong(2), r.getString(3)) else null }
                }
            } ?: return@post call.familyFail("PLAN_OR_PAIRING_SESSION_INVALID", HttpStatusCode.Forbidden)
            val key = familyEnv("RAZORPAY_KEY_ID")
            val secret = familyEnv("RAZORPAY_KEY_SECRET")
            if (key.isBlank() || secret.isBlank()) return@post call.familyFail("PAYMENT_GATEWAY_NOT_CONFIGURED", HttpStatusCode.ServiceUnavailable)
            val localId = UUID.randomUUID()
            val payload = buildJsonObject { put("amount", target.second); put("currency", target.third); put("receipt", localId.toString()) }.toString()
            val gatewayResponse = runCatching {
                familyHttp.send(HttpRequest.newBuilder(URI("https://api.razorpay.com/v1/orders"))
                    .header("Authorization", "Basic ${familyBasicAuth()}")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString())
            }.getOrNull() ?: return@post call.familyFail("PAYMENT_GATEWAY_UNAVAILABLE", HttpStatusCode.BadGateway)
            if (gatewayResponse.statusCode() !in 200..299) return@post call.familyFail("GATEWAY_ORDER_CREATE_FAILED", HttpStatusCode.BadGateway)
            val gatewayOrderId = runCatching { familyJson.parseToJsonElement(gatewayResponse.body()).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
                ?: return@post call.familyFail("GATEWAY_INVALID_RESPONSE", HttpStatusCode.BadGateway)
            familyDb.connection.use { c ->
                c.prepareStatement("INSERT INTO payment_orders(id,parent_device_id,kids_device_id,pairing_session_id,plan_id,gateway_order_id,amount_paise,currency,status) VALUES(?,?,?,?,?,?,?,?,'CREATED')").use { p ->
                    p.setObject(1, localId); p.setObject(2, parentId); p.setObject(3, target.first); p.setObject(4, sessionId)
                    p.setObject(5, planId); p.setString(6, gatewayOrderId); p.setLong(7, target.second); p.setString(8, target.third); p.executeUpdate()
                }
            }
            call.respond(CreateOrderResponse(localId.toString(), gatewayOrderId, target.second, target.third, key))
        }

        post("/parent/payments/verify") {
            val parentId = call.deviceId("PARENT") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val req = call.receive<VerifyPaymentRequest>()
            val orderId = runCatching { UUID.fromString(req.orderId) }.getOrNull() ?: return@post call.familyFail("ORDER_INVALID")
            val order = familyDb.connection.use { c ->
                c.prepareStatement("SELECT gateway_order_id,amount_paise,currency,kids_device_id,plan_id,status FROM payment_orders WHERE id=? AND parent_device_id=?").use { p ->
                    p.setObject(1, orderId); p.setObject(2, parentId)
                    p.executeQuery().use { r -> if (r.next()) arrayOf(r.getString(1), r.getLong(2), r.getString(3), r.getObject(4, UUID::class.java), r.getObject(5, UUID::class.java), r.getString(6)) else null }
                }
            } ?: return@post call.familyFail("ORDER_NOT_FOUND", HttpStatusCode.NotFound)
            if (order[5] != "CREATED") return@post call.familyFail("ORDER_ALREADY_PROCESSED", HttpStatusCode.Conflict)
            val key = familyEnv("RAZORPAY_KEY_ID")
            val secret = familyEnv("RAZORPAY_KEY_SECRET")
            if (key.isBlank() || secret.isBlank()) return@post call.familyFail("PAYMENT_GATEWAY_NOT_CONFIGURED", HttpStatusCode.ServiceUnavailable)
            val expected = familyHmac(secret, "${order[0]}|${req.gatewayPaymentId}")
            if (!MessageDigest.isEqual(expected.toByteArray(), req.gatewaySignature.lowercase().toByteArray()))
                return@post call.familyFail("PAYMENT_SIGNATURE_INVALID", HttpStatusCode.Forbidden)
            val gatewayPayment = runCatching {
                familyHttp.send(HttpRequest.newBuilder(URI("https://api.razorpay.com/v1/payments/${req.gatewayPaymentId}"))
                    .header("Authorization", "Basic ${familyBasicAuth()}").GET().build(), HttpResponse.BodyHandlers.ofString())
            }.getOrNull() ?: return@post call.familyFail("PAYMENT_GATEWAY_UNAVAILABLE", HttpStatusCode.BadGateway)
            if (gatewayPayment.statusCode() !in 200..299) return@post call.familyFail("GATEWAY_PAYMENT_LOOKUP_FAILED", HttpStatusCode.BadGateway)
            val payment = runCatching { familyJson.parseToJsonElement(gatewayPayment.body()).jsonObject }.getOrNull()
                ?: return@post call.familyFail("GATEWAY_INVALID_RESPONSE", HttpStatusCode.BadGateway)
            val paidOrder = payment["order_id"]?.jsonPrimitive?.content
            val paidAmount = payment["amount"]?.jsonPrimitive?.longOrNull
            val paidCurrency = payment["currency"]?.jsonPrimitive?.content
            val paidStatus = payment["status"]?.jsonPrimitive?.content
            if (paidOrder != order[0] || paidAmount != order[1] || paidCurrency != order[2] || paidStatus != "captured")
                return@post call.familyFail("PAYMENT_NOT_CAPTURED_OR_MISMATCH", HttpStatusCode.PaymentRequired)

            val result = familyDb.connection.use { c ->
                c.autoCommit = false
                try {
                    c.prepareStatement("SELECT status FROM payment_orders WHERE id=? FOR UPDATE").use { p ->
                        p.setObject(1, orderId)
                        p.executeQuery().use { r -> if (!r.next() || r.getString(1) != "CREATED") throw IllegalStateException("ORDER_ALREADY_PROCESSED") }
                    }
                    val duration = c.prepareStatement("SELECT duration_value,duration_unit FROM subscription_plans WHERE id=? AND active=true").use { p ->
                        p.setObject(1, order[4]); p.executeQuery().use { r -> if (r.next()) r.getInt(1) to r.getString(2) else null }
                    } ?: throw IllegalStateException("PLAN_UNAVAILABLE")
                    val now = Instant.now()
                    val expiry = when (duration.second) {
                        "MINUTES" -> now.plusSeconds(duration.first * 60L)
                        "HOURS" -> now.plusSeconds(duration.first * 3600L)
                        "DAYS" -> now.plusSeconds(duration.first * 86400L)
                        "MONTHS" -> now.atZone(ZoneOffset.UTC).plusMonths(duration.first.toLong()).toInstant()
                        "YEARS" -> now.atZone(ZoneOffset.UTC).plusYears(duration.first.toLong()).toInstant()
                        else -> throw IllegalStateException("INVALID_PLAN_DURATION")
                    }
                    val alreadyBound = c.prepareStatement("SELECT 1 FROM device_bindings WHERE kids_device_id=? AND revoked_at IS NULL").use { p ->
                        p.setObject(1, order[3]); p.executeQuery().use { it.next() }
                    }
                    if (alreadyBound) throw IllegalStateException("KIDS_DEVICE_ALREADY_BOUND")
                    c.prepareStatement("UPDATE payment_orders SET status='CAPTURED',gateway_payment_id=?,verified_at=now() WHERE id=?").use { p ->
                        p.setString(1, req.gatewayPaymentId); p.setObject(2, orderId); p.executeUpdate()
                    }
                    val permitCode = "FS-" + familyToken().uppercase()
                    val permitId = UUID.randomUUID()
                    c.prepareStatement("INSERT INTO permits(id,public_code,kids_device_id,parent_device_id,plan_id,payment_order_id,starts_at,expires_at) VALUES(?,?,?,?,?,?,?,?)").use { p ->
                        p.setObject(1, permitId); p.setString(2, permitCode); p.setObject(3, order[3]); p.setObject(4, parentId)
                        p.setObject(5, order[4]); p.setObject(6, orderId)
                        p.setObject(7, OffsetDateTime.ofInstant(now, ZoneOffset.UTC)); p.setObject(8, OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)); p.executeUpdate()
                    }
                    c.prepareStatement("INSERT INTO device_bindings(kids_device_id,parent_device_id,permit_id) VALUES(?,?,?)").use { p ->
                        p.setObject(1, order[3]); p.setObject(2, parentId); p.setObject(3, permitId); p.executeUpdate()
                    }
                    c.commit()
                    VerifyPaymentResponse("VERIFIED_AND_LINKED", permitCode, expiry.toString(), (order[3] as UUID).toString())
                } catch (e: Exception) { c.rollback(); throw e } finally { c.autoCommit = true }
            }
            call.respond(result)
        }

        post("/kids/reconnect") {
            val kidsId = call.deviceId("KIDS") ?: return@post call.familyFail("DEVICE_AUTH_REQUIRED", HttpStatusCode.Unauthorized)
            val binding = familyDb.connection.use { c ->
                c.prepareStatement("SELECT p.public_code,p.expires_at,b.parent_device_id FROM device_bindings b JOIN permits p ON p.id=b.permit_id WHERE b.kids_device_id=? AND b.revoked_at IS NULL AND p.revoked_at IS NULL AND p.expires_at>now()").use { p ->
                    p.setObject(1, kidsId)
                    p.executeQuery().use { r -> if (r.next()) Triple(r.getString(1), r.getObject(2).toString(), r.getObject(3).toString()) else null }
                }
            } ?: return@post call.familyFail("NO_VALID_BINDING", HttpStatusCode.Forbidden)
            call.respond(mapOf("status" to "BOUND", "permit" to binding.first, "expiresAt" to binding.second, "parentDeviceId" to binding.third))
        }
    }
}

private suspend fun io.ktor.server.routing.RoutingContext.bootstrapFamilyDevice(role: String) {
    val request = runCatching { call.receive<DeviceBootstrapRequest>() }.getOrNull()
        ?: return call.familyFail("INVALID_BOOTSTRAP_REQUEST")
    if (request.installFingerprint.length !in 16..128) return call.familyFail("INSTALL_FINGERPRINT_INVALID")
    val token = familyToken()
    val otp = if (role == "KIDS") familyOtp() else null
    val expiry = if (otp != null) Instant.now().plusSeconds(600) else null
    val deviceId = familyDb.connection.use { c ->
        c.prepareStatement("INSERT INTO app_devices(role,install_fingerprint,token_hash) VALUES(?,?,?) ON CONFLICT(role,install_fingerprint) DO UPDATE SET last_seen_at=now(),token_hash=EXCLUDED.token_hash RETURNING id").use { p ->
            p.setString(1, role); p.setString(2, request.installFingerprint); p.setString(3, familyHashToken(token))
            p.executeQuery().use { r -> r.next(); r.getObject(1, UUID::class.java) }
        }
    }
    if (otp != null && expiry != null) {
        familyDb.connection.use { c ->
            c.prepareStatement("INSERT INTO pairing_otps(kids_device_id,otp_hash,expires_at) VALUES(?,?,?)").use { p ->
                p.setObject(1, deviceId); p.setString(2, familySha(otp + familyEnv("TOKEN_PEPPER")))
                p.setObject(3, OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)); p.executeUpdate()
            }
        }
    }
    call.respond(DeviceBootstrapResponse(deviceId.toString(), token, otp, expiry?.toString()))
}
