package com.tekinflow.demo.presentation.purchasing

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.domain.usecase.TalepOlusturUseCase
import com.tekinflow.demo.domain.usecase.TalepleriGetirUseCase
import com.tekinflow.demo.presentation.components.AppMessageType
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.MainDispatcherRule
import com.tekinflow.demo.testutil.awaitItemWhere
import com.tekinflow.demo.testutil.mockSyncManager
import com.tekinflow.demo.testutil.mockUserSession
import com.tekinflow.demo.testutil.sampleNotification
import com.tekinflow.demo.testutil.sampleRequest
import com.tekinflow.demo.testutil.sampleUser
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SatinAlmaViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `requests are loaded and sorted descending`() = runTest {
        val older = sampleRequest(id = "r1", updatedAt = 10L)
        val newer = sampleRequest(id = "r2", updatedAt = 20L)
        val viewModel = createViewModel(
            repository = FakeTalepDeposu(initialRequests = listOf(older, newer))
        )

        viewModel.uiState.test {
            val state = awaitItemWhere { !it.isLoading }
            assertThat(state.requests.map { request -> request.id }).containsExactly("r2", "r1").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load request for new generates request number`() = runTest {
        val repository = FakeTalepDeposu().apply {
            generatedRequestNumber = "TLP-2026-123"
        }
        val viewModel = createViewModel(repository = repository)

        viewModel.formState.test {
            awaitItem()
            viewModel.loadRequestForEdit("new")
            advanceUntilIdle()

            val state = awaitItemWhere { it.requestNumber == "TLP-2026-123" }
            assertThat(repository.generateRequestNumberCallCount).isEqualTo(1)
            assertThat(state.requestNumber).isEqualTo("TLP-2026-123")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank item shows validation error`() = runTest {
        val viewModel = createViewModel()

        viewModel.formState.test {
            awaitItem()
            viewModel.onCompanyNameChange("TekinFlow")
            advanceUntilIdle()
            awaitItemWhere { it.companyName == "TekinFlow" }

            viewModel.saveRequest()
            val state = awaitItemWhere { it.itemError != null }
            assertThat(state.itemError).isNotEmpty()
            assertThat(state.companyError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `blank company shows validation error`() = runTest {
        val viewModel = createViewModel()

        viewModel.formState.test {
            awaitItem()
            viewModel.onItemToPurchaseChange("Çelik Profil")
            advanceUntilIdle()
            awaitItemWhere { it.itemToPurchase == "Çelik Profil" }

            viewModel.saveRequest()
            val state = awaitItemWhere { it.companyError != null }
            assertThat(state.companyError).isNotEmpty()
            assertThat(state.itemError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save request with valid form calls create use case`() = runTest {
        val repository = FakeTalepDeposu().apply {
            generatedRequestNumber = "TLP-2026-555"
        }
        val viewModel = createViewModel(repository = repository)

        viewModel.events.test {
            viewModel.onItemToPurchaseChange("  Çelik Profil  ")
            viewModel.onCompanyNameChange("  Test Firma  ")
            viewModel.onUrgencyLevelChange(UrgencyLevel.HIGH)
            advanceUntilIdle()

            viewModel.saveRequest()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            assertThat(repository.createdRequests).hasSize(1)
            val created = repository.createdRequests.single()
            assertThat(created.requestNumber).isEqualTo("TLP-2026-555")
            assertThat(created.itemToPurchase).isEqualTo("Çelik Profil")
            assertThat(created.companyName).isEqualTo("Test Firma")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save request keeps multiple item rows with units`() = runTest {
        val repository = FakeTalepDeposu().apply {
            generatedRequestNumber = "TLP-2026-556"
        }
        val viewModel = createViewModel(repository = repository)

        viewModel.events.test {
            viewModel.onItemNameChange(0, "Çelik")
            viewModel.onItemQuantityChange(0, "5")
            viewModel.onItemUnitChange(0, MaterialUnit.KG)
            viewModel.addItemRow()
            viewModel.onItemNameChange(1, "Vida")
            viewModel.onItemQuantityChange(1, "200")
            viewModel.onItemUnitChange(1, MaterialUnit.ADET)
            viewModel.addItemRow()
            viewModel.onItemNameChange(2, "Beton")
            viewModel.onItemQuantityChange(2, "1.5")
            viewModel.onItemUnitChange(2, MaterialUnit.M3)
            viewModel.onCompanyNameChange("Test Firma")
            advanceUntilIdle()

            viewModel.saveRequest()
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            val created = repository.createdRequests.single()
            assertThat(created.itemToPurchase).isEqualTo("Çelik")
            assertThat(created.items.map { it.itemName }).containsExactly("Çelik", "Vida", "Beton").inOrder()
            assertThat(created.items.map { it.unit }).containsExactly(MaterialUnit.KG, MaterialUnit.ADET, MaterialUnit.M3).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `save request with existing id calls update request`() = runTest {
        val existing = sampleRequest(id = "existing-1", requestNumber = "TLP-2026-001")
        val repository = FakeTalepDeposu(initialRequests = listOf(existing))
        val viewModel = createViewModel(repository = repository)

        viewModel.events.test {
            viewModel.loadRequestForEdit(existing.id)
            advanceUntilIdle()
            viewModel.onNotesChange("Güncel not")
            advanceUntilIdle()

            viewModel.saveRequest(existing.id)
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            assertThat(repository.updatedRequests).hasSize(1)
            assertThat(repository.updatedRequests.single().notes).isEqualTo("Güncel not")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel request calls repository cancel`() = runTest {
        val currentUser = sampleUser(id = "staff-1")
        val request = sampleRequest(id = "request-1", assignedPurchasingStaffId = currentUser.id)
        val repository = FakeTalepDeposu(initialRequests = listOf(request))
        val viewModel = createViewModel(repository = repository, currentUser = currentUser)

        viewModel.events.test {
            viewModel.cancelRequest(request.id, "Vazgeçildi")
            advanceUntilIdle()

            val event = awaitItem()
            assertThat(event.type).isEqualTo(AppMessageType.SUCCESS)
            assertThat(repository.cancelledRequests).containsExactly(
                Triple(request.id, "Vazgeçildi", currentUser.id)
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh toggles state and calls sync manager`() = runTest {
        val syncManager = mockSyncManager(MutableSharedFlow(extraBufferCapacity = 4))
        val viewModel = createViewModel(syncManager = syncManager)

        viewModel.uiState.test {
            awaitItemWhere { !it.isLoading }

            viewModel.refresh()
            val refreshing = awaitItemWhere { it.isRefreshing }
            assertThat(refreshing.isRefreshing).isTrue()

            advanceTimeBy(250)
            advanceUntilIdle()

            val done = awaitItemWhere { !it.isRefreshing }
            assertThat(done.isRefreshing).isFalse()
            coVerify { syncManager.performSync(force = true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notifications are exposed and can be marked read`() = runTest {
        val notification = sampleNotification(targetUserId = "staff-1")
        val repository = FakeTalepDeposu(initialNotifications = listOf(notification))
        val viewModel = createViewModel(
            repository = repository,
            currentUser = sampleUser(id = "staff-1")
        )

        advanceUntilIdle()

        assertThat(viewModel.getNotifications()).containsExactly(notification)
        viewModel.markNotificationRead(notification.id)
        advanceUntilIdle()
        assertThat(repository.markNotificationReadCalls).containsExactly(notification.id)
    }

    private fun createViewModel(
        repository: FakeTalepDeposu = FakeTalepDeposu(),
        currentUser: com.tekinflow.demo.domain.model.User = sampleUser(id = "staff-1"),
        syncManager: com.tekinflow.demo.data.sync.SyncManager = mockSyncManager()
    ): SatinAlmaViewModel {
        return SatinAlmaViewModel(
            getPurchaseRequestsUseCase = TalepleriGetirUseCase(repository),
            createPurchaseUseCase = TalepOlusturUseCase(repository),
            repository = repository,
            userSession = mockUserSession(currentUser),
            syncManager = syncManager
        )
    }
}
