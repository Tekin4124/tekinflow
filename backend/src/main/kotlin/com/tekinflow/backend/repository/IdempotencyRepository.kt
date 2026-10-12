package com.tekinflow.backend.repository

import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.IdempotencyKeysTable
import com.tekinflow.backend.util.ApiException
import com.tekinflow.backend.util.ConflictException
import com.tekinflow.backend.util.ValidationException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

class IdempotencyRepository {
    suspend fun <T> execute(key: String?, userId: UUID, requestPath: String, requestBody: String,
        serializer: KSerializer<T>, operation: suspend () -> T): T = dbQuery {
        val uuid = runCatching { UUID.fromString(key?.trim()) }.getOrElse { throw ValidationException("Idempotency anahtarı gerekli") }
        if (requestPath.length > 255) throw ValidationException()
        val digest = MessageDigest.getInstance("SHA-256").digest(requestBody.toByteArray()).joinToString("") { "%02x".format(it) }
        val inserted = IdempotencyKeysTable.insertIgnore {
            it[IdempotencyKeysTable.key] = uuid
            it[IdempotencyKeysTable.userId] = userId
            it[IdempotencyKeysTable.requestPath] = requestPath
            it[IdempotencyKeysTable.requestHash] = digest
            it[IdempotencyKeysTable.createdAt] = Instant.now()
        }.insertedCount > 0
        if (!inserted) {
            val existing = IdempotencyKeysTable.selectAll().where { IdempotencyKeysTable.key eq uuid }.single()
            if (existing[IdempotencyKeysTable.userId].value != userId || existing[IdempotencyKeysTable.requestPath] != requestPath || existing[IdempotencyKeysTable.requestHash] != digest) {
                throw ConflictException("Anahtar farklı bir işleme ait")
            }
            val response = existing[IdempotencyKeysTable.responseJson]
                ?: throw ApiException(HttpStatusCode.fromValue(425), "İşlem henüz tamamlanmadı", "RETRY_LATER")
            return@dbQuery Json.decodeFromString(serializer, response)
        }
        // Reservation, business mutations and cached success share the same transaction.
        // Any exception rolls all of them back, leaving the key reusable after failure.
        val result = operation()
        IdempotencyKeysTable.update({ IdempotencyKeysTable.key eq uuid }) {
            it[responseJson] = Json.encodeToString(serializer, result)
        }
        result
    }
}
