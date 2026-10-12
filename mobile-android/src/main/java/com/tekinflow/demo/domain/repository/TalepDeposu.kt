/*
 * TalepDeposu.kt
 * Satın alma talepleri için veri erişim sözleşmesini (interface) tanımlar.
 * Domain katmanı bu arayüze bağımlıdır; implementasyon data katmanında yapılır.
 */
package com.tekinflow.demo.domain.repository

import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Satın alma talebi veri erişim arayüzü
 */
interface TalepDeposu {

    /** Tüm talepleri gerçek zamanlı akış olarak döner */
    fun getAllRequests(): Flow<List<PurchaseRequest>>

    /** Belirtilen ID'ye sahip talebi döner */
    suspend fun getRequestById(id: String): PurchaseRequest?

    /** Yeni talep oluşturur */
    suspend fun createRequest(request: PurchaseRequest)

    /** Mevcut talebi günceller */
    suspend fun updateRequest(request: PurchaseRequest)

    /** Talep durumunu günceller */
    suspend fun updateStatus(requestId: String, status: MaterialStatus, deliveredTo: String? = null)

    /** Şoförü talebe atar ve plaka bilgisini günceller */
    suspend fun assignDriver(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest>

    /** Spot araç bilgisiyle talebe plaka/şoför bilgisi ekler */
    suspend fun assignSpotDriver(
        requestId: String,
        driverName: String,
        licensePlate: String,
        identityNumber: String,
        companyName: String,
        phone: String = ""
    ): Result<PurchaseRequest>

    /** Talebin sürücü atamasını kaldırır (üstünden çıkarma) */
    suspend fun unassignDriver(requestId: String): Result<PurchaseRequest>

    /** Şoförün atanmamış talebi üstüne almasını sağlar */
    suspend fun takeOverRequest(
        requestId: String,
        driverId: String,
        licensePlate: String
    ): Result<PurchaseRequest>

    /** Talebi iptal eder */
    suspend fun cancelRequest(requestId: String, reason: String?, cancelledBy: String)

    /** Şoför itirazı gönderir */
    suspend fun submitObjection(requestId: String, driverId: String, reason: String)

    /** Talebi okundu olarak işaretler */
    suspend fun markAsSeen(requestId: String, userId: String)

    /** Mevcut şoförlerin listesini döner */
    fun getDrivers(): Flow<List<User>>

    /** Kullanıcıya yönelik bildirimleri döner */
    fun getNotifications(userId: String): Flow<List<AppNotification>>

    /** Bildirimi okundu olarak işaretler */
    suspend fun markNotificationRead(notificationId: String)

    /** Sonraki talep numarasını üretir (TLP-YYYY-NNN formatında) */
    suspend fun generateRequestNumber(): String

    /** Daha önce girilen teslim alan kişi adlarını döner */
    suspend fun getDeliveredRecipients(): List<String>
}
