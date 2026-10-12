package com.tekinflow.backend.repository

import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.AuditLogResponse
import com.tekinflow.backend.model.AuditLogTable
import com.tekinflow.backend.model.UsersTable
import com.tekinflow.backend.util.toEpochMillis
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant
import java.util.UUID

object AuditLogWriter {
    fun record(
        actorId: UUID?,
        action: String,
        entityType: String,
        entityId: UUID?,
        entityLabel: String?,
        oldValue: String?,
        newValue: String?,
        ipAddress: String?,
        createdAt: Instant = Instant.now()
    ) {
        val actor = actorId?.let { id ->
            UsersTable
                .selectAll()
                .where { UsersTable.id eq id }
                .singleOrNull()
        }

        AuditLogTable.insert {
            it[AuditLogTable.actorUserId] = actorId
            it[AuditLogTable.actorUsername] = actor?.get(UsersTable.username)?.take(50)
            it[AuditLogTable.actorFullName] = actor?.get(UsersTable.fullName)?.take(100)
            it[AuditLogTable.action] = action.take(80)
            it[AuditLogTable.entityType] = entityType.take(40)
            it[AuditLogTable.entityId] = entityId
            it[AuditLogTable.entityLabel] = entityLabel?.take(255)
            it[AuditLogTable.oldValue] = oldValue?.take(4_000)
            it[AuditLogTable.newValue] = newValue?.take(4_000)
            it[AuditLogTable.ipAddress] = ipAddress?.take(64)
            it[AuditLogTable.createdAt] = createdAt
        }
    }
}

class AuditRepository {
    suspend fun list(
        limit: Int,
        entityType: String?,
        entityId: UUID?
    ): List<AuditLogResponse> = dbQuery {
        AuditLogTable
            .selectAll()
            .apply {
                entityType?.takeIf { it.isNotBlank() }?.let { type ->
                    andWhere { AuditLogTable.entityType eq type.take(40) }
                }
                entityId?.let { id ->
                    andWhere { AuditLogTable.entityId eq id }
                }
            }
            .orderBy(AuditLogTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 500))
            .map { it.toAuditLogResponse() }
    }

    private fun ResultRow.toAuditLogResponse() = AuditLogResponse(
        id = this[AuditLogTable.id].value.toString(),
        actorUserId = this[AuditLogTable.actorUserId]?.value?.toString(),
        actorUsername = this[AuditLogTable.actorUsername],
        actorFullName = this[AuditLogTable.actorFullName],
        action = this[AuditLogTable.action],
        entityType = this[AuditLogTable.entityType],
        entityId = this[AuditLogTable.entityId]?.toString(),
        entityLabel = this[AuditLogTable.entityLabel],
        oldValue = this[AuditLogTable.oldValue],
        newValue = this[AuditLogTable.newValue],
        ipAddress = this[AuditLogTable.ipAddress],
        createdAt = this[AuditLogTable.createdAt].toEpochMillis()
    )
}
