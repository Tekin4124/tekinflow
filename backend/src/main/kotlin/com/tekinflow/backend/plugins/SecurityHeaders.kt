package com.tekinflow.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install

private const val ContentSecurityPolicy =
    "default-src 'self'; " +
        "connect-src 'self'; " +
        "img-src 'self' data:; " +
        "style-src 'self' 'unsafe-inline'; " +
        "script-src 'self'; " +
        "font-src 'self'; " +
        "object-src 'none'; " +
        "base-uri 'none'; " +
        "frame-ancestors 'none'"

private val SecurityHeadersPlugin = createApplicationPlugin("SecurityHeadersPlugin") {
    onCall { call ->
        call.response.headers.append("Content-Security-Policy", ContentSecurityPolicy, safeOnly = false)
        call.response.headers.append("X-Frame-Options", "DENY", safeOnly = false)
        call.response.headers.append("X-Content-Type-Options", "nosniff", safeOnly = false)
        call.response.headers.append("Referrer-Policy", "no-referrer", safeOnly = false)
        call.response.headers.append("Permissions-Policy", "camera=(), microphone=(), geolocation=()", safeOnly = false)
        call.response.headers.append("Strict-Transport-Security", "max-age=31536000; includeSubDomains", safeOnly = false)
    }
}

fun Application.configureSecurityHeaders() {
    install(SecurityHeadersPlugin)
}
