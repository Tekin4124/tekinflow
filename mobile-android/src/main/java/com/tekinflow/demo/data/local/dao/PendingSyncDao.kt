package com.tekinflow.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tekinflow.demo.data.local.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync WHERE failed = 0 ORDER BY createdAt ASC")
    fun observePendingSyncs(): Flow<List<PendingSyncEntity>>

    @Query("SELECT * FROM pending_sync WHERE failed = 0 ORDER BY createdAt ASC")
    suspend fun getPendingSyncs(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync WHERE failed = 1 ORDER BY createdAt ASC")
    suspend fun getFailedSyncs(): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM pending_sync")
    suspend fun clearAll()

    @Query(
        "UPDATE pending_sync SET retryCount = :retryCount, lastError = :lastError WHERE id = :id"
    )
    suspend fun updateRetry(id: String, retryCount: Int, lastError: String?)

    @Query(
        "UPDATE pending_sync SET retryCount = :retryCount, lastError = :lastError, failed = 1 WHERE id = :id"
    )
    suspend fun markFailed(id: String, retryCount: Int, lastError: String?)
}
