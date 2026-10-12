/*
 * PurchaseRequest.kt
 * Satın alma talebini temsil eden domain model sınıfı.
 * Tüm talep bilgilerini (firma, ürün, durum, plaka, tarih, iptal, itiraz vb.) barındırır.
 */
package com.tekinflow.demo.domain.model

import java.util.UUID

/**
 * Satın alma talebi domain modeli
 * Yeni alanlar: iptal nedeni, itiraz bilgileri, yönlendirme ve yeni talep göstergesi
 */
data class PurchaseRequest(
    val id: String = UUID.randomUUID().toString(),
    val requestNumber: String = "",
    val itemToPurchase: String = "",
    val items: List<PurchaseRequestItem> = listOf(PurchaseRequestItem()),
    val companyName: String = "",
    val companyAddress: String = "",
    val contactNumber: String = "",
    val contactPersonName: String? = null,
    val productDimensions: String = "",
    val productWeight: String = "",
    val packageCount: Int = 0,
    val urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    val purchaseDate: String = "",
    val actualPurchaseDate: Long? = null,
    val notes: String = "",
    val licensePlate: String = "",
    val spotDriverName: String? = null,
    val spotDriverIdentityNumber: String? = null,
    val spotDriverCompanyName: String? = null,
    val spotDriverPhone: String? = null,
    val deliveredTo: String? = null,
    val materialStatus: MaterialStatus = MaterialStatus.IN_PROGRESS,
    val assignedDriverId: String? = null,
    val assignedPurchasingStaffId: String = "",
    val assignedPurchasingStaffName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // İptal bilgileri
    val cancellationReason: String? = null,
    val cancelledBy: String? = null,
    // Şoför itiraz bilgileri
    val objectionReason: String? = null,
    val hasObjection: Boolean = false,
    val objectionBy: String? = null,
    // Başka talebe yönlendirme
    val redirectedToRequestId: String? = null,
    // Yeni eklenen iş göstergesi (şoför ekranında okunmamış talepler)
    val isNew: Boolean = true,
    // Görülmüş kullanıcı ID'leri
    val seenByUserIds: Set<String> = emptySet()
) {
    val hasFleetDriverAssignment: Boolean
        get() = !assignedDriverId.isNullOrBlank()

    val hasSpotDriverAssignment: Boolean
        get() = !hasFleetDriverAssignment && listOf(
            licensePlate,
            spotDriverName,
            spotDriverIdentityNumber,
            spotDriverCompanyName
        ).any { !it.isNullOrBlank() }

    val hasDriverAssignment: Boolean
        get() = hasFleetDriverAssignment || hasSpotDriverAssignment

    val canBeTakenOverByDriver: Boolean
        get() = !hasDriverAssignment &&
            materialStatus != MaterialStatus.DELIVERED &&
            materialStatus != MaterialStatus.CANCELLED
}

data class PurchaseRequestItem(
    val id: String = UUID.randomUUID().toString(),
    val itemName: String = "",
    val quantity: String = "1",
    val unit: MaterialUnit = MaterialUnit.KG,
    val packageCount: Int = 0,
    val dimensionsNote: String = "",
    val sortOrder: Int = 0
) {
    val displayText: String
        get() = listOf(
            itemName,
            quantity.takeIf { it.isNotBlank() }?.let { "$it ${unit.displayName}" },
            packageCount.takeIf { it > 0 }?.let { "$it kap" },
            dimensionsNote.takeIf { it.isNotBlank() }?.let { "Ölçü: $it" }
        ).filterNotNull().joinToString(" · ")
}
