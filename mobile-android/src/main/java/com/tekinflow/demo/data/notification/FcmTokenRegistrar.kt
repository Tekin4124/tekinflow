package com.tekinflow.demo.data.notification
import javax.inject.Inject
import javax.inject.Singleton
/** Local demo has no Firebase project and never registers a push token. */
@Singleton class FcmTokenRegistrar @Inject constructor() {
    fun register() = Unit
    fun uploadToken(token: String) = Unit
    fun clear() = Unit
}
