package com.tekinflow.demo.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.data.remote.api.AuthApi
import com.tekinflow.demo.data.remote.api.executeApi
import com.tekinflow.demo.data.remote.dto.LoginRequestDto
import com.tekinflow.demo.data.notification.FcmTokenRegistrar
import com.tekinflow.demo.data.remote.mapper.toDomain
import com.tekinflow.demo.data.session.SessionManager
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val twoFactorCode: String = "",
    val twoFactorRequired: Boolean = false,
    val twoFactorSetupRequired: Boolean = false,
    val twoFactorSecret: String? = null,
    val twoFactorOtpAuthUrl: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loggedInUser: User? = null,
    val failedAttempts: Int = 0,
    val lockoutSecondsRemaining: Int = 0
)

@HiltViewModel
class GirisViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val userSession: KullaniciOturumu,
    private val sessionManager: SessionManager,
    private val fcmTokenRegistrar: FcmTokenRegistrar
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var lockoutJob: Job? = null
    private var lockoutEndTime: Long = 0L

    init {
        sessionManager.consumePendingLoginMessage()?.let { message ->
            _uiState.update { it.copy(errorMessage = message) }
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.update {
            it.copy(
                username = value,
                twoFactorCode = "",
                twoFactorRequired = false,
                twoFactorSetupRequired = false,
                twoFactorSecret = null,
                twoFactorOtpAuthUrl = null,
                errorMessage = null
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                twoFactorCode = "",
                twoFactorRequired = false,
                twoFactorSetupRequired = false,
                twoFactorSecret = null,
                twoFactorOtpAuthUrl = null,
                errorMessage = null
            )
        }
    }

    fun onTwoFactorCodeChange(value: String) {
        _uiState.update {
            it.copy(
                twoFactorCode = value.filter(Char::isDigit).take(6),
                errorMessage = null
            )
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.lockoutSecondsRemaining > 0) return

        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Kullanıcı adı ve şifre boş bırakılamaz") }
            return
        }

        if ((state.twoFactorRequired || state.twoFactorSetupRequired) && state.twoFactorCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "2FA kodu gerekli") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            delay(350)

            runCatching {
                executeApi(
                    unauthorizedMessage = INVALID_CREDENTIALS_MESSAGE,
                    defaultMessage = "Giriş yapılamadı"
                ) {
                    authApi.login(
                        LoginRequestDto(
                            username = state.username.trim(),
                            password = state.password.trim(),
                            twoFactorCode = state.twoFactorCode.takeIf { state.twoFactorRequired || state.twoFactorSetupRequired },
                            twoFactorSecret = state.twoFactorSecret.takeIf { state.twoFactorSetupRequired }
                        )
                    )
                }
            }.onSuccess { response ->
                if (response.twoFactorSetupRequired) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            twoFactorRequired = false,
                            twoFactorSetupRequired = true,
                            twoFactorCode = "",
                            twoFactorSecret = response.twoFactorSecret,
                            twoFactorOtpAuthUrl = response.twoFactorOtpAuthUrl,
                            errorMessage = null
                        )
                    }
                    return@onSuccess
                }

                if (response.twoFactorRequired) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            twoFactorRequired = true,
                            twoFactorSetupRequired = false,
                            twoFactorCode = "",
                            twoFactorSecret = null,
                            twoFactorOtpAuthUrl = null,
                            errorMessage = null
                        )
                    }
                    return@onSuccess
                }

                val accessToken = response.accessToken
                val refreshToken = response.refreshToken
                val responseUser = response.user
                if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || responseUser == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Giriş tamamlanamadı"
                        )
                    }
                    return@onSuccess
                }

                val user = responseUser.toDomain()
                withContext(Dispatchers.IO) {
                userSession.setTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = SessionManager.ACCESS_TOKEN_TTL_SECONDS
                )
                userSession.setUser(user)
                }
                fcmTokenRegistrar.register()
                lockoutJob?.cancel()
                lockoutEndTime = 0L
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loggedInUser = user,
                        twoFactorCode = "",
                        twoFactorRequired = false,
                        twoFactorSetupRequired = false,
                        twoFactorSecret = null,
                        twoFactorOtpAuthUrl = null,
                        failedAttempts = 0,
                        lockoutSecondsRemaining = 0,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                val message = error.message ?: "Giriş yapılamadı"
                handleLoginFailure(
                    message = message,
                    countAsAttempt = message == INVALID_CREDENTIALS_MESSAGE
                )
            }
        }
    }

    private fun handleLoginFailure(message: String, countAsAttempt: Boolean) {
        if (!countAsAttempt) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = message
                )
            }
            return
        }

        val newFailedAttempts = _uiState.value.failedAttempts + 1
        if (newFailedAttempts >= MAX_ATTEMPTS) {
            lockoutEndTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Çok fazla başarısız deneme. Lütfen bekleyin.",
                    failedAttempts = newFailedAttempts,
                    lockoutSecondsRemaining = (LOCKOUT_DURATION_MS / 1_000L).toInt()
                )
            }
            startLockoutCountdown()
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = message,
                    failedAttempts = newFailedAttempts
                )
            }
        }
    }

    private fun startLockoutCountdown() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            while (true) {
                val remaining = ((lockoutEndTime - System.currentTimeMillis()) / 1_000L).toInt()
                if (remaining <= 0) {
                    _uiState.update {
                        it.copy(
                            lockoutSecondsRemaining = 0,
                            failedAttempts = 0,
                            errorMessage = null
                        )
                    }
                    break
                }
                _uiState.update { it.copy(lockoutSecondsRemaining = remaining) }
                delay(1_000L)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val LOCKOUT_DURATION_MS = 30_000L
        const val INVALID_CREDENTIALS_MESSAGE = "Kullanıcı adı veya şifre hatalı"
    }
}
