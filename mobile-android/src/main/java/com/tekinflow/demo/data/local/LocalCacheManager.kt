package com.tekinflow.demo.data.local

import com.tekinflow.demo.data.sync.SyncStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCacheManager @Inject constructor(
    private val database: SatinalmaDatabase,
    private val syncStateStore: SyncStateStore
) {

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        syncStateStore.reset()
    }
}
