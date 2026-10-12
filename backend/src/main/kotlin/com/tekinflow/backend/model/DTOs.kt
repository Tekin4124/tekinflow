package com.tekinflow.backend.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    PURCHASING,
    PURCHASING_ADMIN,
    LOGISTICS_OFFICE,
    LOGISTICS_ADMIN,
    DRIVER,
    ADMIN
}

@Serializable
enum class UrgencyLevel {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

@Serializable
enum class MaterialStatus {
    PENDING,
    IN_PROGRESS,
    PURCHASED,
    ON_THE_WAY,
    DELIVERED,
    CANCELLED
}

@Serializable
enum class MaterialUnit {
    KG,
    CM,
    M,
    MM,
    LITRE,
    M3,
    ADET
}

@Serializable
data class ErrorResponse(
    val error: String,
    val code: String = "GENERIC_ERROR"
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val twoFactorCode: String? = null,
    val twoFactorSecret: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class LogoutRequest(
    val refreshToken: String
)

@Serializable
data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserResponse? = null,
    val twoFactorRequired: Boolean = false,
    val twoFactorSetupRequired: Boolean = false,
    val twoFactorSecret: String? = null,
    val twoFactorOtpAuthUrl: String? = null
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val licensePlate: String? = null,
    val isActive: Boolean = true,
    val isSuperAdmin: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val fullName: String,
    val password: String,
    val role: UserRole,
    val licensePlate: String? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val fullName: String? = null,
    val password: String? = null,
    val role: UserRole? = null,
    val licensePlate: String? = null
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class PurchaseRequestResponse(
    val id: String,
    val requestNumber: String,
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemResponse> = emptyList(),
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
    val assignedDriverName: String? = null,
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

@Serializable
data class PurchaseRequestItemResponse(
    val id: String,
    val itemName: String,
    val quantity: String,
    val unit: MaterialUnit = MaterialUnit.KG,
    val packageCount: Int? = null,
    val dimensionsNote: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class PurchaseRequestItemRequest(
    val itemName: String,
    val quantity: String = "1",
    val unit: MaterialUnit = MaterialUnit.KG,
    val packageCount: Int? = null,
    val dimensionsNote: String? = null
)

@Serializable
data class CreatePurchaseRequestRequest(
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemRequest> = emptyList(),
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

@Serializable
data class UpdatePurchaseRequestRequest(
    val itemToPurchase: String,
    val items: List<PurchaseRequestItemRequest> = emptyList(),
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

@Serializable
data class CancelRequestBody(
    val reason: String? = null
)

@Serializable
data class AssignDriverRequest(
    val driverId: String? = null,
    val licensePlate: String? = null,
    val spotDriverName: String? = null,
    val spotDriverIdentityNumber: String? = null,
    val spotDriverCompanyName: String? = null,
    val spotDriverPhone: String? = null
)

@Serializable
data class UpdateStatusRequest(
    val status: MaterialStatus,
    val deliveredTo: String? = null
)

@Serializable
data class ObjectionRequest(
    val reason: String
)

@Serializable
data class NotificationResponse(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean,
    val targetUserId: String,
    val requestId: String? = null
)

@Serializable
data class FcmTokenRequest(
    val token: String?
)

@Serializable
data class UserPreferenceResponse(
    val key: String,
    val value: String
)

@Serializable
data class UserPreferenceRequest(
    val value: String
)

@Serializable
data class AuditLogResponse(
    val id: String,
    val actorUserId: String? = null,
    val actorUsername: String? = null,
    val actorFullName: String? = null,
    val action: String,
    val entityType: String,
    val entityId: String? = null,
    val entityLabel: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val ipAddress: String? = null,
    val createdAt: Long
)
