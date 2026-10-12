package com.tekinflow.backend.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.tekinflow.backend.model.UserRole
import io.ktor.server.config.ApplicationConfig
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class JwtConfig(
    secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpirySeconds: Long,
    val refreshTokenExpirySeconds: Long
) {
    private val algorithm = Algorithm.HMAC256(secret)

    init {
        require(secret.toByteArray().size >= 32) { "JWT_SECRET en az 256-bit olmalıdır" }
    }

    fun accessToken(userId: UUID, role: UserRole, tokenVersion: Int): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId.toString())
            .withClaim("role", role.name)
            .withClaim("tokenVersion", tokenVersion)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(accessTokenExpirySeconds, ChronoUnit.SECONDS)))
            .sign(algorithm)
    }

    fun refreshTokenExpiry(): Instant = Instant.now().plus(refreshTokenExpirySeconds, ChronoUnit.SECONDS)

    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .acceptLeeway(30)
        .build()

    companion object {
        fun from(config: ApplicationConfig): JwtConfig {
            val secret = config.propertyOrNull("jwt.secret")?.getString()
                ?: error("JWT_SECRET tanımlanmadı")
            return JwtConfig(
                secret = secret,
                issuer = config.property("jwt.issuer").getString(),
                audience = config.property("jwt.audience").getString(),
                accessTokenExpirySeconds = config.property("jwt.accessTokenExpiry").getString().toLong(),
                refreshTokenExpirySeconds = config.property("jwt.refreshTokenExpiry").getString().toLong()
            )
        }
    }
}
