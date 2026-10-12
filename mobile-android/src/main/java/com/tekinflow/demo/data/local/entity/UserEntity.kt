package com.tekinflow.demo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tekinflow.demo.domain.model.UserRole

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val licensePlate: String? = null,
    val isActive: Boolean = true,
    val isSuperAdmin: Boolean = false
)
