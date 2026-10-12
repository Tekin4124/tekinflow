package com.tekinflow.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tekinflow.demo.data.local.entity.PurchaseRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseRequestDao {

    @Query("SELECT * FROM purchase_requests ORDER BY updatedAt DESC")
    fun observeAllRequests(): Flow<List<PurchaseRequestEntity>>

    @Query("SELECT * FROM purchase_requests ORDER BY updatedAt DESC")
    suspend fun getAllRequestsNow(): List<PurchaseRequestEntity>

    @Query("SELECT * FROM purchase_requests WHERE id = :requestId LIMIT 1")
    suspend fun getRequestById(requestId: String): PurchaseRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: PurchaseRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requests: List<PurchaseRequestEntity>)

    @Query("DELETE FROM purchase_requests")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(requests: List<PurchaseRequestEntity>) {
        if (getAllRequestsNow().sortedBy { it.id } == requests.sortedBy { it.id }) return
        clearAll()
        if (requests.isNotEmpty()) {
            upsertAll(requests)
        }
    }
}
