package com.tekinflow.demo.testutil

import app.cash.turbine.ReceiveTurbine
import com.squareup.moshi.Moshi
import com.tekinflow.demo.data.local.dao.NotificationDao
import com.tekinflow.demo.data.local.dao.PendingSyncDao
import com.tekinflow.demo.data.local.dao.PurchaseRequestDao
import com.tekinflow.demo.data.local.dao.UserDao
import com.tekinflow.demo.data.local.entity.MarkSeenPayload
import com.tekinflow.demo.data.local.entity.NotificationEntity
import com.tekinflow.demo.data.local.entity.ObjectionPayload
import com.tekinflow.demo.data.local.entity.PendingSyncEntity
import com.tekinflow.demo.data.local.entity.PurchaseRequestEntity
import com.tekinflow.demo.data.local.entity.TakeOverPayload
import com.tekinflow.demo.data.local.entity.UpdateStatusPayload
import com.tekinflow.demo.data.local.entity.UserEntity
import com.tekinflow.demo.data.local.mapper.toEntity
import com.tekinflow.demo.data.remote.api.AuthApi
import com.tekinflow.demo.data.remote.api.NotificationApi
import com.tekinflow.demo.data.remote.api.RequestApi
import com.tekinflow.demo.data.remote.api.UserApi
import com.tekinflow.demo.data.remote.dto.AssignDriverRequestDto
import com.tekinflow.demo.data.remote.dto.CancelRequestDto
import com.tekinflow.demo.data.remote.dto.CreatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.LoginRequestDto
import com.tekinflow.demo.data.remote.dto.LoginResponseDto
import com.tekinflow.demo.data.remote.dto.LogoutRequestDto
import com.tekinflow.demo.data.remote.dto.NotificationDto
import com.tekinflow.demo.data.remote.dto.ObjectionRequestDto
import com.tekinflow.demo.data.remote.dto.PurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.RefreshRequestDto
import com.tekinflow.demo.data.remote.dto.SuccessResponseDto
import com.tekinflow.demo.data.remote.dto.TokenResponseDto
import com.tekinflow.demo.data.remote.dto.UpdatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.UpdateStatusRequestDto
import com.tekinflow.demo.data.remote.dto.UserDto
import com.tekinflow.demo.data.notification.FcmTokenRegistrar
import com.tekinflow.demo.data.session.SessionManager
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.repository.TalepDeposu
import com.tekinflow.demo.presentation.components.AppMessage
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.ConnectException
import java.util.Calendar

fun sampleUser(
    id: String = "user-1",
    username: String = "mehmet",
    fullName: String = "Mehmet Demir",
    role: UserRole = UserRole.PURCHASING,
    licensePlate: String? = null
): User {
    return User(
        id = id,
        username = username,
        fullName = fullName,
        role = role,
        licensePlate = licensePlate
    )
}

fun sampleUserDto(
    id: String = "user-1",
    username: String = "mehmet",
    fullName: String = "Mehmet Demir",
    role: UserRole = UserRole.PURCHASING,
    licensePlate: String? = null
): UserDto {
    return UserDto(
        id = id,
        username = username,
        fullName = fullName,
        role = role,
        licensePlate = licensePlate
    )
}

fun sampleRequest(
    id: String = "request-1",
    requestNumber: String = "TLP-${Calendar.getInstance().get(Calendar.YEAR)}-001",
    itemToPurchase: String = "Çelik Profil",
    companyName: String = "TekinFlow",
    assignedDriverId: String? = null,
    assignedPurchasingStaffId: String = "staff-1",
    materialStatus: MaterialStatus = MaterialStatus.PENDING,
    urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    licensePlate: String = "",
    spotDriverName: String? = null,
    spotDriverIdentityNumber: String? = null,
    spotDriverCompanyName: String? = null,
    hasObjection: Boolean = false,
    objectionReason: String? = null,
    objectionBy: String? = null,
    isNew: Boolean = true,
    seenByUserIds: Set<String> = emptySet(),
    updatedAt: Long = 1_700_000_000_000L
): PurchaseRequest {
    return PurchaseRequest(
        id = id,
        requestNumber = requestNumber,
        itemToPurchase = itemToPurchase,
        companyName = companyName,
        companyAddress = "Gebze",
        contactNumber = "05550000000",
        productDimensions = "100x50",
        productWeight = "25kg",
        urgencyLevel = urgencyLevel,
        purchaseDate = "15.04.2026",
        notes = "Not",
        licensePlate = licensePlate,
        spotDriverName = spotDriverName,
        spotDriverIdentityNumber = spotDriverIdentityNumber,
        spotDriverCompanyName = spotDriverCompanyName,
        materialStatus = materialStatus,
        assignedDriverId = assignedDriverId,
        assignedPurchasingStaffId = assignedPurchasingStaffId,
        createdAt = 1_700_000_000_000L,
        updatedAt = updatedAt,
        cancellationReason = null,
        cancelledBy = null,
        objectionReason = objectionReason,
        hasObjection = hasObjection,
        objectionBy = objectionBy,
        redirectedToRequestId = null,
        isNew = isNew,
        seenByUserIds = seenByUserIds
    )
}

