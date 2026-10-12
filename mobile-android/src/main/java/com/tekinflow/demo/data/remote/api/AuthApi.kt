package com.tekinflow.demo.data.remote.api

import com.tekinflow.demo.data.remote.dto.LoginRequestDto
import com.tekinflow.demo.data.remote.dto.LoginResponseDto
import com.tekinflow.demo.data.remote.dto.LogoutRequestDto
import com.tekinflow.demo.data.remote.dto.RefreshRequestDto
import com.tekinflow.demo.data.remote.dto.SuccessResponseDto
import com.tekinflow.demo.data.remote.dto.TokenResponseDto
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): Response<LoginResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequestDto
    ): Response<TokenResponseDto>

    @POST("auth/refresh")
    fun refreshBlocking(
        @Body request: RefreshRequestDto
    ): Call<TokenResponseDto>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: LogoutRequestDto
    ): Response<SuccessResponseDto>
}
