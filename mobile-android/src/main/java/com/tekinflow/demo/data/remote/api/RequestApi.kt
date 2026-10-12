package com.tekinflow.demo.data.remote.api

import com.tekinflow.demo.data.remote.dto.AssignDriverRequestDto
import com.tekinflow.demo.data.remote.dto.CancelRequestDto
import com.tekinflow.demo.data.remote.dto.CreatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.ObjectionRequestDto
import com.tekinflow.demo.data.remote.dto.PurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.SuccessResponseDto
import com.tekinflow.demo.data.remote.dto.UpdatePurchaseRequestDto
import com.tekinflow.demo.data.remote.dto.UpdateStatusRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface RequestApi {

    @GET("requests")
    suspend fun getRequests(
        @Query("status") status: String? = null,
        @Query("assignedDriverId") assignedDriverId: String? = null,
        @Query("staffId") staffId: String? = null,
        @Header("Authorization") authorization: String? = null
    ): Response<List<PurchaseRequestDto>>

    @GET("requests/delivered-recipients")
    suspend fun getDeliveredRecipients(
        @Header("Authorization") authorization: String? = null
    ): Response<List<String>>

    @GET("requests/{id}")
    suspend fun getRequestById(
        @Path("id") requestId: String,
        @Header("Authorization") authorization: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests")
    suspend fun createRequest(
        @Body request: CreatePurchaseRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @PUT("requests/{id}")
    suspend fun updateRequest(
        @Path("id") requestId: String,
        @Body request: UpdatePurchaseRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/cancel")
    suspend fun cancelRequest(
        @Path("id") requestId: String,
        @Body request: CancelRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/assign-driver")
    suspend fun assignDriver(
        @Path("id") requestId: String,
        @Body request: AssignDriverRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/unassign-driver")
    suspend fun unassignDriver(
        @Path("id") requestId: String,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/take-over")
    suspend fun takeOverRequest(
        @Path("id") requestId: String,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/update-status")
    suspend fun updateStatus(
        @Path("id") requestId: String,
        @Body request: UpdateStatusRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/object")
    suspend fun submitObjection(
        @Path("id") requestId: String,
        @Body request: ObjectionRequestDto,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<PurchaseRequestDto>

    @POST("requests/{id}/mark-seen")
    suspend fun markSeen(
        @Path("id") requestId: String,
        @Header("Authorization") authorization: String? = null,
        @Header("X-Idempotency-Key") idempotencyKey: String? = null
    ): Response<SuccessResponseDto>
}
