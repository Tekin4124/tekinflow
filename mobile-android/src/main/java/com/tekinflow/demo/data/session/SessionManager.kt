package com.tekinflow.demo.data.session

import com.tekinflow.demo.data.local.LocalCacheManager
import com.tekinflow.demo.data.notification.FcmTokenRegistrar
import com.tekinflow.demo.data.remote.api.AuthApi
import com.tekinflow.demo.data.remote.dto.LogoutRequestDto
import com.tekinflow.demo.domain.model.KullaniciOturumu
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val authApi: AuthApi,
    private val userSession: KullaniciOturumu,
    private val localCacheManager: LocalCacheManager,
    private val fcmTokenRegistrar: Lazy<FcmTokenRegistrar>
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sessionExpiredEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sessionExpiredEvents: SharedFlow<String> = _sessionExpiredEvents.asSharedFlow()

    @Volatile
    private var pendingLoginMessage: String? = null

    fun logout() {
        val refreshToken = userSession.getRefreshToken()
        fcmTokenRegistrar.get().clear()
        userSession.clearSession()
        scope.launch {
            localCacheManager.clearAll()
            refreshToken?.takeIf { it.isNotBlank() }?.let { token ->
                runCatching {
                    authApi.logout(LogoutRequestDto(token))
                }
            }
        }
    }

    fun onSessionExpired(message: String = "Oturum süresi doldu") {
        pendingLoginMessage = message
        scope.launch {
            userSession.clearSession()
            localCacheManager.clearAll()
            _sessionExpiredEvents.emit(message)
        }
    }

    fun consumePendingLoginMessage(): String? {
        val message = pendingLoginMessage
        pendingLoginMessage = null
        return message
    }

    companion object {
        const val ACCESS_TOKEN_TTL_SECONDS = 900L
    }
}
