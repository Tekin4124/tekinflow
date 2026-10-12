package com.tekinflow.demo.data.remote.interceptor

import com.tekinflow.demo.data.remote.api.AuthApi
import com.tekinflow.demo.data.remote.dto.RefreshRequestDto
import com.tekinflow.demo.data.session.SessionManager
import com.tekinflow.demo.domain.model.KullaniciOturumu
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val authApi: AuthApi,
    private val userSession: KullaniciOturumu,
    private val sessionManager: SessionManager
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.contains("/auth/")) return null

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        synchronized(refreshLock) {
            val latestToken = userSession.getAccessToken()
            if (!latestToken.isNullOrBlank() && latestToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val refreshToken = userSession.getRefreshToken()
                ?: run {
                    sessionManager.onSessionExpired()
                    return null
                }

            val refreshResponse = runCatching {
                authApi.refreshBlocking(RefreshRequestDto(refreshToken)).execute()
            }.getOrNull() ?: return null

            if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                sessionManager.onSessionExpired()
                return null
            }

            if (!refreshResponse.isSuccessful) return null

            val tokenBody = refreshResponse.body() ?: return null

            userSession.setTokens(
                accessToken = tokenBody.accessToken,
                refreshToken = tokenBody.refreshToken,
                expiresIn = SessionManager.ACCESS_TOKEN_TTL_SECONDS
            )

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${tokenBody.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var result = 1
        while (current?.priorResponse != null) {
            result++
            current = current.priorResponse
        }
        return result
    }
}
