package com.tekinflow.backend.plugins

import com.tekinflow.backend.auth.JwtConfig
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.repository.UserRepository
import com.tekinflow.backend.util.AuthenticationFailureException
import com.tekinflow.backend.util.AuthorizationFailureException
import com.tekinflow.backend.util.requireClaim
import com.tekinflow.backend.util.respondApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.Principal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import java.util.UUID

data class UserPrincipal(
    val userId: UUID,
    val role: UserRole,
    val isSuperAdmin: Boolean = false
) : Principal

class RoleAuthorizationConfig {
    var allowedRoles: Set<UserRole> = emptySet()
}

private val RoleAuthorizationPlugin = createRouteScopedPlugin(
    name = "RoleAuthorization",
    createConfiguration = ::RoleAuthorizationConfig
) {
    val allowedRoles = pluginConfig.allowedRoles
    on(AuthenticationChecked) { call ->
        val principal = call.principal<UserPrincipal>() ?: throw AuthenticationFailureException()
        val roleAllowed = allowedRoles.isEmpty() || principal.role in allowedRoles
        val genericAdminWithoutSuper = principal.role == UserRole.ADMIN && !principal.isSuperAdmin
        if ((!roleAllowed && !principal.isSuperAdmin) || (roleAllowed && genericAdminWithoutSuper)) {
            application.environment.log.warn(
                "Authorization failure userId={} role={} path={}",
                principal.userId,
                principal.role,
                call.request.path()
            )
            throw AuthorizationFailureException()
        }
    }
}

fun Application.configureAuthentication(jwtConfig: JwtConfig, userRepository: UserRepository) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtConfig.issuer
            verifier(jwtConfig.verifier())
            validate { credential ->
                runCatching {
                    val principal = JWTPrincipal(credential.payload)
                    val userId = UUID.fromString(principal.requireClaim("userId"))
                    val role = UserRole.valueOf(principal.requireClaim("role"))
                    val tokenVersion = principal.getClaim("tokenVersion", Int::class) ?: return@runCatching null
                    val activeUser = userRepository.findActiveUserById(userId) ?: return@runCatching null
                    if (activeUser.role != role) return@runCatching null
                    if (activeUser.tokenVersion != tokenVersion) return@runCatching null
                    UserPrincipal(userId, role, activeUser.isSuperAdmin)
                }.getOrNull()
            }
            challenge { _, _ ->
                call.respondApiError(HttpStatusCode.Unauthorized, "Kimlik doğrulama başarısız")
            }
        }
    }
}

fun Route.authorized(vararg allowedRoles: UserRole, build: Route.() -> Unit) {
    authenticate("auth-jwt") {
        install(RoleAuthorizationPlugin) {
            this.allowedRoles = allowedRoles.toSet()
        }
        build()
    }
}
