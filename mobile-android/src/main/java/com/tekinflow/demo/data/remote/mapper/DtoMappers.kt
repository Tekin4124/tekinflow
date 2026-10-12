package com.tekinflow.demo.data.remote.mapper

import com.tekinflow.demo.data.remote.dto.CreatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.NotificationDto
import com.tekinflow.demo.data.remote.dto.PurchaseRequestItemDto
import com.tekinflow.demo.data.remote.dto.PurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.UpdatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.UserDto
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.PurchaseRequestItem
import com.tekinflow.demo.domain.model.User
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val backendDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val uiDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("tr", "TR"))

fun UserDto.toDomain(): User {
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

fun NotificationDto.toDomain(): AppNotification {
    return AppNotification(
        id = id,
        message = message,
        timestamp = timestamp,
        isRead = isRead,
        targetUserId = targetUserId,
        requestId = requestId
    )
}

fun PurchaseRequestDto.toDomain(): PurchaseRequest {
    val requestItems = items
        .sortedBy { it.sortOrder }
        .map { it.toDomain() }
        .ifEmpty {
            listOf(
                PurchaseRequestItem(
                    itemName = itemToPurchase,
                    quantity = "1",
                    unit = MaterialUnit.KG,
                    packageCount = packageCount ?: 0,
                    dimensionsNote = productDimensions
                )
            )
        }
    return PurchaseRequest(
        id = id,
        requestNumber = requestNumber,
        itemToPurchase = requestItems.firstOrNull()?.itemName ?: itemToPurchase,
        items = requestItems,
        companyName = companyName,
        companyAddress = companyAddress,
        contactNumber = contactNumber,
        contactPersonName = contactPersonName,
        productDimensions = productDimensions,
        productWeight = productWeight,
        packageCount = packageCount ?: 0,
        urgencyLevel = urgencyLevel,
        purchaseDate = purchaseDate.toUiDate(),
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

fun PurchaseRequest.toCreateDto(): CreatePurchaseRequestDto {
    val requestItems = normalizedItems()
    return CreatePurchaseRequestDto(
        itemToPurchase = requestItems.first().itemName.trim(),
        items = requestItems.map { it.toDto() },
        companyName = companyName.trim(),
        companyAddress = companyAddress.trim().ifBlank { null },
        contactNumber = contactNumber.trim().ifBlank { null },
        contactPersonName = contactPersonName?.trim()?.ifBlank { null },
        productDimensions = productDimensions.trim().ifBlank { null },
        productWeight = productWeight.trim().ifBlank { null },
        packageCount = packageCount.takeIf { it > 0 },
        urgencyLevel = urgencyLevel,
        purchaseDate = purchaseDate.toBackendDate(),
        notes = notes.trim().ifBlank { null }
    )
}

fun PurchaseRequest.toUpdateDto(): UpdatePurchaseRequestDto {
    val requestItems = normalizedItems()
    return UpdatePurchaseRequestDto(
        itemToPurchase = requestItems.first().itemName.trim(),
        items = requestItems.map { it.toDto() },
        companyName = companyName.trim(),
        companyAddress = companyAddress.trim().ifBlank { null },
        contactNumber = contactNumber.trim().ifBlank { null },
        contactPersonName = contactPersonName?.trim()?.ifBlank { null },
        productDimensions = productDimensions.trim().ifBlank { null },
        productWeight = productWeight.trim().ifBlank { null },
        packageCount = packageCount.takeIf { it > 0 },
        urgencyLevel = urgencyLevel,
        purchaseDate = purchaseDate.toBackendDate(),
        notes = notes.trim().ifBlank { null }
    )
}

private fun PurchaseRequestItemDto.toDomain(): PurchaseRequestItem {
    return PurchaseRequestItem(
        id = id.orEmpty(),
        itemName = itemName,
        quantity = quantity,
        unit = unit,
        packageCount = packageCount ?: 0,
        dimensionsNote = dimensionsNote.orEmpty(),
        sortOrder = sortOrder
    )
}

private fun PurchaseRequestItem.toDto(): PurchaseRequestItemDto {
    return PurchaseRequestItemDto(
        id = id.takeIf { it.isNotBlank() },
        itemName = itemName.trim(),
        quantity = quantity.trim().ifBlank { "1" },
        unit = unit,
        packageCount = packageCount.takeIf { it > 0 },
        dimensionsNote = dimensionsNote.trim().ifBlank { null },
        sortOrder = sortOrder
    )
}

private fun PurchaseRequest.normalizedItems(): List<PurchaseRequestItem> {
    return items
        .filter { it.itemName.isNotBlank() }
        .ifEmpty {
            listOf(
                PurchaseRequestItem(
                    itemName = itemToPurchase,
                    quantity = "1",
                    unit = MaterialUnit.KG,
                    packageCount = packageCount,
                    dimensionsNote = productDimensions
                )
            )
        }
        .mapIndexed { index, item -> item.copy(sortOrder = index) }
}

private fun String.toUiDate(): String {
    if (isBlank()) return ""
    return runCatching {
        LocalDate.parse(this, backendDateFormatter).format(uiDateFormatter)
    }.getOrElse { this }
}

private fun String.toBackendDate(): String? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    return runCatching {
        LocalDate.parse(normalized, uiDateFormatter).format(backendDateFormatter)
    }.recoverCatching {
        LocalDate.parse(normalized, backendDateFormatter).format(backendDateFormatter)
    }.getOrNull()
}
