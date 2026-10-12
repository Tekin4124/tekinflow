package com.tekinflow.backend.util

import com.tekinflow.backend.model.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.response.respond
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private val serverZoneId: ZoneId = ZoneId.systemDefault()
private val trustProxyHeaders: Boolean = System.getenv("TRUST_PROXY_HEADERS").equals("true", ignoreCase = true)
private val trustedProxyCidrs: List<Ipv4Cidr> = System.getenv("TRUSTED_PROXY_CIDRS")
    ?.split(",")
    ?.mapNotNull { Ipv4Cidr.parse(it.trim()) }
    .orEmpty()

open class ApiException(
    val statusCode: HttpStatusCode,
    val publicMessage: String,
    val errorCode: String = statusCode.defaultErrorCode()
) : RuntimeException(publicMessage)

class ValidationException(
    message: String = "Geçersiz istek"
) : ApiException(HttpStatusCode.BadRequest, message, "VALIDATION_ERROR")

class AuthenticationFailureException(
    message: String = "Kimlik doğrulama başarısız"
) : ApiException(HttpStatusCode.Unauthorized, message, "AUTHENTICATION_FAILED")

class AuthorizationFailureException(
    message: String = "Yetkisiz işlem"
) : ApiException(HttpStatusCode.Forbidden, message, "FORBIDDEN")

class NotFoundException(
    message: String = "Kayıt bulunamadı"
) : ApiException(HttpStatusCode.NotFound, message, "NOT_FOUND")

class ConflictException(
    message: String = "İstek işlenemedi"
) : ApiException(HttpStatusCode.Conflict, message, "CONFLICT")

fun String?.clean(maxLength: Int, fieldRequired: Boolean = false): String? {
    val normalized = this?.trim()
    if (normalized.isNullOrEmpty()) {
        if (fieldRequired) throw ValidationException()
        return null
    }
    if (normalized.length > maxLength) throw ValidationException()
    return normalized
}

fun String?.cleanRequired(maxLength: Int): String = clean(maxLength, fieldRequired = true)!!

fun String?.toUuidOrValidationError(): UUID {
    val normalized = cleanRequired(64)
    return runCatching { UUID.fromString(normalized) }.getOrElse { throw ValidationException() }
}

fun String?.toLocalDateOrNull(): LocalDate? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(normalized) }.getOrElse { throw ValidationException() }
}

fun LocalDate?.toApiString(): String = this?.toString().orEmpty()

fun LocalDateTime.toEpochMillis(): Long = atZone(serverZoneId).toInstant().toEpochMilli()

fun Instant.toEpochMillis(): Long = toEpochMilli()

fun ApplicationCall.clientIp(): String {
    val remoteHost = request.origin.remoteHost.takeIf { it.isNotBlank() } ?: "unknown"
    if (!trustProxyHeaders || !remoteHost.isTrustedProxyAddress()) return remoteHost

    // Reverse proxy zinciri arkasinda (Nginx -> Caddy -> backend) gercek istemci
    // IP'si proxy header'larindan okunur. ONCELIK X-Real-IP'dedir: kenardaki
    // Nginx bunu gercek TCP peer'i ($remote_addr) ile EZER, bu yuzden istemci
    // tarafindan sahtelenemez. Yedek olarak X-Forwarded-For kullanilirsa, EN
    // SAGDAKI deger alinir (kenar proxy'nin ekledigi hop = gercek istemci);
    // en soldaki deger istemcinin gonderdigi, sahtelenebilir degerdir ve
    // KULLANILMAZ. Eski davranis (en soldaki XFF) rate-limit/audit IP'sinin
    // sahtelenmesine izin veriyordu.
    val realIp = request.header("X-Real-IP")?.trim()?.takeIf { it.isNotEmpty() }
    if (realIp != null) return realIp

    val forwarded = request.header("X-Forwarded-For")
        ?.split(",")
        ?.map { it.trim() }
        ?.lastOrNull { it.isNotEmpty() }
    return forwarded ?: remoteHost
}

private fun String.isTrustedProxyAddress(): Boolean {
    if (this == "127.0.0.1" ||
        this == "0:0:0:0:0:0:0:1" ||
        this == "::1" ||
        this.equals("localhost", ignoreCase = true)
    ) {
        return true
    }

    val address = toIpv4Long() ?: return false
    return trustedProxyCidrs.any { it.contains(address) }
}

private data class Ipv4Cidr(
    val network: Long,
    val mask: Long
) {
    fun contains(address: Long): Boolean = (address and mask) == network

    companion object {
        fun parse(value: String): Ipv4Cidr? {
            if (value.isBlank()) return null
            val addressPart = value.substringBefore("/")
            val prefix = value.substringAfter("/", "32").toIntOrNull()?.takeIf { it in 0..32 } ?: return null
            val address = addressPart.toIpv4Long() ?: return null
            val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFF_FFFFL
            return Ipv4Cidr(address and mask, mask)
        }
    }
}

private fun String.toIpv4Long(): Long? {
    val parts = split(".")
    if (parts.size != 4) return null
    return parts.fold(0L) { acc, part ->
        val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        (acc shl 8) or octet.toLong()
    }
}

suspend inline fun <reified T : Any> ApplicationCall.receiveValidated(
    crossinline receiveBlock: suspend ApplicationCall.() -> T
): T {
    return try {
        this.receiveBlock()
    } catch (_: ContentTransformationException) {
        throw ValidationException()
    }
}

fun JWTPrincipal.requireClaim(name: String): String {
    return getClaim(name, String::class) ?: throw AuthenticationFailureException()
}

suspend fun ApplicationCall.respondApiError(statusCode: HttpStatusCode, message: String) {
    respond(statusCode, ErrorResponse(message, statusCode.defaultErrorCode()))
}

fun HttpStatusCode.defaultErrorCode(): String {
    return when (this) {
        HttpStatusCode.BadRequest -> "VALIDATION_ERROR"
        HttpStatusCode.Unauthorized -> "AUTHENTICATION_FAILED"
        HttpStatusCode.Forbidden -> "FORBIDDEN"
        HttpStatusCode.NotFound -> "NOT_FOUND"
        HttpStatusCode.Conflict -> "CONFLICT"
        HttpStatusCode.TooManyRequests -> "RATE_LIMITED"
        HttpStatusCode.PayloadTooLarge -> "PAYLOAD_TOO_LARGE"
        else -> "GENERIC_ERROR"
    }
}
