package com.tekinflow.demo.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.data.repository.UserManagementRepository
import com.tekinflow.demo.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordRepeat: String = "",
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val userManagementRepository: UserManagementRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onCurrentPasswordChange(value: String) {
        _uiState.update { it.copy(currentPassword = value, errorMessage = null) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onNewPasswordRepeatChange(value: String) {
        _uiState.update { it.copy(newPasswordRepeat = value, errorMessage = null) }
    }

    fun reset() {
        _uiState.value = ChangePasswordUiState()
    }

    fun changePassword() {
        val state = _uiState.value
        val validationMessage = validate(state)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                userManagementRepository.changeOwnPassword(
                    currentPassword = state.currentPassword,
                    newPassword = state.newPassword
                )
            }.onSuccess {
                sessionManager.onSessionExpired("Şifreniz değiştirildi. Lütfen tekrar giriş yapın.")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "Şifre değiştirilemedi"
                    )
                }
            }
        }
    }

    private fun validate(state: ChangePasswordUiState): String? {
        val currentPassword = state.currentPassword.trim()
        val newPassword = state.newPassword.trim()
        val repeat = state.newPasswordRepeat.trim()
        if (currentPassword.isEmpty() || newPassword.isEmpty() || repeat.isEmpty()) {
            return "Tüm alanları doldurun"
        }
        if (newPassword != repeat) {
            return "Yeni şifreler eşleşmiyor"
        }
        if (currentPassword == newPassword) {
            return "Yeni şifre mevcut şifreden farklı olmalı"
        }
        if (newPassword.length < 10) {
            return "Şifre en az 10 karakter olmalı"
        }
        val hasUppercase = newPassword.any { it.isUpperCase() }
        val hasLowercase = newPassword.any { it.isLowerCase() }
        val hasDigit = newPassword.any { it.isDigit() }
        val hasSpecial = newPassword.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSpecial) {
            return "Şifre büyük harf, küçük harf, rakam ve özel karakter içermeli"
        }
        return null
    }
}
