package com.tekinflow.demo.data.local.mapper

import com.tekinflow.demo.data.local.entity.NotificationEntity
import com.tekinflow.demo.data.local.entity.PurchaseRequestEntity
import com.tekinflow.demo.data.local.entity.UserEntity
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User

fun PurchaseRequestEntity.toDomain(): PurchaseRequest {
    return PurchaseRequest(
        id = id,
        requestNumber = requestNumber,
        itemToPurchase = itemToPurchase,
        items = items.ifEmpty {
            listOf(
                com.tekinflow.demo.domain.model.PurchaseRequestItem(
                    itemName = itemToPurchase,
                    quantity = "1",
                    packageCount = packageCount,
                    dimensionsNote = productDimensions
                )
            )
        },
        companyName = companyName,
        companyAddress = companyAddress,
        contactNumber = contactNumber,
        contactPersonName = contactPersonName,
        productDimensions = productDimensions,
        productWeight = productWeight,
        packageCount = packageCount,
        urgencyLevel = urgencyLevel,
        purchaseDate = purchaseDate,
        actualPurchaseDate = actualPurchaseDate,
        notes = notes,
        licensePlate = licensePlate,
        spotDriverName = spotDriverName,
        spotDriverIdentityNumber = spotDriverIdentityNumber,
        spotDriverCompanyName = spotDriverCompanyName,
        spotDriverPhone = spotDriverPhone,
        deliveredTo = deliveredTo,
        materialStatus = materialStatus,
        assignedDriverId = assignedDriverId,
        assignedPurchasingStaffId = assignedPurchasingStaffId,
        assignedPurchasingStaffName = assignedPurchasingStaffName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cancellationReason = cancellationReason,
        cancelledBy = cancelledBy,
        objectionReason = objectionReason,
        hasObjection = hasObjection,
        objectionBy = objectionBy,
        redirectedToRequestId = redirectedToRequestId,
        isNew = isNew,
        seenByUserIds = seenByUserIds
    )
}

fun PurchaseRequest.toEntity(): PurchaseRequestEntity {
    return PurchaseRequestEntity(
        id = id,
        requestNumber = requestNumber,
        itemToPurchase = itemToPurchase,
        items = items,
        companyName = companyName,
        companyAddress = companyAddress,
        contactNumber = contactNumber,
        contactPersonName = contactPersonName,
        productDimensions = productDimensions,
        productWeight = productWeight,
        packageCount = packageCount,
        urgencyLevel = urgencyLevel,
        purchaseDate = purchaseDate,
        actualPurchaseDate = actualPurchaseDate,
        notes = notes,
        licensePlate = licensePlate,
        spotDriverName = spotDriverName,
        spotDriverIdentityNumber = spotDriverIdentityNumber,
        spotDriverCompanyName = spotDriverCompanyName,
        spotDriverPhone = spotDriverPhone,
        deliveredTo = deliveredTo,
        materialStatus = materialStatus,
        assignedDriverId = assignedDriverId,
        assignedPurchasingStaffId = assignedPurchasingStaffId,
        assignedPurchasingStaffName = assignedPurchasingStaffName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cancellationReason = cancellationReason,
        cancelledBy = cancelledBy,
        objectionReason = objectionReason,
        hasObjection = hasObjection,
        objectionBy = objectionBy,
        redirectedToRequestId = redirectedToRequestId,
        isNew = isNew,
        seenByUserIds = seenByUserIds
    )
}

fun NotificationEntity.toDomain(): AppNotification {
    return AppNotification(
        id = id,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        targetUserId = targetUserId,
        requestId = requestId
    )
}

fun AppNotification.toEntity(): NotificationEntity {
    return NotificationEntity(
        id = id,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        targetUserId = targetUserId,
        requestId = requestId
    )
}

fun UserEntity.toDomain(): User {
    return User(
        id = id,
        username = username,
        fullName = fullName,
        role = role,
        licensePlate = licensePlate,
        isActive = isActive,
        isSuperAdmin = isSuperAdmin
    )
}

fun User.toEntity(isActive: Boolean = true): UserEntity {
    return UserEntity(
        id = id,
        username = username,
        fullName = fullName,
        role = role,
        licensePlate = licensePlate,
        isActive = isActive,
        isSuperAdmin = isSuperAdmin
    )
}
