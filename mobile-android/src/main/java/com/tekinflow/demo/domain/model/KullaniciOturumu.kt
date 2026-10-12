package com.tekinflow.demo.domain.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KullaniciOturumu @Inject constructor(
    @ApplicationContext context: Context
) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _currentUser = MutableStateFlow<User?>(loadPersistedUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(loadPersistedDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _accessToken = MutableStateFlow(loadPersistedString(KEY_ACCESS_TOKEN))
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow(loadPersistedString(KEY_REFRESH_TOKEN))
    val refreshToken: StateFlow<String?> = _refreshToken.asStateFlow()

    private val _tokenExpiresAt = MutableStateFlow(loadPersistedLong(KEY_TOKEN_EXPIRES_AT))
    val tokenExpiresAt: StateFlow<Long> = _tokenExpiresAt.asStateFlow()

    fun setUser(user: User) {
        val sessionStart = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_ROLE, user.role.name)
            .putString(KEY_LICENSE, user.licensePlate)
            .putBoolean(KEY_IS_SUPER_ADMIN, user.isSuperAdmin)
            .putLong(KEY_SESSION_START, sessionStart)
            .commit()
        _currentUser.value = user
    }

    fun clearUser() {
        _currentUser.value = null
        prefs.edit()
            .remove(KEY_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_LICENSE)
            .remove(KEY_IS_SUPER_ADMIN)
            .remove(KEY_SESSION_START)
            .apply()
    }

    fun setDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit()
            .putBoolean(KEY_DARK_THEME, enabled)
            .apply()
    }

    fun setTokens(
        accessToken: String,
        refreshToken: String,
        expiresIn: Long
    ) {
        val resolvedExpiry = decodeJwtExpiry(accessToken)
            ?: (System.currentTimeMillis() + expiresIn * 1_000L)

        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
        _tokenExpiresAt.value = resolvedExpiry

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRES_AT, resolvedExpiry)
            .commit()
    }

    fun getUser(): User? {
        if (expireSessionIfNeeded()) return null
        return _currentUser.value
    }

    fun getAccessToken(): String? {
        if (expireSessionIfNeeded()) return null
        return _accessToken.value?.takeIf { it.isNotBlank() }
    }

    fun getRefreshToken(): String? {
        if (expireSessionIfNeeded()) return null
        return _refreshToken.value?.takeIf { it.isNotBlank() }
    }

    fun isTokenExpired(): Boolean {
        if (expireSessionIfNeeded()) return true
        val expiry = _tokenExpiresAt.value
        if (expiry <= 0L) return true
        return System.currentTimeMillis() >= (expiry - TOKEN_EXPIRY_LEEWAY_MS)
    }

    fun clearSession() {
        clearUser()
        _accessToken.value = null
        _refreshToken.value = null
        _tokenExpiresAt.value = 0L
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }

    private fun loadPersistedUser(): User? {
        val sessionStart = prefs.getLong(KEY_SESSION_START, 0L)
        if (sessionStart <= 0L || System.currentTimeMillis() - sessionStart > SESSION_TTL_MS) {
            clearPersistedSession()
            return null
        }

        val id = prefs.getString(KEY_ID, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val storedName = prefs.getString(KEY_FULL_NAME, null) ?: return null
        val fullName = if (com.tekinflow.demo.BuildConfig.DEMO_MODE) {
            when (storedName) {
                "Demo Satın Alma" -> "Satın Alma Kullanıcısı"
                "Demo Sevkiyat" -> "Sevkiyat Kullanıcısı"
                "Demo Şoför" -> "Şoför Kullanıcısı"
                "Demo Yönetici" -> "Yönetici"
                else -> storedName
            }
        } else storedName
        val roleName = prefs.getString(KEY_ROLE, null) ?: return null
        val role = runCatching { UserRole.valueOf(roleName) }.getOrNull() ?: return null
        val license = prefs.getString(KEY_LICENSE, null)
        val isSuperAdmin = prefs.getBoolean(KEY_IS_SUPER_ADMIN, false)

        return User(
            id = id,
            username = username,
            fullName = fullName,
            role = role,
            licensePlate = license,
            isSuperAdmin = isSuperAdmin
        )
    }

    private fun loadPersistedDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, true)
    }

    private fun loadPersistedString(key: String): String? {
        return prefs.getString(key, null)
    }

    private fun loadPersistedLong(key: String): Long {
        return prefs.getLong(key, 0L)
    }

    private fun decodeJwtExpiry(token: String): Long? {
        return runCatching {
            val payload = token.split(".").getOrNull(1) ?: return null
            val decoded = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val expSeconds = JSONObject(String(decoded)).optLong("exp")
            if (expSeconds > 0) expSeconds * 1_000L else null
        }.getOrNull()
    }

    private fun expireSessionIfNeeded(): Boolean {
        _currentUser.value ?: return false
        val sessionStart = prefs.getLong(KEY_SESSION_START, 0L)
        if (sessionStart <= 0L && _currentUser.value != null) {
            prefs.edit()
                .putLong(KEY_SESSION_START, System.currentTimeMillis())
                .commit()
            return false
        }
        if (sessionStart > 0L && System.currentTimeMillis() - sessionStart <= SESSION_TTL_MS) {
            return false
        }

        clearSession()
        return true
    }

    private fun clearPersistedSession() {
        prefs.edit()
            .remove(KEY_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_LICENSE)
            .remove(KEY_IS_SUPER_ADMIN)
            .remove(KEY_SESSION_START)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "satinalma_secure_session"
        const val KEY_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_FULL_NAME = "full_name"
        const val KEY_ROLE = "role"
        const val KEY_LICENSE = "license_plate"
        const val KEY_IS_SUPER_ADMIN = "is_super_admin"
        const val KEY_SESSION_START = "session_start"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        const val TOKEN_EXPIRY_LEEWAY_MS = 30_000L
        const val SESSION_TTL_MS = 28_800_000L
    }
}
