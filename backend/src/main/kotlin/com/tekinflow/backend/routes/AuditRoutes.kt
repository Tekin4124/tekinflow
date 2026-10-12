package com.tekinflow.backend.routes

import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.plugins.authorized
import com.tekinflow.backend.repository.AuditRepository
import com.tekinflow.backend.util.toUuidOrValidationError
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.auditRoutes(auditRepository: AuditRepository) {
    authorized(UserRole.ADMIN) {
        get("/api/v1/audit") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200
            val entityType = call.request.queryParameters["entityType"]?.takeIf { it.isNotBlank() }
            val entityId = call.request.queryParameters["entityId"]?.takeIf { it.isNotBlank() }?.toUuidOrValidationError()
            call.respond(auditRepository.list(limit, entityType, entityId))
        }
    }
}