fun sampleRequestDto(
    id: String = "request-1",
    requestNumber: String = "TLP-${Calendar.getInstance().get(Calendar.YEAR)}-001",
    itemToPurchase: String = "Çelik Profil",
    companyName: String = "TekinFlow",
    assignedDriverId: String? = null,
    assignedPurchasingStaffId: String = "staff-1",
    materialStatus: MaterialStatus = MaterialStatus.PENDING,
    urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    licensePlate: String = "",
    hasObjection: Boolean = false,
    objectionReason: String? = null,
    objectionBy: String? = null,
    isNew: Boolean = true,
    seenByUserIds: Set<String> = emptySet(),
    updatedAt: Long = 1_700_000_000_000L
): PurchaseRequestDto {
    return PurchaseRequestDto(
        id = id,
        requestNumber = requestNumber,
        itemToPurchase = itemToPurchase,
        companyName = companyName,
        companyAddress = "Gebze",
        contactNumber = "05550000000",
        productDimensions = "100x50",
        productWeight = "25kg",
        urgencyLevel = urgencyLevel,
        purchaseDate = "2026-04-15",
        notes = "Not",
        licensePlate = licensePlate,
        materialStatus = materialStatus,
        assignedDriverId = assignedDriverId,
        assignedPurchasingStaffId = assignedPurchasingStaffId,
        createdAt = 1_700_000_000_000L,
        updatedAt = updatedAt,
        cancellationReason = null,
        cancelledBy = null,
        objectionReason = objectionReason,
        hasObjection = hasObjection,
        objectionBy = objectionBy,
        redirectedToRequestId = null,
        isNew = isNew,
        seenByUserIds = seenByUserIds
    )
}

fun sampleNotification(
    id: String = "notification-1",
    targetUserId: String = "user-1",
    isRead: Boolean = false
): AppNotification {
    return AppNotification(
        id = id,
        message = "Yeni görev",
        timestamp = 1_700_000_000_000L,
        isRead = isRead,
        targetUserId = targetUserId
    )
}

fun sampleNotificationDto(
    id: String = "notification-1",
    targetUserId: String = "user-1",
    isRead: Boolean = false
): NotificationDto {
    return NotificationDto(
        id = id,
        message = "Yeni görev",
        timestamp = 1_700_000_000_000L,
        isRead = isRead,
        targetUserId = targetUserId
    )
}

fun sampleLoginResponse(userDto: UserDto = sampleUserDto()): LoginResponseDto {
    return LoginResponseDto(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        user = userDto
    )
}

fun errorResponse(code: Int, message: String): ResponseBodyWithCode {
    return ResponseBodyWithCode(
        code = code,
        body = """{"error":"$message"}"""
            .toResponseBody("application/json".toMediaType())
    )
}

data class ResponseBodyWithCode(
    val code: Int,
    val body: okhttp3.ResponseBody
)

fun <T> errorHttpResponse(code: Int, message: String): Response<T> {
    val error = errorResponse(code, message)
    return Response.error(error.code, error.body)
}

