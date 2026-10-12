/*
 * AppNotification.kt
 * Uygulama içi bildirim modelini tanımlar.
 * Kullanıcıya yönelik sistem bildirimleri bu model üzerinden yönetilir.
 */
package com.tekinflow.demo.domain.model

/**
 * Uygulama bildirimi domain modeli
 */
data class AppNotification(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean,
    val targetUserId: String,
    val requestId: String? = null
) {
    val relatedRequestNumber: String?
        get() = requestNumberPattern.find(message)?.value

    companion object {
        private val requestNumberPattern = Regex("""TLP-\d{4}-\d+""")
    }
}
