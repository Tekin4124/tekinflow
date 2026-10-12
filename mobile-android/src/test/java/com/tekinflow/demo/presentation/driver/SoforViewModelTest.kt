package com.tekinflow.demo.presentation.driver

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.usecase.DurumGuncelleUseCase
import com.tekinflow.demo.domain.usecase.TalepleriGetirUseCase
import com.tekinflow.demo.presentation.components.AppMessageType
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.MainDispatcherRule
import com.tekinflow.demo.testutil.awaitItemWhere
import com.tekinflow.demo.testutil.mockSyncManager
import com.tekinflow.demo.testutil.mockUserSession
import com.tekinflow.demo.testutil.sampleRequest
import com.tekinflow.demo.testutil.sampleUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SoforViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load requests filters my requests`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER, licensePlate = "34 ABC 123")
        val myRequest = sampleRequest(id = "mine", assignedDriverId = currentDriver.id)
        val otherRequest = sampleRequest(id = "other", assignedDriverId = "driver-2")
        val viewModel = createViewModel(
            repository = FakeTalepDeposu(initialRequests = listOf(myRequest, otherRequest)),
            currentDriver = currentDriver
        )

        viewModel.uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertThat(state.myRequests.map { it.id }).containsExactly("mine")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load requests filters unassigned requests excluding cancelled and delivered`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER)
        val openRequest = sampleRequest(id = "open", assignedDriverId = null, materialStatus = MaterialStatus.PENDING)
        val cancelled = sampleRequest(id = "cancelled", assignedDriverId = null, materialStatus = MaterialStatus.CANCELLED)
        val delivered = sampleRequest(id = "delivered", assignedDriverId = null, materialStatus = MaterialStatus.DELIVERED)
        val assigned = sampleRequest(id = "assigned", assignedDriverId = "driver-2")
        val viewModel = createViewModel(
            repository = FakeTalepDeposu(initialRequests = listOf(openRequest, cancelled, delivered, assigned)),
            currentDriver = currentDriver
        )

        viewModel.uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertThat(state.unassignedRequests.map { it.id }).containsExactly("open")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load request marks request as seen`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER)
        val request = sampleRequest(
            id = "request-1",
            assignedDriverId = currentDriver.id,
            materialStatus = MaterialStatus.IN_PROGRESS
        )
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        viewModel.formState.test {
            awaitItem()
            viewModel.loadRequest(request.id)
            advanceUntilIdle()

            val state = awaitItemWhere { it.request?.id == request.id }
            assertThat(state.request?.id).isEqualTo(request.id)
            assertThat(repository.markAsSeenCalls).containsExactly(request.id to currentDriver.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update status with unauthorized driver shows error`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER)
        val request = sampleRequest(id = "request-1", assignedDriverId = "driver-2")
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        advanceUntilIdle()
        viewModel.loadRequest(request.id)
        advanceUntilIdle()
        viewModel.onStatusSelected(MaterialStatus.ON_THE_WAY)

        viewModel.events.test {
            viewModel.updateStatus()
            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.ERROR)
            assertThat(repository.updatedStatuses).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update status with assigned driver calls repository`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER)
        val request = sampleRequest(
            id = "request-1",
            assignedDriverId = currentDriver.id,
            materialStatus = MaterialStatus.IN_PROGRESS
        )
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        advanceUntilIdle()
        viewModel.loadRequest(request.id)
        advanceUntilIdle()
        viewModel.onStatusSelected(MaterialStatus.ON_THE_WAY)

        viewModel.events.test {
            viewModel.updateStatus()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            assertThat(repository.updatedStatuses).containsExactly(request.id to MaterialStatus.ON_THE_WAY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `take over request calls repository`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER, licensePlate = "34 ABC 123")
        val request = sampleRequest(id = "request-1", assignedDriverId = null)
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        viewModel.events.test {
            viewModel.takeOverRequest(request.id)
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            assertThat(repository.takeOverCalls).containsExactly(
                Triple(request.id, currentDriver.id, "34 ABC 123")
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `take over spot assigned request is blocked before repository call`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER, licensePlate = "34 ABC 123")
        val request = sampleRequest(
            id = "request-spot",
            assignedDriverId = null,
            spotDriverCompanyName = "Spot Lojistik"
        )
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        viewModel.events.test {
            viewModel.takeOverRequest(request.id)
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.ERROR)
            assertThat(event.message).isEqualTo("Bu talep spot araca atanmış")
            assertThat(repository.takeOverCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit objection calls repository`() = runTest {
        val currentDriver = sampleUser(id = "driver-1", role = UserRole.DRIVER)
        val request = sampleRequest(id = "request-1", assignedDriverId = currentDriver.id)
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentDriver = currentDriver)

        advanceUntilIdle()
        viewModel.loadRequest(request.id)
        advanceUntilIdle()

        viewModel.formState.test {
            awaitItem()
            viewModel.submitObjection("Kapı kapalı")
            advanceUntilIdle()

            val state = awaitItemWhere { !it.isSaving && it.request?.hasObjection == true }
            assertThat(repository.objectionCalls).containsExactly(
                Triple(request.id, currentDriver.id, "Kapı kapalı")
            )
            assertThat(state.request?.objectionReason).isEqualTo("Kapı kapalı")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh toggles state`() = runTest {
        val viewModel = createViewModel(syncManager = mockSyncManager(MutableSharedFlow(extraBufferCapacity = 4)))

        viewModel.uiState.test {
            awaitItemWhere { !it.isLoading }
            viewModel.refresh()

            val refreshing = awaitItemWhere { it.isRefreshing }
            assertThat(refreshing.isRefreshing).isTrue()

            advanceTimeBy(250)
            advanceUntilIdle()

            val done = awaitItemWhere { !it.isRefreshing }
            assertThat(done.isRefreshing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(
        repository: FakeTalepDeposu = FakeTalepDeposu(),
        currentDriver: com.tekinflow.demo.domain.model.User = sampleUser(
            id = "driver-1",
            role = UserRole.DRIVER,
            licensePlate = "34 ABC 123"
        ),
        syncManager: com.tekinflow.demo.data.sync.SyncManager = mockSyncManager()
    ): SoforViewModel {
        return SoforViewModel(
            getPurchaseRequestsUseCase = TalepleriGetirUseCase(repository),
            updateStatusUseCase = DurumGuncelleUseCase(repository),
            repository = repository,
            userSession = mockUserSession(currentDriver),
            syncManager = syncManager
        )
    }
}
