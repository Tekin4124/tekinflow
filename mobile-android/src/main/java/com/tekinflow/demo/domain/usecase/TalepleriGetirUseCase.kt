/*
 * TalepleriGetirUseCase.kt
 * Tüm satın alma taleplerini listeleme iş kuralını kapsar.
 * Repository'den Flow olarak veri akışı sağlar.
 */
package com.tekinflow.demo.domain.usecase

import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.repository.TalepDeposu
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Tüm satın alma taleplerini dönen use case
 */
class TalepleriGetirUseCase @Inject constructor(
    private val repository: TalepDeposu
) {
    operator fun invoke(): Flow<List<PurchaseRequest>> {
        return repository.getAllRequests()
    }
}
