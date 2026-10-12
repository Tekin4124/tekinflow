package com.tekinflow.demo.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.sampleRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TalepOlusturUseCaseTest {

    @Test
    fun `blank request number generates a new number before create`() = runTest {
        val repository = FakeTalepDeposu()
        repository.generatedRequestNumber = "TLP-2026-123"
        val useCase = TalepOlusturUseCase(repository)

        useCase(sampleRequest(requestNumber = ""))

        assertThat(repository.generateRequestNumberCallCount).isEqualTo(1)
        assertThat(repository.createdRequests).hasSize(1)
        assertThat(repository.createdRequests.single().requestNumber).isEqualTo("TLP-2026-123")
    }

    @Test
    fun `existing request number is preserved`() = runTest {
        val repository = FakeTalepDeposu()
        val useCase = TalepOlusturUseCase(repository)

        useCase(sampleRequest(requestNumber = "TLP-2026-009"))

        assertThat(repository.generateRequestNumberCallCount).isEqualTo(0)
        assertThat(repository.createdRequests.single().requestNumber).isEqualTo("TLP-2026-009")
    }

    @Test
    fun `create request forwards the original fields`() = runTest {
        val repository = FakeTalepDeposu()
        val useCase = TalepOlusturUseCase(repository)
        val request = sampleRequest(
            requestNumber = "",
            itemToPurchase = "Paslanmaz Sac",
            companyName = "Firma A"
        )

        useCase(request)

        val created = repository.createdRequests.single()
        assertThat(created.itemToPurchase).isEqualTo("Paslanmaz Sac")
        assertThat(created.companyName).isEqualTo("Firma A")
        assertThat(created.assignedPurchasingStaffId).isEqualTo(request.assignedPurchasingStaffId)
    }
}
