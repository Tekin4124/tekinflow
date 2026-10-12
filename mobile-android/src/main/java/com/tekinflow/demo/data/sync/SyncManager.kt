package com.tekinflow.demo.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import com.squareup.moshi.Moshi
import com.tekinflow.demo.R
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
import com.tekinflow.demo.data.local.mapper.toEntity
import com.tekinflow.demo.data.notification.SystemNotificationNotifier
import com.tekinflow.demo.data.remote.api.ApiCallResult
import com.tekinflow.demo.data.remote.api.NotificationApi
import com.tekinflow.demo.data.remote.api.RequestApi
import com.tekinflow.demo.data.remote.api.UserApi
import com.tekinflow.demo.data.remote.api.executeApiResult
import com.tekinflow.demo.data.remote.dto.ObjectionRequestDto
import com.tekinflow.demo.data.remote.dto.UpdateStatusRequestDto
import com.tekinflow.demo.data.remote.mapper.toDomain
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.components.AppMessage
import com.tekinflow.demo.presentation.components.AppMessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val requestApi: RequestApi,
    private val notificationApi: NotificationApi,
    private val userApi: UserApi,
    private val requestDao: PurchaseRequestDao,
    private val notificationDao: NotificationDao,
    private val userDao: UserDao,
    private val pendingSyncDao: PendingSyncDao,
    private val userSession: KullaniciOturumu,
    moshi: Moshi,
    private val syncStateStore: SyncStateStore,
    private val systemNotificationNotifier: SystemNotificationNotifier
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val updateStatusAdapter = moshi.adapter(UpdateStatusPayload::class.java)
    private val takeOverAdapter = moshi.adapter(TakeOverPayload::class.java)
    private val objectionAdapter = moshi.adapter(ObjectionPayload::class.java)
    private val markSeenAdapter = moshi.adapter(MarkSeenPayload::class.java)

    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    @Volatile
    var isAppForeground = false

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        if (!com.tekinflow.demo.BuildConfig.DEMO_MODE) {
            registerConnectivityCallback()
            schedulePeriodicSync()
        }
        observeSession()
        requestSync(force = true)
    }

    fun requestSync(force: Boolean = false) {
        scope.launch {
            performSync(force = force)
        }
    }

    suspend fun performSync(force: Boolean = false): Boolean = syncMutex.withLock {
        val currentUser = userSession.getUser()
        val accessToken = userSession.getAccessToken()
        if (currentUser == null || accessToken.isNullOrBlank()) {
            syncStateStore.setSyncing(false)
            return false
        }

        syncStateStore.setSyncing(true)
        val connected = isConnected()
        syncStateStore.setOffline(!connected)
        if (!connected) {
            syncStateStore.setSyncing(false)
            return false
        }

        var hasChanges = false

        hasChanges = processPendingQueue() || hasChanges

        when (
            val requestsResult = executeApiResult(defaultMessage = "Talepler alınamadı") {
                requestApi.getRequests(authorization = authHeader())
            }
        ) {
            is ApiCallResult.Success -> {
                val pending = pendingSyncDao.getPendingSyncs().filter { it.retryCount < MAX_RETRY_COUNT }
                val requests = applyPendingOverlays(
                    requestsResult.body.map { it.toDomain() },
                    pending
                )
                requestDao.replaceAll(requests.map { it.toEntity() })
                syncStateStore.updateLastSync(System.currentTimeMillis())
                hasChanges = true
            }

            is ApiCallResult.HttpError -> Unit
            is ApiCallResult.NetworkError -> {
                syncStateStore.setOffline(true)
                syncStateStore.setSyncing(false)
                return hasChanges
            }
        }

        when (
            val notificationResult = executeApiResult(defaultMessage = "Bildirimler alınamadı") {
                notificationApi.getNotifications(authHeader())
            }
        ) {
            is ApiCallResult.Success -> {
                val existingIds = notificationDao.getNotificationIds().toSet()
                val notifications = notificationResult.body.map { it.toDomain() }
                val baselineReady = syncStateStore.isNotificationBaselineReady(currentUser.id) ||
                    existingIds.isNotEmpty()
                val newUnreadNotifications = if (baselineReady) {
                    notifications.filter { notification ->
                        !notification.isRead && notification.id !in existingIds
                    }
                } else {
                    emptyList()
                }
                notificationDao.replaceAll(notifications.map { it.toEntity() })
                if (!baselineReady) {
                    syncStateStore.markNotificationBaselineReady(currentUser.id)
                }
                newUnreadNotifications.forEach(systemNotificationNotifier::show)
            }

            is ApiCallResult.HttpError -> Unit
            is ApiCallResult.NetworkError -> syncStateStore.setOffline(true)
        }

        if (
            currentUser.role == UserRole.LOGISTICS_OFFICE ||
            currentUser.role == UserRole.LOGISTICS_ADMIN ||
            currentUser.role == UserRole.ADMIN
        ) {
            when (
                val driversResult = executeApiResult(defaultMessage = "Şoför listesi alınamadı") {
                    userApi.getDrivers(authHeader())
                }
            ) {
                is ApiCallResult.Success -> {
                    userDao.replaceAll(driversResult.body.map { it.toDomain().toEntity() })
                }

                is ApiCallResult.HttpError -> Unit
                is ApiCallResult.NetworkError -> syncStateStore.setOffline(true)
            }
        } else {
            userDao.replaceAll(emptyList())
        }

        syncStateStore.setSyncing(false)
        if (force && isConnected()) {
            syncStateStore.setOffline(false)
        }
        hasChanges
    }

    private fun observeSession() {
        scope.launch {
            userSession.currentUser.collectLatest { user ->
                if (user == null || userSession.getAccessToken().isNullOrBlank()) {
                    syncStateStore.reset()
                } else {
                    delay(300)
                    requestSync(force = true)
                }
            }
        }
    }

    private fun registerConnectivityCallback() {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    syncStateStore.setOffline(false)
                    requestSync(force = true)
                }

                override fun onLost(network: Network) {
                    syncStateStore.setOffline(!isConnected())
                }
            }
        )
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        scheduleBackgroundFetch(context)
    }

    private suspend fun processPendingQueue(): Boolean {
        val pendingItems = pendingSyncDao.getPendingSyncs()
            .sortedBy { it.createdAt }
            .filter { it.retryCount < MAX_RETRY_COUNT }
        syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)

        var changed = false
        for (item in pendingItems) {
            when (item.action) {
                PendingSyncActions.UPDATE_STATUS -> {
                    val payload = updateStatusAdapter.fromJson(item.payload) ?: continue
                    when (
                        val result = executeApiResult(defaultMessage = "Durum güncellenemedi") {
                            requestApi.updateStatus(
                                requestId = item.requestId,
                                request = UpdateStatusRequestDto(payload.status, payload.deliveredTo),
                                authorization = authHeader(),
                                idempotencyKey = item.idempotencyKey
                            )
                        }
                    ) {
                        is ApiCallResult.Success -> {
                            requestDao.upsert(result.body.toDomain().toEntity())
                            pendingSyncDao.deleteById(item.id)
                            syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                            changed = true
                        }

                        is ApiCallResult.HttpError -> {
                            if (handleHttpError(item, result.code, result.message)) {
                                changed = true
                            } else {
                                return changed
                            }
                        }

                        is ApiCallResult.NetworkError -> {
                            handleRetryableFailure(item, result.message)
                            syncStateStore.setOffline(true)
                            return changed
                        }
                    }
                }

                PendingSyncActions.TAKE_OVER -> {
                    when (
                        val result = executeApiResult(defaultMessage = "Talep üzerinize alınamadı") {
                            requestApi.takeOverRequest(
                                requestId = item.requestId,
                                authorization = authHeader(),
                                idempotencyKey = item.idempotencyKey
                            )
                        }
                    ) {
                        is ApiCallResult.Success -> {
                            requestDao.upsert(result.body.toDomain().toEntity())
                            pendingSyncDao.deleteById(item.id)
                            syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                            changed = true
                        }

                        is ApiCallResult.HttpError -> {
                            if (handleHttpError(item, result.code, result.message)) {
                                changed = true
                            } else {
                                return changed
                            }
                        }

                        is ApiCallResult.NetworkError -> {
                            handleRetryableFailure(item, result.message)
                            syncStateStore.setOffline(true)
                            return changed
                        }
                    }
                }

                PendingSyncActions.OBJECT -> {
                    val payload = objectionAdapter.fromJson(item.payload) ?: continue
                    when (
                        val result = executeApiResult(defaultMessage = "İtiraz iletilemedi") {
                            requestApi.submitObjection(
                                requestId = item.requestId,
                                request = ObjectionRequestDto(payload.reason),
                                authorization = authHeader(),
                                idempotencyKey = item.idempotencyKey
                            )
                        }
                    ) {
                        is ApiCallResult.Success -> {
                            requestDao.upsert(result.body.toDomain().toEntity())
                            pendingSyncDao.deleteById(item.id)
                            syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                            changed = true
                        }

                        is ApiCallResult.HttpError -> {
                            if (handleHttpError(item, result.code, result.message)) {
                                changed = true
                            } else {
                                return changed
                            }
                        }

                        is ApiCallResult.NetworkError -> {
                            handleRetryableFailure(item, result.message)
                            syncStateStore.setOffline(true)
                            return changed
                        }
                    }
                }

                PendingSyncActions.MARK_SEEN -> {
                    when (
                        val result = executeApiResult(defaultMessage = "Talep okundu olarak işaretlenemedi") {
                            requestApi.markSeen(
                                requestId = item.requestId,
                                authorization = authHeader(),
                                idempotencyKey = item.idempotencyKey
                            )
                        }
                    ) {
                        is ApiCallResult.Success -> {
                            pendingSyncDao.deleteById(item.id)
                            syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                            changed = true
                        }

                        is ApiCallResult.HttpError -> {
                            if (result.code in 400..499) {
                                pendingSyncDao.deleteById(item.id)
                            } else {
                                handleRetryableFailure(item, result.message)
                                return changed
                            }
                        }

                        is ApiCallResult.NetworkError -> {
                            handleRetryableFailure(item, result.message)
                            syncStateStore.setOffline(true)
                            return changed
                        }
                    }
                }
            }
        }

        return changed
    }

    private suspend fun handleHttpError(
        item: PendingSyncEntity,
        code: Int,
        message: String
    ): Boolean {
        return when {
            code == 409 -> {
                pendingSyncDao.deleteById(item.id)
                syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                val refreshedRequest = refreshSingleRequest(item.requestId)
                _events.emit(
                    AppMessage(
                        conflictMessage(item, refreshedRequest),
                        AppMessageType.ERROR
                    )
                )
                true
            }

            code in 400..499 -> {
                pendingSyncDao.markFailed(
                    id = item.id,
                    retryCount = MAX_RETRY_COUNT,
                    lastError = message
                )
                syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
                refreshSingleRequest(item.requestId)
                _events.emit(AppMessage(message, AppMessageType.ERROR))
                true
            }

            else -> {
                handleRetryableFailure(item, message)
                false
            }
        }
    }

    private suspend fun handleRetryableFailure(item: PendingSyncEntity, message: String) {
        val nextRetryCount = item.retryCount + 1
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            pendingSyncDao.markFailed(
                id = item.id,
                retryCount = MAX_RETRY_COUNT,
                lastError = message
            )
            syncStateStore.setFailedSyncCount(pendingSyncDao.getFailedSyncs().size)
            refreshSingleRequest(item.requestId)
            _events.emit(
                AppMessage(
                    context.getString(R.string.sync_failed_message),
                    AppMessageType.ERROR
                )
            )
            return
        }

        pendingSyncDao.updateRetry(
            id = item.id,
            retryCount = nextRetryCount,
            lastError = message
        )

        scope.launch {
            kotlinx.coroutines.delay(BASE_RETRY_DELAY_MS * (1L shl (nextRetryCount - 1)))
            performSync(force = true)
        }
    }

    private suspend fun refreshSingleRequest(requestId: String): PurchaseRequest? {
        return when (
            val result = executeApiResult(defaultMessage = "Talep bilgisi alınamadı") {
                requestApi.getRequestById(requestId, authHeader())
            }
        ) {
            is ApiCallResult.Success -> {
                val request = result.body.toDomain()
                requestDao.upsert(request.toEntity())
                request
            }

            is ApiCallResult.HttpError -> null
            is ApiCallResult.NetworkError -> null
        }
    }

    private fun conflictMessage(
        item: PendingSyncEntity,
        refreshedRequest: PurchaseRequest?
    ): String {
        if (item.action != PendingSyncActions.UPDATE_STATUS || refreshedRequest == null) {
            return context.getString(R.string.sync_conflict_message)
        }

        return "Bu talep sunucuda değişmiş. Yeni durum: ${refreshedRequest.materialStatus.displayName()}. Eski işleminiz uygulanmadı."
    }

    private fun applyPendingOverlays(
        requests: List<PurchaseRequest>,
        pendingItems: List<PendingSyncEntity>
    ): List<PurchaseRequest> {
        if (pendingItems.isEmpty()) return requests
        val requestMap = requests.associateBy { it.id }.toMutableMap()

        pendingItems
            .sortedBy { it.createdAt }
            .forEach { item ->
                val current = requestMap[item.requestId] ?: return@forEach
                val updated = when (item.action) {
                    PendingSyncActions.UPDATE_STATUS -> {
                        val payload = updateStatusAdapter.fromJson(item.payload) ?: return@forEach
                        current.copy(
                            materialStatus = payload.status,
                            deliveredTo = payload.deliveredTo ?: current.deliveredTo,
                            updatedAt = maxOf(current.updatedAt, item.createdAt)
                        )
                    }

                    PendingSyncActions.TAKE_OVER -> {
                        val payload = takeOverAdapter.fromJson(item.payload) ?: return@forEach
                        current.copy(
                            assignedDriverId = payload.driverId,
                            licensePlate = payload.licensePlate,
                            materialStatus = com.tekinflow.demo.domain.model.MaterialStatus.IN_PROGRESS,
                            updatedAt = maxOf(current.updatedAt, item.createdAt)
                        )
                    }

                    PendingSyncActions.OBJECT -> {
                        val payload = objectionAdapter.fromJson(item.payload) ?: return@forEach
                        current.copy(
                            hasObjection = true,
                            objectionReason = payload.reason,
                            objectionBy = payload.driverId,
                            updatedAt = maxOf(current.updatedAt, item.createdAt)
                        )
                    }

                    PendingSyncActions.MARK_SEEN -> {
                        val payload = markSeenAdapter.fromJson(item.payload) ?: return@forEach
                        current.copy(
                            isNew = false,
                            seenByUserIds = current.seenByUserIds + payload.userId
                        )
                    }

                    else -> current
                }
                requestMap[item.requestId] = updated
            }

        return requestMap.values.sortedByDescending { it.updatedAt }
    }

    private fun isConnected(): Boolean {
        if (com.tekinflow.demo.BuildConfig.DEMO_MODE) return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun authHeader(): String? = userSession.getAccessToken()
        ?.let { token -> "Bearer $token" }

    private fun com.tekinflow.demo.domain.model.MaterialStatus.displayName(): String {
        return when (this) {
            com.tekinflow.demo.domain.model.MaterialStatus.PENDING -> "Beklemede"
            com.tekinflow.demo.domain.model.MaterialStatus.IN_PROGRESS -> "Devam Ediyor"
            com.tekinflow.demo.domain.model.MaterialStatus.PURCHASED -> "Devam Ediyor"
            com.tekinflow.demo.domain.model.MaterialStatus.ON_THE_WAY -> "Yolda"
            com.tekinflow.demo.domain.model.MaterialStatus.DELIVERED -> "Teslim Edildi"
            com.tekinflow.demo.domain.model.MaterialStatus.CANCELLED -> "İptal Edildi"
        }
    }

    companion object {
        const val SYNC_WORK_NAME = "satinalma_periodic_sync"
        const val BACKGROUND_FETCH_WORK_NAME = "satinalma_background_fetch_5m"
        const val MAX_RETRY_COUNT = 5
        const val BASE_RETRY_DELAY_MS = 1_000L

        fun scheduleBackgroundFetch(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                BACKGROUND_FETCH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
