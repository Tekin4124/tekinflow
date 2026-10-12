package com.tekinflow.backend.repository

import com.tekinflow.backend.auth.PasswordHasher
import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.ChangePasswordRequest
import com.tekinflow.backend.model.CreateUserRequest
import com.tekinflow.backend.model.IdempotencyKeysTable
import com.tekinflow.backend.model.NotificationsTable
import com.tekinflow.backend.model.PurchaseRequestsTable
import com.tekinflow.backend.model.RefreshTokensTable
import com.tekinflow.backend.model.RequestAuditTable
import com.tekinflow.backend.model.RequestSeenTable
import com.tekinflow.backend.model.RequestStatusHistoryTable
import com.tekinflow.backend.model.UpdateUserRequest
import com.tekinflow.backend.model.UserPreferenceResponse
import com.tekinflow.backend.model.UserPreferencesTable
import com.tekinflow.backend.model.UserResponse
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.model.UsersTable
import com.tekinflow.backend.util.AuthenticationFailureException
import com.tekinflow.backend.util.AuthorizationFailureException
import com.tekinflow.backend.util.ConflictException
import com.tekinflow.backend.util.NotFoundException
import com.tekinflow.backend.util.ValidationException
import com.tekinflow.backend.util.clean
import com.tekinflow.backend.util.cleanRequired
import com.tekinflow.backend.util.toEpochMillis
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class AuthUser(
    val id: UUID,
    val username: String,
    val fullName: String,
    val passwordHash: String,
    val role: UserRole,
    val licensePlate: String?,
    val isActive: Boolean,
    val isSuperAdmin: Boolean,
    val tokenVersion: Int,
    val failedAttempts: Int,
    val lockedUntil: Instant?,
    val twoFactorSecret: String?,
    val twoFactorEnabled: Boolean
)

data class RefreshTokenRecord(
    val token: String,
    val userId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?
)

sealed class RefreshTokenRotationResult {
    data class Success(
        val user: AuthUser,
        val refreshToken: UUID
    ) : RefreshTokenRotationResult()

    data object InvalidOrExpired : RefreshTokenRotationResult()
    data object ReuseDetected : RefreshTokenRotationResult()
}

