package com.tekinflow.backend.repository

import com.tekinflow.backend.config.dbQuery
import com.tekinflow.backend.model.NotificationResponse
import com.tekinflow.backend.model.NotificationsTable
import com.tekinflow.backend.model.UsersTable
import com.tekinflow.backend.util.NotFoundException
import com.tekinflow.backend.util.toEpochMillis
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class NotificationLimitState(
    var windowStartedAt: Long,
    var count: Int
)

class NotificationRepository {
    private val logger = LoggerFactory.getLogger(NotificationRepository::class.java)
    private val fanOutCounters = ConcurrentHashMap<UUID, NotificationLimitState>()
    private val messaging: FirebaseMessaging? = initMessaging()

    private fun initMessaging(): FirebaseMessaging? {
        val credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH")?.takeIf { it.isNotBlank() }
            ?: System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.takeIf { it.isNotBlank() }
        if (credentialsPath == null) {
            logger.info("FIREBASE_CREDENTIALS_PATH ayarlanmadi; push bildirim devre disi.")
            return null
        }
        return runCatching {
            val credentials = FileInputStream(credentialsPath).use { GoogleCredentials.fromStream(it) }
            val options = FirebaseOptions.builder().setCredentials(credentials).build()
            val app = if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
            } else {
                FirebaseApp.getInstance()
            }
            FirebaseMessaging.getInstance(app)
        }.onSuccess {
            logger.info("Firebase push bildirim aktif.")
        }.onFailure { error ->
            logger.warn("Firebase baslatilamadi; push bildirim devre disi.", error)
        }.getOrNull()
    }

    suspend fun listForUser(userId: UUID): List<NotificationResponse> = dbQuery {
        NotificationsTable
            .selectAll()
            .where { NotificationsTable.targetUserId eq userId }
            .orderBy(NotificationsTable.createdAt, SortOrder.DESC)
            .map { it.toNotificationResponse() }
    }

    suspend fun markRead(notificationId: UUID, userId: UUID) = dbQuery {
        val updatedRows = NotificationsTable.update({
            (NotificationsTable.id eq notificationId) and (NotificationsTable.targetUserId eq userId)
        }) {
            it[isRead] = true
        }
        if (updatedRows == 0) throw NotFoundException()
    }

    suspend fun createForUser(userId: UUID, message: String, requestId: UUID? = null) = dbQuery {
        if (!allowNotificationFor(userId)) return@dbQuery
        val safeMessage = message.toNotificationMessage()
        NotificationsTable.insert {
            it[targetUserId] = userId
            it[this.message] = safeMessage
            it[NotificationsTable.requestId] = requestId
            it[isRead] = false
            it[NotificationsTable.createdAt] = Instant.now()
        }
        sendPushIfConfigured(userId, safeMessage, requestId)
    }

    suspend fun createForUsers(userIds: Collection<UUID>, message: String, requestId: UUID? = null) = dbQuery {
        if (userIds.isEmpty()) return@dbQuery
        val now = Instant.now()
        val safeMessage = message.toNotificationMessage()
        userIds.distinct().forEach { userId ->
            if (!allowNotificationFor(userId)) return@forEach
            NotificationsTable.insert {
                it[targetUserId] = userId
                it[this.message] = safeMessage
                it[NotificationsTable.requestId] = requestId
                it[isRead] = false
                it[NotificationsTable.createdAt] = now
            }
            sendPushIfConfigured(userId, safeMessage, requestId)
        }
    }

    private fun sendPushIfConfigured(userId: UUID, message: String, requestId: UUID?) {
        val firebaseMessaging = messaging ?: return
        val token = UsersTable
            .selectAll()
            .where { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.fcmToken)
            ?.takeIf { it.isNotBlank() }
            ?: return

        Thread {
            runCatching {
                val push = Message.builder()
                    .setToken(token)
                    .setNotification(
                        Notification.builder()
                            .setTitle(pushTitle)
                            .setBody(message)
                            .build()
                    )
                    .putData("title", pushTitle)
                    .putData("body", message)
                    .putData("requestId", requestId?.toString().orEmpty())
                    .setAndroidConfig(
                        AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(
                                AndroidNotification.builder()
                                    .setChannelId(androidChannelId)
                                    .setSound("default")
                                    .build()
                            )
                            .build()
                    )
                    .build()
                firebaseMessaging.send(push)
            }.onFailure { error ->
                logger.warn("Push bildirim gonderilemedi userId={}", userId, error)
            }
        }.start()
    }

    private fun ResultRow.toNotificationResponse(): NotificationResponse {
        return NotificationResponse(
            id = this[NotificationsTable.id].value.toString(),
            message = this[NotificationsTable.message],
            timestamp = this[NotificationsTable.createdAt].toEpochMillis(),
            isRead = this[NotificationsTable.isRead],
            targetUserId = this[NotificationsTable.targetUserId].value.toString(),
            requestId = this[NotificationsTable.requestId]?.value?.toString()
        )
    }

    private fun allowNotificationFor(userId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val state = fanOutCounters.compute(userId) { _, current ->
            when {
                current == null || now - current.windowStartedAt >= windowMillis ->
                    NotificationLimitState(now, 1)
                else -> current.apply { count += 1 }
            }
        } ?: return true
        return state.count <= maxNotificationsPerWindow
    }

    private fun String.toNotificationMessage(): String = trim().take(maxMessageLength)

    private companion object {
        const val windowMillis = 60_000L
        const val maxNotificationsPerWindow = 10
        const val maxMessageLength = 500
        const val pushTitle = "TekinFlow Sevkiyat"
        const val androidChannelId = "satinalma_notifications"
    }
}
