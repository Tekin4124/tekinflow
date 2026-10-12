package com.tekinflow.demo.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        return runCatching {
            entryPoint.syncManager().performSync(force = true)
            SyncManager.scheduleBackgroundFetch(applicationContext)
            Result.success()
        }.getOrElse {
            SyncManager.scheduleBackgroundFetch(applicationContext)
            Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncManager(): SyncManager
}
