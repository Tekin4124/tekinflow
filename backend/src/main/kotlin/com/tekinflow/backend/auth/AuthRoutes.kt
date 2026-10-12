package com.tekinflow.backend.auth

import com.tekinflow.backend.model.LoginRequest
import com.tekinflow.backend.model.LoginResponse
import com.tekinflow.backend.model.LogoutRequest
import com.tekinflow.backend.model.RefreshTokenRequest
import com.tekinflow.backend.model.RefreshTokenResponse
import com.tekinflow.backend.repository.RefreshTokenRotationResult
import com.tekinflow.backend.repository.UserRepository
import com.tekinflow.backend.util.AuthenticationFailureException
import com.tekinflow.backend.util.ValidationException
import com.tekinflow.backend.util.cleanRequired
import com.tekinflow.backend.util.clientIp
import com.tekinflow.backend.util.receiveValidated
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.Instant
import java.util.UUID

fun Route.authRoutes(
    application: Application,
    userRepository: UserRepository,
    passwordHasher: PasswordHasher,
    jwtConfig: JwtConfig
) {
    route("/api/v1/auth") {
        post("/login") {
            val request = call.receiveValidated { receive<LoginRequest>() }
            val username = request.username.cleanRequired(50)
            val password = request.password.trim()
            val ip = call.clientIp()

            if (password.isEmpty()) {
                application.environment.log.warn("Login failure ip={}", ip)
                throw ValidationException()
            }

            val authUser = userRepository.findActiveAuthUserByUsername(username)
            val now = Instant.now()
            if (authUser?.lockedUntil?.isAfter(now) == true) {
                // Generic error (identical to invalid-credentials) so a locked
                // account cannot be distinguished from a wrong password, which
                // would otherwise leak that the username exists.
                application.environment.log.warn("Login locked userId={} ip={}", authUser.id, ip)
                throw AuthenticationFailureException()
            }

            if (authUser == null || !passwordHasher.verify(password, authUser.passwordHash)) {
                if (authUser == null) {
                    // Spend comparable time so response latency does not reveal
                    // whether the username exists (username enumeration defense).
                    passwordHasher.verifyDummy(password)
                }
                userRepository.recordFailedLogin(username)
                application.environment.log.warn("Login failure ip={}", ip)
                throw AuthenticationFailureException()
            }

            if (authUser.twoFactorEnabled) {
                if (request.twoFactorCode.isNullOrBlank()) {
                    call.respond(LoginResponse(twoFactorRequired = true))
                    return@post
                }
                if (!Totp.verify(authUser.twoFactorSecret, request.twoFactorCode)) {
                    userRepository.recordFailedLogin(username)
                    application.environment.log.warn("Login 2FA failure userId={} ip={}", authUser.id, ip)
                    throw AuthenticationFailureException()
                }
            } else {
                val setupSecret = request.twoFactorSecret
                    ?.trim()
                    ?.uppercase()
                    ?.takeIf { it.matches(Regex("[A-Z2-7]{16,64}")) }
                    ?: Totp.generateSecret()

                if (request.twoFactorCode.isNullOrBlank()) {
                    call.respond(
                        LoginResponse(
                            twoFactorSetupRequired = true,
                            twoFactorSecret = setupSecret,
                            twoFactorOtpAuthUrl = Totp.otpauthUrl(authUser.username, setupSecret)
                        )
                    )
                    return@post
                }

                if (!Totp.verify(setupSecret, request.twoFactorCode)) {
                    userRepository.recordFailedLogin(username)
                    application.environment.log.warn("Login 2FA setup failure userId={} ip={}", authUser.id, ip)
                    throw AuthenticationFailureException()
                }
                userRepository.enableTwoFactor(authUser.id, setupSecret, ip)
            }

            userRepository.resetLoginFailures(authUser.id)
            val refreshToken = userRepository.createRefreshToken(authUser.id, jwtConfig.refreshTokenExpiry())
            val accessToken = jwtConfig.accessToken(authUser.id, authUser.role, authUser.tokenVersion)
            val user = requireNotNull(userRepository.findUserById(authUser.id))

            application.environment.log.info("Login success userId={} ip={}", authUser.id, ip)
            call.respond(
                LoginResponse(
                    accessToken = accessToken,
                    refreshToken = refreshToken.toString(),
                    user = user
                )
            )
        }

        post("/refresh") {
            val request = call.receiveValidated { receive<RefreshTokenRequest>() }
            val token = parseRefreshToken(request.refreshToken)
            val rotation = userRepository.rotateRefreshToken(token, jwtConfig.refreshTokenExpiry())
            if (rotation is RefreshTokenRotationResult.ReuseDetected) {
                application.environment.log.warn("Refresh token reuse detected ip={}", call.clientIp())
            }
            val success = rotation as? RefreshTokenRotationResult.Success
                ?: throw AuthenticationFailureException()

            val accessToken = jwtConfig.accessToken(success.user.id, success.user.role, success.user.tokenVersion)

            call.respond(
                RefreshTokenResponse(
                    accessToken = accessToken,
                    refreshToken = success.refreshToken.toString()
                )
            )
        }

        post("/logout") {
            val request = call.receiveValidated { receive<LogoutRequest>() }
            val token = parseRefreshToken(request.refreshToken)
            userRepository.deleteRefreshToken(token)
            call.respond(mapOf("success" to true))
        }
    }
}

private fun parseRefreshToken(refreshToken: String): UUID {
    return runCatching { UUID.fromString(refreshToken.trim()) }
        .getOrElse { throw ValidationException() }
}
