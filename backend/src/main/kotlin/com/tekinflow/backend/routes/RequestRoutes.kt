package com.tekinflow.backend.routes

import com.tekinflow.backend.model.AssignDriverRequest
import com.tekinflow.backend.model.CancelRequestBody
import com.tekinflow.backend.model.CreatePurchaseRequestRequest
import com.tekinflow.backend.model.ErrorResponse
import com.tekinflow.backend.model.PurchaseRequestResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import kotlinx.serialization.json.Json
import com.tekinflow.backend.model.MaterialStatus
import com.tekinflow.backend.model.ObjectionRequest
import com.tekinflow.backend.model.UpdatePurchaseRequestRequest
import com.tekinflow.backend.model.UpdateStatusRequest
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.plugins.UserPrincipal
import com.tekinflow.backend.plugins.authorized
import com.tekinflow.backend.repository.IdempotencyRepository
import com.tekinflow.backend.repository.RequestRepository
import com.tekinflow.backend.util.ValidationException
import com.tekinflow.backend.util.clientIp
import com.tekinflow.backend.util.receiveValidated
import com.tekinflow.backend.util.toUuidOrValidationError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.requestRoutes(
    requestRepository: RequestRepository,
    idempotencyRepository: IdempotencyRepository
) {
    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        route("/api/v1/requests") {
            get {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val status = call.request.queryParameters["status"]?.let(::parseStatus)
                val assignedDriverId = call.request.queryParameters["assignedDriverId"]?.takeIf { it.isNotBlank() }?.toUuidOrValidationError()
                val staffId = call.request.queryParameters["staffId"]?.takeIf { it.isNotBlank() }?.toUuidOrValidationError()
                call.respond(
                    requestRepository.listRequests(
                        currentUserId = principal.userId,
                        currentRole = principal.role,
                        status = status,
                        assignedDriverId = assignedDriverId,
                        staffId = staffId
                    )
                )
            }

            get("/{id}") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
                call.respond(requestRepository.getRequestById(id, principal.userId, principal.role))
            }
        }
    }

    authorized(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        get("/api/v1/requests/delivered-recipients") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            call.respond(requestRepository.listDeliveredRecipients(principal.userId, principal.role))
        }
    }

    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.ADMIN) {
        route("/api/v1/requests") {
            post {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val request = call.receiveValidated { receive<CreatePurchaseRequestRequest>() }
                val created = call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.createRequest(principal.userId, principal.role, request, call.clientIp()) }
                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
                val request = call.receiveValidated { receive<UpdatePurchaseRequestRequest>() }
                call.respond(call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.updateRequest(id, principal.userId, principal.role, request, call.clientIp()) })
            }
        }
    }

    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.ADMIN) {
        post("/api/v1/requests/{id}/cancel") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            val request = call.receiveValidated { receive<CancelRequestBody>() }
            call.respond(call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.cancelRequest(id, principal.userId, principal.role, request, call.clientIp()) })
        }
    }

    authorized(UserRole.ADMIN) {
        delete("/api/v1/requests/{id}") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            requestRepository.deleteRequest(id, principal.userId, call.clientIp())
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        delete("/api/v1/requests/{id}/objection") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            call.respond(requestRepository.clearObjection(id, principal.userId, call.clientIp()))
        }
    }

    authorized(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.ADMIN) {
        post("/api/v1/requests/{id}/assign-driver") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            val request = call.receiveValidated { receive<AssignDriverRequest>() }
            call.respond(call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.assignDriver(id, principal.userId, principal.role, request, call.clientIp()) })
        }

        post("/api/v1/requests/{id}/unassign-driver") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            call.respond(call.replayRequest(idempotencyRepository, principal, "{}") { requestRepository.unassignDriver(id, principal.userId, principal.role, call.clientIp()) })
        }
    }

    authorized(UserRole.DRIVER) {
        post("/api/v1/requests/{id}/take-over") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            call.respond(call.replayRequest(idempotencyRepository, principal, "{}") { requestRepository.takeOverRequest(id, principal.userId, principal.role, call.clientIp()) })
        }

    }

    authorized(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER) {
        post("/api/v1/requests/{id}/object") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            val request = call.receiveValidated { receive<ObjectionRequest>() }
            call.respond(call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.submitObjection(id, principal.userId, principal.role, request, call.clientIp()) })
        }
    }

    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        post("/api/v1/requests/{id}/update-status") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            val request = call.receiveValidated { receive<UpdateStatusRequest>() }
            call.respond(call.replayRequest(idempotencyRepository, principal, Json.encodeToString(request)) { requestRepository.updateStatus(id, principal.userId, principal.role, request, call.clientIp()) })
        }
    }

    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        post("/api/v1/requests/{id}/mark-seen") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val id = (call.parameters["id"] ?: throw ValidationException()).toUuidOrValidationError()
            val result = idempotencyRepository.execute(
                call.request.header("X-Idempotency-Key"), principal.userId,
                "${call.request.httpMethod.value} ${call.request.path()}", "{}", Acknowledgement.serializer()
            ) { requestRepository.markSeen(id, principal.userId, principal.role); Acknowledgement() }
            call.respond(result)
        }
    }
}

private fun parseStatus(value: String): MaterialStatus {
    return runCatching { MaterialStatus.valueOf(value.trim()) }
        .getOrElse { throw ValidationException() }
}

@Serializable
private data class Acknowledgement(val success: Boolean = true)
private suspend fun ApplicationCall.replayRequest(repo: IdempotencyRepository, principal: UserPrincipal, body: String, operation: suspend () -> PurchaseRequestResponse): PurchaseRequestResponse = repo.execute(
    request.header("X-Idempotency-Key"), principal.userId,
    "${request.httpMethod.value} ${request.path()}", body,
    PurchaseRequestResponse.serializer(), operation
)
