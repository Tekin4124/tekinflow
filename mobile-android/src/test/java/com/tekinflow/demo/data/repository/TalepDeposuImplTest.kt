package com.tekinflow.demo.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.data.local.entity.PendingSyncActions
import com.tekinflow.demo.data.local.mapper.toDomain
import com.tekinflow.demo.data.local.mapper.toEntity
import com.tekinflow.demo.data.remote.api.RemoteDataException
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.testutil.FakeNotificationApi
import com.tekinflow.demo.testutil.FakeNotificationDao
import com.tekinflow.demo.testutil.FakePendingSyncDao
import com.tekinflow.demo.testutil.FakePurchaseRequestDao
import com.tekinflow.demo.testutil.FakeRequestApi
import com.tekinflow.demo.testutil.FakeUserApi
import com.tekinflow.demo.testutil.FakeUserDao
import com.tekinflow.demo.testutil.connectException
import com.tekinflow.demo.testutil.errorHttpResponse
import com.tekinflow.demo.testutil.mockSyncManager
import com.tekinflow.demo.testutil.mockUserSession
import com.tekinflow.demo.testutil.moshiForTests
import com.tekinflow.demo.testutil.sampleNotification
import com.tekinflow.demo.testutil.sampleRequest
import com.tekinflow.demo.testutil.sampleRequestDto
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Calendar

class TalepDeposuImplTest {

