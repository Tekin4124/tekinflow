package com.tekinflow.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tekinflow.demo.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications")
    suspend fun getAllNow(): List<NotificationEntity>


    @Query("SELECT * FROM notifications WHERE targetUserId = :userId ORDER BY timestamp DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT id FROM notifications")
    suspend fun getNotificationIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notifications: List<NotificationEntity>)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :notificationId")
    suspend fun markAsRead(notificationId: String)

    @Transaction
    suspend fun replaceAll(notifications: List<NotificationEntity>) {
        if (getAllNow().sortedBy { it.id } == notifications.sortedBy { it.id }) return
        clearAll()
        if (notifications.isNotEmpty()) {
            upsertAll(notifications)
        }
    }
}
