package com.tekinflow.demo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tekinflow.demo.data.local.converter.Converters
import com.tekinflow.demo.data.local.dao.NotificationDao
import com.tekinflow.demo.data.local.dao.PendingSyncDao
import com.tekinflow.demo.data.local.dao.PurchaseRequestDao
import com.tekinflow.demo.data.local.dao.UserDao
import com.tekinflow.demo.data.local.entity.NotificationEntity
import com.tekinflow.demo.data.local.entity.PendingSyncEntity
import com.tekinflow.demo.data.local.entity.PurchaseRequestEntity
import com.tekinflow.demo.data.local.entity.UserEntity

@Database(
    entities = [
        PurchaseRequestEntity::class,
        NotificationEntity::class,
        UserEntity::class,
        PendingSyncEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SatinalmaDatabase : RoomDatabase() {
    abstract fun requestDao(): PurchaseRequestDao
    abstract fun notificationDao(): NotificationDao
    abstract fun userDao(): UserDao
    abstract fun pendingSyncDao(): PendingSyncDao
}
