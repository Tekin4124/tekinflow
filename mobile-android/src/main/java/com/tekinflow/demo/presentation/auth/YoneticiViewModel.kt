package com.tekinflow.demo.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.data.repository.UserManagementRepository
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.components.AppMessage
import com.tekinflow.demo.presentation.components.AppMessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val username: String = "",
    val fullName: String = "",
    val password: String = "",
    val selectedRole: UserRole = UserRole.PURCHASING,
    val licensePlate: String = "",
    val users: List<User> = emptyList(),
    val isSaving: Boolean = false,
    val editingUserId: String? = null,
    val isEditorOpen: Boolean = false
)

@HiltViewModel
class YoneticiViewModel @Inject constructor(
    private val userManagementRepository: UserManagementRepository,
    private val userSession: KullaniciOturumu
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 1)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            userManagementRepository.users.collectLatest { users ->
                _uiState.update { current -> current.copy(users = users) }
            }
        }
        viewModelScope.launch {
            delay(350)
            val currentUser = userSession.getUser()
            if (currentUser?.role != UserRole.ADMIN ||
                !currentUser.isSuperAdmin ||
                userSession.getAccessToken().isNullOrBlank()
            ) {
                return@launch
            }
            runCatching { userManagementRepository.refreshUsers() }
                .onFailure { error ->
                    _events.emit(
                        AppMessage(
                            error.message ?: "Kullanıcılar yüklenemedi",
                            AppMessageType.ERROR
                        )
                    )
                }
        }
    }

    fun openCreateSheet() {
        if (!isCurrentSuperAdmin()) {
            _events.tryEmit(AppMessage("Bu işlem için süper admin yetkisi gerekli", AppMessageType.ERROR))
            return
        }
        _uiState.update {
            AdminUiState(
                users = it.users,
                isEditorOpen = true
            )
        }
    }

    fun openEditSheet(user: User) {
        if (!canEditUser(user)) {
            _events.tryEmit(AppMessage("Bu kullanıcıyı düzenleme yetkiniz yok", AppMessageType.ERROR))
            return
        }

        _uiState.update {
            it.copy(
                username = user.username,
                fullName = user.fullName,
                password = "",
                selectedRole = user.role.takeUnless { role -> role == UserRole.ADMIN } ?: UserRole.PURCHASING_ADMIN,
                licensePlate = user.licensePlate.orEmpty(),
                editingUserId = user.id,
                isEditorOpen = true
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                username = "",
                fullName = "",
                password = "",
                selectedRole = UserRole.PURCHASING,
                licensePlate = "",
                editingUserId = null,
                isEditorOpen = false,
                isSaving = false
            )
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onFullNameChange(value: String) = _uiState.update { it.copy(fullName = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onRoleSelected(value: UserRole) {
        if (value == UserRole.ADMIN) return
        _uiState.update {
            it.copy(
                selectedRole = value,
                licensePlate = if (value == UserRole.DRIVER) it.licensePlate else ""
            )
        }
    }

    fun onLicensePlateChange(value: String) = _uiState.update { it.copy(licensePlate = value) }

    fun saveUser() {
        val state = _uiState.value
        if (!isCurrentSuperAdmin() || state.selectedRole == UserRole.ADMIN) {
            _events.tryEmit(AppMessage("Bu rol atanamaz", AppMessageType.ERROR))
            return
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            runCatching {
                if (state.editingUserId == null) {
                    userManagementRepository.createUser(
                        username = state.username,
                        fullName = state.fullName,
                        password = state.password,
                        role = state.selectedRole,
                        licensePlate = state.licensePlate
                    )
                } else {
                    userManagementRepository.updateUser(
                        userId = state.editingUserId,
                        username = state.username,
                        fullName = state.fullName,
                        password = state.password.takeIf { it.isNotBlank() },
                        role = state.selectedRole,
                        licensePlate = state.licensePlate
                    )
                }
            }.onSuccess {
                closeEditor()
                _events.emit(
                    AppMessage(
                        if (state.editingUserId == null) "Kullanıcı eklendi" else "Kullanıcı güncellendi",
                        AppMessageType.SUCCESS
                    )
                )
            }.onFailure { error ->
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(
                    AppMessage(
                        error.message ?: "İşlem tamamlanamadı",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    fun deleteUser(userId: String) {
        val target = _uiState.value.users.firstOrNull { it.id == userId }
        if (target == null) {
            _events.tryEmit(AppMessage("Kullanıcı bulunamadı", AppMessageType.ERROR))
            return
        }

        if (!canDeleteUser(target)) {
            _events.tryEmit(AppMessage("Bu kullanıcıyı silme yetkiniz yok", AppMessageType.ERROR))
            return
        }

        viewModelScope.launch {
            runCatching {
                userManagementRepository.deleteUser(userId)
            }.onSuccess {
                _events.emit(AppMessage("Kullanıcı silindi", AppMessageType.SUCCESS))
            }.onFailure { error ->
                _events.emit(
                    AppMessage(
                        error.message ?: "Kullanıcı silinemedi",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    fun activateUser(userId: String) {
        val target = _uiState.value.users.firstOrNull { it.id == userId }
        if (target == null) {
            _events.tryEmit(AppMessage("Kullanıcı bulunamadı", AppMessageType.ERROR))
            return
        }
        if (!canDeleteUser(target)) {
            _events.tryEmit(AppMessage("Bu işlem için süper admin yetkisi gerekli", AppMessageType.ERROR))
            return
        }

        viewModelScope.launch {
            runCatching {
                userManagementRepository.activateUser(userId)
            }.onSuccess {
                _events.emit(AppMessage("Kullanıcı aktif edildi", AppMessageType.SUCCESS))
            }.onFailure { error ->
                _events.emit(AppMessage(error.message ?: "Kullanıcı aktif edilemedi", AppMessageType.ERROR))
            }
        }
    }

    fun deleteUserPermanently(userId: String) {
        val target = _uiState.value.users.firstOrNull { it.id == userId }
        if (target == null) {
            _events.tryEmit(AppMessage("Kullanıcı bulunamadı", AppMessageType.ERROR))
            return
        }
        if (!canDeleteUser(target)) {
            _events.tryEmit(AppMessage("Bu kullanıcıyı silme yetkiniz yok", AppMessageType.ERROR))
            return
        }

        viewModelScope.launch {
            runCatching {
                userManagementRepository.deleteUserPermanently(userId)
            }.onSuccess {
                _events.emit(AppMessage("Kullanıcı kalıcı silindi", AppMessageType.SUCCESS))
            }.onFailure { error ->
                _events.emit(AppMessage(error.message ?: "Kullanıcı kalıcı silinemedi", AppMessageType.ERROR))
            }
        }
    }

    fun getCurrentUser(): User? = userSession.getUser()

    private fun isCurrentSuperAdmin(): Boolean {
        val current = userSession.getUser() ?: return false
        return current.role == UserRole.ADMIN && current.isSuperAdmin
    }

    private fun canEditUser(target: User): Boolean {
        val current = userSession.getUser() ?: return false
        if (current.role != UserRole.ADMIN || !current.isSuperAdmin) return false
        if (target.isSuperAdmin || target.role == UserRole.ADMIN) return false
        return true
    }

    private fun canDeleteUser(target: User): Boolean {
        val current = userSession.getUser() ?: return false
        if (target.id == current.id) return false
        if (current.role != UserRole.ADMIN || !current.isSuperAdmin || target.isSuperAdmin) return false
        return true
    }
}
