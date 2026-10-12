package com.tekinflow.demo.di
import com.squareup.moshi.Moshi
import com.tekinflow.demo.data.demo.LocalDemoApi
import com.tekinflow.demo.data.remote.api.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun provideMoshi(): Moshi = Moshi.Builder().build()
    @Provides fun authApi(api: LocalDemoApi): AuthApi = api
    @Provides fun requestApi(api: LocalDemoApi): RequestApi = api
    @Provides fun userApi(api: LocalDemoApi): UserApi = api
    @Provides fun notificationApi(api: LocalDemoApi): NotificationApi = api
}
