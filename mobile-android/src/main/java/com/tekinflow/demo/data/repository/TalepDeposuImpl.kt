package com.tekinflow.demo.data.repository

import com.squareup.moshi.Moshi
import com.tekinflow.demo.data.local.dao.NotificationDao
import com.tekinflow.demo.data.local.dao.PendingSyncDao
import com.tekinflow.demo.data.local.dao.PurchaseRequestDao
import com.tekinflow.demo.data.local.dao.UserDao
import com.tekinflow.demo.data.local.entity.MarkSeenPayload
import com.tekinflow.demo.data.local.entity.ObjectionPayload
import com.tekinflow.demo.data.local.entity.PendingSyncActions
import com.tekinflow.demo.data.local.entity.PendingSyncEntity
import com.tekinflow.demo.data.local.entity.TakeOverPayload
import com.tekinflow.demo.data.local.entity.UpdateStatusPayload
import com.tekinflow.demo.data.local.mapper.toDomain
import com.tekinflow.demo.data.local.mapper.toEntity
import com.tekinflow.demo.data.remote.api.ApiCallResult
import com.tekinflow.demo.data.remote.api.NotificationApi
import com.tekinflow.demo.data.remote.api.RemoteDataException
import com.tekinflow.demo.data.remote.api.RequestApi
import com.tekinflow.demo.data.remote.api.UserApi
import com.tekinflow.demo.data.remote.api.executeApi
import com.tekinflow.demo.data.remote.api.executeApiResult
import com.tekinflow.demo.data.remote.dto.AssignDriverRequestDto
import com.tekinflow.demo.data.remote.dto.CancelRequestDto
import com.tekinflow.demo.data.remote.dto.ObjectionRequestDto
import com.tekinflow.demo.data.remote.dto.UpdateStatusRequestDto
import com.tekinflow.demo.data.remote.mapper.toCreateDto
import com.tekinflow.demo.data.remote.mapper.toDomain
import com.tekinflow.demo.data.remote.mapper.toUpdateDto
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.repository.TalepDeposu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TalepDeposuImpl @Inject constructor(
    private val requestApi: RequestApi,
    private val notificationApi: NotificationApi,
    private val userApi: UserApi,
    private val requestDao: PurchaseRequestDao,
    private val notificationDao: NotificationDao,
    private val userDao: UserDao,
    private val pendingSyncDao: PendingSyncDao,
    private val syncManager: SyncManager,
    private val userSession: KullaniciOturumu,
    moshi: Moshi
) : TalepDeposu {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateStatusAdapter = moshi.adapter(UpdateStatusPayload::class.java)
    private val takeOverAdapter = moshi.adapter(TakeOverPayload::class.java)
    private val objectionAdapter = moshi.adapter(ObjectionPayload::class.java)
    private val markSeenAdapter = moshi.adapter(MarkSeenPayload::class.java)

    init {
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (syncManager.isAppForeground) syncManager.requestSync()
            }
        }
    }

    override fun getAllRequests(): Flow<List<PurchaseRequest>> {
        syncManager.requestSync()
        return requestDao.observeAllRequests()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override suspend fun getRequestById(id: String): PurchaseRequest? {
        val localRequest = requestDao.getRequestById(id)?.toDomain()
        if (localRequest != null) {
            syncManager.requestSync()
            return localRequest
        }

        return when (
            val result = executeApiResult(defaultMessage = "Talep bilgisi alınamadı") {
                requestApi.getRequestById(id, authHeader())
            }
        ) {
            is ApiCallResult.Success -> {
                val request = result.body.toDomain()
                requestDao.upsert(request.toEntity())
                request
            }

            is ApiCallResult.HttpError -> {
                if (result.code == 404) null else throw RemoteDataException(result.message)
            }

            is ApiCallResult.NetworkError -> localRequest ?: throw RemoteDataException(result.message)
        }
    }

    override suspend fun createRequest(request: PurchaseRequest) {
        val created = executeApi(defaultMessage = "Talep oluşturulamadı") {
            requestApi.createRequest(
                request = request.toCreateDto(),
                authorization = authHeader(),
                idempotencyKey = newIdempotencyKey()
            )
        }.toDomain()
        requestDao.upsert(created.toEntity())
        syncManager.requestSync(force = true)
    }

    override suspend fun updateRequest(request: PurchaseRequest) {
        val updated = executeApi(defaultMessage = "Talep güncellenemedi") {
            requestApi.updateRequest(
                requestId = request.id,
                request = request.toUpdateDto(),
                authorization = authHeader(),
                idempotencyKey = newIdempotencyKey()
            )
        }.toDomain()
        requestDao.upsert(updated.toEntity())
        syncManager.requestSync(force = true)
    }

    override suspend fun updateStatus(requestId: String, status: MaterialStatus, deliveredTo: String?) {
        val recipient = deliveredTo?.trim()?.ifBlank { null }
        val currentRequest = requestDao.getRequestById(requestId)?.toDomain()
            ?: throw RemoteDataException("Talep bulunamadı")
        val optimisticRequest = currentRequest.copy(
            materialStatus = status,
            deliveredTo = recipient ?: currentRequest.deliveredTo,
            updatedAt = System.currentTimeMillis()
        )
        requestDao.upsert(optimisticRequest.toEntity())

        val idempotencyKey = newIdempotencyKey()
        when (
            val result = executeApiResult(defaultMessage = "Durum güncellenemedi") {
                requestApi.updateStatus(
                    requestId = requestId,
                    request = UpdateStatusRequestDto(status, recipient),
                    authorization = authHeader(),
                    idempotencyKey = idempotencyKey
                )
            }
        ) {
            is ApiCallResult.Success -> {
                requestDao.upsert(result.body.toDomain().toEntity())
                syncManager.requestSync(force = true)
            }

            is ApiCallResult.HttpError -> {
                refreshRequestSnapshot(requestId)
                throw RemoteDataException(result.message)
            }

            is ApiCallResult.NetworkError -> {
                enqueuePendingSync(
                    PendingSyncEntity(
                        idempotencyKey = idempotencyKey,
                        action = PendingSyncActions.UPDATE_STATUS,
                        requestId = requestId,
                        payload = updateStatusAdapter.toJson(UpdateStatusPayload(status, recipient))
                    )
                )
            }
        }
    }

    override suspend fun assignDriver(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest> {
        val normalizedPlate = licensePlate.normalizePlate()
        return runCatching {
            executeApi(defaultMessage = "Şoför ataması yapılamadı") {
                requestApi.assignDriver(
                    requestId = requestId,
                    request = AssignDriverRequestDto(
                        driverId = driverId,
                        licensePlate = normalizedPlate.ifBlank { null }
                    ),
                    authorization = authHeader(),
                    idempotencyKey = newIdempotencyKey()
                )
            }.toDomain().also {
                requestDao.upsert(it.toEntity())
                syncManager.requestSync(force = true)
            }
        }
    }

    override suspend fun assignSpotDriver(
        requestId: String,
        driverName: String,
        licensePlate: String,
        identityNumber: String,
        companyName: String,
        phone: String
    ): Result<PurchaseRequest> {
        val normalizedPlate = licensePlate.normalizePlate()
        return runCatching {
            executeApi(defaultMessage = "Spot araç bilgisi kaydedilemedi") {
                requestApi.assignDriver(
                    requestId = requestId,
                    request = AssignDriverRequestDto(
                        driverId = null,
                        licensePlate = normalizedPlate.ifBlank { null },
                        spotDriverName = driverName.ifBlank { null },
                        spotDriverIdentityNumber = identityNumber.ifBlank { null },
                        spotDriverCompanyName = companyName.ifBlank { null },
                        spotDriverPhone = phone.trim().ifBlank { null }
                    ),
                    authorization = authHeader(),
                    idempotencyKey = newIdempotencyKey()
                )
            }.toDomain().also {
                requestDao.upsert(it.toEntity())
                syncManager.requestSync(force = true)
            }
        }
    }

    override suspend fun unassignDriver(requestId: String): Result<PurchaseRequest> {
        return runCatching {
            executeApi(defaultMessage = "Atama kaldırılamadı") {
                requestApi.unassignDriver(
                    requestId = requestId,
                    authorization = authHeader(),
                    idempotencyKey = newIdempotencyKey()
                )
            }.toDomain().also {
                requestDao.upsert(it.toEntity())
                syncManager.requestSync(force = true)
            }
        }
    }

    override suspend fun takeOverRequest(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest> {
        val currentRequest = requestDao.getRequestById(requestId)?.toDomain()
            ?: return Result.failure(RemoteDataException("Talep bulunamadı"))
        val normalizedPlate = licensePlate.normalizePlate()
        val optimisticRequest = currentRequest.copy(
            assignedDriverId = driverId,
            licensePlate = normalizedPlate,
            materialStatus = MaterialStatus.IN_PROGRESS,
            updatedAt = System.currentTimeMillis()
        )
        requestDao.upsert(optimisticRequest.toEntity())

        val idempotencyKey = newIdempotencyKey()
        return when (
            val result = executeApiResult(defaultMessage = "Talep üzerinize alınamadı") {
                requestApi.takeOverRequest(
                    requestId = requestId,
                    authorization = authHeader(),
                    idempotencyKey = idempotencyKey
                )
            }
        ) {
            is ApiCallResult.Success -> {
                val request = result.body.toDomain()
                requestDao.upsert(request.toEntity())
                syncManager.requestSync(force = true)
                Result.success(request)
            }

            is ApiCallResult.HttpError -> {
                refreshRequestSnapshot(requestId)
                Result.failure(RemoteDataException(result.message))
            }

            is ApiCallResult.NetworkError -> {
                enqueuePendingSync(
                    PendingSyncEntity(
                        idempotencyKey = idempotencyKey,
                        action = PendingSyncActions.TAKE_OVER,
                        requestId = requestId,
                        payload = takeOverAdapter.toJson(
                            TakeOverPayload(
                                driverId = driverId,
                                licensePlate = normalizedPlate
                            )
                        )
                    )
                )
                Result.success(optimisticRequest)
            }
        }
    }

    override suspend fun cancelRequest(requestId: String, reason: String?, cancelledBy: String) {
        val updated = executeApi(defaultMessage = "Talep iptal edilemedi") {
            requestApi.cancelRequest(
                requestId = requestId,
                request = CancelRequestDto(reason?.trim()?.ifBlank { null }),
                authorization = authHeader(),
                idempotencyKey = newIdempotencyKey()
            )
        }.toDomain()
        requestDao.upsert(updated.toEntity())
        syncManager.requestSync(force = true)
    }

    override suspend fun submitObjection(requestId: String, driverId: String, reason: String) {
        val currentRequest = requestDao.getRequestById(requestId)?.toDomain()
            ?: throw RemoteDataException("Talep bulunamadı")
        val optimisticRequest = currentRequest.copy(
            hasObjection = true,
            objectionReason = reason.trim(),
            objectionBy = driverId,
            updatedAt = System.currentTimeMillis()
        )
        requestDao.upsert(optimisticRequest.toEntity())

        val idempotencyKey = newIdempotencyKey()
        when (
            val result = executeApiResult(defaultMessage = "İtiraz iletilemedi") {
                requestApi.submitObjection(
                    requestId = requestId,
                    request = ObjectionRequestDto(reason.trim()),
                    authorization = authHeader(),
                    idempotencyKey = idempotencyKey
                )
            }
        ) {
            is ApiCallResult.Success -> {
                requestDao.upsert(result.body.toDomain().toEntity())
                syncManager.requestSync(force = true)
            }

            is ApiCallResult.HttpError -> {
                refreshRequestSnapshot(requestId)
                throw RemoteDataException(result.message)
            }

            is ApiCallResult.NetworkError -> {
                enqueuePendingSync(
                    PendingSyncEntity(
                        idempotencyKey = idempotencyKey,
                        action = PendingSyncActions.OBJECT,
                        requestId = requestId,
                        payload = objectionAdapter.toJson(
                            ObjectionPayload(
                                driverId = driverId,
                                reason = reason.trim()
                            )
                        )
                    )
                )
            }
        }
    }

    override suspend fun markAsSeen(requestId: String, userId: String) {
        val currentRequest = requestDao.getRequestById(requestId)?.toDomain() ?: return
        requestDao.upsert(
            currentRequest.copy(
                isNew = false,
                seenByUserIds = currentRequest.seenByUserIds + userId
            ).toEntity()
        )

        val idempotencyKey = newIdempotencyKey()
        when (
            val result = executeApiResult(defaultMessage = "Talep okundu olarak işaretlenemedi") {
                requestApi.markSeen(
                    requestId = requestId,
                    authorization = authHeader(),
                    idempotencyKey = idempotencyKey
                )
            }
        ) {
            is ApiCallResult.Success -> Unit
            is ApiCallResult.HttpError -> if (result.code !in 400..499) {
                enqueuePendingSync(
                    PendingSyncEntity(
                        idempotencyKey = idempotencyKey,
                        action = PendingSyncActions.MARK_SEEN,
                        requestId = requestId,
                        payload = markSeenAdapter.toJson(MarkSeenPayload(userId))
                    )
                )
            }

            is ApiCallResult.NetworkError -> {
                enqueuePendingSync(
                    PendingSyncEntity(
                        idempotencyKey = idempotencyKey,
                        action = PendingSyncActions.MARK_SEEN,
                        requestId = requestId,
                        payload = markSeenAdapter.toJson(MarkSeenPayload(userId))
                    )
                )
            }
        }
    }

    override fun getDrivers(): Flow<List<User>> {
        syncManager.requestSync()
        return userDao.observeDrivers()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun getNotifications(userId: String): Flow<List<AppNotification>> {
        syncManager.requestSync()
        return notificationDao.observeNotifications(userId)
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override suspend fun markNotificationRead(notificationId: String) {
        notificationDao.markAsRead(notificationId)
        when (
            val result = executeApiResult(defaultMessage = "Bildirim güncellenemedi") {
                notificationApi.markAsRead(notificationId, authHeader())
            }
        ) {
            is ApiCallResult.Success -> Unit
            is ApiCallResult.HttpError -> Unit
            is ApiCallResult.NetworkError -> Unit
        }
    }

    override suspend fun generateRequestNumber(): String {
        syncManager.requestSync()
        val requests = requestDao.getAllRequestsNow().map { it.toDomain() }
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val prefix = "TLP-$year-"
        val nextSequence = requests
            .mapNotNull { request ->
                request.requestNumber
                    .takeIf { it.startsWith(prefix) }
                    ?.removePrefix(prefix)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?.plus(1)
            ?: 1

        return "TLP-$year-%06d".format(nextSequence)
    }

    override suspend fun getDeliveredRecipients(): List<String> {
        return when (
            val result = executeApiResult(defaultMessage = "Teslim alan kişiler alınamadı") {
                requestApi.getDeliveredRecipients(authHeader())
            }
        ) {
            is ApiCallResult.Success -> result.body
            is ApiCallResult.HttpError -> emptyList()
            is ApiCallResult.NetworkError -> emptyList()
        }
    }

    private suspend fun enqueuePendingSync(entity: PendingSyncEntity) {
        pendingSyncDao.enqueue(entity)
        syncManager.requestSync()
    }

    private fun authHeader(): String? = runCatching { userSession.getAccessToken() }
        .getOrNull()
        ?.let { token -> "Bearer $token" }

    private fun newIdempotencyKey(): String = UUID.randomUUID().toString()

    private suspend fun refreshRequestSnapshot(requestId: String) {
        when (
            val result = executeApiResult(defaultMessage = "Talep bilgisi alınamadı") {
                requestApi.getRequestById(requestId, authHeader())
            }
        ) {
            is ApiCallResult.Success -> requestDao.upsert(result.body.toDomain().toEntity())
            is ApiCallResult.HttpError -> Unit
            is ApiCallResult.NetworkError -> Unit
        }
    }

    private fun String.normalizePlate(): String = trim().uppercase(Locale.ROOT)

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
    }
}
