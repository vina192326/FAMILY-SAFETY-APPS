package com.familysafety.backend

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, host = "0.0.0.0", port = port, module = Application::module).start(wait = true)
}

private val historyPurgeScheduler = Executors.newSingleThreadScheduledExecutor { task ->
    Thread(task, "family-history-retention").apply { isDaemon = true }
}

fun Application.module() {
    historyPurgeScheduler.scheduleWithFixedDelay(
        { runCatching { purgeExpiredHistoryRecords() } },
        60, 60, TimeUnit.SECONDS
    )
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    initializeOwnerAuthentication()
    routing {
        familyApiRoutes()
        historyApiRoutes()\n        liveTelemetryRoutes()
        ownerAuthenticationRoutes()
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
    }
}
