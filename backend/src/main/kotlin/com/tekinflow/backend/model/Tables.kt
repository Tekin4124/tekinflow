package com.tekinflow.backend.model

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import org.postgresql.util.PGobject

object UsersTable : UUIDTable("users") {
    val username = varchar("username", 50).uniqueIndex()
    val fullName = varchar("full_name", 100)
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 20, UserRole::class)
    val licensePlate = varchar("license_plate", 20).nullable()
    val isActive = bool("is_active").default(true)
    val isSuperAdmin = bool("is_super_admin").default(false)
    val fcmToken = text("fcm_token").nullable()
    val fcmTokenUpdatedAt = timestamp("fcm_token_updated_at").nullable()
    val tokenVersion = integer("token_version").default(0)
    val failedAttempts = integer("failed_attempts").default(0)
    val lockedUntil = timestamp("locked_until").nullable()
    val lockoutCount = integer("lockout_count").default(0)
    val twoFactorSecret = text("two_factor_secret").nullable()
    val twoFactorEnabled = bool("two_factor_enabled").default(false)
    val twoFactorConfirmedAt = timestamp("two_factor_confirmed_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PurchaseRequestsTable : UUIDTable("purchase_requests") {
    val requestNumber = varchar("request_number", 20).uniqueIndex()
    val itemToPurchase = varchar("item_to_purchase", 255)
    val companyName = varchar("company_name", 255)
    val companyAddress = text("company_address").nullable()
    val contactNumber = varchar("contact_number", 50).nullable()
    val contactPersonName = varchar("contact_person_name", 100).nullable()
    val productDimensions = varchar("product_dimensions", 100).nullable()
    val productWeight = varchar("product_weight", 100).nullable()
    val packageCount = integer("package_count").nullable()
    val urgencyLevel = enumerationByName("urgency_level", 10, UrgencyLevel::class).default(UrgencyLevel.NORMAL)
    val purchaseDate = date("purchase_date").nullable()
    val notes = text("notes").nullable()
    val licensePlate = varchar("license_plate", 20).nullable()
    val spotDriverName = varchar("spot_driver_name", 100).nullable()
    val spotDriverIdentityNumber = varchar("spot_driver_identity_number", 20).nullable()
    val spotDriverCompanyName = varchar("spot_driver_company_name", 100).nullable()
    val spotDriverPhone = varchar("spot_driver_phone", 50).nullable()
    val deliveredTo = varchar("delivered_to", 200).nullable()
    val actualPurchaseDate = timestamp("actual_purchase_date").nullable()
    val materialStatus = enumerationByName("material_status", 20, MaterialStatus::class).default(MaterialStatus.IN_PROGRESS)
    val assignedDriverId = reference("assigned_driver_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val assignedPurchasingStaffId = reference("assigned_purchasing_staff_id", UsersTable)
    val cancellationReason = text("cancellation_reason").nullable()
    val cancelledBy = reference("cancelled_by", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val objectionReason = text("objection_reason").nullable()
    val hasObjection = bool("has_objection").default(false)
    val objectionBy = reference("objection_by", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val redirectedToRequestId = reference("redirected_to_request_id", PurchaseRequestsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PurchaseRequestItemsTable : UUIDTable("purchase_request_items") {
    val requestId = reference("request_id", PurchaseRequestsTable, onDelete = ReferenceOption.CASCADE)
    val itemName = varchar("item_name", 255)
    val quantity = decimal("quantity", 12, 3)
    val unit = enumerationByName("unit", 20, MaterialUnit::class).default(MaterialUnit.KG)
    val packageCount = integer("package_count").nullable()
    val dimensionsNote = varchar("dimensions_note", 255).nullable()
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestamp("created_at")
}

object NotificationsTable : UUIDTable("notifications") {
    val message = varchar("message", 500)
    val targetUserId = reference("target_user_id", UsersTable)
    val requestId = reference("request_id", PurchaseRequestsTable, onDelete = ReferenceOption.CASCADE).nullable()
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at")
}

object RequestSeenTable : Table("request_seen") {
    val requestId = reference("request_id", PurchaseRequestsTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val seenAt = timestamp("seen_at")

    override val primaryKey = PrimaryKey(requestId, userId)
}

object UserPreferencesTable : Table("user_preferences") {
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val key = varchar("key", 100)
    val value = jsonb("value")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId, key)
}

private class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value ?: "{}"
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any = PGobject().apply {
        type = "jsonb"
        this.value = value
    }

    override fun nonNullValueToString(value: String): String = "'${value.replace("'", "''")}'::jsonb"
}

private fun Table.jsonb(name: String) = registerColumn<String>(name, JsonbColumnType())

object RefreshTokensTable : Table("refresh_tokens") {
    val token = varchar("token", 64)
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val parentToken = varchar("parent_token", 64).nullable()
    val revokedAt = timestamp("revoked_at").nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(token)
}

object IdempotencyKeysTable : Table("idempotency_keys") {
    val requestHash = varchar("request_hash", 64).nullable()
    val responseJson = text("response_json").nullable()
    val key = uuid("key")
    val userId = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val requestPath = varchar("request_path", 255)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(key)
}

object RequestAuditTable : UUIDTable("request_audit") {
    val requestId = reference("request_id", PurchaseRequestsTable, onDelete = ReferenceOption.CASCADE)
    val actorUserId = reference("actor_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val action = varchar("action", 50)
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val ipAddress = varchar("ip_address", 64).nullable()
    val createdAt = timestamp("created_at")
}

object RequestStatusHistoryTable : UUIDTable("request_status_history") {
    val requestId = reference("request_id", PurchaseRequestsTable, onDelete = ReferenceOption.CASCADE)
    val actorUserId = reference("actor_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val oldStatus = enumerationByName("old_status", 20, MaterialStatus::class).nullable()
    val newStatus = enumerationByName("new_status", 20, MaterialStatus::class)
    val createdAt = timestamp("created_at")
}

object AuditLogTable : UUIDTable("audit_log") {
    val actorUserId = reference("actor_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val actorUsername = varchar("actor_username", 50).nullable()
    val actorFullName = varchar("actor_full_name", 100).nullable()
    val action = varchar("action", 80)
    val entityType = varchar("entity_type", 40)
    val entityId = uuid("entity_id").nullable()
    val entityLabel = varchar("entity_label", 255).nullable()
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val ipAddress = varchar("ip_address", 64).nullable()
    val createdAt = timestamp("created_at")
}
