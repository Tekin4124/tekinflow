package com.tekinflow.backend

import com.tekinflow.backend.auth.JwtConfig
import com.tekinflow.backend.auth.PasswordHasher
import com.tekinflow.backend.auth.authRoutes
import com.tekinflow.backend.config.DatabaseConfig
import com.tekinflow.backend.model.ErrorResponse
import com.tekinflow.backend.plugins.configureAuthentication
import com.tekinflow.backend.plugins.configureRateLimiting
import com.tekinflow.backend.plugins.configureSecurityHeaders
import com.tekinflow.backend.plugins.configureSerialization
import com.tekinflow.backend.repository.NotificationRepository
import com.tekinflow.backend.repository.AuditRepository
import com.tekinflow.backend.repository.IdempotencyRepository
import com.tekinflow.backend.repository.RequestRepository
import com.tekinflow.backend.repository.UserRepository
import com.tekinflow.backend.routes.notificationRoutes
import com.tekinflow.backend.routes.auditRoutes
import com.tekinflow.backend.routes.requestRoutes
import com.tekinflow.backend.routes.userRoutes
import com.tekinflow.backend.util.ApiException
import com.tekinflow.backend.util.RequestNumberGenerator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.http.content.default
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.io.File

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    com.tekinflow.backend.auth.TotpSecretVault.cipher
    val passwordHasher = PasswordHasher()
    val jwtConfig = JwtConfig.from(environment.config)

    DatabaseConfig.init(environment.config, environment.log)
    runBlocking {
        com.tekinflow.backend.config.dbQuery {
            com.tekinflow.backend.model.UsersTable.selectAll().forEach { row ->
                val secret = row[com.tekinflow.backend.model.UsersTable.twoFactorSecret]
                if (secret != null && !secret.startsWith(com.tekinflow.backend.auth.SecretCipher.PREFIX)) {
                    com.tekinflow.backend.model.UsersTable.update({ com.tekinflow.backend.model.UsersTable.id eq row[com.tekinflow.backend.model.UsersTable.id] }) {
                        it[twoFactorSecret] = com.tekinflow.backend.auth.TotpSecretVault.encrypt(secret)
                    }
                }
            }
        }
        DatabaseConfig.seedInitialAdminIfNeeded(passwordHasher, environment.log)
        DatabaseConfig.seedDemoUsersIfEnabled(passwordHasher, environment.log)
        if (System.getenv("SEED_DEMO_REQUESTS").equals("true", ignoreCase = true)) {
            DatabaseConfig.seedDemoRequestsIfNeeded(environment.log)
        } else {
            environment.log.info("Demo talep seed kapali. Acmak icin SEED_DEMO_REQUESTS=true verin.")
        }
    }

    val userRepository = UserRepository(passwordHasher)
    val notificationRepository = NotificationRepository()
    val auditRepository = AuditRepository()
    val idempotencyRepository = IdempotencyRepository()
    val requestRepository = RequestRepository(notificationRepository, RequestNumberGenerator())
    val webRoot = resolveWebRoot()

    install(CallLogging)
    configureSecurityHeaders()
    configureSerialization()
    configureRateLimiting()
    configureAuthentication(jwtConfig, userRepository)

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.statusCode, ErrorResponse(cause.publicMessage, cause.errorCode))
        }
        exception<BadRequestException> { call, cause ->
            this@module.environment.log.warn("Geçersiz istek gövdesi: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Geçersiz istek gövdesi", "BAD_REQUEST"))
        }
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Beklenmeyen sunucu hatası", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Sunucu hatası", "SERVER_ERROR"))
        }
    }

    routing {
        // Liveness only: no DB query, so a public probe cannot exhaust the
        // connection pool or confirm DB reachability to attackers.
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        authRoutes(
            application = this@module,
            userRepository = userRepository,
            passwordHasher = passwordHasher,
            jwtConfig = jwtConfig
        )
        userRoutes(userRepository)
        auditRoutes(auditRepository)
        requestRoutes(requestRepository, idempotencyRepository)
        notificationRoutes(notificationRepository)

        if (webRoot != null) {
            this@module.environment.log.info("Web panel served from ${webRoot.absolutePath}")
            staticFiles("/", webRoot) {
                default("index.html")
            }
        }
    }
}

private fun resolveWebRoot(): File? {
    val envRoot = System.getenv("WEB_ROOT")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let(::File)

    val candidates = listOfNotNull(
        envRoot,
        File("web/dist"),
        File("../web/dist"),
        File("dist")
    )

    return candidates
        .map { it.absoluteFile }
        .firstOrNull { File(it, "index.html").isFile }
}
