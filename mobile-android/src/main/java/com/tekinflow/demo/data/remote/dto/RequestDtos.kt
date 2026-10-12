package com.tekinflow.demo.data.remote.dto

import com.squareup.moshi.JsonClass
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.UrgencyLevel

@JsonClass(generateAdapter = true)
data class PurchaseRequestDto(
    val id: String,
    val requestNumber: String,
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemDto> = emptyList(),
    val companyName: String,
    val companyAddress: String,
    val contactNumber: String,
    val contactPersonName: String? = null,
    val productDimensions: String,
    val productWeight: String,
    val packageCount: Int? = null,
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

@JsonClass(generateAdapter = true)
data class PurchaseRequestItemDto(
    val id: String? = null,
    val itemName: String,
    val quantity: String = "1",
    val unit: MaterialUnit = MaterialUnit.KG,
    val packageCount: Int? = null,
    val dimensionsNote: String? = null,
    val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class CreatePurchaseRequestDto(
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemDto> = emptyList(),
    val companyName: String,
    val companyAddress: String? = null,
    val contactNumber: String? = null,
    val contactPersonName: String? = null,
    val productDimensions: String? = null,
    val productWeight: String? = null,
    val packageCount: Int? = null,
    val urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    val purchaseDate: String? = null,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdatePurchaseRequestDto(
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemDto> = emptyList(),
    val companyName: String,
    val companyAddress: String? = null,
    val contactNumber: String? = null,
    val contactPersonName: String? = null,
    val productDimensions: String? = null,
    val productWeight: String? = null,
    val packageCount: Int? = null,
    val urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    val purchaseDate: String? = null,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class CancelRequestDto(
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class AssignDriverRequestDto(
    val driverId: String? = null,
    val licensePlate: String? = null,
    val spotDriverName: String? = null,
    val spotDriverIdentityNumber: String? = null,
    val spotDriverCompanyName: String? = null,
    val spotDriverPhone: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateStatusRequestDto(
    val status: MaterialStatus,
    val deliveredTo: String? = null
)

@JsonClass(generateAdapter = true)
data class ObjectionRequestDto(
    val reason: String
)
