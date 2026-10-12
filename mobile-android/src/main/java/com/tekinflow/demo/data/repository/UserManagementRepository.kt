package com.tekinflow.demo.data.repository

import com.tekinflow.demo.data.remote.api.UserApi
import com.tekinflow.demo.data.remote.api.executeApi
import com.tekinflow.demo.data.remote.dto.ChangePasswordRequestDto
import com.tekinflow.demo.data.remote.dto.CreateUserRequestDto
import com.tekinflow.demo.data.remote.dto.UpdateUserRequestDto
import com.tekinflow.demo.data.remote.mapper.toDomain
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManagementRepository @Inject constructor(
    private val userApi: UserApi,
    private val userSession: KullaniciOturumu
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    init {
        scope.launch {
            userSession.currentUser.collectLatest { currentUser ->
                if (currentUser?.role == UserRole.ADMIN &&
                    currentUser.isSuperAdmin &&
                    !userSession.getAccessToken().isNullOrBlank()
                ) {
                    delay(300)
                    runCatching { refreshUsers() }
                } else {
                    _users.value = emptyList()
                }
            }
        }
    }

    suspend fun refreshUsers() {
        val response = executeApi { userApi.getUsers(authHeader()) }
        _users.value = response
            .asSequence()
            .map { it.toDomain() }
            .sortedWith(compareBy<User> { !it.isActive }.thenBy { it.fullName.lowercase() })
            .toList()
    }

    suspend fun changeOwnPassword(
        currentPassword: String,
        newPassword: String
    ) {
        executeApi(
            unauthorizedMessage = "Mevcut şifre hatalı veya oturum süresi doldu",
            defaultMessage = "Şifre değiştirilemedi"
        ) {
            userApi.changePassword(
                request = ChangePasswordRequestDto(
                    currentPassword = currentPassword.trim(),
                    newPassword = newPassword.trim()
                ),
                authorization = authHeader()
            )
        }
    }

    suspend fun createUser(
        username: String,
        fullName: String,
        password: String,
        role: UserRole,
        licensePlate: String?
    ): User {
        val created = executeApi(defaultMessage = "Kullanıcı eklenemedi") {
            userApi.createUser(
                request = CreateUserRequestDto(
                    username = username.trim(),
                    fullName = fullName.trim(),
                    password = password.trim(),
                    role = role,
                    licensePlate = licensePlate.normalizePlateOrNull()
                ),
                authorization = authHeader()
            )
        }.toDomain()
        refreshUsers()
        return created
    }

    suspend fun updateUser(
        userId: String,
        username: String,
        fullName: String,
        password: String?,
        role: UserRole,
        licensePlate: String?
    ): User {
        val updated = executeApi(defaultMessage = "Kullanıcı güncellenemedi") {
            userApi.updateUser(
                userId = userId,
                request = UpdateUserRequestDto(
                    username = username.trim(),
                    fullName = fullName.trim(),
                    password = password?.trim()?.ifBlank { null },
                    role = role,
                    licensePlate = licensePlate.normalizePlateOrNull()
                ),
                authorization = authHeader()
            )
        }.toDomain()
        refreshUsers()
        return updated
    }

    suspend fun deleteUser(userId: String) {
        executeApi(defaultMessage = "Kullanıcı silinemedi") {
            userApi.deleteUser(userId, authHeader())
        }
        refreshUsers()
    }

    suspend fun activateUser(userId: String): User {
        val activated = executeApi(defaultMessage = "Kullanıcı aktif edilemedi") {
            userApi.activateUser(userId, authHeader())
        }.toDomain()
        refreshUsers()
        return activated
    }

    suspend fun deleteUserPermanently(userId: String) {
        executeApi(defaultMessage = "Kullanıcı kalıcı silinemedi") {
            userApi.deleteUserPermanently(userId, authHeader())
        }
        refreshUsers()
    }

    private fun authHeader(): String? = userSession.getAccessToken()
        ?.let { token -> "Bearer $token" }

    private fun String?.normalizePlateOrNull(): String? = this
        ?.trim()
        ?.uppercase(Locale.ROOT)
        ?.ifBlank { null }
}
