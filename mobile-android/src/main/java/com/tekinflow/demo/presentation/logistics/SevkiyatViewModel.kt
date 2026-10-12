package com.tekinflow.demo.presentation.logistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.repository.TalepDeposu
import com.tekinflow.demo.domain.usecase.TalepleriGetirUseCase
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class LogisticsUiState(
    val requests: List<PurchaseRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

data class LogisticsFormState(
    val request: PurchaseRequest? = null,
    val selectedDriver: User? = null,
    val licensePlate: String = "",
    val spotDriverName: String = "",
    val spotDriverIdentityNumber: String = "",
    val spotDriverCompanyName: String = "",
    val spotDriverPhone: String = "",
    val recipientOptions: List<String> = emptyList(),
    val isSaving: Boolean = false
)

@HiltViewModel
class SevkiyatViewModel @Inject constructor(
    private val getPurchaseRequestsUseCase: TalepleriGetirUseCase,
    private val repository: TalepDeposu,
    private val userSession: KullaniciOturumu,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogisticsUiState())
    val uiState: StateFlow<LogisticsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(LogisticsFormState())
    val formState: StateFlow<LogisticsFormState> = _formState.asStateFlow()

    private val _drivers = MutableStateFlow(emptyList<User>())
    val drivers: StateFlow<List<User>> = _drivers.asStateFlow()

    private val _notifications = MutableStateFlow(emptyList<AppNotification>())
    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 1)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    init {
        loadRequests()
        loadDrivers()
        loadNotifications()
        loadRecipientOptions()
        syncManager.events
            .onEach { _events.emit(it) }
            .launchIn(viewModelScope)
    }

    private fun loadRecipientOptions() {
        viewModelScope.launch {
            val recipients = runCatching { repository.getDeliveredRecipients() }.getOrDefault(emptyList())
            if (recipients.isNotEmpty()) {
                _formState.update { it.copy(recipientOptions = recipients) }
            }
        }
    }

    private fun loadRequests() {
        getPurchaseRequestsUseCase()
            .onEach { requests ->
                _uiState.update {
                    it.copy(
                        requests = requests.sortedByDescending { request -> request.updatedAt },
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun loadDrivers() {
        repository.getDrivers()
            .onEach { _drivers.value = it.sortedBy { driver -> driver.fullName } }
            .launchIn(viewModelScope)
    }

    private fun loadNotifications() {
        val userId = userSession.getUser()?.id ?: return
        repository.getNotifications(userId)
            .onEach { _notifications.value = it }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            syncManager.performSync(force = true)
            delay(250)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun getCurrentUser(): User? = userSession.getUser()

    fun getNotifications(): List<AppNotification> = _notifications.value

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { repository.markNotificationRead(notificationId) }
    }

    fun requestIdForNotification(notification: AppNotification): String? {
        notification.requestId?.let { return it }
        val requestNumber = notification.relatedRequestNumber ?: return null
        return _uiState.value.requests.firstOrNull { it.requestNumber == requestNumber }?.id
    }

    fun loadRequest(requestId: String) {
        viewModelScope.launch {
            val request = repository.getRequestById(requestId)
            val selectedDriver = request?.assignedDriverId?.let { assignedId ->
                _drivers.value.firstOrNull { driver -> driver.id == assignedId }
            }
            _formState.value = LogisticsFormState(
                request = request,
                selectedDriver = selectedDriver,
                licensePlate = request?.licensePlate.orEmpty(),
                spotDriverName = request?.spotDriverName.orEmpty(),
                spotDriverIdentityNumber = request?.spotDriverIdentityNumber.orEmpty(),
                spotDriverCompanyName = request?.spotDriverCompanyName.orEmpty(),
                spotDriverPhone = request?.spotDriverPhone.orEmpty(),
                recipientOptions = _formState.value.recipientOptions
            )
        }
    }

    fun onDriverSelected(driver: User) {
        _formState.update {
            it.copy(
                selectedDriver = driver,
                licensePlate = driver.licensePlate.orEmpty().normalizePlate()
            )
        }
    }

    fun onSpotVehicleSelected() {
        _formState.update {
            it.copy(
                selectedDriver = null,
                licensePlate = "",
                spotDriverName = "",
                spotDriverIdentityNumber = "",
                spotDriverCompanyName = "",
                spotDriverPhone = ""
            )
        }
    }

    fun onLicensePlateChange(value: String) {
        _formState.update { it.copy(licensePlate = value.normalizePlate()) }
    }

    fun onSpotDriverNameChange(value: String) {
        _formState.update { it.copy(spotDriverName = value) }
    }

    fun onSpotDriverIdentityNumberChange(value: String) {
        _formState.update { it.copy(spotDriverIdentityNumber = value.filter(Char::isDigit).take(11)) }
    }

    fun onSpotDriverCompanyNameChange(value: String) {
        _formState.update { it.copy(spotDriverCompanyName = value) }
    }

    fun onSpotDriverPhoneChange(value: String) {
        _formState.update { it.copy(spotDriverPhone = value) }
    }

    fun assignDriver() {
        val form = _formState.value
        val requestId = form.request?.id ?: return
        val driver = form.selectedDriver
        val plate = form.licensePlate.ifBlank { driver?.licensePlate.orEmpty() }.normalizePlate()
        val spotCompanyName = form.spotDriverCompanyName.trim()

        if (driver == null && spotCompanyName.length < MIN_SPOT_COMPANY_LENGTH) {
            _events.tryEmit(AppMessage("Spot araç için firma adı en az 2 karakter olmalı", AppMessageType.ERROR))
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            val result = if (driver != null) {
                repository.assignDriver(requestId, driver.id, plate)
            } else {
                repository.assignSpotDriver(
                    requestId = requestId,
                    driverName = form.spotDriverName.trim(),
                    licensePlate = plate,
                    identityNumber = form.spotDriverIdentityNumber,
                    companyName = spotCompanyName,
                    phone = form.spotDriverPhone.trim()
                )
            }
            result.fold(
                onSuccess = { request ->
                    _formState.update {
                        it.copy(
                            request = request,
                            licensePlate = request.licensePlate,
                            spotDriverName = request.spotDriverName.orEmpty(),
                            spotDriverIdentityNumber = request.spotDriverIdentityNumber.orEmpty(),
                            spotDriverCompanyName = request.spotDriverCompanyName.orEmpty(),
                            spotDriverPhone = request.spotDriverPhone.orEmpty(),
                            isSaving = false
                        )
                    }
                    _events.emit(AppMessage("Şoför ataması kaydedildi", AppMessageType.SUCCESS))
                },
                onFailure = { error ->
                    _formState.update { it.copy(isSaving = false) }
                    _events.emit(
                        AppMessage(
                            error.message ?: "Şoför ataması yapılamadı",
                            AppMessageType.ERROR
                        )
                    )
                }
            )
        }
    }

    fun unassignDriver() {
        val requestId = _formState.value.request?.id ?: return
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            repository.unassignDriver(requestId).fold(
                onSuccess = { request ->
                    _formState.update {
                        it.copy(
                            request = request,
                            selectedDriver = null,
                            licensePlate = "",
                            spotDriverName = "",
                            spotDriverIdentityNumber = "",
                            spotDriverCompanyName = "",
                            spotDriverPhone = "",
                            isSaving = false
                        )
                    }
                    _events.emit(AppMessage("Atama kaldırıldı", AppMessageType.SUCCESS))
                },
                onFailure = { error ->
                    _formState.update { it.copy(isSaving = false) }
                    _events.emit(
                        AppMessage(
                            error.message ?: "Atama kaldırılamadı",
                            AppMessageType.ERROR
                        )
                    )
                }
            )
        }
    }

    fun markDelivered(deliveredTo: String? = null) {
        val request = _formState.value.request ?: return
        if (request.materialStatus != MaterialStatus.ON_THE_WAY) {
            _events.tryEmit(AppMessage("Bu talep yalnızca yoldayken tamamlanabilir", AppMessageType.ERROR))
            return
        }
        val recipient = deliveredTo?.trim()?.ifBlank { null }
        if (recipient == null) {
            _events.tryEmit(AppMessage("Teslim alan kişiyi giriniz", AppMessageType.ERROR))
            return
        }
        val requestId = request.id
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                repository.updateStatus(requestId, com.tekinflow.demo.domain.model.MaterialStatus.DELIVERED, recipient)
                val updated = repository.getRequestById(requestId)
                _formState.update {
                    val mergedRecipients = if (it.recipientOptions.none { name -> name.equals(recipient, ignoreCase = true) }) {
                        (it.recipientOptions + recipient).sortedBy { name -> name.lowercase(java.util.Locale.ROOT) }
                    } else {
                        it.recipientOptions
                    }
                    it.copy(request = updated, recipientOptions = mergedRecipients, isSaving = false)
                }
                _events.emit(AppMessage("Talep tamamlandı", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(AppMessage(exception.message ?: "Durum güncellenemedi", AppMessageType.ERROR))
            }
        }
    }

    fun markOnTheWay() {
        val request = _formState.value.request ?: return
        if (request.materialStatus !in setOf(MaterialStatus.IN_PROGRESS, MaterialStatus.PURCHASED) &&
            !canReopenDelivered(request)
        ) {
            _events.tryEmit(AppMessage("Bu talep yolda durumuna alınamaz", AppMessageType.ERROR))
            return
        }
        val requestId = request.id
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                repository.updateStatus(requestId, MaterialStatus.ON_THE_WAY)
                val updated = repository.getRequestById(requestId)
                _formState.update { it.copy(request = updated, isSaving = false) }
                _events.emit(AppMessage("Talep yolda olarak güncellendi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(AppMessage(exception.message ?: "Durum güncellenemedi", AppMessageType.ERROR))
            }
        }
    }

    fun markInProgress() {
        val request = _formState.value.request ?: return
        if (!canReopenDelivered(request)) {
            _events.tryEmit(AppMessage("Bu talep devama alınamaz", AppMessageType.ERROR))
            return
        }
        val requestId = request.id
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                repository.updateStatus(requestId, MaterialStatus.IN_PROGRESS)
                val updated = repository.getRequestById(requestId)
                _formState.update { it.copy(request = updated, isSaving = false) }
                _events.emit(AppMessage("Talep devam ediyor olarak güncellendi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(AppMessage(exception.message ?: "Durum güncellenemedi", AppMessageType.ERROR))
            }
        }
    }

    fun submitObjection(reason: String) {
        val requestId = _formState.value.request?.id ?: return
        val currentUserId = userSession.getUser()?.id ?: return
        if (reason.isBlank()) {
            _events.tryEmit(AppMessage("İtiraz nedeni girin", AppMessageType.ERROR))
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                repository.submitObjection(requestId, currentUserId, reason)
                val updated = repository.getRequestById(requestId)
                _formState.update { it.copy(request = updated, isSaving = false) }
                _events.emit(AppMessage("İtiraz iletildi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(AppMessage(exception.message ?: "İtiraz iletilemedi", AppMessageType.ERROR))
            }
        }
    }

    private fun canReopenDelivered(request: PurchaseRequest): Boolean {
        val currentUser = userSession.getUser() ?: return false
        val hasOverrideRole = currentUser.isSuperAdmin || currentUser.role == UserRole.LOGISTICS_ADMIN
        return request.materialStatus == MaterialStatus.DELIVERED &&
            !request.hasObjection &&
            hasOverrideRole
    }

    private fun String.normalizePlate(): String = trim().uppercase(Locale.ROOT)

    private companion object {
        const val MIN_SPOT_COMPANY_LENGTH = 2
    }
}
