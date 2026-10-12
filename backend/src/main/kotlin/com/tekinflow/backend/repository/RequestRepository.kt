package com.tekinflow.backend.repository

import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.AssignDriverRequest
import com.tekinflow.backend.model.CancelRequestBody
import com.tekinflow.backend.model.CreatePurchaseRequestRequest
import com.tekinflow.backend.model.MaterialStatus
import com.tekinflow.backend.model.MaterialUnit
import com.tekinflow.backend.model.NotificationsTable
import com.tekinflow.backend.model.ObjectionRequest
import com.tekinflow.backend.model.PurchaseRequestItemRequest
import com.tekinflow.backend.model.PurchaseRequestItemResponse
import com.tekinflow.backend.model.PurchaseRequestItemsTable
import com.tekinflow.backend.model.PurchaseRequestResponse
import com.tekinflow.backend.model.PurchaseRequestsTable
import com.tekinflow.backend.model.RequestAuditTable
import com.tekinflow.backend.model.RequestSeenTable
import com.tekinflow.backend.model.RequestStatusHistoryTable
import com.tekinflow.backend.model.UpdatePurchaseRequestRequest
import com.tekinflow.backend.model.UpdateStatusRequest
import com.tekinflow.backend.model.UrgencyLevel
import com.tekinflow.backend.model.UserRole
import com.tekinflow.backend.model.UsersTable
import com.tekinflow.backend.util.AuthorizationFailureException
import com.tekinflow.backend.util.ConflictException
import com.tekinflow.backend.util.NotFoundException
import com.tekinflow.backend.util.RequestNumberGenerator
import com.tekinflow.backend.util.StatusTransitionValidator
import com.tekinflow.backend.util.ValidationException
import com.tekinflow.backend.util.clean
import com.tekinflow.backend.util.cleanRequired
import com.tekinflow.backend.util.toApiString
import com.tekinflow.backend.util.toEpochMillis
import com.tekinflow.backend.util.toLocalDateOrNull
import com.tekinflow.backend.util.toUuidOrValidationError
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

private data class RequestPayload(
    val itemToPurchase: String,
    val items: List<RequestItemPayload>,
    val companyName: String,
    val companyAddress: String?,
    val contactNumber: String?,
    val contactPersonName: String?,
    val productDimensions: String?,
    val productWeight: String?,
    val packageCount: Int?,
    val urgencyLevel: UrgencyLevel,
    val purchaseDate: LocalDate?,
    val notes: String?
)

private data class RequestItemPayload(
    val itemName: String,
    val quantity: BigDecimal,
    val unit: MaterialUnit,
    val packageCount: Int?,
    val dimensionsNote: String?,
    val sortOrder: Int
)

private data class CreatedRequestResult(
    val response: PurchaseRequestResponse,
    val notificationMessage: String,
    val logisticsUserIds: List<UUID>,
    val driverUserIds: List<UUID>,
    val requestId: UUID
)

