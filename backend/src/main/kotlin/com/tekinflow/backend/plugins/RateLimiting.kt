package com.tekinflow.backend.plugins

import com.tekinflow.backend.util.ApiException
import com.tekinflow.backend.util.clientIp
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import java.util.concurrent.ConcurrentHashMap

private const val windowMillis = 60_000L
private const val maxRequestsPerWindow = 60
private const val maxBodySizeBytes = 1_048_576L
private const val cleanupIntervalMillis = 60_000L
private const val maxTrackedClients = 10_000

// Stricter throttle for unauthenticated auth endpoints (login/refresh) to slow
// credential stuffing / brute-force from a single source. Keyed per-IP and
// applied in addition to the global limit above.
private const val authMaxRequestsPerWindow = 10
private val authThrottledPaths = listOf("/api/v1/auth/login", "/api/v1/auth/refresh")

private data class RateLimitState(
    var windowStartedAt: Long,
    var requestCount: Int
)

private val RequestGuardPlugin = createApplicationPlugin("RequestGuardPlugin") {
    val counters = ConcurrentHashMap<String, RateLimitState>()
    val authCounters = ConcurrentHashMap<String, RateLimitState>()
    var lastCleanupAt = 0L

    fun hit(map: ConcurrentHashMap<String, RateLimitState>, key: String, now: Long): Int {
        val state = map.compute(key) { _, current ->
            when {
                current == null || now - current.windowStartedAt >= windowMillis -> RateLimitState(now, 1)
                else -> current.apply { requestCount += 1 }
            }
        } ?: RateLimitState(now, 1)
        return state.requestCount
    }

    onCall { call ->
        val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBodySizeBytes) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, "İstek boyutu çok büyük")
        }

        val now = System.currentTimeMillis()
        if (now - lastCleanupAt >= cleanupIntervalMillis) {
            lastCleanupAt = now
            counters.entries.removeIf { (_, state) -> now - state.windowStartedAt >= windowMillis }
            authCounters.entries.removeIf { (_, state) -> now - state.windowStartedAt >= windowMillis }
            if (counters.size > maxTrackedClients) {
                counters.clear()
            }
            if (authCounters.size > maxTrackedClients) {
                authCounters.clear()
            }
        }

        val clientIp = call.clientIp()

        val path = call.request.local.uri.substringBefore('?')
        if (authThrottledPaths.any { path == it }) {
            if (hit(authCounters, clientIp, now) > authMaxRequestsPerWindow) {
                throw ApiException(HttpStatusCode.TooManyRequests, "Çok fazla istek")
            }
        }

        if (hit(counters, clientIp, now) > maxRequestsPerWindow) {
            throw ApiException(HttpStatusCode.TooManyRequests, "Çok fazla istek")
        }
    }
}

fun Application.configureRateLimiting() {
    install(RequestGuardPlugin)
}
