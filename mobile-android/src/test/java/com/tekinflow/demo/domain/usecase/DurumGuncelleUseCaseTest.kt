package com.tekinflow.demo.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.testutil.FakeTalepDeposu
import com.tekinflow.demo.testutil.sampleRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DurumGuncelleUseCaseTest {

    @Test
    fun `use case delegates update status to repository`() = runTest {
        val repository = FakeTalepDeposu(
            initialRequests = listOf(
                sampleRequest(id = "request-42", materialStatus = MaterialStatus.IN_PROGRESS)
            )
        )
        val useCase = DurumGuncelleUseCase(repository)

        useCase("request-42", MaterialStatus.ON_THE_WAY)

        assertThat(repository.updatedStatuses).containsExactly(
            "request-42" to MaterialStatus.ON_THE_WAY
        )
    }

    @Test
    fun `use case rejects skipped forward transition`() = runTest {
        val repository = FakeTalepDeposu(
            initialRequests = listOf(
                sampleRequest(id = "request-42", materialStatus = MaterialStatus.IN_PROGRESS)
            )
        )
        val useCase = DurumGuncelleUseCase(repository)

        val result = runCatching {
            useCase("request-42", MaterialStatus.DELIVERED)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.updatedStatuses).isEmpty()
    }

    @Test
    fun `use case rejects backward transition`() = runTest {
        val repository = FakeTalepDeposu(
            initialRequests = listOf(
                sampleRequest(id = "request-42", materialStatus = MaterialStatus.ON_THE_WAY)
            )
        )
        val useCase = DurumGuncelleUseCase(repository)

        val result = runCatching {
            useCase("request-42", MaterialStatus.PURCHASED)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repository.updatedStatuses).isEmpty()
    }
}
