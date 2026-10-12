package com.tekinflow.demo.data.remote.interceptor

import com.tekinflow.demo.domain.model.KullaniciOturumu
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val userSession: KullaniciOturumu
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }

        val accessToken = userSession.getAccessToken()
        if (accessToken.isNullOrBlank()) {
            return chain.proceed(request)
        }

        val authorizedRequest = request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()
        return chain.proceed(authorizedRequest)
    }
}
