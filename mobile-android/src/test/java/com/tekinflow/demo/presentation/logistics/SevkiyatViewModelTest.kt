package com.tekinflow.demo.presentation.logistics

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.usecase.TalepleriGetirUseCase
import com.tekinflow.demo.presentation.components.AppMessage
import com.tekinflow.demo.presentation.components.AppMessageType
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.MainDispatcherRule
import com.tekinflow.demo.testutil.awaitItemWhere
import com.tekinflow.demo.testutil.mockSyncManager
import com.tekinflow.demo.testutil.mockUserSession
import com.tekinflow.demo.testutil.sampleRequest
import com.tekinflow.demo.testutil.sampleUser
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SevkiyatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ui state loads requests from repository`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val requests = listOf(sampleRequest(id = "r1", updatedAt = 1L), sampleRequest(id = "r2", updatedAt = 5L))
        val viewModel = createViewModel(repository = FakeTalepDeposu(initialRequests = requests))

        viewModel.uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertThat(state.requests.map { it.id }).containsExactly("r2", "r1").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `assign spot driver without company shows error`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val request = sampleRequest(id = "request-1")
        val viewModel = createViewModel(repository = FakeTalepDeposu(initialRequests = listOf(request)))
        viewModel.loadRequest(request.id)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.assignDriver()

            assertThat(awaitItem()).isEqualTo(AppMessage("Spot araç için firma adı en az 2 karakter olmalı", AppMessageType.ERROR))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `on driver selected updates form license plate`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val driver = sampleUser(id = "driver-1", fullName = "Ahmet YÄ±lmaz", role = UserRole.DRIVER, licensePlate = "34 ABC 123")
        val viewModel = createViewModel(repository = FakeTalepDeposu(initialDrivers = listOf(driver)))

        viewModel.formState.test {
            awaitItem()
            viewModel.onDriverSelected(driver)
            val state = awaitItemWhere { it.selectedDriver != null }

            assertThat(state.selectedDriver).isEqualTo(driver)
            assertThat(state.licensePlate).isEqualTo("34 ABC 123")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load request selects currently assigned driver`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val driver = sampleUser(id = "driver-2", fullName = "Ali Åahin", role = UserRole.DRIVER, licensePlate = "06 XYZ 123")
        val request = sampleRequest(id = "request-2", assignedDriverId = driver.id, licensePlate = driver.licensePlate.orEmpty())
        val viewModel = createViewModel(
            repository = FakeTalepDeposu(
                initialRequests = listOf(request),
                initialDrivers = listOf(driver)
            )
        )

        viewModel.formState.test {
            awaitItem()
            viewModel.loadRequest(request.id)
            advanceUntilIdle()

            val state = awaitItemWhere { it.request?.id == request.id }
            assertThat(state.selectedDriver?.id).isEqualTo(driver.id)
            assertThat(state.licensePlate).isEqualTo("06 XYZ 123")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `assign driver with valid data calls repository`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val driver = sampleUser(id = "driver-3", role = UserRole.DRIVER, licensePlate = "35 DEF 456")
        val request = sampleRequest(id = "request-3")
        val repository = FakeTalepDeposu(
            initialRequests = listOf(request),
            initialDrivers = listOf(driver)
        )
        val viewModel = createViewModel(repository)
        viewModel.loadRequest(request.id)
        advanceUntilIdle()
        viewModel.onDriverSelected(driver)

        viewModel.events.test {
            viewModel.assignDriver()
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppMessage("Şoför ataması kaydedildi", AppMessageType.SUCCESS))
            assertThat(repository.assignDriverCalls.single())
                .isEqualTo(Triple("request-3", "driver-3", "35 DEF 456"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unassign driver calls repository`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val request = sampleRequest(id = "request-4", assignedDriverId = "driver-4", licensePlate = "34 AAA 001")
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository)
        viewModel.loadRequest(request.id)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.unassignDriver()
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppMessage("Atama kaldırıldı", AppMessageType.SUCCESS))
            assertThat(repository.unassignDriverCalls).containsExactly("request-4")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `objected delivered request cannot be reopened from logistics`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val request = sampleRequest(
            id = "request-5",
            materialStatus = com.tekinflow.demo.domain.model.MaterialStatus.DELIVERED,
            hasObjection = true
        )
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(
            repository = repository,
            user = sampleUser(id = "logistics-admin-1", role = UserRole.LOGISTICS_ADMIN)
        )
        viewModel.loadRequest(request.id)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.markInProgress()
            advanceUntilIdle()

            assertThat(awaitItem()).isEqualTo(AppMessage("Bu talep devama alınamaz", AppMessageType.ERROR))
            assertThat(repository.updatedStatuses).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh triggers sync manager`() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val syncManager = mockSyncManager()
        val viewModel = createViewModel(syncManager = syncManager)

        viewModel.uiState.test {
            awaitItemWhere { !it.isLoading }
            viewModel.refresh()
            advanceUntilIdle()

            coVerify { syncManager.performSync(true) }
            assertThat(viewModel.uiState.value.isRefreshing).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createViewModel(
        repository: FakeTalepDeposu = FakeTalepDeposu(),
        syncManager: SyncManager = mockSyncManager(),
        user: com.tekinflow.demo.domain.model.User = sampleUser(id = "logistics-1", role = UserRole.LOGISTICS_OFFICE)
    ): SevkiyatViewModel {
        val userSession = mockUserSession(user)
        return SevkiyatViewModel(
            getPurchaseRequestsUseCase = TalepleriGetirUseCase(repository),
            repository = repository,
            userSession = userSession,
            syncManager = syncManager
        )
    }
}