class RequestRepository(
    private val notificationRepository: NotificationRepository,
    private val requestNumberGenerator: RequestNumberGenerator
) {

    private val logger = LoggerFactory.getLogger(RequestRepository::class.java)

    suspend fun listRequests(
        currentUserId: UUID,
        currentRole: UserRole,
        status: MaterialStatus?,
        assignedDriverId: UUID?,
        staffId: UUID?
    ): List<PurchaseRequestResponse> = dbQuery {
        val query = PurchaseRequestsTable
            .selectAll()
            .apply {
                if (currentRole == UserRole.DRIVER) {
                    andWhere {
                        (PurchaseRequestsTable.assignedDriverId eq currentUserId) or
                            (
                                PurchaseRequestsTable.assignedDriverId.isNull() and
                                    PurchaseRequestsTable.licensePlate.isNull() and
                                    PurchaseRequestsTable.spotDriverName.isNull() and
                                    PurchaseRequestsTable.spotDriverIdentityNumber.isNull() and
                                    PurchaseRequestsTable.spotDriverCompanyName.isNull() and
                                    (
                                        (PurchaseRequestsTable.materialStatus eq MaterialStatus.PENDING) or
                                            (PurchaseRequestsTable.materialStatus eq MaterialStatus.IN_PROGRESS) or
                                            (PurchaseRequestsTable.materialStatus eq MaterialStatus.PURCHASED)
                                        )
                                )
                    }
                }
                status?.let { requestedStatus ->
                    andWhere { PurchaseRequestsTable.materialStatus eq requestedStatus }
                }
                assignedDriverId?.let { driverId ->
                    andWhere { PurchaseRequestsTable.assignedDriverId eq driverId }
                }
                staffId?.let { staffUserId ->
                    andWhere { PurchaseRequestsTable.assignedPurchasingStaffId eq staffUserId }
                }
            }
            .orderBy(PurchaseRequestsTable.updatedAt, SortOrder.DESC)
            // Hard upper bound so a single list call can never pull the whole
            // table (and its per-row seen/items lookups) into memory. Newest
            // requests are returned first; the active working set is far below
            // this cap.
            .limit(MAX_LIST_RESULTS)

        val rows = query.toList()
        val requestIds = rows.map { it[PurchaseRequestsTable.id].value }
        val seenMap = loadSeenMap(requestIds)
        val itemsMap = loadItemsMap(requestIds)
        rows.map { row ->
            row.toPurchaseRequestResponse(
                items = itemsMap[row[PurchaseRequestsTable.id].value].orEmpty(),
                seenByUserIds = seenMap[row[PurchaseRequestsTable.id].value].orEmpty(),
                currentUserId = currentUserId,
                viewerRole = currentRole
            )
        }
    }

    suspend fun getRequestById(
        requestId: UUID,
        currentUserId: UUID,
        currentRole: UserRole
    ): PurchaseRequestResponse = dbQuery {
        val row = findRequestRow(requestId)
        enforceDriverVisibility(row, currentUserId, currentRole)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        val items = loadItemsMap(listOf(requestId))[requestId].orEmpty()
        row.toPurchaseRequestResponse(items, seenByIds, currentUserId, currentRole)
    }

    suspend fun listDeliveredRecipients(userId: UUID, role: UserRole): List<String> = dbQuery {
        PurchaseRequestsTable
            .selectAll()
            .where { PurchaseRequestsTable.deliveredTo.isNotNull() }
            .apply { if (role == UserRole.DRIVER) andWhere { PurchaseRequestsTable.assignedDriverId eq userId } }
            .mapNotNull { it[PurchaseRequestsTable.deliveredTo]?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .distinct()
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    suspend fun createRequest(
        actorId: UUID,
        actorRole: UserRole,
        request: CreatePurchaseRequestRequest,
        actorIp: String?
    ): PurchaseRequestResponse {
        val createdResult = dbQuery {
            ensureActivePurchasing(actorId)
            val payload = request.toPayload()
            val now = Instant.now()
            val requestNumber = requestNumberGenerator.nextRequestNumber()

            val requestId = PurchaseRequestsTable.insertAndGetId {
                it[this.requestNumber] = requestNumber
                it[PurchaseRequestsTable.itemToPurchase] = payload.itemToPurchase
                it[PurchaseRequestsTable.companyName] = payload.companyName
                it[PurchaseRequestsTable.companyAddress] = payload.companyAddress
                it[PurchaseRequestsTable.contactNumber] = payload.contactNumber
                it[PurchaseRequestsTable.contactPersonName] = payload.contactPersonName
                it[PurchaseRequestsTable.productDimensions] = payload.productDimensions
                it[PurchaseRequestsTable.productWeight] = payload.productWeight
                it[PurchaseRequestsTable.packageCount] = payload.packageCount
                it[PurchaseRequestsTable.urgencyLevel] = payload.urgencyLevel
                it[PurchaseRequestsTable.purchaseDate] = payload.purchaseDate
                it[PurchaseRequestsTable.actualPurchaseDate] = null
                it[PurchaseRequestsTable.notes] = payload.notes
                it[PurchaseRequestsTable.licensePlate] = null
                it[PurchaseRequestsTable.spotDriverName] = null
                it[PurchaseRequestsTable.spotDriverIdentityNumber] = null
                it[PurchaseRequestsTable.spotDriverCompanyName] = null
                it[PurchaseRequestsTable.spotDriverPhone] = null
                it[PurchaseRequestsTable.deliveredTo] = null
                it[PurchaseRequestsTable.materialStatus] = MaterialStatus.IN_PROGRESS
                it[PurchaseRequestsTable.assignedDriverId] = null
                it[PurchaseRequestsTable.assignedPurchasingStaffId] = actorId
                it[PurchaseRequestsTable.cancellationReason] = null
                it[PurchaseRequestsTable.cancelledBy] = null
                it[PurchaseRequestsTable.objectionReason] = null
                it[PurchaseRequestsTable.hasObjection] = false
                it[PurchaseRequestsTable.objectionBy] = null
                it[PurchaseRequestsTable.redirectedToRequestId] = null
                it[PurchaseRequestsTable.createdAt] = now
                it[PurchaseRequestsTable.updatedAt] = now
            }.value

            replaceRequestItems(requestId, payload.items, now)
            RequestSeenTable.insertIgnore {
                it[this.requestId] = requestId
                it[RequestSeenTable.userId] = actorId
                it[RequestSeenTable.seenAt] = now
            }
            recordAudit(requestId, actorId, "CREATE_REQUEST", null, "requestNumber=$requestNumber,status=${MaterialStatus.IN_PROGRESS}", actorIp, now)
            recordStatusHistory(requestId, actorId, null, MaterialStatus.IN_PROGRESS, now)

            val created = findRequestRow(requestId)
            CreatedRequestResult(
                response = created.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), setOf(actorId), actorId, actorRole),
                notificationMessage = "Yeni satın alma talebi oluşturuldu: ${payload.companyName}",
                logisticsUserIds = activeUserIdsByRoles(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN),
                driverUserIds = activeUserIdsByRole(UserRole.DRIVER),
                requestId = requestId
            )
        }

        runCatching {
            notificationRepository.createForUsers(createdResult.logisticsUserIds, createdResult.notificationMessage, createdResult.requestId)
            notificationRepository.createForUsers(createdResult.driverUserIds, createdResult.notificationMessage, createdResult.requestId)
        }.onFailure { error ->
            logger.warn("Talep bildirimleri oluşturulamadı requestId={}", createdResult.requestId, error)
        }

        return createdResult.response
    }

    suspend fun updateRequest(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        request: UpdatePurchaseRequestRequest,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        val existing = findRequestRow(requestId)
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val canEdit = when (actorRole) {
            UserRole.PURCHASING -> ownerId == actorId
            UserRole.PURCHASING_ADMIN -> true
            UserRole.ADMIN -> isSuperAdmin(actorId)
            UserRole.LOGISTICS_OFFICE,
            UserRole.LOGISTICS_ADMIN,
            UserRole.DRIVER -> false
        }
        if (!canEdit) {
            throw AuthorizationFailureException()
        }
        if (existing[PurchaseRequestsTable.materialStatus] in setOf(MaterialStatus.CANCELLED, MaterialStatus.DELIVERED)) {
            throw ConflictException("Bu talep güncellenemez")
        }

        val payload = request.toPayload()
        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.itemToPurchase] = payload.itemToPurchase
            it[PurchaseRequestsTable.companyName] = payload.companyName
            it[PurchaseRequestsTable.companyAddress] = payload.companyAddress
            it[PurchaseRequestsTable.contactNumber] = payload.contactNumber
            it[PurchaseRequestsTable.contactPersonName] = payload.contactPersonName
            it[PurchaseRequestsTable.productDimensions] = payload.productDimensions
            it[PurchaseRequestsTable.productWeight] = payload.productWeight
            it[PurchaseRequestsTable.packageCount] = payload.packageCount
            it[PurchaseRequestsTable.urgencyLevel] = payload.urgencyLevel
            it[PurchaseRequestsTable.purchaseDate] = payload.purchaseDate
            it[PurchaseRequestsTable.notes] = payload.notes
            it[PurchaseRequestsTable.updatedAt] = Instant.now()
        }
        replaceRequestItems(requestId, payload.items, Instant.now())
        recordAudit(requestId, actorId, "UPDATE_REQUEST", existing.requestSummary(), "item=${payload.itemToPurchase},company=${payload.companyName}", actorIp)

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
    }

    suspend fun cancelRequest(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        body: CancelRequestBody,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        val existing = findRequestRow(requestId)
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val canCancel = when (actorRole) {
            UserRole.ADMIN -> isSuperAdmin(actorId)
            UserRole.PURCHASING_ADMIN -> true
            UserRole.PURCHASING -> ownerId == actorId
            UserRole.LOGISTICS_OFFICE -> false
            UserRole.LOGISTICS_ADMIN -> false
            UserRole.DRIVER -> false
        }
        if (!canCancel) throw AuthorizationFailureException()
        if (existing[PurchaseRequestsTable.materialStatus] in setOf(MaterialStatus.CANCELLED, MaterialStatus.DELIVERED)) {
            throw ConflictException("Bu talep iptal edilemez")
        }

        val oldStatus = existing[PurchaseRequestsTable.materialStatus]
        val now = Instant.now()
        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.materialStatus] = MaterialStatus.CANCELLED
            it[PurchaseRequestsTable.cancellationReason] = body.reason.clean(2_000)
            it[PurchaseRequestsTable.cancelledBy] = actorId
            it[PurchaseRequestsTable.updatedAt] = now
        }
        recordStatusChangeAudit(requestId, actorId, oldStatus, MaterialStatus.CANCELLED, "CANCEL_REQUEST", actorIp, now)

        val companyName = existing[PurchaseRequestsTable.companyName]
        existing[PurchaseRequestsTable.assignedDriverId]?.value?.let { driverId ->
            if (driverId != actorId) {
                notificationRepository.createForUser(driverId, "Talep iptal edildi: $companyName", requestId)
            }
        }
        if (ownerId != actorId) {
            notificationRepository.createForUser(ownerId, "Talep iptal edildi: $companyName", requestId)
        }

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
    }

    suspend fun deleteRequest(
        requestId: UUID,
        actorId: UUID,
        actorIp: String?
    ) = dbQuery {
        ensureSuperAdmin(actorId)
        val existing = findRequestRow(requestId)
        val deletedLabel = existing[PurchaseRequestsTable.requestNumber]
        val deletedSummary = existing.requestSummary()

        PurchaseRequestsTable.update({ PurchaseRequestsTable.redirectedToRequestId eq requestId }) {
            it[PurchaseRequestsTable.redirectedToRequestId] = null
        }
        NotificationsTable.deleteWhere { NotificationsTable.requestId eq requestId }
        RequestSeenTable.deleteWhere { RequestSeenTable.requestId eq requestId }
        RequestStatusHistoryTable.deleteWhere { RequestStatusHistoryTable.requestId eq requestId }
        RequestAuditTable.deleteWhere { RequestAuditTable.requestId eq requestId }

        val deletedRows = PurchaseRequestsTable.deleteWhere { PurchaseRequestsTable.id eq requestId }
        if (deletedRows == 0) throw NotFoundException()
        AuditLogWriter.record(
            actorId = actorId,
            action = "DELETE_REQUEST",
            entityType = "REQUEST",
            entityId = requestId,
            entityLabel = deletedLabel,
            oldValue = deletedSummary,
            newValue = null,
            ipAddress = actorIp
        )
    }

    suspend fun clearObjection(
        requestId: UUID,
        actorId: UUID,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        ensureSuperAdmin(actorId)
        val existing = findRequestRow(requestId)
        if (!existing[PurchaseRequestsTable.hasObjection] &&
            existing[PurchaseRequestsTable.objectionReason].isNullOrBlank() &&
            existing[PurchaseRequestsTable.objectionBy] == null
        ) {
            throw ConflictException("Bu talepte silinecek itiraz yok")
        }

        val now = Instant.now()
        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.hasObjection] = false
            it[PurchaseRequestsTable.objectionReason] = null
            it[PurchaseRequestsTable.objectionBy] = null
            it[PurchaseRequestsTable.updatedAt] = now
        }
        recordAudit(
            requestId = requestId,
            actorId = actorId,
            action = "CLEAR_OBJECTION",
            oldValue = "hasObjection=${existing[PurchaseRequestsTable.hasObjection]},reasonLength=${existing[PurchaseRequestsTable.objectionReason]?.length ?: 0}",
            newValue = "hasObjection=false",
            actorIp = actorIp,
            createdAt = now
        )

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, UserRole.ADMIN)
    }

    suspend fun assignDriver(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        request: AssignDriverRequest,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        if (actorRole !in setOf(UserRole.ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN)) {
            throw AuthorizationFailureException()
        }
        if (actorRole == UserRole.ADMIN && !isSuperAdmin(actorId)) {
            throw AuthorizationFailureException()
        }

        val existing = findRequestRow(requestId)
        if (actorRole != UserRole.LOGISTICS_ADMIN && !isSuperAdmin(actorId)) {
            ensureRequestAssignable(existing)
        }

        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val companyName = existing[PurchaseRequestsTable.companyName]
        val oldDriverId = existing[PurchaseRequestsTable.assignedDriverId]?.value
        val driverId = request.driverId?.takeIf { it.isNotBlank() }?.toUuidOrValidationError()
        val driver = driverId?.let(::findDriver)
        val isSpotDriver = driverId == null
        val nextPlate = if (isSpotDriver) {
            request.licensePlate.clean(20)
        } else {
            request.licensePlate.clean(20) ?: driver?.licensePlate ?: throw ValidationException()
        }?.uppercase(Locale.ROOT)
        val spotDriverName = if (isSpotDriver) request.spotDriverName.clean(100) else null
        val spotDriverIdentityNumber = if (isSpotDriver) request.spotDriverIdentityNumber.clean(20) else null
        val spotDriverCompanyName = if (isSpotDriver) request.spotDriverCompanyName.clean(100, fieldRequired = true) else null
        val spotDriverPhone = if (isSpotDriver) request.spotDriverPhone.clean(50) else null
        val oldStatus = existing[PurchaseRequestsTable.materialStatus]
        val newStatus = if (oldStatus == MaterialStatus.PENDING) MaterialStatus.IN_PROGRESS else oldStatus
        val now = Instant.now()

        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.assignedDriverId] = driverId
            it[PurchaseRequestsTable.licensePlate] = nextPlate
            it[PurchaseRequestsTable.spotDriverName] = spotDriverName
            it[PurchaseRequestsTable.spotDriverIdentityNumber] = spotDriverIdentityNumber
            it[PurchaseRequestsTable.spotDriverCompanyName] = spotDriverCompanyName
            it[PurchaseRequestsTable.spotDriverPhone] = spotDriverPhone
            it[PurchaseRequestsTable.materialStatus] = newStatus
            it[PurchaseRequestsTable.updatedAt] = now
        }
        recordAudit(
            requestId,
            actorId,
            "ASSIGN_DRIVER",
            "driver=${oldDriverId?.toString().orEmpty()},status=$oldStatus",
            "driver=${driverId?.toString().orEmpty()},spot=$isSpotDriver,plate=$nextPlate,status=$newStatus",
            actorIp,
            now
        )
        if (oldStatus != newStatus) recordStatusHistory(requestId, actorId, oldStatus, newStatus, now)

        driverId?.let { assignedId ->
            RequestSeenTable.deleteWhere {
                (RequestSeenTable.requestId eq requestId) and (RequestSeenTable.userId eq assignedId)
            }
        }

        driverId?.let { assignedId ->
            val driverMessage = if (oldDriverId == null) {
                "Size yeni bir iş atandı: $companyName"
            } else {
                "Size bir iş yeniden atandı: $companyName"
            }
            notificationRepository.createForUser(assignedId, driverMessage, requestId)
        }
        notificationRepository.createForUser(ownerId, "Talep şoföre atandı: $companyName", requestId)
        oldDriverId?.takeIf { it != driverId }?.let { previousDriverId ->
            notificationRepository.createForUser(previousDriverId, "Atamanız kaldırıldı: $companyName", requestId)
        }

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, ownerId, actorRole)
    }

    suspend fun unassignDriver(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        if (actorRole !in setOf(UserRole.ADMIN, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN)) {
            throw AuthorizationFailureException()
        }
        if (actorRole == UserRole.ADMIN && !isSuperAdmin(actorId)) {
            throw AuthorizationFailureException()
        }

        val existing = findRequestRow(requestId)
        if (actorRole != UserRole.LOGISTICS_ADMIN && !isSuperAdmin(actorId)) {
            ensureRequestAssignable(existing)
        }
        val driverId = existing[PurchaseRequestsTable.assignedDriverId]?.value
        if (driverId == null && !hasSpotDriverAssignment(existing)) {
            throw ConflictException("Atama bulunamadı")
        }
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val companyName = existing[PurchaseRequestsTable.companyName]
        val oldStatus = existing[PurchaseRequestsTable.materialStatus]
        val newStatus = if (oldStatus in setOf(
                MaterialStatus.PENDING,
                MaterialStatus.IN_PROGRESS,
                MaterialStatus.PURCHASED,
                MaterialStatus.ON_THE_WAY
            )
        ) {
            MaterialStatus.PENDING
        } else {
            oldStatus
        }
        val now = Instant.now()

        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.assignedDriverId] = null
            it[PurchaseRequestsTable.licensePlate] = null
            it[PurchaseRequestsTable.spotDriverName] = null
            it[PurchaseRequestsTable.spotDriverIdentityNumber] = null
            it[PurchaseRequestsTable.spotDriverCompanyName] = null
            it[PurchaseRequestsTable.spotDriverPhone] = null
            it[PurchaseRequestsTable.materialStatus] = newStatus
            it[PurchaseRequestsTable.updatedAt] = now
        }
        recordAudit(requestId, actorId, "UNASSIGN_DRIVER", "driver=$driverId,status=$oldStatus", "driver=,status=$newStatus", actorIp, now)
        if (oldStatus != newStatus) recordStatusHistory(requestId, actorId, oldStatus, newStatus, now)

        driverId?.let {
            notificationRepository.createForUser(it, "Atamanız kaldırıldı: $companyName", requestId)
        }
        notificationRepository.createForUser(ownerId, "Şoför ataması kaldırıldı: $companyName", requestId)

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, ownerId, actorRole)
    }

    suspend fun takeOverRequest(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        if (actorRole != UserRole.DRIVER) throw AuthorizationFailureException()

        val existing = findRequestRow(requestId)
        if (existing[PurchaseRequestsTable.materialStatus] in setOf(MaterialStatus.CANCELLED, MaterialStatus.DELIVERED)) {
            throw ConflictException("Bu talep üstlenilemez")
        }

        val currentAssigned = existing[PurchaseRequestsTable.assignedDriverId]?.value
        val currentPlate = existing[PurchaseRequestsTable.licensePlate]
        if (currentAssigned != null && currentAssigned != actorId) {
            throw ConflictException("Bu talep başka bir şoföre atanmış")
        }
        if (currentAssigned == null && !currentPlate.isNullOrBlank()) {
            throw ConflictException("Bu talep spot araç için ayrılmış")
        }

        val driver = findDriver(actorId)
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val companyName = existing[PurchaseRequestsTable.companyName]
        val oldStatus = existing[PurchaseRequestsTable.materialStatus]
        if (currentAssigned == null && oldStatus !in driverVisibleUnassignedStatuses) {
            throw ConflictException("Bu talep üstlenilemez")
        }
        val newStatus = if (oldStatus == MaterialStatus.PENDING) MaterialStatus.IN_PROGRESS else oldStatus
        val now = Instant.now()
        val driverPlate = driver.licensePlate.cleanRequired(20).uppercase(Locale.ROOT)

        if (currentAssigned == actorId) {
            val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
            return@dbQuery existing.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
        }

        // Atomic guard against a take-over race: only assign if still unassigned.
        // Two drivers hitting take-over concurrently both pass the read checks above,
        // so the winner is decided here and the loser gets a 409.
        val updatedRows = PurchaseRequestsTable.update({
            (PurchaseRequestsTable.id eq requestId) and PurchaseRequestsTable.assignedDriverId.isNull()
        }) {
            it[PurchaseRequestsTable.assignedDriverId] = actorId
            it[PurchaseRequestsTable.licensePlate] = driverPlate
            it[PurchaseRequestsTable.materialStatus] = newStatus
            // Taking over resolves the objection state: the request leaves the
            // "İtirazlı" category and returns to the normal active flow. The
            // objectionReason/objectionBy text is preserved as history for
            // authorized (non-driver) roles to review.
            it[PurchaseRequestsTable.hasObjection] = false
            it[PurchaseRequestsTable.updatedAt] = now
        }
        if (updatedRows != 1) {
            throw ConflictException("Bu talep az önce başka bir şoför tarafından üstlenildi")
        }
        recordAudit(requestId, actorId, "TAKE_OVER", "driver=,status=$oldStatus", "driver=$actorId,plate=${driverPlate.orEmpty()},status=$newStatus", actorIp, now)
        if (oldStatus != newStatus) recordStatusHistory(requestId, actorId, oldStatus, newStatus, now)

        RequestSeenTable.deleteWhere {
            (RequestSeenTable.requestId eq requestId) and (RequestSeenTable.userId eq actorId)
        }

        notificationRepository.createForUser(ownerId, "Şoför talebi üstlendi: $companyName", requestId)
        notificationRepository.createForUsers(
            activeUserIdsByRoles(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN),
            "Talep üstlenildi: $companyName",
            requestId
        )

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
    }

    suspend fun updateStatus(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        request: UpdateStatusRequest,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        val existing = findRequestRow(requestId)
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val assignedDriverId = existing[PurchaseRequestsTable.assignedDriverId]?.value
        val oldStatus = existing[PurchaseRequestsTable.materialStatus]
        val adminCanReopenDelivered = actorRole in setOf(UserRole.ADMIN, UserRole.LOGISTICS_ADMIN) &&
            oldStatus == MaterialStatus.DELIVERED &&
            request.status in setOf(MaterialStatus.IN_PROGRESS, MaterialStatus.ON_THE_WAY)
        val canUpdate = when (actorRole) {
            UserRole.ADMIN -> isSuperAdmin(actorId)
            UserRole.PURCHASING_ADMIN -> ownerId == actorId && isPurchasingStatusTransition(oldStatus, request.status)
            UserRole.PURCHASING -> ownerId == actorId && isPurchasingStatusTransition(oldStatus, request.status)
            UserRole.LOGISTICS_ADMIN -> isLogisticsStatusTransition(oldStatus, request.status) || adminCanReopenDelivered
            UserRole.LOGISTICS_OFFICE -> isLogisticsStatusTransition(oldStatus, request.status)
            UserRole.DRIVER -> assignedDriverId == actorId && isDriverStatusTransition(oldStatus, request.status)
        }
        if (!canUpdate) throw AuthorizationFailureException()
        if (!adminCanReopenDelivered && !isValidStatusTransition(oldStatus, request.status)) {
            throw ValidationException("Geçersiz durum geçişi")
        }
        val targetStatus = if (
            oldStatus == MaterialStatus.DELIVERED &&
            request.status == MaterialStatus.IN_PROGRESS &&
            !hasReadyTransportAssignment(existing)
        ) {
            MaterialStatus.PENDING
        } else {
            request.status
        }
        ensureTransportAssignmentForStatus(existing, targetStatus)

        val now = Instant.now()
        val deliveredRecipient = if (targetStatus == MaterialStatus.DELIVERED) {
            request.deliveredTo.clean(200, fieldRequired = true)
        } else {
            null
        }
        val updatedRows = PurchaseRequestsTable.update({
            (PurchaseRequestsTable.id eq requestId) and
                (PurchaseRequestsTable.materialStatus eq oldStatus)
        }) {
            it[PurchaseRequestsTable.materialStatus] = targetStatus
            it[PurchaseRequestsTable.updatedAt] = now
            // Teslim alındığında gerçekleşen tarih ve teslim alan kişi kaydedilir.
            if (targetStatus == MaterialStatus.DELIVERED) {
                it[PurchaseRequestsTable.actualPurchaseDate] = now
                if (deliveredRecipient != null) {
                    it[PurchaseRequestsTable.deliveredTo] = deliveredRecipient
                }
            } else if (oldStatus == MaterialStatus.DELIVERED) {
                // Tamamlanan talep tekrar aktif duruma alındığında teslim bilgisi temizlenir.
                it[PurchaseRequestsTable.actualPurchaseDate] = null
                it[PurchaseRequestsTable.deliveredTo] = null
            }
        }
        if (updatedRows == 0) {
            throw ConflictException("Talep durumu değişmiş. Lütfen yenileyip tekrar deneyin")
        }
        if (oldStatus != targetStatus) {
            recordStatusChangeAudit(requestId, actorId, oldStatus, targetStatus, "UPDATE_STATUS", actorIp, now)
        }
        assignedDriverId?.takeIf { it != actorId }?.let { driverId ->
            notificationRepository.createForUser(
                driverId,
                "Size atanan talebin durumu güncellendi: ${existing[PurchaseRequestsTable.companyName]} -> ${targetStatus.displayName()}",
                requestId
            )
        }

        notificationRepository.createForUser(
            ownerId,
            "Talep durumu güncellendi: ${existing[PurchaseRequestsTable.companyName]} -> ${targetStatus.displayName()}",
            requestId
        )

        notificationRepository.createForUsers(
            activeUserIdsByRoles(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN).filter { it != actorId },
            "Talep durumu güncellendi: ${existing[PurchaseRequestsTable.companyName]} -> ${targetStatus.displayName()}",
            requestId
        )

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
    }

    suspend fun submitObjection(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole,
        request: ObjectionRequest,
        actorIp: String?
    ): PurchaseRequestResponse = dbQuery {
        if (actorRole !in setOf(UserRole.DRIVER, UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN)) {
            throw AuthorizationFailureException()
        }
        val existing = findRequestRow(requestId)
        if (actorRole == UserRole.DRIVER && existing[PurchaseRequestsTable.assignedDriverId]?.value != actorId) {
            throw AuthorizationFailureException()
        }
        // Objections can be filed at every stage of the request lifecycle,
        // including after it has been delivered or cancelled.

        val reason = request.reason.cleanRequired(2_000)
        val ownerId = existing[PurchaseRequestsTable.assignedPurchasingStaffId].value
        val companyName = existing[PurchaseRequestsTable.companyName]
        val now = Instant.now()

        // Accumulate objections instead of overwriting: each new objection is
        // appended with the objector's name so earlier objections are preserved
        // as history for authorized (non-driver) roles to review.
        val objectorName = userFullName(actorId) ?: actorRole.objectionActorName()
        val previousReason = existing[PurchaseRequestsTable.objectionReason]
        val entry = "$objectorName: $reason"
        val combinedReason = if (previousReason.isNullOrBlank()) entry else "$previousReason\n\n$entry"

        PurchaseRequestsTable.update({ PurchaseRequestsTable.id eq requestId }) {
            it[PurchaseRequestsTable.hasObjection] = true
            it[PurchaseRequestsTable.objectionReason] = combinedReason
            it[PurchaseRequestsTable.objectionBy] = actorId
            it[PurchaseRequestsTable.updatedAt] = now
        }
        recordAudit(requestId, actorId, "SUBMIT_OBJECTION", "hasObjection=${existing[PurchaseRequestsTable.hasObjection]}", "hasObjection=true,reasonLength=${reason.length}", actorIp, now)

        notificationRepository.createForUsers(
            activeUserIdsByRoles(UserRole.LOGISTICS_OFFICE, UserRole.LOGISTICS_ADMIN),
            "${actorRole.objectionActorName()} itiraz etti: $companyName",
            requestId
        )
        notificationRepository.createForUser(ownerId, "${actorRole.objectionActorName()} itiraz etti: $companyName", requestId)

        val updated = findRequestRow(requestId)
        val seenByIds = loadSeenMap(listOf(requestId))[requestId].orEmpty()
        updated.toPurchaseRequestResponse(loadItemsMap(listOf(requestId))[requestId].orEmpty(), seenByIds, actorId, actorRole)
    }

    suspend fun markSeen(
        requestId: UUID,
        actorId: UUID,
        actorRole: UserRole
    ) = dbQuery {
        val existing = findRequestRow(requestId)
        enforceDriverVisibility(existing, actorId, actorRole)
        RequestSeenTable.insertIgnore {
            it[this.requestId] = requestId
            it[RequestSeenTable.userId] = actorId
            it[RequestSeenTable.seenAt] = Instant.now()
        }
    }

    private fun recordStatusChangeAudit(
        requestId: UUID,
        actorId: UUID,
        oldStatus: MaterialStatus,
        newStatus: MaterialStatus,
        action: String,
        actorIp: String?,
        createdAt: Instant = Instant.now()
    ) {
        recordAudit(
            requestId = requestId,
            actorId = actorId,
            action = action,
            oldValue = "status=$oldStatus",
            newValue = "status=$newStatus",
            actorIp = actorIp,
            createdAt = createdAt
        )
        recordStatusHistory(
            requestId = requestId,
            actorId = actorId,
            oldStatus = oldStatus,
            newStatus = newStatus,
            createdAt = createdAt
        )
    }

    private fun recordAudit(
        requestId: UUID,
        actorId: UUID,
        action: String,
        oldValue: String?,
        newValue: String?,
        actorIp: String?,
        createdAt: Instant = Instant.now()
    ) {
        RequestAuditTable.insert {
            it[RequestAuditTable.requestId] = requestId
            it[RequestAuditTable.actorUserId] = actorId
            it[RequestAuditTable.action] = action
            it[RequestAuditTable.oldValue] = oldValue?.take(4_000)
            it[RequestAuditTable.newValue] = newValue?.take(4_000)
            it[RequestAuditTable.ipAddress] = actorIp?.take(64)
            it[RequestAuditTable.createdAt] = createdAt
        }
        AuditLogWriter.record(
            actorId = actorId,
            action = action,
            entityType = "REQUEST",
            entityId = requestId,
            entityLabel = requestLabel(requestId),
            oldValue = oldValue,
            newValue = newValue,
            ipAddress = actorIp,
            createdAt = createdAt
        )
    }

    private fun requestLabel(requestId: UUID): String? {
        return PurchaseRequestsTable
            .selectAll()
            .where { PurchaseRequestsTable.id eq requestId }
            .singleOrNull()
            ?.get(PurchaseRequestsTable.requestNumber)
    }

    private fun recordStatusHistory(
        requestId: UUID,
        actorId: UUID,
        oldStatus: MaterialStatus?,
        newStatus: MaterialStatus,
        createdAt: Instant = Instant.now()
    ) {
        RequestStatusHistoryTable.insert {
            it[RequestStatusHistoryTable.requestId] = requestId
            it[RequestStatusHistoryTable.actorUserId] = actorId
            it[RequestStatusHistoryTable.oldStatus] = oldStatus
            it[RequestStatusHistoryTable.newStatus] = newStatus
            it[RequestStatusHistoryTable.createdAt] = createdAt
        }
    }

    private fun ResultRow.requestSummary(): String {
        return "requestNumber=${this[PurchaseRequestsTable.requestNumber]}," +
            "item=${this[PurchaseRequestsTable.itemToPurchase]}," +
            "company=${this[PurchaseRequestsTable.companyName]}," +
            "status=${this[PurchaseRequestsTable.materialStatus]}," +
            "driver=${this[PurchaseRequestsTable.assignedDriverId]?.value?.toString().orEmpty()}"
    }

    private fun CreatePurchaseRequestRequest.toPayload(): RequestPayload {
        val itemPayloads = buildItemPayloads(
            requestedItems = items,
            legacyItemName = itemToPurchase,
            legacyPackageCount = packageCount,
            legacyDimensions = productDimensions
        )
        return RequestPayload(
            itemToPurchase = itemPayloads.first().itemName,
            items = itemPayloads,
            companyName = companyName.cleanRequired(255),
            companyAddress = companyAddress.clean(4_000),
            contactNumber = contactNumber.clean(50),
            contactPersonName = contactPersonName.clean(100),
            productDimensions = productDimensions.clean(100) ?: itemPayloads.first().dimensionsNote,
            productWeight = productWeight.clean(100),
            packageCount = itemPayloads.first().packageCount ?: packageCount?.takeIf { it > 0 },
            urgencyLevel = urgencyLevel,
            purchaseDate = purchaseDate.toLocalDateOrNull(),
            notes = notes.clean(4_000)
        )
    }

    private fun UpdatePurchaseRequestRequest.toPayload(): RequestPayload {
        val itemPayloads = buildItemPayloads(
            requestedItems = items,
            legacyItemName = itemToPurchase,
            legacyPackageCount = packageCount,
            legacyDimensions = productDimensions
        )
        return RequestPayload(
            itemToPurchase = itemPayloads.first().itemName,
            items = itemPayloads,
            companyName = companyName.cleanRequired(255),
            companyAddress = companyAddress.clean(4_000),
            contactNumber = contactNumber.clean(50),
            contactPersonName = contactPersonName.clean(100),
            productDimensions = productDimensions.clean(100) ?: itemPayloads.first().dimensionsNote,
            productWeight = productWeight.clean(100),
            packageCount = itemPayloads.first().packageCount ?: packageCount?.takeIf { it > 0 },
            urgencyLevel = urgencyLevel,
            purchaseDate = purchaseDate.toLocalDateOrNull(),
            notes = notes.clean(4_000)
        )
    }

    private fun buildItemPayloads(
        requestedItems: List<PurchaseRequestItemRequest>,
        legacyItemName: String,
        legacyPackageCount: Int?,
        legacyDimensions: String?
    ): List<RequestItemPayload> {
        val source = requestedItems.takeIf { it.isNotEmpty() }
            ?: listOf(
                PurchaseRequestItemRequest(
                    itemName = legacyItemName,
                    quantity = "1",
                    unit = MaterialUnit.KG,
                    packageCount = legacyPackageCount,
                    dimensionsNote = legacyDimensions
                )
            )
        if (source.isEmpty() || source.size > MAX_REQUEST_ITEMS) {
            throw ValidationException("Talep kalem sayisi 1 ile $MAX_REQUEST_ITEMS arasinda olmali")
        }

        return source.mapIndexed { index, item ->
            val itemName = item.itemName.cleanRequired(255)
            val quantity = item.quantity.trim()
                .replace(",", ".")
                .toBigDecimalOrNull()
                ?.setScale(3, RoundingMode.HALF_UP)
                ?: throw ValidationException("Kalem miktari gecersiz")
            if (quantity <= BigDecimal.ZERO || quantity > MAX_ITEM_QUANTITY) {
                throw ValidationException("Kalem miktari gecersiz")
            }

            RequestItemPayload(
                itemName = itemName,
                quantity = quantity,
                unit = item.unit,
                packageCount = item.packageCount?.takeIf { it > 0 },
                dimensionsNote = item.dimensionsNote.clean(255),
                sortOrder = index
            )
        }
    }

    private fun enforceDriverVisibility(row: ResultRow, userId: UUID, role: UserRole) {
        if (role != UserRole.DRIVER) return
        val assignedDriverId = row[PurchaseRequestsTable.assignedDriverId]?.value
        if (assignedDriverId != null && assignedDriverId != userId) {
            throw AuthorizationFailureException()
        }
        if (assignedDriverId == null && row[PurchaseRequestsTable.materialStatus] !in driverVisibleUnassignedStatuses) {
            throw AuthorizationFailureException()
        }
        if (assignedDriverId == null && hasSpotDriverAssignment(row)) {
            throw AuthorizationFailureException()
        }
    }

    private fun hasSpotDriverAssignment(row: ResultRow): Boolean {
        return !row[PurchaseRequestsTable.licensePlate].isNullOrBlank() ||
            !row[PurchaseRequestsTable.spotDriverName].isNullOrBlank() ||
            !row[PurchaseRequestsTable.spotDriverIdentityNumber].isNullOrBlank() ||
            !row[PurchaseRequestsTable.spotDriverCompanyName].isNullOrBlank() ||
            !row[PurchaseRequestsTable.spotDriverPhone].isNullOrBlank()
    }

    private fun hasReadyTransportAssignment(row: ResultRow): Boolean {
        val licensePlate = row[PurchaseRequestsTable.licensePlate]
        if (licensePlate.isNullOrBlank()) return false

        val assignedDriverId = row[PurchaseRequestsTable.assignedDriverId]?.value
        if (assignedDriverId != null) return true

        return !row[PurchaseRequestsTable.spotDriverName].isNullOrBlank() &&
            !row[PurchaseRequestsTable.spotDriverIdentityNumber].isNullOrBlank() &&
            !row[PurchaseRequestsTable.spotDriverCompanyName].isNullOrBlank() &&
            !row[PurchaseRequestsTable.spotDriverPhone].isNullOrBlank()
    }

    private fun ensureTransportAssignmentForStatus(row: ResultRow, targetStatus: MaterialStatus) {
        if (targetStatus !in setOf(MaterialStatus.ON_THE_WAY, MaterialStatus.DELIVERED)) return
        if (!hasReadyTransportAssignment(row)) {
            throw ConflictException("Önce şoför, plaka, TC ve telefon bilgileri girilmeli")
        }
    }

    private fun ensureRequestAssignable(row: ResultRow) {
        if (row[PurchaseRequestsTable.materialStatus] in setOf(
            MaterialStatus.CANCELLED,
            MaterialStatus.DELIVERED
            )
        ) {
            throw ConflictException("Bu talep için atama değişikliği yapılamaz")
        }
    }

    private fun isValidStatusTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        return StatusTransitionValidator.isForwardTransition(currentStatus, newStatus)
    }

    private fun isPurchasingStatusTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        return currentStatus == newStatus
    }

    private fun isLogisticsStatusTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        return currentStatus == newStatus ||
            (currentStatus == MaterialStatus.IN_PROGRESS && newStatus == MaterialStatus.ON_THE_WAY) ||
            (currentStatus == MaterialStatus.PURCHASED && newStatus == MaterialStatus.ON_THE_WAY) ||
            (currentStatus == MaterialStatus.ON_THE_WAY && newStatus == MaterialStatus.DELIVERED)
    }

    private fun isDriverStatusTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        return currentStatus == newStatus ||
            (currentStatus == MaterialStatus.IN_PROGRESS && newStatus == MaterialStatus.ON_THE_WAY) ||
            (currentStatus == MaterialStatus.PURCHASED && newStatus == MaterialStatus.ON_THE_WAY) ||
            (currentStatus == MaterialStatus.ON_THE_WAY && newStatus == MaterialStatus.DELIVERED)
    }

    private fun ResultRow.toPurchaseRequestResponse(
        items: List<PurchaseRequestItemResponse>,
        seenByUserIds: Set<UUID>,
        currentUserId: UUID,
        viewerRole: UserRole
    ): PurchaseRequestResponse {
        val firstItem = items.firstOrNull()
        val assignedDriverId = this[PurchaseRequestsTable.assignedDriverId]?.value
        val isDriverViewer = viewerRole == UserRole.DRIVER
        val driverOwnsRequest = isDriverViewer && assignedDriverId == currentUserId
        // A driver browsing the unassigned pool is only choosing what to take on;
        // contact PII (address/phone/contact person) is withheld until the job is
        // actually theirs. Read-receipts and internal staff identities are never
        // exposed to any driver.
        val maskContactPii = isDriverViewer && !driverOwnsRequest
        return PurchaseRequestResponse(
            id = this[PurchaseRequestsTable.id].value.toString(),
            requestNumber = this[PurchaseRequestsTable.requestNumber],
            itemToPurchase = firstItem?.itemName ?: this[PurchaseRequestsTable.itemToPurchase],
            items = items,
            companyName = this[PurchaseRequestsTable.companyName],
            companyAddress = if (maskContactPii) "" else this[PurchaseRequestsTable.companyAddress].orEmpty(),
            contactNumber = if (maskContactPii) "" else this[PurchaseRequestsTable.contactNumber].orEmpty(),
            contactPersonName = if (maskContactPii) null else this[PurchaseRequestsTable.contactPersonName],
            productDimensions = this[PurchaseRequestsTable.productDimensions].orEmpty(),
            productWeight = this[PurchaseRequestsTable.productWeight].orEmpty(),
            packageCount = firstItem?.packageCount ?: this[PurchaseRequestsTable.packageCount],
            urgencyLevel = this[PurchaseRequestsTable.urgencyLevel],
            purchaseDate = this[PurchaseRequestsTable.purchaseDate].toApiString(),
            actualPurchaseDate = this[PurchaseRequestsTable.actualPurchaseDate]?.toEpochMillis(),
            notes = this[PurchaseRequestsTable.notes].orEmpty(),
            licensePlate = this[PurchaseRequestsTable.licensePlate].orEmpty(),
            spotDriverName = this[PurchaseRequestsTable.spotDriverName],
            spotDriverIdentityNumber = if (isDriverViewer) null else this[PurchaseRequestsTable.spotDriverIdentityNumber],
            spotDriverCompanyName = this[PurchaseRequestsTable.spotDriverCompanyName],
            spotDriverPhone = if (isDriverViewer) null else this[PurchaseRequestsTable.spotDriverPhone],
            deliveredTo = this[PurchaseRequestsTable.deliveredTo],
            materialStatus = this[PurchaseRequestsTable.materialStatus],
            assignedDriverId = assignedDriverId?.toString(),
            assignedDriverName = assignedDriverId?.let { userFullName(it) },
            assignedPurchasingStaffId = if (isDriverViewer) "" else this[PurchaseRequestsTable.assignedPurchasingStaffId].value.toString(),
            assignedPurchasingStaffName = if (isDriverViewer) null else userFullName(this[PurchaseRequestsTable.assignedPurchasingStaffId].value),
            createdAt = this[PurchaseRequestsTable.createdAt].toEpochMillis(),
            updatedAt = this[PurchaseRequestsTable.updatedAt].toEpochMillis(),
            cancellationReason = this[PurchaseRequestsTable.cancellationReason],
            cancelledBy = if (isDriverViewer) null else this[PurchaseRequestsTable.cancelledBy]?.value?.toString(),
            objectionReason = this[PurchaseRequestsTable.objectionReason],
            hasObjection = this[PurchaseRequestsTable.hasObjection],
            objectionBy = if (isDriverViewer) null else this[PurchaseRequestsTable.objectionBy]?.value?.toString(),
            redirectedToRequestId = this[PurchaseRequestsTable.redirectedToRequestId]?.value?.toString(),
            isNew = currentUserId !in seenByUserIds,
            seenByUserIds = if (isDriverViewer) emptySet() else seenByUserIds.map(UUID::toString).toSet()
        )
    }

    private fun findRequestRow(requestId: UUID): ResultRow {
        return PurchaseRequestsTable
            .selectAll()
            .where { PurchaseRequestsTable.id eq requestId }
            .singleOrNull()
            ?: throw NotFoundException()
    }

    private fun replaceRequestItems(
        requestId: UUID,
        items: List<RequestItemPayload>,
        createdAt: Instant
    ) {
        PurchaseRequestItemsTable.deleteWhere { PurchaseRequestItemsTable.requestId eq requestId }
        items.forEach { item ->
            PurchaseRequestItemsTable.insert {
                it[PurchaseRequestItemsTable.requestId] = requestId
                it[PurchaseRequestItemsTable.itemName] = item.itemName
                it[PurchaseRequestItemsTable.quantity] = item.quantity
                it[PurchaseRequestItemsTable.unit] = item.unit
                it[PurchaseRequestItemsTable.packageCount] = item.packageCount
                it[PurchaseRequestItemsTable.dimensionsNote] = item.dimensionsNote
                it[PurchaseRequestItemsTable.sortOrder] = item.sortOrder
                it[PurchaseRequestItemsTable.createdAt] = createdAt
            }
        }
    }

    private fun loadItemsMap(requestIds: List<UUID>): Map<UUID, List<PurchaseRequestItemResponse>> {
        if (requestIds.isEmpty()) return emptyMap()
        return PurchaseRequestItemsTable
            .selectAll()
            .where { PurchaseRequestItemsTable.requestId inList requestIds }
            .orderBy(PurchaseRequestItemsTable.sortOrder, SortOrder.ASC)
            .groupBy { it[PurchaseRequestItemsTable.requestId].value }
            .mapValues { entry ->
                entry.value.map { row ->
                    PurchaseRequestItemResponse(
                        id = row[PurchaseRequestItemsTable.id].value.toString(),
                        itemName = row[PurchaseRequestItemsTable.itemName],
                        quantity = row[PurchaseRequestItemsTable.quantity].stripTrailingZeros().toPlainString(),
                        unit = row[PurchaseRequestItemsTable.unit],
                        packageCount = row[PurchaseRequestItemsTable.packageCount],
                        dimensionsNote = row[PurchaseRequestItemsTable.dimensionsNote],
                        sortOrder = row[PurchaseRequestItemsTable.sortOrder]
                    )
                }
            }
    }

    private fun findDriver(driverId: UUID): AuthUser {
        val row = UsersTable
            .selectAll()
            .where {
                (UsersTable.id eq driverId) and
                    (UsersTable.role eq UserRole.DRIVER) and
                    (UsersTable.isActive eq true)
            }
            .singleOrNull()
            ?: throw NotFoundException("Şoför bulunamadı")

        return AuthUser(
            id = row[UsersTable.id].value,
            username = row[UsersTable.username],
            fullName = row[UsersTable.fullName],
            passwordHash = row[UsersTable.passwordHash],
            role = row[UsersTable.role],
            licensePlate = row[UsersTable.licensePlate],
            isActive = row[UsersTable.isActive],
            isSuperAdmin = row[UsersTable.isSuperAdmin],
            tokenVersion = row[UsersTable.tokenVersion],
            failedAttempts = row[UsersTable.failedAttempts],
            lockedUntil = row[UsersTable.lockedUntil],
            twoFactorSecret = row[UsersTable.twoFactorSecret],
            twoFactorEnabled = row[UsersTable.twoFactorEnabled]
        )
    }

    private fun ensureActivePurchasing(userId: UUID) {
        val exists = UsersTable
            .selectAll()
            .where {
                (UsersTable.id eq userId) and
                    ((UsersTable.role eq UserRole.PURCHASING) or (UsersTable.role eq UserRole.PURCHASING_ADMIN) or (UsersTable.role eq UserRole.ADMIN)) and
                    (UsersTable.isActive eq true)
            }
            .singleOrNull()
        if (exists == null) throw AuthorizationFailureException()
    }

    private fun ensureSuperAdmin(userId: UUID) {
        if (!isSuperAdmin(userId)) {
            throw AuthorizationFailureException()
        }
    }

    private fun isSuperAdmin(userId: UUID): Boolean {
        val row = UsersTable
            .selectAll()
            .where { (UsersTable.id eq userId) and (UsersTable.isActive eq true) }
            .singleOrNull()
            ?: return false

        return row[UsersTable.isSuperAdmin]
    }

    private fun loadSeenMap(requestIds: List<UUID>): Map<UUID, Set<UUID>> {
        if (requestIds.isEmpty()) return emptyMap()
        return RequestSeenTable
            .selectAll()
            .where { RequestSeenTable.requestId inList requestIds }
            .groupBy { it[RequestSeenTable.requestId].value }
            .mapValues { entry ->
                entry.value.map { row -> row[RequestSeenTable.userId].value }.toSet()
            }
    }

    private fun activeUserIdsByRole(role: UserRole): List<UUID> {
        return UsersTable
            .selectAll()
            .where { (UsersTable.role eq role) and (UsersTable.isActive eq true) }
            .map { it[UsersTable.id].value }
    }

    private fun activeUserIdsByRoles(vararg roles: UserRole): List<UUID> {
        return UsersTable
            .selectAll()
            .where { (UsersTable.role inList roles.toList()) and (UsersTable.isActive eq true) }
            .map { it[UsersTable.id].value }
    }

    private fun userFullName(userId: UUID): String? {
        return UsersTable
            .selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.fullName)
    }

    private fun MaterialStatus.displayName(): String {
        return when (this) {
            MaterialStatus.PENDING -> "Beklemede"
            MaterialStatus.IN_PROGRESS -> "Devam Ediyor"
            MaterialStatus.PURCHASED -> "Devam Ediyor"
            MaterialStatus.ON_THE_WAY -> "Yolda"
            MaterialStatus.DELIVERED -> "Teslim Edildi"
            MaterialStatus.CANCELLED -> "İptal Edildi"
        }
    }

    private fun UserRole.objectionActorName(): String {
        return when (this) {
            UserRole.LOGISTICS_OFFICE -> "Sevkiyat"
            UserRole.LOGISTICS_ADMIN -> "Sevkiyat Yönetici"
            UserRole.DRIVER -> "Şoför"
            UserRole.PURCHASING -> "Satınalma"
            UserRole.PURCHASING_ADMIN -> "Satınalma Yönetici"
            UserRole.ADMIN -> "Admin"
        }
    }

    companion object {
        private const val MAX_LIST_RESULTS = 500
        private const val MAX_REQUEST_ITEMS = 50
        private val MAX_ITEM_QUANTITY = BigDecimal("999999999.999")
        private val driverVisibleUnassignedStatuses = setOf(
            MaterialStatus.PENDING,
            MaterialStatus.IN_PROGRESS,
            MaterialStatus.PURCHASED
        )
    }
}
