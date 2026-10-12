/*
 * DurumGuncelleUseCase.kt
 * Talebin sevkiyat durumunu güncelleme iş kuralını kapsar.
 */
package com.tekinflow.demo.domain.usecase

import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.repository.TalepDeposu
import javax.inject.Inject

/**
 * Talep durumunu güncelleyen use case
 */
class DurumGuncelleUseCase @Inject constructor(
    private val repository: TalepDeposu
) {
    suspend operator fun invoke(requestId: String, status: MaterialStatus, deliveredTo: String? = null) {
        val request = repository.getRequestById(requestId)
            ?: throw IllegalArgumentException("Talep bulunamadı")
        if (!isAllowedTransition(request.materialStatus, status)) {
            throw IllegalArgumentException("Geçersiz durum geçişi")
        }
        repository.updateStatus(requestId, status, deliveredTo)
    }

    private fun isAllowedTransition(currentStatus: MaterialStatus, newStatus: MaterialStatus): Boolean {
        if (currentStatus == newStatus) return true
        return when (currentStatus) {
            MaterialStatus.PENDING -> newStatus == MaterialStatus.IN_PROGRESS
            MaterialStatus.IN_PROGRESS -> newStatus == MaterialStatus.ON_THE_WAY
            MaterialStatus.PURCHASED -> newStatus == MaterialStatus.ON_THE_WAY
            MaterialStatus.ON_THE_WAY -> newStatus == MaterialStatus.DELIVERED
            MaterialStatus.DELIVERED,
            MaterialStatus.CANCELLED -> false
        }
    }
}
