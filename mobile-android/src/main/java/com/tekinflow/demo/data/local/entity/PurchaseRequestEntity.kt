package com.tekinflow.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequestItem
import com.tekinflow.demo.domain.model.UrgencyLevel

@Entity(tableName = "purchase_requests")
data class PurchaseRequestEntity(
    @PrimaryKey val id: String,
    val requestNumber: String,
    val itemToPurchase: String,
    val items: List<PurchaseRequestItem> = emptyList(),
    val companyName: String,
    val companyAddress: String,
    val contactNumber: String,
    val contactPersonName: String? = null,
    val productDimensions: String,
    val productWeight: String,
    val packageCount: Int = 0,
    val urgencyLevel: UrgencyLevel,
    val purchaseDate: String,
    val actualPurchaseDate: Long? = null,
    val notes: String,
    val licensePlate: String,
    val spotDriverName: String? = null,
    val spotDriverIdentityNumber: String? = null,
    val spotDriverCompanyName: String? = null,
    val spotDriverPhone: String? = null,
    val deliveredTo: String? = null,
    val materialStatus: MaterialStatus,
    val assignedDriverId: String? = null,
    val assignedPurchasingStaffId: String,
    val assignedPurchasingStaffName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val cancellationReason: String? = null,
    val cancelledBy: String? = null,
    val objectionReason: String? = null,
    val hasObjection: Boolean = false,
    val objectionBy: String? = null,
    val redirectedToRequestId: String? = null,
    val isNew: Boolean = true,
    val seenByUserIds: Set<String> = emptySet()
)
