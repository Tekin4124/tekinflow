package com.tekinflow.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean,
    val targetUserId: String,
    val requestId: String? = null
)
