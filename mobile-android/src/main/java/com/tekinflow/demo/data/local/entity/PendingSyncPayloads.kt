package com.tekinflow.demo.data.local.entity

import com.squareup.moshi.JsonClass
import com.tekinflow.demo.domain.model.MaterialStatus

object PendingSyncActions {
    const val UPDATE_STATUS = "UPDATE_STATUS"
    const val TAKE_OVER = "TAKE_OVER"
    const val OBJECT = "OBJECT"
    const val MARK_SEEN = "MARK_SEEN"
}

@JsonClass(generateAdapter = true)
data class UpdateStatusPayload(
    val status: MaterialStatus,
    val deliveredTo: String? = null
)

@JsonClass(generateAdapter = true)
data class TakeOverPayload(
    val driverId: String,
    val licensePlate: String
)

@JsonClass(generateAdapter = true)
data class ObjectionPayload(
    val driverId: String,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class MarkSeenPayload(
    val userId: String
)
