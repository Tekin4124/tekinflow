package com.tekinflow.demo.data.remote.api

import com.tekinflow.demo.data.remote.dto.NotificationDto
import com.tekinflow.demo.data.remote.dto.SuccessResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface NotificationApi {

    @GET("notifications")
    suspend fun getNotifications(
        @Header("Authorization") authorization: String? = null
    ): Response<List<NotificationDto>>

    @POST("notifications/{id}/read")
    suspend fun markAsRead(
        @Path("id") notificationId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<SuccessResponseDto>
}
