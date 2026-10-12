package com.tekinflow.backend.routes

import com.tekinflow.backend.model.CreateUserRequest
import com.tekinflow.backend.model.ChangePasswordRequest
import com.tekinflow.backend.model.FcmTokenRequest
import com.tekinflow.backend.model.UpdateUserRequest
import com.tekinflow.backend.model.UserPreferenceRequest
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.plugins.UserPrincipal
import com.tekinflow.backend.plugins.authorized
import com.tekinflow.backend.repository.UserRepository
import com.tekinflow.backend.util.clientIp
import com.tekinflow.backend.util.receiveValidated
import com.tekinflow.backend.util.toUuidOrValidationError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.userRoutes(userRepository: UserRepository) {
    authorized(UserRole.PURCHASING, UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.DRIVER, UserRole.ADMIN) {
        post("/api/v1/users/me/fcm-token") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val request = call.receiveValidated { receive<FcmTokenRequest>() }
            userRepository.updateFcmToken(principal.userId, request.token)
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }

        get("/api/v1/users/me/preferences/{key}") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val key = call.parameters["key"] ?: throw com.tekinflow.backend.util.ValidationException()
            call.respond(userRepository.getPreference(principal.userId, key))
        }

        put("/api/v1/users/me/preferences/{key}") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val key = call.parameters["key"] ?: throw com.tekinflow.backend.util.ValidationException()
            val request = call.receiveValidated { receive<UserPreferenceRequest>() }
            call.respond(userRepository.putPreference(principal.userId, key, request.value))
        }

        put("/api/v1/users/me/password") {
            val principal = checkNotNull(call.principal<UserPrincipal>())
            val request = call.receiveValidated { receive<ChangePasswordRequest>() }
            userRepository.changeOwnPassword(principal.userId, request, call.clientIp())
            call.respond(HttpStatusCode.OK, mapOf("success" to true))
        }
    }

    authorized(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN, UserRole.ADMIN) {
        get("/api/v1/users/drivers") {
            call.respond(userRepository.listActiveDrivers())
        }
    }

    authorized(UserRole.PURCHASING_ADMIN, UserRole.LOGISTICS_ADMIN, UserRole.ADMIN) {
        route("/api/v1/users") {
            get {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                call.respond(userRepository.listUsers(principal.userId))
            }

            post {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val request = call.receiveValidated { receive<CreateUserRequest>() }
                val created = userRepository.createUser(principal.userId, request, call.clientIp())
                call.respond(HttpStatusCode.Created, created)
            }

            put("/{id}") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw com.tekinflow.backend.util.ValidationException()).toUuidOrValidationError()
                val request = call.receiveValidated { receive<UpdateUserRequest>() }
                call.respond(userRepository.updateUser(id, principal.userId, request, call.clientIp()))
            }

            delete("/{id}") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw com.tekinflow.backend.util.ValidationException()).toUuidOrValidationError()
                userRepository.deactivateUser(id, principal.userId, call.clientIp())
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }

            post("/{id}/activate") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw com.tekinflow.backend.util.ValidationException()).toUuidOrValidationError()
                call.respond(userRepository.activateUser(id, principal.userId, call.clientIp()))
            }

            delete("/{id}/permanent") {
                val principal = checkNotNull(call.principal<UserPrincipal>())
                val id = (call.parameters["id"] ?: throw com.tekinflow.backend.util.ValidationException()).toUuidOrValidationError()
                userRepository.deleteUser(id, principal.userId, call.clientIp())
                call.respond(HttpStatusCode.OK, mapOf("success" to true))
            }
        }
    }
}
