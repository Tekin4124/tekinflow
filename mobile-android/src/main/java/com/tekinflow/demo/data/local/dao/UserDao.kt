package com.tekinflow.demo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tekinflow.demo.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    suspend fun getAllNow(): List<UserEntity>


    @Query("SELECT * FROM users WHERE role = 'DRIVER' AND isActive = 1 ORDER BY fullName ASC")
    fun observeDrivers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("DELETE FROM users")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(users: List<UserEntity>) {
        if (getAllNow().sortedBy { it.id } == users.sortedBy { it.id }) return
        clearAll()
        if (users.isNotEmpty()) {
            upsertAll(users)
        }
    }
}
