package com.tekinflow.demo.data.remote.dto

import com.squareup.moshi.JsonClass
import com.tekinflow.demo.domain.model.UserRole

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val username: String,
    val fullName: String,
    val role: UserRole,
    val licensePlate: String? = null,
    val isActive: Boolean = true,
    val isSuperAdmin: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@JsonClass(generateAdapter = true)
data class FcmTokenRequestDto(
    val token: String?
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String
)

@JsonClass(generateAdapter = true)
data class CreateUserRequestDto(
    val username: String,
    val fullName: String,
    val password: String,
    val role: UserRole,
    val licensePlate: String? = null
)

@JsonClass(generateAdapter = true)
data class UpdateUserRequestDto(
    val username: String? = null,
    val fullName: String? = null,
    val password: String? = null,
    val role: UserRole? = null,
    val licensePlate: String? = null
)
