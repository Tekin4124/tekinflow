package com.tekinflow.backend.routes

import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.plugins.UserPrincipal
import com.tekinflow.backend.plugins.authorized
import com.tekinflow.backend.repository.NotificationRepository
import com.tekinflow.backend.util.toUuidOrValidationError
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.notificationRoutes(notificationRepository: NotificationRepository) {
    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        route("/api/v1/notifications") {
            get {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                call.respond(notificationRepository.listForUser(principal.userId))
            }

            post("/{id}/read") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val notificationId = (call.parameters["id"] ?: throw com.tekinflow.backend.util.ValidationException()).toUuidOrValidationError()
                notificationRepository.markRead(notificationId, principal.userId)
                call.respond(mapOf("success" to true))
            }
        }
    }
}
