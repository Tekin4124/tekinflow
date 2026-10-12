/*
 * TalepOlusturUseCase.kt
 * Yeni satın alma talebi oluşturma iş kuralını kapsar.
 * Talep numarası repository üzerinden otomatik olarak üretilir (TLP-YYYY-NNN formatı).
 */
package com.tekinflow.demo.domain.usecase

import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.repository.TalepDeposu
import javax.inject.Inject

/**
 * Yeni satın alma talebi oluşturan use case
 */
class TalepOlusturUseCase @Inject constructor(
    private val repository: TalepDeposu
) {
    suspend operator fun invoke(request: PurchaseRequest) {
        val requestWithNumber = if (request.requestNumber.isBlank()) {
            request.copy(requestNumber = repository.generateRequestNumber())
        } else {
            request
        }
        repository.createRequest(requestWithNumber)
    }
}