class FakeTalepDeposu(
    initialRequests: List<PurchaseRequest> = emptyList(),
    initialDrivers: List<User> = emptyList(),
    initialNotifications: List<AppNotification> = emptyList()
) : TalepDeposu {

    private val _requests = MutableStateFlow(initialRequests)
    private val _drivers = MutableStateFlow(initialDrivers)
    private val _notifications = MutableStateFlow(initialNotifications)

    val createdRequests = mutableListOf<PurchaseRequest>()
    val updatedRequests = mutableListOf<PurchaseRequest>()
    val cancelledRequests = mutableListOf<Triple<String, String?, String>>()
    val updatedStatuses = mutableListOf<Pair<String, MaterialStatus>>()
    val assignDriverCalls = mutableListOf<Triple<String, String, String>>()
    val assignSpotDriverCalls = mutableListOf<List<String>>()
    val unassignDriverCalls = mutableListOf<String>()
    val takeOverCalls = mutableListOf<Triple<String, String, String>>()
    val objectionCalls = mutableListOf<Triple<String, String, String>>()
    val markAsSeenCalls = mutableListOf<Pair<String, String>>()
    val markNotificationReadCalls = mutableListOf<String>()

    var createRequestException: Exception? = null
    var updateRequestException: Exception? = null
    var cancelRequestException: Exception? = null
    var updateStatusException: Exception? = null
    var submitObjectionException: Exception? = null
    var getRequestByIdOverrides = mutableMapOf<String, PurchaseRequest?>()
    var generatedRequestNumber = "TLP-${Calendar.getInstance().get(Calendar.YEAR)}-099"
    var generateRequestNumberCallCount = 0
    var assignDriverResultProvider: (String, String, String) -> Result<PurchaseRequest> = { requestId, driverId, plate ->
        val updated = requireNotNull(findRequest(requestId)).copy(
            assignedDriverId = driverId,
            licensePlate = plate
        )
        upsertRequest(updated)
        Result.success(updated)
    }
    var unassignDriverResultProvider: (String) -> Result<PurchaseRequest> = { requestId ->
        val updated = requireNotNull(findRequest(requestId)).copy(
            assignedDriverId = null,
            licensePlate = ""
        )
        upsertRequest(updated)
        Result.success(updated)
    }
    var takeOverResultProvider: (String, String, String) -> Result<PurchaseRequest> = { requestId, driverId, plate ->
        val updated = requireNotNull(findRequest(requestId)).copy(
            assignedDriverId = driverId,
            licensePlate = plate,
            materialStatus = MaterialStatus.IN_PROGRESS
        )
        upsertRequest(updated)
        Result.success(updated)
    }

    override fun getAllRequests(): Flow<List<PurchaseRequest>> = _requests.asStateFlow()

    override suspend fun getRequestById(id: String): PurchaseRequest? {
        return getRequestByIdOverrides[id] ?: findRequest(id)
    }

    override suspend fun createRequest(request: PurchaseRequest) {
        createRequestException?.let { throw it }
        createdRequests += request
        upsertRequest(request)
    }

    override suspend fun updateRequest(request: PurchaseRequest) {
        updateRequestException?.let { throw it }
        updatedRequests += request
        upsertRequest(request)
    }

    override suspend fun updateStatus(requestId: String, status: MaterialStatus, deliveredTo: String?) {
        updateStatusException?.let { throw it }
        updatedStatuses += requestId to status
        findRequest(requestId)?.let { request ->
            upsertRequest(
                request.copy(
                    materialStatus = status,
                    deliveredTo = deliveredTo ?: request.deliveredTo
                )
            )
        }
    }

    override suspend fun assignDriver(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest> {
        assignDriverCalls += Triple(requestId, driverId, licensePlate)
        return assignDriverResultProvider(requestId, driverId, licensePlate)
    }

    override suspend fun assignSpotDriver(
        requestId: String,
        driverName: String,
        licensePlate: String,
        identityNumber: String,
        companyName: String,
        phone: String
    ): Result<PurchaseRequest> {
        assignSpotDriverCalls += listOf(requestId, driverName, licensePlate, identityNumber, companyName, phone)
        val updated = requireNotNull(findRequest(requestId)).copy(
            assignedDriverId = null,
            spotDriverName = driverName,
            spotDriverIdentityNumber = identityNumber.ifBlank { null },
            spotDriverCompanyName = companyName.ifBlank { null },
            licensePlate = licensePlate,
            materialStatus = MaterialStatus.ON_THE_WAY
        )
        upsertRequest(updated)
        return Result.success(updated)
    }

    override suspend fun unassignDriver(requestId: String): Result<PurchaseRequest> {
        unassignDriverCalls += requestId
        return unassignDriverResultProvider(requestId)
    }

    override suspend fun takeOverRequest(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest> {
        takeOverCalls += Triple(requestId, driverId, licensePlate)
        return takeOverResultProvider(requestId, driverId, licensePlate)
    }

    override suspend fun cancelRequest(requestId: String, reason: String?, cancelledBy: String) {
        cancelRequestException?.let { throw it }
        cancelledRequests += Triple(requestId, reason, cancelledBy)
        findRequest(requestId)?.let { request ->
            upsertRequest(
                request.copy(
                    materialStatus = MaterialStatus.CANCELLED,
                    cancellationReason = reason,
                    cancelledBy = cancelledBy
                )
            )
        }
    }

    override suspend fun submitObjection(requestId: String, driverId: String, reason: String) {
        submitObjectionException?.let { throw it }
        objectionCalls += Triple(requestId, driverId, reason)
        findRequest(requestId)?.let { request ->
            upsertRequest(
                request.copy(
                    hasObjection = true,
                    objectionBy = driverId,
                    objectionReason = reason
                )
            )
        }
    }

    override suspend fun markAsSeen(requestId: String, userId: String) {
        markAsSeenCalls += requestId to userId
        findRequest(requestId)?.let { request ->
            upsertRequest(
                request.copy(
                    isNew = false,
                    seenByUserIds = request.seenByUserIds + userId
                )
            )
        }
    }

    override fun getDrivers(): Flow<List<User>> = _drivers.asStateFlow()

    override fun getNotifications(userId: String): Flow<List<AppNotification>> = _notifications.asStateFlow()

    override suspend fun markNotificationRead(notificationId: String) {
        markNotificationReadCalls += notificationId
        _notifications.value = _notifications.value.map { notification ->
            if (notification.id == notificationId) notification.copy(isRead = true) else notification
        }
    }

    override suspend fun generateRequestNumber(): String {
        generateRequestNumberCallCount++
        return generatedRequestNumber
    }

    var deliveredRecipients: List<String> = emptyList()

    override suspend fun getDeliveredRecipients(): List<String> = deliveredRecipients

    fun setRequests(requests: List<PurchaseRequest>) {
        _requests.value = requests
    }

    fun setDrivers(drivers: List<User>) {
        _drivers.value = drivers
    }

    fun setNotifications(notifications: List<AppNotification>) {
        _notifications.value = notifications
    }

    private fun findRequest(id: String): PurchaseRequest? {
        return _requests.value.firstOrNull { it.id == id }
    }

    private fun upsertRequest(request: PurchaseRequest) {
        _requests.value = _requests.value
            .filterNot { it.id == request.id }
            .plus(request)
            .sortedByDescending { it.updatedAt }
    }
}

class FakeAuthApi : AuthApi {
    val loginRequests = mutableListOf<LoginRequestDto>()
    var loginHandler: suspend (LoginRequestDto) -> Response<LoginResponseDto> = {
        Response.success(sampleLoginResponse())
    }

    override suspend fun login(request: LoginRequestDto): Response<LoginResponseDto> {
        loginRequests += request
        return loginHandler(request)
    }

    override suspend fun refresh(request: RefreshRequestDto): Response<TokenResponseDto> {
        return Response.success(TokenResponseDto("access", "refresh"))
    }

    override fun refreshBlocking(request: RefreshRequestDto): Call<TokenResponseDto> {
        return object : Call<TokenResponseDto> {
            override fun clone(): Call<TokenResponseDto> = this
            override fun execute(): Response<TokenResponseDto> = Response.success(TokenResponseDto("access", "refresh"))
            override fun enqueue(callback: Callback<TokenResponseDto>) = Unit
            override fun isExecuted(): Boolean = false
            override fun cancel() = Unit
            override fun isCanceled(): Boolean = false
            override fun request(): okhttp3.Request = okhttp3.Request.Builder().url("http://localhost").build()
            override fun timeout(): Timeout = Timeout.NONE
        }
    }

    override suspend fun logout(request: LogoutRequestDto): Response<SuccessResponseDto> {
        return Response.success(SuccessResponseDto(true))
    }
}

class FakePurchaseRequestDao(
    initialItems: List<PurchaseRequestEntity> = emptyList()
) : PurchaseRequestDao {
    var clearCalls = 0
        private set
    private val state = MutableStateFlow(initialItems.sortedByDescending { it.updatedAt })

    override fun observeAllRequests(): Flow<List<PurchaseRequestEntity>> = state.asStateFlow()

    override suspend fun getAllRequestsNow(): List<PurchaseRequestEntity> = state.value

    override suspend fun getRequestById(requestId: String): PurchaseRequestEntity? {
        return state.value.firstOrNull { it.id == requestId }
    }

    override suspend fun upsert(request: PurchaseRequestEntity) {
        state.value = state.value
            .filterNot { it.id == request.id }
            .plus(request)
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun upsertAll(requests: List<PurchaseRequestEntity>) {
        val merged = state.value.toMutableList()
        requests.forEach { request ->
            merged.removeAll { it.id == request.id }
            merged += request
        }
        state.value = merged.sortedByDescending { it.updatedAt }
    }

    override suspend fun clearAll() {
        clearCalls += 1
        state.value = emptyList()
    }
}

class FakeNotificationDao(
    initialItems: List<NotificationEntity> = emptyList()
) : NotificationDao {
    var clearCalls = 0
        private set
    private val state = MutableStateFlow(initialItems)

    override suspend fun getAllNow(): List<NotificationEntity> = state.value

    override fun observeNotifications(userId: String): Flow<List<NotificationEntity>> {
        return state.map { notifications ->
            notifications
                .filter { it.targetUserId == userId }
                .sortedByDescending { it.timestamp }
        }
    }

    override suspend fun upsert(notification: NotificationEntity) {
        state.value = state.value.filterNot { it.id == notification.id } + notification
    }

    override suspend fun upsertAll(notifications: List<NotificationEntity>) {
        val merged = state.value.toMutableList()
        notifications.forEach { notification ->
            merged.removeAll { it.id == notification.id }
            merged += notification
        }
        state.value = merged
    }

    override suspend fun clearAll() {
        clearCalls += 1
        state.value = emptyList()
    }

    override suspend fun markAsRead(notificationId: String) {
        state.value = state.value.map { notification ->
            if (notification.id == notificationId) notification.copy(isRead = true) else notification
        }
    }

    override suspend fun getNotificationIds(): List<String> = state.value.map { it.id }
}

class FakeUserDao(
    initialItems: List<UserEntity> = emptyList()
) : UserDao {
    var clearCalls = 0
        private set
    private val state = MutableStateFlow(initialItems)

    override suspend fun getAllNow(): List<UserEntity> = state.value

    override fun observeDrivers(): Flow<List<UserEntity>> = state.map { users ->
        users.filter { it.role == UserRole.DRIVER && it.isActive }.sortedBy { it.fullName }
    }

    override suspend fun upsertAll(users: List<UserEntity>) {
        state.value = users
    }

    override suspend fun clearAll() {
        clearCalls += 1
        state.value = emptyList()
    }
}

class FakePendingSyncDao(
    initialItems: List<PendingSyncEntity> = emptyList()
) : PendingSyncDao {
    private val state = MutableStateFlow(initialItems.sortedBy { it.createdAt })

    override fun observePendingSyncs(): Flow<List<PendingSyncEntity>> = state.map { items ->
        items.filterNot { it.failed }
    }

    override suspend fun getPendingSyncs(): List<PendingSyncEntity> = state.value.filterNot { it.failed }

    override suspend fun getFailedSyncs(): List<PendingSyncEntity> = state.value.filter { it.failed }

    override suspend fun enqueue(entity: PendingSyncEntity) {
        state.value = state.value.filterNot { it.id == entity.id }.plus(entity).sortedBy { it.createdAt }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }

    override suspend fun updateRetry(id: String, retryCount: Int, lastError: String?) {
        state.value = state.value.map { entity ->
            if (entity.id == id) entity.copy(retryCount = retryCount, lastError = lastError) else entity
        }
    }

    override suspend fun markFailed(id: String, retryCount: Int, lastError: String?) {
        state.value = state.value.map { entity ->
            if (entity.id == id) {
                entity.copy(retryCount = retryCount, lastError = lastError, failed = true)
            } else {
                entity
            }
        }
    }
}

class FakeRequestApi : RequestApi {
    var getRequestsHandler: suspend () -> Response<List<PurchaseRequestDto>> = {
        Response.success(emptyList())
    }
    var getRequestByIdHandler: suspend (String) -> Response<PurchaseRequestDto> = { requestId ->
        Response.success(sampleRequestDto(id = requestId))
    }
    var createRequestHandler: suspend (CreatePurchaseRequestDto) -> Response<PurchaseRequestDto> = {
        Response.success(sampleRequestDto())
    }
    var updateRequestHandler: suspend (String, UpdatePurchaseRequestDto) -> Response<PurchaseRequestDto> = { requestId, _ ->
        Response.success(sampleRequestDto(id = requestId))
    }
    var cancelRequestHandler: suspend (String, CancelRequestDto) -> Response<PurchaseRequestDto> = { requestId, _ ->
        Response.success(sampleRequestDto(id = requestId, materialStatus = MaterialStatus.CANCELLED))
    }
    var assignDriverHandler: suspend (String, AssignDriverRequestDto) -> Response<PurchaseRequestDto> = { requestId, request ->
        Response.success(
            sampleRequestDto(
                id = requestId,
                assignedDriverId = request.driverId,
                licensePlate = request.licensePlate.orEmpty()
            )
        )
    }
    var unassignDriverHandler: suspend (String) -> Response<PurchaseRequestDto> = { requestId ->
        Response.success(sampleRequestDto(id = requestId))
    }
    var takeOverHandler: suspend (String) -> Response<PurchaseRequestDto> = { requestId ->
        Response.success(sampleRequestDto(id = requestId, assignedDriverId = "driver-1", materialStatus = MaterialStatus.IN_PROGRESS))
    }
    var updateStatusHandler: suspend (String, UpdateStatusRequestDto) -> Response<PurchaseRequestDto> = { requestId, request ->
        Response.success(sampleRequestDto(id = requestId, materialStatus = request.status))
    }
    var submitObjectionHandler: suspend (String, ObjectionRequestDto) -> Response<PurchaseRequestDto> = { requestId, request ->
        Response.success(sampleRequestDto(id = requestId, hasObjection = true, objectionReason = request.reason))
    }
    var markSeenHandler: suspend (String) -> Response<SuccessResponseDto> = {
        Response.success(SuccessResponseDto(true))
    }

    override suspend fun getRequests(
        status: String?,
        assignedDriverId: String?,
        staffId: String?,
        authorization: String?
    ): Response<List<PurchaseRequestDto>> = getRequestsHandler()

    override suspend fun getRequestById(
        requestId: String,
        authorization: String?
    ): Response<PurchaseRequestDto> {
        return getRequestByIdHandler(requestId)
    }

    override suspend fun createRequest(
        request: CreatePurchaseRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return createRequestHandler(request)
    }

    override suspend fun updateRequest(
        requestId: String,
        request: UpdatePurchaseRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return updateRequestHandler(requestId, request)
    }

    override suspend fun cancelRequest(
        requestId: String,
        request: CancelRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return cancelRequestHandler(requestId, request)
    }

    override suspend fun assignDriver(
        requestId: String,
        request: AssignDriverRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return assignDriverHandler(requestId, request)
    }

    override suspend fun unassignDriver(
        requestId: String,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return unassignDriverHandler(requestId)
    }

    override suspend fun takeOverRequest(
        requestId: String,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return takeOverHandler(requestId)
    }

    override suspend fun updateStatus(
        requestId: String,
        request: UpdateStatusRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return updateStatusHandler(requestId, request)
    }

    override suspend fun submitObjection(
        requestId: String,
        request: ObjectionRequestDto,
        authorization: String?,
        idempotencyKey: String?
    ): Response<PurchaseRequestDto> {
        return submitObjectionHandler(requestId, request)
    }

    override suspend fun markSeen(
        requestId: String,
        authorization: String?,
        idempotencyKey: String?
    ): Response<SuccessResponseDto> {
        return markSeenHandler(requestId)
    }

    var getDeliveredRecipientsHandler: suspend () -> Response<List<String>> = {
        Response.success(emptyList())
    }

    override suspend fun getDeliveredRecipients(
        authorization: String?
    ): Response<List<String>> {
        return getDeliveredRecipientsHandler()
    }
}

class FakeNotificationApi : NotificationApi {
    var getNotificationsHandler: suspend () -> Response<List<NotificationDto>> = {
        Response.success(emptyList())
    }
    var markAsReadHandler: suspend (String) -> Response<SuccessResponseDto> = {
        Response.success(SuccessResponseDto(true))
    }

    override suspend fun getNotifications(authorization: String?): Response<List<NotificationDto>> = getNotificationsHandler()

    override suspend fun markAsRead(notificationId: String, authorization: String?): Response<SuccessResponseDto> {
        return markAsReadHandler(notificationId)
    }
}

class FakeUserApi : UserApi {
    override suspend fun changePassword(request: com.tekinflow.demo.data.remote.dto.ChangePasswordRequestDto, authorization: String?): Response<SuccessResponseDto> = Response.success(SuccessResponseDto(true))
    var getUsersHandler: suspend () -> Response<List<UserDto>> = { Response.success(emptyList()) }
    var getDriversHandler: suspend () -> Response<List<UserDto>> = { Response.success(emptyList()) }

    override suspend fun getUsers(authorization: String?): Response<List<UserDto>> = getUsersHandler()

    override suspend fun getDrivers(authorization: String?): Response<List<UserDto>> = getDriversHandler()

    override suspend fun updateFcmToken(
        request: com.tekinflow.demo.data.remote.dto.FcmTokenRequestDto,
        authorization: String?
    ): Response<SuccessResponseDto> {
        return Response.success(SuccessResponseDto(true))
    }

    override suspend fun createUser(
        request: com.tekinflow.demo.data.remote.dto.CreateUserRequestDto,
        authorization: String?
    ): Response<UserDto> {
        throw UnsupportedOperationException("createUser is not used in these tests")
    }

    override suspend fun updateUser(
        userId: String,
        request: com.tekinflow.demo.data.remote.dto.UpdateUserRequestDto,
        authorization: String?
    ): Response<UserDto> {
        throw UnsupportedOperationException("updateUser is not used in these tests")
    }

    override suspend fun deleteUser(userId: String, authorization: String?): Response<SuccessResponseDto> {
        throw UnsupportedOperationException("deleteUser is not used in these tests")
    }

    override suspend fun activateUser(userId: String, authorization: String?): Response<UserDto> {
        throw UnsupportedOperationException("activateUser is not used in these tests")
    }

    override suspend fun deleteUserPermanently(
        userId: String,
        authorization: String?
    ): Response<SuccessResponseDto> {
        throw UnsupportedOperationException("deleteUserPermanently is not used in these tests")
    }
}

fun mockUserSession(user: User? = sampleUser()): KullaniciOturumu {
    var currentUser: User? = user
    return mockk(relaxed = true) {
        every { getUser() } answers { currentUser }
        every { setUser(any()) } answers {
            currentUser = firstArg()
        }
        every { setTokens(any(), any(), any()) } just Runs
        every { clearSession() } answers {
            currentUser = null
        }
    }
}

fun mockSessionManager(
    pendingLoginMessage: String? = null
): SessionManager {
    return mockk(relaxed = true) {
        every { consumePendingLoginMessage() } returns pendingLoginMessage
    }
}

fun mockFcmTokenRegistrar(): FcmTokenRegistrar = mockk(relaxed = true)

fun mockSyncManager(
    eventsFlow: MutableSharedFlow<AppMessage> = MutableSharedFlow(extraBufferCapacity = 8)
): SyncManager {
    return mockk(relaxed = true) {
        every { events } returns eventsFlow
        every { requestSync(any()) } just Runs
        coEvery { performSync(any()) } returns true
    }
}

suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(
    predicate: (T) -> Boolean
): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

fun moshiForTests(): Moshi = Moshi.Builder().build()

fun connectException(): ConnectException = ConnectException("Sunucuya bağlanılamıyor")