class UserRepository(
    private val passwordHasher: PasswordHasher
) {

    suspend fun countUsers(): Long = dbQuery {
        UsersTable.selectAll().count()
    }

    suspend fun listUsers(actorId: UUID): List<UserResponse> = dbQuery {
        val actor = findActorRow(actorId)
        val visibleRoles = visibleRolesFor(actor)
        UsersTable
            .selectAll()
            .orderBy(UsersTable.createdAt, SortOrder.ASC)
            .filter { isSuperAdmin(actor) || it[UsersTable.role] in visibleRoles }
            .map { it.toUserResponse() }
    }

    suspend fun listActiveDrivers(): List<UserResponse> = dbQuery {
        UsersTable
            .selectAll()
            .where {
                (UsersTable.role eq UserRole.DRIVER) and
                    (UsersTable.isActive eq true)
            }
            .orderBy(UsersTable.fullName, SortOrder.ASC)
            .map { it.toUserResponse() }
    }

    suspend fun findActiveAuthUserByUsername(username: String): AuthUser? = dbQuery {
        val normalized = normalizeUsername(username)
        UsersTable
            .selectAll()
            .where { (UsersTable.username eq normalized) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?.toAuthUser()
    }

    suspend fun findActiveUserById(id: UUID): AuthUser? = dbQuery {
        UsersTable
            .selectAll()
            .where { (UsersTable.id eq id) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?.toAuthUser()
    }

    suspend fun findUserById(id: UUID): UserResponse? = dbQuery {
        UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUserResponse()
    }

    suspend fun updateFcmToken(userId: UUID, token: String?) = dbQuery {
        val safeToken = token.clean(4_096)
        val updatedRows = UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.fcmToken] = safeToken
            it[UsersTable.fcmTokenUpdatedAt] = if (safeToken == null) null else Instant.now()
            it[UsersTable.updatedAt] = Instant.now()
        }
        if (updatedRows == 0) throw NotFoundException()
    }

    suspend fun getPreference(userId: UUID, key: String): UserPreferenceResponse = dbQuery {
        val safeKey = key.cleanPreferenceKey()
        val value = UserPreferencesTable
            .selectAll()
            .where { (UserPreferencesTable.userId eq userId) and (UserPreferencesTable.key eq safeKey) }
            .singleOrNull()
            ?.get(UserPreferencesTable.value)
            ?: "{}"
        UserPreferenceResponse(safeKey, value)
    }

    suspend fun putPreference(userId: UUID, key: String, value: String): UserPreferenceResponse = dbQuery {
        val safeKey = key.cleanPreferenceKey()
        val safeValue = value.cleanRequired(20_000)
        val now = Instant.now()
        UserPreferencesTable.insertIgnore {
            it[UserPreferencesTable.userId] = userId
            it[UserPreferencesTable.key] = safeKey
            it[UserPreferencesTable.value] = safeValue
            it[UserPreferencesTable.updatedAt] = now
        }
        UserPreferencesTable.update({
            (UserPreferencesTable.userId eq userId) and (UserPreferencesTable.key eq safeKey)
        }) {
            it[UserPreferencesTable.value] = safeValue
            it[UserPreferencesTable.updatedAt] = now
        }
        UserPreferenceResponse(safeKey, safeValue)
    }

    suspend fun createUser(actorId: UUID, request: CreateUserRequest, actorIp: String?): UserResponse = dbQuery {
        val actor = findActorRow(actorId)
        val username = normalizeUsername(request.username.cleanRequired(50))
        val fullName = request.fullName.cleanRequired(100)
        val password = request.password.trim()
        validatePasswordPolicy(password)
        val licensePlate = request.licensePlate.clean(20)

        validateAssignableRole(request.role)
        ensureActorCanAssignRole(actor, request.role)
        validateDriverPlate(request.role, licensePlate)
        ensureUsernameAvailable(username)

        val now = Instant.now()
        val userId = UsersTable.insertAndGetId {
            it[UsersTable.username] = username
            it[UsersTable.fullName] = fullName
            it[UsersTable.passwordHash] = passwordHasher.hash(password)
            it[UsersTable.role] = request.role
            it[UsersTable.licensePlate] = if (request.role == UserRole.DRIVER) licensePlate else null
            it[UsersTable.isActive] = true
            it[UsersTable.isSuperAdmin] = false
            it[UsersTable.tokenVersion] = 0
            it[UsersTable.failedAttempts] = 0
            it[UsersTable.lockedUntil] = null
            it[UsersTable.createdAt] = now
            it[UsersTable.updatedAt] = now
        }.value

        val created = findUserRowById(userId)
        AuditLogWriter.record(
            actorId = actorId,
            action = "CREATE_USER",
            entityType = "USER",
            entityId = userId,
            entityLabel = created[UsersTable.username],
            oldValue = null,
            newValue = created.userSummary(),
            ipAddress = actorIp,
            createdAt = now
        )
        created.toUserResponse()
    }

    suspend fun updateUser(id: UUID, actorId: UUID, request: UpdateUserRequest, actorIp: String?): UserResponse = dbQuery {
        val actor = findActorRow(actorId)
        val existing = UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?: throw NotFoundException()

        val newUsername = request.username.clean(50)?.let(::normalizeUsername) ?: existing[UsersTable.username]
        val newFullName = request.fullName.clean(100) ?: existing[UsersTable.fullName]
        val newRole = request.role ?: existing[UsersTable.role]
        val explicitPlate = request.licensePlate?.trim()
        val newPlate = when {
            newRole != UserRole.DRIVER -> null
            explicitPlate != null -> explicitPlate.clean(20, fieldRequired = true)
            else -> existing[UsersTable.licensePlate]
        }
        if (!existing[UsersTable.isSuperAdmin]) {
            validateAssignableRole(newRole)
        }
        ensureActorCanManageUser(actor, existing)
        if (newRole != existing[UsersTable.role]) {
            ensureActorCanAssignRole(actor, newRole)
        }
        validateDriverPlate(newRole, newPlate)
        ensureAdminRoleCanChange(
            userId = id,
            actorId = actorId,
            currentRole = existing[UsersTable.role],
            currentActive = existing[UsersTable.isActive],
            currentSuperAdmin = existing[UsersTable.isSuperAdmin],
            newRole = newRole
        )

        if (newUsername != existing[UsersTable.username]) {
            ensureUsernameAvailable(newUsername, excludeUserId = id)
        }

        val password = request.password?.trim()
        val passwordChanged = password != null && password.isNotEmpty()
        if (passwordChanged) {
            validatePasswordPolicy(password!!)
        }

        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.username] = newUsername
            it[UsersTable.fullName] = newFullName
            it[UsersTable.role] = newRole
            it[UsersTable.licensePlate] = newPlate
            password?.takeIf { value -> value.isNotEmpty() }?.let { value ->
                it[UsersTable.passwordHash] = passwordHasher.hash(value)
                it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
                it[UsersTable.twoFactorSecret] = null
                it[UsersTable.twoFactorEnabled] = false
                it[UsersTable.twoFactorConfirmedAt] = null
            }
            it[UsersTable.updatedAt] = Instant.now()
        }

        val updated = findUserRowById(id)
        AuditLogWriter.record(
            actorId = actorId,
            action = "UPDATE_USER",
            entityType = "USER",
            entityId = id,
            entityLabel = updated[UsersTable.username],
            oldValue = existing.userSummary(),
            newValue = "${updated.userSummary()},passwordChanged=$passwordChanged",
            ipAddress = actorIp
        )
        updated.toUserResponse()
    }

    suspend fun deactivateUser(id: UUID, actorId: UUID, actorIp: String?) = dbQuery {
        if (id == actorId) {
            throw ConflictException("Kendi kullanicinizi pasif edemezsiniz")
        }

        val actor = findActorRow(actorId)
        val existing = UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?: throw NotFoundException()

        ensureActorCanManageUser(actor, existing)

        if (isSuperAdmin(existing)) {
            throw ConflictException("Admin kullanicisi pasif edilemez")
        }

        if (existing[UsersTable.role] == UserRole.ADMIN && existing[UsersTable.isActive] && activeAdminCount() <= 1) {
            throw ConflictException("Son admin pasif edilemez")
        }

        val updatedRows = UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.isActive] = false
            it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
            it[UsersTable.updatedAt] = Instant.now()
        }
        if (updatedRows == 0) throw NotFoundException()
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq id }
        AuditLogWriter.record(
            actorId = actorId,
            action = "DEACTIVATE_USER",
            entityType = "USER",
            entityId = id,
            entityLabel = existing[UsersTable.username],
            oldValue = existing.userSummary(),
            newValue = "active=false",
            ipAddress = actorIp
        )
    }

    suspend fun activateUser(id: UUID, actorId: UUID, actorIp: String?): UserResponse = dbQuery {
        val actor = findActorRow(actorId)
        val existing = findUserRowById(id)
        ensureActorCanManageUser(actor, existing)
        if (isSuperAdmin(existing)) {
            throw ConflictException("Admin kullanicisi zaten aktif tutulmalidir")
        }

        UsersTable.update({ UsersTable.id eq id }) {
            it[UsersTable.isActive] = true
            it[UsersTable.failedAttempts] = 0
            it[UsersTable.lockedUntil] = null
            it[UsersTable.lockoutCount] = 0
            it[UsersTable.updatedAt] = Instant.now()
        }

        val updated = findUserRowById(id)
        AuditLogWriter.record(
            actorId = actorId,
            action = "ACTIVATE_USER",
            entityType = "USER",
            entityId = id,
            entityLabel = updated[UsersTable.username],
            oldValue = existing.userSummary(),
            newValue = updated.userSummary(),
            ipAddress = actorIp
        )
        updated.toUserResponse()
    }

    suspend fun deleteUser(id: UUID, actorId: UUID, actorIp: String?) = dbQuery {
        ensureSuperAdminActor(actorId)
        val existing = findUserRowById(id)
        if (isSuperAdmin(existing)) {
            throw ConflictException("Admin kullanicisi silinemez")
        }

        val now = Instant.now()
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq id }
        IdempotencyKeysTable.deleteWhere { IdempotencyKeysTable.userId eq id }
        NotificationsTable.deleteWhere { NotificationsTable.targetUserId eq id }
        RequestSeenTable.deleteWhere { RequestSeenTable.userId eq id }

        RequestAuditTable.update({ RequestAuditTable.actorUserId eq id }) {
            it[RequestAuditTable.actorUserId] = null
        }
        RequestStatusHistoryTable.update({ RequestStatusHistoryTable.actorUserId eq id }) {
            it[RequestStatusHistoryTable.actorUserId] = null
        }

        PurchaseRequestsTable.update({ PurchaseRequestsTable.assignedDriverId eq id }) {
            it[PurchaseRequestsTable.assignedDriverId] = null
            it[PurchaseRequestsTable.licensePlate] = null
            it[PurchaseRequestsTable.spotDriverName] = null
            it[PurchaseRequestsTable.spotDriverIdentityNumber] = null
            it[PurchaseRequestsTable.spotDriverCompanyName] = null
            it[PurchaseRequestsTable.updatedAt] = now
        }
        PurchaseRequestsTable.update({ PurchaseRequestsTable.cancelledBy eq id }) {
            it[PurchaseRequestsTable.cancelledBy] = null
            it[PurchaseRequestsTable.updatedAt] = now
        }
        PurchaseRequestsTable.update({ PurchaseRequestsTable.objectionBy eq id }) {
            it[PurchaseRequestsTable.objectionBy] = null
            it[PurchaseRequestsTable.updatedAt] = now
        }
        PurchaseRequestsTable.update({ PurchaseRequestsTable.assignedPurchasingStaffId eq id }) {
            it[PurchaseRequestsTable.assignedPurchasingStaffId] = actorId
            it[PurchaseRequestsTable.updatedAt] = now
        }

        val deletedRows = UsersTable.deleteWhere { UsersTable.id eq id }
        if (deletedRows == 0) throw NotFoundException()
        AuditLogWriter.record(
            actorId = actorId,
            action = "DELETE_USER",
            entityType = "USER",
            entityId = id,
            entityLabel = existing[UsersTable.username],
            oldValue = existing.userSummary(),
            newValue = null,
            ipAddress = actorIp,
            createdAt = now
        )
    }

    suspend fun changeOwnPassword(userId: UUID, request: ChangePasswordRequest, actorIp: String?) = dbQuery {
        val currentPassword = request.currentPassword.trim()
        val newPassword = request.newPassword.trim()
        if (currentPassword.isEmpty() || newPassword.isEmpty()) {
            throw ValidationException("Sifre bos olamaz")
        }
        validatePasswordPolicy(newPassword)

        val existing = UsersTable
            .selectAll()
            .where { (UsersTable.id eq userId) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?: throw AuthenticationFailureException()

        if (!passwordHasher.verify(currentPassword, existing[UsersTable.passwordHash])) {
            throw AuthenticationFailureException()
        }
        if (passwordHasher.verify(newPassword, existing[UsersTable.passwordHash])) {
            throw ConflictException("Yeni sifre mevcut sifreden farkli olmali")
        }

        val now = Instant.now()
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.passwordHash] = passwordHasher.hash(newPassword)
            it[UsersTable.failedAttempts] = 0
            it[UsersTable.lockedUntil] = null
            it[UsersTable.lockoutCount] = 0
            it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
            it[UsersTable.updatedAt] = now
        }
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
        AuditLogWriter.record(
            actorId = userId,
            action = "CHANGE_OWN_PASSWORD",
            entityType = "USER",
            entityId = userId,
            entityLabel = existing[UsersTable.username],
            oldValue = null,
            newValue = "passwordChanged=true",
            ipAddress = actorIp,
            createdAt = now
        )
    }

    suspend fun enableTwoFactor(userId: UUID, secret: String, actorIp: String?) = dbQuery {
        val existing = findUserRowById(userId)
        val now = Instant.now()
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.twoFactorSecret] = com.tekinflow.backend.auth.TotpSecretVault.encrypt(secret.trim().uppercase())
            it[UsersTable.twoFactorEnabled] = true
            it[UsersTable.twoFactorConfirmedAt] = now
            it[UsersTable.updatedAt] = now
        }
        AuditLogWriter.record(
            actorId = userId,
            action = "ENABLE_2FA",
            entityType = "USER",
            entityId = userId,
            entityLabel = existing[UsersTable.username],
            oldValue = null,
            newValue = "twoFactorEnabled=true",
            ipAddress = actorIp,
            createdAt = now
        )
    }

    suspend fun invalidateRefreshTokensForUser(userId: UUID) = dbQuery {
        val now = Instant.now()
        RefreshTokensTable.update({ (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull() }) {
            it[RefreshTokensTable.revokedAt] = now
        }
        bumpTokenVersion(userId)
    }

    suspend fun createRefreshToken(userId: UUID, expiresAt: Instant, parentToken: UUID? = null): UUID = dbQuery {
        val token = UUID.randomUUID()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.token] = hashRefreshToken(token)
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.parentToken] = parentToken?.let(::hashRefreshToken)
            it[RefreshTokensTable.revokedAt] = null
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[RefreshTokensTable.createdAt] = Instant.now()
        }
        token
    }

    suspend fun recordFailedLogin(username: String): Int = dbQuery {
        val normalized = normalizeUsername(username)
        val row = UsersTable
            .selectAll()
            .where { (UsersTable.username eq normalized) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?: return@dbQuery 0

        val now = Instant.now()
        val updatedRows = UsersTable.update({
            (UsersTable.id eq row[UsersTable.id].value) and (UsersTable.isActive eq true)
        }) {
            it[UsersTable.failedAttempts] = UsersTable.failedAttempts + 1
            it[UsersTable.updatedAt] = now
        }
        if (updatedRows == 0) return@dbQuery 0

        val current = UsersTable
            .selectAll()
            .where { UsersTable.id eq row[UsersTable.id].value }
            .single()
        val failedAttempts = current[UsersTable.failedAttempts]

        if (failedAttempts >= maxFailedLoginAttempts) {
            val lockoutCount = current[UsersTable.lockoutCount]
            UsersTable.update({ UsersTable.id eq row[UsersTable.id].value }) {
                it[UsersTable.failedAttempts] = 0
                it[UsersTable.lockoutCount] = lockoutCount + 1
                it[UsersTable.updatedAt] = now
                when (lockoutCount) {
                    0 -> it[UsersTable.lockedUntil] = now.plusSeconds(firstLockoutSeconds)
                    1 -> it[UsersTable.lockedUntil] = now.plusSeconds(secondLockoutSeconds)
                    // Third lockout: deactivate the account (kept, not deleted).
                    // A super admin must reactivate it.
                    else -> {
                        it[UsersTable.isActive] = false
                        it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
                    }
                }
            }
        }

        failedAttempts
    }

    suspend fun resetLoginFailures(userId: UUID) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.failedAttempts] = 0
            it[UsersTable.lockedUntil] = null
            it[UsersTable.lockoutCount] = 0
            it[UsersTable.updatedAt] = Instant.now()
        }
    }

    suspend fun rotateRefreshToken(token: UUID, expiresAt: Instant): RefreshTokenRotationResult = dbQuery {
        val now = Instant.now()
        val tokenHash = hashRefreshToken(token)
        val tokenRow = RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.token eq tokenHash }
            .singleOrNull()
            ?: return@dbQuery RefreshTokenRotationResult.InvalidOrExpired

        val userId = tokenRow[RefreshTokensTable.userId].value
        if (tokenRow[RefreshTokensTable.revokedAt] != null) {
            revokeActiveRefreshTokensForUser(userId, now)
            return@dbQuery RefreshTokenRotationResult.ReuseDetected
        }

        if (tokenRow[RefreshTokensTable.expiresAt].isBefore(now)) {
            revokeRefreshToken(tokenHash, now)
            return@dbQuery RefreshTokenRotationResult.InvalidOrExpired
        }

        val revokedRows = RefreshTokensTable.update({
            (RefreshTokensTable.token eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[RefreshTokensTable.revokedAt] = now
        }
        if (revokedRows == 0) {
            revokeActiveRefreshTokensForUser(userId, now)
            return@dbQuery RefreshTokenRotationResult.ReuseDetected
        }

        val user = UsersTable
            .selectAll()
            .where { (UsersTable.id eq userId) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?.toAuthUser()
            ?: return@dbQuery RefreshTokenRotationResult.InvalidOrExpired

        val newToken = UUID.randomUUID()
        RefreshTokensTable.insert {
            it[RefreshTokensTable.token] = hashRefreshToken(newToken)
            it[RefreshTokensTable.userId] = user.id
            it[RefreshTokensTable.parentToken] = tokenHash
            it[RefreshTokensTable.revokedAt] = null
            it[RefreshTokensTable.expiresAt] = expiresAt
            it[RefreshTokensTable.createdAt] = now
        }

        RefreshTokenRotationResult.Success(user, newToken)
    }

    suspend fun findRefreshToken(token: UUID): RefreshTokenRecord? = dbQuery {
        val tokenHash = hashRefreshToken(token)
        RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.token eq tokenHash }
            .singleOrNull()
            ?.let { row ->
                RefreshTokenRecord(
                    token = row[RefreshTokensTable.token],
                    userId = row[RefreshTokensTable.userId].value,
                    expiresAt = row[RefreshTokensTable.expiresAt],
                    revokedAt = row[RefreshTokensTable.revokedAt]
                )
            }
    }

    suspend fun deleteRefreshToken(token: UUID) = dbQuery {
        revokeRefreshTokenAndBumpAccessVersion(hashRefreshToken(token), Instant.now())
    }

    private fun revokeRefreshToken(tokenHash: String, revokedAt: Instant) {
        RefreshTokensTable.update({ (RefreshTokensTable.token eq tokenHash) and RefreshTokensTable.revokedAt.isNull() }) {
            it[RefreshTokensTable.revokedAt] = revokedAt
        }
    }

    private fun revokeRefreshTokenAndBumpAccessVersion(tokenHash: String, revokedAt: Instant) {
        val tokenRow = RefreshTokensTable
            .selectAll()
            .where { RefreshTokensTable.token eq tokenHash }
            .singleOrNull()
        val updatedRows = RefreshTokensTable.update({
            (RefreshTokensTable.token eq tokenHash) and RefreshTokensTable.revokedAt.isNull()
        }) {
            it[RefreshTokensTable.revokedAt] = revokedAt
        }
        if (updatedRows > 0 && tokenRow != null) {
            bumpTokenVersion(tokenRow[RefreshTokensTable.userId].value)
        }
    }

    private fun hashRefreshToken(token: UUID): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toString().toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun revokeActiveRefreshTokensForUser(userId: UUID, revokedAt: Instant) {
        RefreshTokensTable.update({ (RefreshTokensTable.userId eq userId) and RefreshTokensTable.revokedAt.isNull() }) {
            it[RefreshTokensTable.revokedAt] = revokedAt
        }
        bumpTokenVersion(userId)
    }

    private fun bumpTokenVersion(userId: UUID) {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[UsersTable.tokenVersion] = UsersTable.tokenVersion + 1
            it[UsersTable.updatedAt] = Instant.now()
        }
    }

    private fun validateDriverPlate(role: UserRole, licensePlate: String?) {
        if (role == UserRole.DRIVER && licensePlate.isNullOrBlank()) {
            throw ValidationException()
        }
    }

    private fun validateAssignableRole(role: UserRole) {
        if (role == UserRole.ADMIN) {
            throw ValidationException("Süper admin rolü sistem tarafından yönetilir")
        }
    }

    private fun validatePasswordPolicy(password: String) {
        if (password.isBlank() || password.length > maxPasswordLength) {
            throw ValidationException("Şifre boş olamaz")
        }
        if (password.length < minPasswordLength) {
            throw ValidationException("Şifre en az $minPasswordLength karakter olmalı")
        }
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSpecial) {
            throw ValidationException("Şifre büyük harf, küçük harf, rakam ve özel karakter içermeli")
        }
        val lower = password.lowercase()
        if (lower in weakPasswordDenylist) {
            throw ValidationException("Bu şifre çok yaygın, lütfen daha güçlü bir şifre seçin")
        }
    }

    private fun ensureAdminRoleCanChange(
        userId: UUID,
        actorId: UUID,
        currentRole: UserRole,
        currentActive: Boolean,
        currentSuperAdmin: Boolean,
        newRole: UserRole
    ) {
        if (currentSuperAdmin && newRole != UserRole.ADMIN) {
            throw ConflictException("Süper admin rolü kaldırılamaz")
        }
        if (currentRole != UserRole.ADMIN || !currentActive || newRole == UserRole.ADMIN) return
        if (userId == actorId) {
            throw ConflictException("Kendi admin rolünüzü kaldıramazsınız")
        }
        if (activeAdminCount() <= 1) {
            throw ConflictException("Son admin rolü kaldırılamaz")
        }
    }

    private fun activeAdminCount(): Long {
        return UsersTable
            .selectAll()
            .where { (UsersTable.role eq UserRole.ADMIN) and (UsersTable.isActive eq true) }
            .count()
    }

    private fun isSuperAdmin(row: ResultRow): Boolean {
        return row[UsersTable.isSuperAdmin]
    }

    private fun findActorRow(actorId: UUID): ResultRow {
        return UsersTable
            .selectAll()
            .where { (UsersTable.id eq actorId) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?: throw AuthorizationFailureException()
    }

    private fun ensureSuperAdminActor(actorId: UUID) {
        val actor = findActorRow(actorId)

        if (!isSuperAdmin(actor)) {
            throw AuthorizationFailureException()
        }
    }

    private fun visibleRolesFor(actor: ResultRow): Set<UserRole> {
        if (isSuperAdmin(actor)) return UserRole.values().toSet()
        return assignableRolesFor(actor).takeIf { it.isNotEmpty() } ?: throw AuthorizationFailureException()
    }

    private fun assignableRolesFor(actor: ResultRow): Set<UserRole> {
        if (isSuperAdmin(actor)) return nonAdminAssignableRoles
        return when (actor[UsersTable.role]) {
            UserRole.PURCHASING_ADMIN -> setOf(UserRole.PURCHASING)
            UserRole.LOGISTICS_ADMIN -> setOf(UserRole.LOGISTICS_OFFICE, UserRole.DRIVER)
            else -> emptySet()
        }
    }

    private fun ensureActorCanAssignRole(actor: ResultRow, role: UserRole) {
        if (role in assignableRolesFor(actor)) return
        throw AuthorizationFailureException()
    }

    private fun ensureActorCanManageUser(actor: ResultRow, target: ResultRow) {
        if (isSuperAdmin(actor)) return
        if (actor[UsersTable.id].value == target[UsersTable.id].value) {
            throw AuthorizationFailureException()
        }
        if (target[UsersTable.isSuperAdmin] || target[UsersTable.role] == UserRole.ADMIN) {
            throw AuthorizationFailureException()
        }
        if (target[UsersTable.role] in assignableRolesFor(actor)) return
        throw AuthorizationFailureException()
    }


    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    private fun String.cleanPreferenceKey(): String {
        val normalized = cleanRequired(100)
        if (!normalized.matches(Regex("[A-Za-z0-9_.:-]+"))) {
            throw ValidationException("Geçersiz tercih anahtarı")
        }
        return normalized
    }

    private fun findUserRowById(id: UUID): ResultRow {
        return UsersTable
            .selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?: throw NotFoundException()
    }

    private fun ensureUsernameAvailable(username: String, excludeUserId: UUID? = null) {
        val existing = UsersTable
            .selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull()
            ?: return
        if (excludeUserId == null || existing[UsersTable.id].value != excludeUserId) {
            throw ConflictException("Kullanıcı adı zaten kayıtlı")
        }
    }

    private fun ResultRow.toAuthUser(): AuthUser {
        return AuthUser(
            id = this[UsersTable.id].value,
            username = this[UsersTable.username],
            fullName = this[UsersTable.fullName],
            passwordHash = this[UsersTable.passwordHash],
            role = this[UsersTable.role],
            licensePlate = this[UsersTable.licensePlate],
            isActive = this[UsersTable.isActive],
            isSuperAdmin = this[UsersTable.isSuperAdmin],
            tokenVersion = this[UsersTable.tokenVersion],
            failedAttempts = this[UsersTable.failedAttempts],
            lockedUntil = this[UsersTable.lockedUntil],
            twoFactorSecret = this[UsersTable.twoFactorSecret]?.let(com.tekinflow.backend.auth.TotpSecretVault::decrypt),
            twoFactorEnabled = this[UsersTable.twoFactorEnabled]
        )
    }

    private fun ResultRow.toUserResponse(): UserResponse {
        return UserResponse(
            id = this[UsersTable.id].value.toString(),
            username = this[UsersTable.username],
            fullName = this[UsersTable.fullName],
            role = this[UsersTable.role],
            licensePlate = this[UsersTable.licensePlate],
            isActive = this[UsersTable.isActive],
            isSuperAdmin = this[UsersTable.isSuperAdmin],
            createdAt = this[UsersTable.createdAt].toEpochMillis(),
            updatedAt = this[UsersTable.updatedAt].toEpochMillis()
        )
    }

    private fun ResultRow.userSummary(): String {
        return "username=${this[UsersTable.username]}," +
            "fullName=${this[UsersTable.fullName]}," +
            "role=${this[UsersTable.role]}," +
            "active=${this[UsersTable.isActive]}," +
            "superAdmin=${this[UsersTable.isSuperAdmin]}," +
            "licensePlate=${this[UsersTable.licensePlate].orEmpty()}"
    }

    companion object {
        private const val maxFailedLoginAttempts = 10
        private const val firstLockoutSeconds = 5 * 60L
        private const val secondLockoutSeconds = 15 * 60L
        private const val minPasswordLength = 10
        private const val maxPasswordLength = 128

        private val nonAdminAssignableRoles = setOf(
            UserRole.PURCHASING,
            UserRole.PURCHASING_ADMIN,
            UserRole.LOGISTICS_OFFICE,
            UserRole.LOGISTICS_ADMIN,
            UserRole.DRIVER
        )

        private val weakPasswordDenylist = setOf(
            "password", "passw0rd", "parola", "sifre", "şifre",
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "qwerty", "qwertyui", "qwerty123", "asdf1234", "asdfghjk",
            "111111", "000000", "112233", "121212", "abc123",
            "iloveyou", "admin123", "administrator", "welcome", "welcome1",
            "TekinFlow", "satinalma", "satınalma", "letmein", "changeme"
        )
    }
}
