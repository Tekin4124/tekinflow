package com.tekinflow.demo.domain.usecase

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.sampleRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TalepleriGetirUseCaseTest {

    @Test
    fun `use case emits the repository requests flow`() = runTest {
        val requests = listOf(sampleRequest(id = "r1"), sampleRequest(id = "r2"))
        val repository = FakeTalepDeposu(initialRequests = requests)
        val useCase = TalepleriGetirUseCase(repository)

        useCase().test {
            assertThat(awaitItem()).containsExactlyElementsIn(requests).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `use case emits subsequent repository updates`() = runTest {
        val repository = FakeTalepDeposu()
        val useCase = TalepleriGetirUseCase(repository)

        useCase().test {
            assertThat(awaitItem()).isEmpty()

            val updatedRequests = listOf(sampleRequest(id = "r1"))
            repository.setRequests(updatedRequests)

            assertThat(awaitItem()).containsExactlyElementsIn(updatedRequests)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
