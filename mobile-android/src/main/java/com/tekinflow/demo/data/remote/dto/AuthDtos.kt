package com.tekinflow.demo.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequestDto(
    val username: String,
    val password: String,
    val twoFactorCode: String? = null,
    val twoFactorSecret: String? = null
)

@JsonClass(generateAdapter = true)
data class RefreshRequestDto(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class LogoutRequestDto(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class LoginResponseDto(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserDto? = null,
    val twoFactorRequired: Boolean = false,
    val twoFactorSetupRequired: Boolean = false,
    val twoFactorSecret: String? = null,
    val twoFactorOtpAuthUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SuccessResponseDto(
    val success: Boolean
)
