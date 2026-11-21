package com.familysafety.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.get
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

private val ownerRandom = SecureRandom()
private val ownerDb by lazy {
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/familysafety"
        username = System.getenv("DATABASE_USER") ?: "familysafety"
        password = System.getenv("DATABASE_PASSWORD") ?: "CHANGE_ME"
        maximumPoolSize = 4
    })
}
private fun ownerEnv(name: String) = System.getenv(name).orEmpty()
private fun ownerSha(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
private fun ownerToken(): String = ByteArray(32).also(ownerRandom::nextBytes)
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

@kotlinx.serialization.Serializable
data class OwnerLoginRequest(val userId: String? = null, val password: String)

@kotlinx.serialization.Serializable
data class OwnerLoginResponse(val accessToken: String, val expiresAt: String)

@kotlinx.serialization.Serializable
data class OwnerPasswordChangeRequest(val currentPassword: String, val newPassword: String)

@kotlinx.serialization.Serializable
data class OwnerError(val message: String)

private const val OWNER_GENERIC_ERROR = "Invalid User ID or Password."

/**
 * Seeds only BCrypt hashes supplied by deployment secret OWNER_PASSWORD_HASHES.
 * This is deliberately insert-once: after an Owner rotates the password, a restart
 * will not re-enable the old deployment hashes.
 */
fun initializeOwnerAuthentication() {
    val configured = ownerEnv("OWNER_PASSWORD_HASHES")
        .split(',')
        .map(String::trim)
        .filter { it.startsWith("$2a$") || it.startsWith("$2b$") || it.startsWith("$2y$") }
    ownerDb.connection.use { connection ->
        val count = connection.prepareStatement("SELECT count(*) FROM owner_passwords WHERE active=true")
            .use { statement -> statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) } }
        if (count == 0 && configured.isNotEmpty()) {
            connection.prepareStatement("INSERT INTO owner_passwords(password_hash) VALUES (?)").use { statement ->
                configured.distinct().forEach { hash -> statement.setString(1, hash); statement.addBatch() }
                statement.executeBatch()
            }
        }
    }
}

/** Call inside the main Ktor routing { } block. */
fun Route.ownerAuthenticationRoutes() {
    route("/v1/private") {
        post("/session") {
            val request = runCatching { call.receive<OwnerLoginRequest>() }.getOrNull()
            if (request == null || request.password.isBlank() || request.password.length > 256 ||
                (request.userId?.length ?: 0) > 128) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError(OWNER_GENERIC_ERROR))
                return@post
            }

            val ipDigest = ownerSha(call.request.origin.remoteHost + ownerEnv("TOKEN_PEPPER"))
            val limited = ownerDb.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT count(*) FROM owner_login_attempts WHERE ip_hash=? AND attempted_at > now() - interval '15 minutes'"
                ).use { statement ->
                    statement.setString(1, ipDigest)
                    statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) >= 8 }
                }
            }
            if (limited) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError(OWNER_GENERIC_ERROR))
                return@post
            }

            val activeHashes = ownerDb.connection.use { connection ->
                connection.prepareStatement("SELECT password_hash FROM owner_passwords WHERE active=true")
                    .use { statement -> statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    } }
            }
            // Always evaluate all active hashes; do not reveal which credential failed.
            var passwordMatches = false
            activeHashes.forEach { hash ->
                val matches = runCatching { BCrypt.checkpw(request.password, hash) }.getOrDefault(false)
                passwordMatches = passwordMatches or matches
            }
            val configuredUserId = ownerEnv("OWNER_USER_ID")
            val userIdMatches = request.userId.isNullOrBlank() ||
                (configuredUserId.isNotBlank() && MessageDigest.isEqual(
                    request.userId.toByteArray(), configuredUserId.toByteArray()
                ))
            val authenticated = passwordMatches && userIdMatches

            ownerDb.connection.use { connection ->
                connection.prepareStatement("INSERT INTO owner_login_attempts(ip_hash,success) VALUES (?,?)").use { statement ->
                    statement.setString(1, ipDigest)
                    statement.setBoolean(2, authenticated)
                    statement.executeUpdate()
                }
            }

            if (!authenticated) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError(OWNER_GENERIC_ERROR))
                return@post
            }

            val rawToken = ownerToken()
            val expiry = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(20)
            ownerDb.connection.use { connection ->
                connection.prepareStatement("INSERT INTO owner_sessions(token_hash,expires_at) VALUES (?,?)").use { statement ->
                    statement.setString(1, ownerSha(rawToken + ownerEnv("TOKEN_PEPPER")))
                    statement.setObject(2, expiry)
                    statement.executeUpdate()
                }
            }
            call.respond(OwnerLoginResponse(rawToken, expiry.toString()))
        }

        post("/password") {
            val bearer = call.request.headers["Authorization"]
                ?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
            if (bearer.isNullOrBlank()) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError("Owner session required."))
                return@post
            }
            val tokenHash = ownerSha(bearer + ownerEnv("TOKEN_PEPPER"))
            val validSession = ownerDb.connection.use { connection ->
                connection.prepareStatement(
                    "SELECT 1 FROM owner_sessions WHERE token_hash=? AND revoked_at IS NULL AND expires_at>now()"
                ).use { statement ->
                    statement.setString(1, tokenHash)
                    statement.executeQuery().use { it.next() }
                }
            }
            if (!validSession) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError("Owner session required."))
                return@post
            }
            val request = runCatching { call.receive<OwnerPasswordChangeRequest>() }.getOrNull()
            if (request == null || request.newPassword.length < 12 || request.newPassword.length > 128) {
                call.respond(HttpStatusCode.BadRequest, OwnerError("New password must be 12–128 characters."))
                return@post
            }

            val hashes = ownerDb.connection.use { connection ->
                connection.prepareStatement("SELECT password_hash FROM owner_passwords WHERE active=true")
                    .use { statement -> statement.executeQuery().use { rows ->
                        buildList { while (rows.next()) add(rows.getString(1)) }
                    } }
            }
            val currentValid = hashes.any { hash -> runCatching { BCrypt.checkpw(request.currentPassword, hash) }.getOrDefault(false) }
            if (!currentValid) {
                call.respond(HttpStatusCode.Unauthorized, OwnerError("Current password is incorrect."))
                return@post
            }

            val newHash = BCrypt.hashpw(request.newPassword, BCrypt.gensalt(12))
            ownerDb.connection.use { connection ->
                connection.autoCommit = false
                try {
                    connection.prepareStatement("UPDATE owner_passwords SET active=false,retired_at=now() WHERE active=true")
                        .use { it.executeUpdate() }
                    connection.prepareStatement("INSERT INTO owner_passwords(password_hash,active) VALUES (?,true)")
                        .use { it.setString(1, newHash); it.executeUpdate() }
                    // Revoke all existing sessions, including the one used to rotate credentials.
                    connection.prepareStatement("UPDATE owner_sessions SET revoked_at=now() WHERE revoked_at IS NULL")
                        .use { it.executeUpdate() }
                    connection.commit()
                } catch (failure: Exception) {
                    connection.rollback()
                    throw failure
                } finally {
                    connection.autoCommit = true
                }
            }
            call.respond(mapOf("status" to "PASSWORD_CHANGED_RELOGIN_REQUIRED"))
        }
    }
}
