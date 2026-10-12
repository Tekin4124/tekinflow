/*
 * AppModule.kt
 * Hilt dependency injection modülü.
 * Repository bağlamaları ve uygulama genelinde singleton nesneler burada tanımlanır.
 */
package com.tekinflow.demo.di

import com.tekinflow.demo.data.repository.TalepDeposuImpl
import com.tekinflow.demo.domain.repository.TalepDeposu
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Uygulama geneli dependency injection modülü
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTalepDeposu(
        impl: TalepDeposuImpl
    ): TalepDeposu
}
