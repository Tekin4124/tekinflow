package com.tekinflow.demo.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean,
    val targetUserId: String,
    val requestId: String? = null
)
