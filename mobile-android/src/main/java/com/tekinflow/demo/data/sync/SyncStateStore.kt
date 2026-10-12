package com.tekinflow.demo.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class SyncUiState(
    val lastSyncAt: Long = 0L,
    val isOffline: Boolean = false,
    val isSyncing: Boolean = false,
    val failedSyncCount: Int = 0
)

@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        SyncUiState(
            lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        )
    )
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    fun updateLastSync(timestamp: Long) {
        _state.update {
            it.copy(
                lastSyncAt = timestamp,
                isOffline = false,
                isSyncing = false
            )
        }
        prefs.edit().putLong(KEY_LAST_SYNC_AT, timestamp).apply()
    }

    fun setOffline(isOffline: Boolean) {
        _state.update { it.copy(isOffline = isOffline) }
    }

    fun setSyncing(isSyncing: Boolean) {
        _state.update { it.copy(isSyncing = isSyncing) }
    }

    fun setFailedSyncCount(count: Int) {
        _state.update { it.copy(failedSyncCount = count.coerceAtLeast(0)) }
    }

    fun isNotificationBaselineReady(userId: String): Boolean {
        return prefs.getString(KEY_NOTIFICATION_BASELINE_USER_ID, null) == userId
    }

    fun markNotificationBaselineReady(userId: String) {
        prefs.edit()
            .putString(KEY_NOTIFICATION_BASELINE_USER_ID, userId)
            .apply()
    }

    fun reset() {
        prefs.edit()
            .remove(KEY_LAST_SYNC_AT)
            .remove(KEY_NOTIFICATION_BASELINE_USER_ID)
            .apply()
        _state.value = SyncUiState()
    }

    private companion object {
        const val PREFS_NAME = "satinalma_sync_state"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
        const val KEY_NOTIFICATION_BASELINE_USER_ID = "notification_baseline_user_id"
    }
}
