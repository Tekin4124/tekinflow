package com.tekinflow.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val action: String,
    val requestId: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val failed: Boolean = false
)
