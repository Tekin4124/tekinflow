package com.tekinflow.demo.presentation.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.data.sync.SyncManager
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.repository.TalepDeposu
import com.tekinflow.demo.domain.usecase.DurumGuncelleUseCase
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

data class DriverUiState(
    val allRequests: List<PurchaseRequest> = emptyList(),
    val myRequests: List<PurchaseRequest> = emptyList(),
    val unassignedRequests: List<PurchaseRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

data class DriverFormState(
    val request: PurchaseRequest? = null,
    val selectedStatus: MaterialStatus = MaterialStatus.PENDING,
    val recipientOptions: List<String> = emptyList(),
    val isSaving: Boolean = false
)

@HiltViewModel
class SoforViewModel @Inject constructor(
    private val getPurchaseRequestsUseCase: TalepleriGetirUseCase,
    private val updateStatusUseCase: DurumGuncelleUseCase,
    private val repository: TalepDeposu,
    private val userSession: KullaniciOturumu,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverUiState())
    val uiState: StateFlow<DriverUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(DriverFormState())
    val formState: StateFlow<DriverFormState> = _formState.asStateFlow()

    private val _notifications = MutableStateFlow(emptyList<AppNotification>())
    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 1)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    private val currentDriverId: String
        get() = userSession.getUser()?.id.orEmpty()

    init {
        loadRequests()
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
                        allRequests = requests,
                        myRequests = requests.filter { request -> request.assignedDriverId == currentDriverId },
                        unassignedRequests = requests.filter { request ->
                            request.canBeTakenOverByDriver
                        },
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
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
        return _uiState.value.allRequests.firstOrNull { it.requestNumber == requestNumber }?.id
    }

    fun markAsSeen(requestId: String) {
        viewModelScope.launch {
            if (currentDriverId.isNotBlank()) {
                repository.markAsSeen(requestId, currentDriverId)
            }
        }
    }

    fun loadRequest(requestId: String) {
        viewModelScope.launch {
            val request = repository.getRequestById(requestId)
            val selectedStatus = request
                ?.let { nextStatusesFor(it.materialStatus).firstOrNull() ?: it.materialStatus }
                ?: MaterialStatus.PENDING
            _formState.value = DriverFormState(
                request = request,
                selectedStatus = selectedStatus
            )
            markAsSeen(requestId)
        }
    }

    fun onStatusSelected(status: MaterialStatus) {
        val request = _formState.value.request ?: return
        if (status !in nextStatusesFor(request.materialStatus)) {
            _events.tryEmit(AppMessage("Bu duruma geçiş yapılamaz", AppMessageType.ERROR))
            return
        }
        _formState.update { it.copy(selectedStatus = status) }
    }

    fun takeOverRequest(requestId: String? = null) {
        val targetRequestId = requestId ?: _formState.value.request?.id ?: return
        val driver = userSession.getUser() ?: return

        viewModelScope.launch {
            val targetRequest = repository.getRequestById(targetRequestId)
            if (targetRequest == null) {
                _events.emit(AppMessage("Talep bulunamadı", AppMessageType.ERROR))
                return@launch
            }
            if (!targetRequest.canBeTakenOverByDriver) {
                val message = if (targetRequest.hasSpotDriverAssignment) {
                    "Bu talep spot araca atanmış"
                } else {
                    "Bu talep başka bir şoföre atanmış"
                }
                _events.emit(AppMessage(message, AppMessageType.ERROR))
                return@launch
            }

            _formState.update { it.copy(isSaving = true) }
            repository.takeOverRequest(targetRequestId, driver.id, driver.licensePlate.orEmpty().normalizePlate()).fold(
                onSuccess = { request ->
                    if (_formState.value.request?.id == targetRequestId) {
                        _formState.update { it.copy(request = request, isSaving = false) }
                    } else {
                        _formState.update { it.copy(isSaving = false) }
                    }
                    _events.emit(AppMessage("Talep üzerinize alındı", AppMessageType.SUCCESS))
                },
                onFailure = { error ->
                    _formState.update { it.copy(isSaving = false) }
                    _events.emit(
                        AppMessage(
                            error.message ?: "Talep üzerinize alınamadı",
                            AppMessageType.ERROR
                        )
                    )
                }
            )
        }
    }

    fun updateStatus(deliveredTo: String? = null) {
        val request = _formState.value.request ?: return
        if (request.assignedDriverId != currentDriverId) {
            _events.tryEmit(AppMessage("Bu talebin durumunu değiştiremezsiniz", AppMessageType.ERROR))
            return
        }
        val targetStatus = _formState.value.selectedStatus
        if (targetStatus !in nextStatusesFor(request.materialStatus)) {
            _events.tryEmit(AppMessage("Bu duruma geçiş yapılamaz", AppMessageType.ERROR))
            return
        }
        val recipient = deliveredTo?.trim()?.ifBlank { null }
        if (targetStatus == MaterialStatus.DELIVERED && recipient == null) {
            _events.tryEmit(AppMessage("Teslim alan kişiyi giriniz", AppMessageType.ERROR))
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                updateStatusUseCase(request.id, targetStatus, recipient)
                val updated = repository.getRequestById(request.id)
                _formState.update {
                    val mergedRecipients = if (recipient != null && it.recipientOptions.none { name -> name.equals(recipient, ignoreCase = true) }) {
                        (it.recipientOptions + recipient).sortedBy { name -> name.lowercase(Locale.ROOT) }
                    } else {
                        it.recipientOptions
                    }
                    it.copy(
                        request = updated,
                        selectedStatus = updated
                            ?.let { request -> nextStatusesFor(request.materialStatus).firstOrNull() ?: request.materialStatus }
                            ?: targetStatus,
                        recipientOptions = mergedRecipients,
                        isSaving = false
                    )
                }
                _events.emit(AppMessage("Durum güncellendi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(
                    AppMessage(
                        exception.message ?: "Durum güncellenemedi",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    fun submitObjection(reason: String) {
        val requestId = _formState.value.request?.id ?: return

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }
            try {
                repository.submitObjection(requestId, currentDriverId, reason)
                val updated = repository.getRequestById(requestId)
                _formState.update { it.copy(request = updated, isSaving = false) }
                _events.emit(AppMessage("İtiraz iletildi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(
                    AppMessage(
                        exception.message ?: "İtiraz iletilemedi",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    private fun nextStatusesFor(status: MaterialStatus): List<MaterialStatus> {
        return when (status) {
            MaterialStatus.IN_PROGRESS,
            MaterialStatus.PURCHASED -> listOf(MaterialStatus.ON_THE_WAY)
            MaterialStatus.ON_THE_WAY -> listOf(MaterialStatus.DELIVERED)
            else -> emptyList()
        }
    }

    private fun String.normalizePlate(): String = trim().uppercase(Locale.ROOT)
}
