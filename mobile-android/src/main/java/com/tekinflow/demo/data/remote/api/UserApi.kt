package com.tekinflow.demo.data.remote.api

import com.tekinflow.demo.data.remote.dto.CreateUserRequestDto
import com.tekinflow.demo.data.remote.dto.ChangePasswordRequestDto
import com.tekinflow.demo.data.remote.dto.FcmTokenRequestDto
import com.tekinflow.demo.data.remote.dto.SuccessResponseDto
import com.tekinflow.demo.data.remote.dto.UpdateUserRequestDto
import com.tekinflow.demo.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface UserApi {

    @GET("users")
    suspend fun getUsers(
        @Header("Authorization") authorization: String? = null
    ): Response<List<UserDto>>

    @GET("users/drivers")
    suspend fun getDrivers(
        @Header("Authorization") authorization: String? = null
    ): Response<List<UserDto>>

    @POST("users/me/fcm-token")
    suspend fun updateFcmToken(
        @Body request: FcmTokenRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<SuccessResponseDto>

    @PUT("users/me/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<SuccessResponseDto>

    @POST("users")
    suspend fun createUser(
        @Body request: CreateUserRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<UserDto>

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") userId: String,
        @Body request: UpdateUserRequestDto,
        @Header("Authorization") authorization: String? = null
    ): Response<UserDto>

    @DELETE("users/{id}")
    suspend fun deleteUser(
        @Path("id") userId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<SuccessResponseDto>

    @POST("users/{id}/activate")
    suspend fun activateUser(
        @Path("id") userId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<UserDto>

    @DELETE("users/{id}/permanent")
    suspend fun deleteUserPermanently(
        @Path("id") userId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<SuccessResponseDto>
}