    @Test
    fun `getAllRequests emits cached requests and triggers sync`() = runTest {
        val request = sampleRequest()
        val syncManager = mockSyncManager()
        val repository = createRepository(
            requestDao = FakePurchaseRequestDao(listOf(request.toEntity())),
            syncManager = syncManager
        )

        repository.getAllRequests().test {
            val items = awaitItem()
            assertThat(items).containsExactly(request)
            verify { syncManager.requestSync(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRequestById returns local request and triggers sync`() = runTest {
        val request = sampleRequest()
        val syncManager = mockSyncManager()
        val repository = createRepository(
            requestDao = FakePurchaseRequestDao(listOf(request.toEntity())),
            syncManager = syncManager
        )

        val result = repository.getRequestById(request.id)

        assertThat(result).isEqualTo(request)
        verify { syncManager.requestSync(any()) }
    }

    @Test
    fun `getRequestById fetches remote and caches when local missing`() = runTest {
        val request = sampleRequest()
        val requestDao = FakePurchaseRequestDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                getRequestByIdHandler = { retrofit2.Response.success(sampleRequestDto(id = request.id)) }
            },
            requestDao = requestDao
        )

        val result = repository.getRequestById(request.id)

        assertThat(result?.id).isEqualTo(request.id)
        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.id).isEqualTo(request.id)
    }

    @Test
    fun `createRequest caches server result and forces sync`() = runTest {
        val syncManager = mockSyncManager()
        val requestDao = FakePurchaseRequestDao()
        val request = sampleRequest(id = "create-1")
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                createRequestHandler = { retrofit2.Response.success(sampleRequestDto(id = "create-1")) }
            },
            requestDao = requestDao,
            syncManager = syncManager
        )

        repository.createRequest(request)

        assertThat(requestDao.getRequestById("create-1")?.toDomain()?.id).isEqualTo("create-1")
        verify { syncManager.requestSync(force = true) }
    }

    @Test
    fun `updateRequest caches server result and forces sync`() = runTest {
        val syncManager = mockSyncManager()
        val request = sampleRequest(id = "update-1")
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                updateRequestHandler = { requestId, _ ->
                    retrofit2.Response.success(sampleRequestDto(id = requestId, companyName = "Güncel Firma"))
                }
            },
            requestDao = requestDao,
            syncManager = syncManager
        )

        repository.updateRequest(request.copy(companyName = "Güncel Firma"))

        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.companyName).isEqualTo("Güncel Firma")
        verify { syncManager.requestSync(force = true) }
    }

    @Test
    fun `updateStatus success updates cache and requests full sync`() = runTest {
        val request = sampleRequest(id = "status-1", materialStatus = MaterialStatus.PENDING)
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val syncManager = mockSyncManager()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                updateStatusHandler = { requestId, body ->
                    retrofit2.Response.success(sampleRequestDto(id = requestId, materialStatus = body.status))
                }
            },
            requestDao = requestDao,
            syncManager = syncManager
        )

        repository.updateStatus(request.id, MaterialStatus.IN_PROGRESS)

        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.materialStatus)
            .isEqualTo(MaterialStatus.IN_PROGRESS)
        verify { syncManager.requestSync(force = true) }
    }

    @Test
    fun `updateStatus network error keeps optimistic state and enqueues pending sync`() = runTest {
        val request = sampleRequest(id = "status-2", materialStatus = MaterialStatus.PENDING)
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val pendingSyncDao = FakePendingSyncDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                updateStatusHandler = { _, _ -> throw connectException() }
            },
            requestDao = requestDao,
            pendingSyncDao = pendingSyncDao
        )

        repository.updateStatus(request.id, MaterialStatus.ON_THE_WAY)

        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.materialStatus)
            .isEqualTo(MaterialStatus.ON_THE_WAY)
        val pending = pendingSyncDao.getPendingSyncs().single()
        assertThat(pending.action).isEqualTo(PendingSyncActions.UPDATE_STATUS)
        assertThat(pending.requestId).isEqualTo(request.id)
    }

    @Test
    fun `updateStatus http error refreshes snapshot and throws`() = runTest {
        val request = sampleRequest(id = "status-3", materialStatus = MaterialStatus.PENDING)
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                updateStatusHandler = { _, _ -> errorHttpResponse(409, "Çakışma") }
                getRequestByIdHandler = { requestId ->
                    retrofit2.Response.success(sampleRequestDto(id = requestId, materialStatus = MaterialStatus.PENDING))
                }
            },
            requestDao = requestDao
        )

        val error = runCatching {
            repository.updateStatus(request.id, MaterialStatus.DELIVERED)
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(RemoteDataException::class.java)
        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.materialStatus)
            .isEqualTo(MaterialStatus.PENDING)
    }

    @Test
    fun `takeOverRequest network error returns optimistic request and enqueues sync`() = runTest {
        val request = sampleRequest(id = "takeover-1", assignedDriverId = null, materialStatus = MaterialStatus.PENDING)
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val pendingSyncDao = FakePendingSyncDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                takeOverHandler = { throw connectException() }
            },
            requestDao = requestDao,
            pendingSyncDao = pendingSyncDao
        )

        val result = repository.takeOverRequest(request.id, "driver-1", "34 ABC 123")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.assignedDriverId).isEqualTo("driver-1")
        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.materialStatus)
            .isEqualTo(MaterialStatus.IN_PROGRESS)
        assertThat(pendingSyncDao.getPendingSyncs().single().action).isEqualTo(PendingSyncActions.TAKE_OVER)
    }

    @Test
    fun `submitObjection network error keeps optimistic objection and queues sync`() = runTest {
        val request = sampleRequest(id = "object-1")
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val pendingSyncDao = FakePendingSyncDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                submitObjectionHandler = { _, _ -> throw connectException() }
            },
            requestDao = requestDao,
            pendingSyncDao = pendingSyncDao
        )

        repository.submitObjection(request.id, "driver-1", "Kapı kapalı")

        val local = requestDao.getRequestById(request.id)?.toDomain()
        assertThat(local?.hasObjection).isTrue()
        assertThat(local?.objectionReason).isEqualTo("Kapı kapalı")
        assertThat(pendingSyncDao.getPendingSyncs().single().action).isEqualTo(PendingSyncActions.OBJECT)
    }

    @Test
    fun `markAsSeen updates local request and queues on network error`() = runTest {
        val request = sampleRequest(id = "seen-1", isNew = true, seenByUserIds = emptySet())
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val pendingSyncDao = FakePendingSyncDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                markSeenHandler = { throw connectException() }
            },
            requestDao = requestDao,
            pendingSyncDao = pendingSyncDao
        )

        repository.markAsSeen(request.id, "driver-1")

        val local = requestDao.getRequestById(request.id)?.toDomain()
        assertThat(local?.isNew).isFalse()
        assertThat(local?.seenByUserIds).contains("driver-1")
        assertThat(pendingSyncDao.getPendingSyncs().single().action).isEqualTo(PendingSyncActions.MARK_SEEN)
    }

    @Test
    fun `markAsSeen ignores client errors without queueing`() = runTest {
        val request = sampleRequest(id = "seen-2", isNew = true)
        val requestDao = FakePurchaseRequestDao(listOf(request.toEntity()))
        val pendingSyncDao = FakePendingSyncDao()
        val repository = createRepository(
            requestApi = FakeRequestApi().apply {
                markSeenHandler = { errorHttpResponse(400, "Geçersiz") }
            },
            requestDao = requestDao,
            pendingSyncDao = pendingSyncDao
        )

        repository.markAsSeen(request.id, "driver-1")

        assertThat(pendingSyncDao.getPendingSyncs()).isEmpty()
        assertThat(requestDao.getRequestById(request.id)?.toDomain()?.isNew).isFalse()
    }

    @Test
    fun `markNotificationRead updates local data even when network fails`() = runTest {
        val notification = sampleNotification(isRead = false)
        val notificationDao = FakeNotificationDao(listOf(notification.toEntity()))
        val repository = createRepository(
            notificationApi = FakeNotificationApi().apply {
                markAsReadHandler = { throw connectException() }
            },
            notificationDao = notificationDao
        )

        repository.markNotificationRead(notification.id)

        val stored = notificationDao.observeNotifications(notification.targetUserId)
        stored.test {
            assertThat(awaitItem().single().isRead).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `generateRequestNumber returns next sequential value for current year`() = runTest {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val requestDao = FakePurchaseRequestDao(
            listOf(
                sampleRequest(requestNumber = "TLP-$year-001").toEntity(),
                sampleRequest(id = "r2", requestNumber = "TLP-$year-010").toEntity(),
                sampleRequest(id = "r3", requestNumber = "TLP-2024-999").toEntity()
            )
        )
        val repository = createRepository(requestDao = requestDao)

        val nextNumber = repository.generateRequestNumber()

        assertThat(nextNumber).isEqualTo("TLP-$year-000011")
    }

    private fun createRepository(
        requestApi: FakeRequestApi = FakeRequestApi(),
        notificationApi: FakeNotificationApi = FakeNotificationApi(),
        userApi: FakeUserApi = FakeUserApi(),
        requestDao: FakePurchaseRequestDao = FakePurchaseRequestDao(),
        notificationDao: FakeNotificationDao = FakeNotificationDao(),
        userDao: FakeUserDao = FakeUserDao(),
        pendingSyncDao: FakePendingSyncDao = FakePendingSyncDao(),
        syncManager: com.tekinflow.demo.data.sync.SyncManager = mockSyncManager()
    ): TalepDeposuImpl {
        return TalepDeposuImpl(
            requestApi = requestApi,
            notificationApi = notificationApi,
            userApi = userApi,
            requestDao = requestDao,
            notificationDao = notificationDao,
            userDao = userDao,
            pendingSyncDao = pendingSyncDao,
            syncManager = syncManager,
            userSession = mockUserSession(),
            moshi = moshiForTests()
        )
    }
}
