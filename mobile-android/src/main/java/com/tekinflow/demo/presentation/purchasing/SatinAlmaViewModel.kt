package com.tekinflow.demo.presentation.purchasing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.KullaniciOturumu
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.PurchaseRequestItem
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.domain.repository.TalepDeposu
import com.tekinflow.demo.domain.usecase.TalepOlusturUseCase
import com.tekinflow.demo.domain.usecase.TalepleriGetirUseCase
import com.tekinflow.demo.data.sync.SyncManager
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
import java.util.UUID
import javax.inject.Inject

data class PurchasingUiState(
    val requests: List<PurchaseRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false
)

data class PurchasingFormState(
    val requestNumber: String = "",
    val itemToPurchase: String = "",
    val items: List<PurchaseRequestItem> = listOf(PurchaseRequestItem()),
    val companyName: String = "",
    val companyAddress: String = "",
    val contactNumber: String = "",
    val contactPersonName: String = "",
    val productDimensions: String = "",
    val productWeight: String = "",
    val packageCount: String = "",
    val urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    val purchaseDate: String = "",
    val notes: String = "",
    val materialStatus: MaterialStatus = MaterialStatus.IN_PROGRESS,
    val purchasingOwnerName: String = "",
    val requestDate: Long? = null,
    val actualPurchaseDate: Long? = null,
    val deliveredTo: String = "",
    val canEdit: Boolean = true,
    val itemError: String? = null,
    val companyError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class SatinAlmaViewModel @Inject constructor(
    private val getPurchaseRequestsUseCase: TalepleriGetirUseCase,
    private val createPurchaseUseCase: TalepOlusturUseCase,
    private val repository: TalepDeposu,
    private val userSession: KullaniciOturumu,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchasingUiState())
    val uiState: StateFlow<PurchasingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(PurchasingFormState())
    val formState: StateFlow<PurchasingFormState> = _formState.asStateFlow()

    private val _notifications = MutableStateFlow(emptyList<AppNotification>())
    private val _events = MutableSharedFlow<AppMessage>(extraBufferCapacity = 1)
    val events: SharedFlow<AppMessage> = _events.asSharedFlow()

    init {
        loadRequests()
        loadNotifications()
        syncManager.events
            .onEach { _events.emit(it) }
            .launchIn(viewModelScope)
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

    private fun loadNotifications() {
        val userId = userSession.getUser()?.id ?: return
        repository.getNotifications(userId)
            .onEach { notifications -> _notifications.value = notifications }
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

    fun loadRequestForEdit(requestId: String) {
        viewModelScope.launch {
            if (requestId == "new") {
                val requestNumber = repository.generateRequestNumber()
                _formState.value = PurchasingFormState(requestNumber = requestNumber)
                return@launch
            }

            val request = repository.getRequestById(requestId)
            if (request == null) {
                _events.emit(AppMessage("Talep bulunamadı", AppMessageType.ERROR))
                return@launch
            }

            val currentUser = userSession.getUser()
            val canEdit = canEditRequest(request, currentUser)

            _formState.value = PurchasingFormState(
                requestNumber = request.requestNumber,
                itemToPurchase = request.itemToPurchase,
                items = request.items.takeIf { items -> items.any { it.itemName.isNotBlank() } } ?: run {
                    listOf(
                        PurchaseRequestItem(
                            itemName = request.itemToPurchase,
                            packageCount = request.packageCount,
                            dimensionsNote = request.productDimensions
                        )
                    )
                },
                companyName = request.companyName,
                companyAddress = request.companyAddress,
                contactNumber = request.contactNumber,
                contactPersonName = request.contactPersonName.orEmpty(),
                productDimensions = request.productDimensions,
                productWeight = request.productWeight,
                packageCount = request.packageCount.takeIf { it > 0 }?.toString().orEmpty(),
                urgencyLevel = request.urgencyLevel,
                purchaseDate = request.purchaseDate,
                notes = request.notes,
                materialStatus = request.materialStatus,
                purchasingOwnerName = request.assignedPurchasingStaffName.orEmpty(),
                requestDate = request.createdAt,
                actualPurchaseDate = request.actualPurchaseDate,
                deliveredTo = request.deliveredTo.orEmpty(),
                canEdit = canEdit
            )
        }
    }

    fun onItemToPurchaseChange(value: String) = _formState.update {
        val nextItems = it.items.ifEmpty { listOf(PurchaseRequestItem()) }
            .mapIndexed { index, item -> if (index == 0) item.copy(itemName = value) else item }
        it.copy(itemToPurchase = value, items = nextItems, itemError = null)
    }

    fun addItemRow() = _formState.update {
        if (it.items.size >= MAX_ITEM_ROWS) it else it.copy(items = it.items + PurchaseRequestItem(sortOrder = it.items.size))
    }

    fun removeItemRow(index: Int) = _formState.update {
        val nextItems = it.items
            .filterIndexed { rowIndex, _ -> rowIndex != index }
            .ifEmpty { listOf(PurchaseRequestItem()) }
            .mapIndexed { rowIndex, item -> item.copy(sortOrder = rowIndex) }
        it.copy(
            items = nextItems,
            itemToPurchase = nextItems.firstOrNull()?.itemName.orEmpty(),
            itemError = null
        )
    }

    fun onItemNameChange(index: Int, value: String) = updateItem(index) { it.copy(itemName = value) }
    fun onItemQuantityChange(index: Int, value: String) = updateItem(index) {
        it.copy(quantity = value.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(12))
    }
    fun onItemUnitChange(index: Int, value: MaterialUnit) = updateItem(index) { it.copy(unit = value) }
    fun onItemPackageCountChange(index: Int, value: String) = updateItem(index) {
        it.copy(packageCount = value.filter(Char::isDigit).take(5).toIntOrNull() ?: 0)
    }
    fun onItemDimensionsNoteChange(index: Int, value: String) = updateItem(index) { it.copy(dimensionsNote = value) }

    private fun updateItem(index: Int, transform: (PurchaseRequestItem) -> PurchaseRequestItem) {
        _formState.update { state ->
            val nextItems = state.items.ifEmpty { listOf(PurchaseRequestItem()) }
                .mapIndexed { rowIndex, item ->
                    if (rowIndex == index) transform(item).copy(sortOrder = rowIndex) else item.copy(sortOrder = rowIndex)
                }
            state.copy(
                items = nextItems,
                itemToPurchase = nextItems.firstOrNull()?.itemName.orEmpty(),
                itemError = null
            )
        }
    }

    fun onCompanyNameChange(value: String) = _formState.update {
        it.copy(companyName = value, companyError = null)
    }

    fun onCompanyAddressChange(value: String) = _formState.update { it.copy(companyAddress = value) }
    fun onContactNumberChange(value: String) = _formState.update { it.copy(contactNumber = value) }
    fun onContactPersonNameChange(value: String) = _formState.update { it.copy(contactPersonName = value) }
    fun onProductDimensionsChange(value: String) = _formState.update { it.copy(productDimensions = value) }
    fun onProductWeightChange(value: String) = _formState.update { it.copy(productWeight = value) }
    fun onPackageCountChange(value: String) = _formState.update {
        it.copy(packageCount = value.filter(Char::isDigit).take(5))
    }
    fun onUrgencyLevelChange(value: UrgencyLevel) = _formState.update { it.copy(urgencyLevel = value) }
    fun onPurchaseDateChange(value: String) = _formState.update { it.copy(purchaseDate = value) }
    fun onNotesChange(value: String) = _formState.update { it.copy(notes = value) }
    fun onMaterialStatusChange(value: MaterialStatus) = _formState.update { it.copy(materialStatus = value) }

    fun saveRequest(existingRequestId: String? = null, onSuccess: (() -> Unit)? = null) {
        val form = _formState.value
        if (existingRequestId != null && existingRequestId != "new" && !form.canEdit) {
            _events.tryEmit(
                AppMessage(
                    "Bu talebi sadece ilanı açan kişi veya satın alma yöneticisi düzenleyebilir",
                    AppMessageType.ERROR
                )
            )
            return
        }
        val validItems = form.items
            .filter { it.itemName.isNotBlank() }
            .take(MAX_ITEM_ROWS)
        val itemError = when {
            validItems.isEmpty() -> "En az bir kalem zorunludur"
            validItems.any { (it.quantity.replace(',', '.').toDoubleOrNull() ?: 0.0) <= 0.0 } -> "Kalem miktari sifirdan buyuk olmalidir"
            else -> null
        }
        val companyError = if (form.companyName.isBlank()) "Firma adı zorunludur" else null

        if (itemError != null || companyError != null) {
            _formState.update {
                it.copy(
                    itemError = itemError,
                    companyError = companyError
                )
            }
            return
        }

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }

            try {
                val existing = existingRequestId
                    ?.takeIf { it != "new" }
                    ?.let { repository.getRequestById(it) }
                val currentUserId = userSession.getUser()?.id.orEmpty()
                val firstItem = validItems.first()

                val request = PurchaseRequest(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    requestNumber = form.requestNumber,
                    itemToPurchase = firstItem.itemName.trim(),
                    items = validItems.mapIndexed { index, item -> item.copy(sortOrder = index) },
                    companyName = form.companyName.trim(),
                    companyAddress = form.companyAddress.trim(),
                    contactNumber = form.contactNumber.trim(),
                    contactPersonName = form.contactPersonName.trim().ifBlank { null },
                    productDimensions = form.productDimensions.trim(),
                    productWeight = form.productWeight.trim(),
                    packageCount = form.packageCount.trim().toIntOrNull() ?: 0,
                    urgencyLevel = form.urgencyLevel,
                    purchaseDate = form.purchaseDate,
                    notes = form.notes.trim(),
                    licensePlate = existing?.licensePlate.orEmpty(),
                    materialStatus = existing?.materialStatus ?: form.materialStatus,
                    assignedDriverId = existing?.assignedDriverId,
                    assignedPurchasingStaffId = existing?.assignedPurchasingStaffId ?: currentUserId,
                    assignedPurchasingStaffName = existing?.assignedPurchasingStaffName,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    cancellationReason = existing?.cancellationReason,
                    cancelledBy = existing?.cancelledBy,
                    objectionReason = existing?.objectionReason,
                    hasObjection = existing?.hasObjection ?: false,
                    objectionBy = existing?.objectionBy,
                    redirectedToRequestId = existing?.redirectedToRequestId,
                    isNew = existing?.isNew ?: true,
                    seenByUserIds = existing?.seenByUserIds ?: emptySet(),
                    actualPurchaseDate = existing?.actualPurchaseDate,
                    spotDriverName = existing?.spotDriverName,
                    spotDriverIdentityNumber = existing?.spotDriverIdentityNumber,
                    spotDriverCompanyName = existing?.spotDriverCompanyName,
                    spotDriverPhone = existing?.spotDriverPhone,
                    deliveredTo = existing?.deliveredTo
                )

                if (existing == null) {
                    createPurchaseUseCase(request)
                    _events.emit(AppMessage("Talep başarıyla oluşturuldu", AppMessageType.SUCCESS))
                } else {
                    repository.updateRequest(request)
                    _events.emit(AppMessage("Talep başarıyla güncellendi", AppMessageType.SUCCESS))
                }

                _formState.update { it.copy(isSaving = false) }
                onSuccess?.invoke()
            } catch (exception: Exception) {
                _formState.update { it.copy(isSaving = false) }
                _events.emit(
                    AppMessage(
                        exception.message ?: "Talep kaydedilemedi",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    fun cancelRequest(requestId: String, reason: String?) {
        val currentUserId = userSession.getUser()?.id ?: return
        viewModelScope.launch {
            try {
                repository.cancelRequest(requestId, reason, currentUserId)
                _events.emit(AppMessage("Talep iptal edildi", AppMessageType.SUCCESS))
            } catch (exception: Exception) {
                _events.emit(
                    AppMessage(
                        exception.message ?: "Talep iptal edilemedi",
                        AppMessageType.ERROR
                    )
                )
            }
        }
    }

    private fun canEditRequest(request: PurchaseRequest, currentUser: User?): Boolean {
        if (currentUser == null) return false
        if (currentUser.isSuperAdmin || currentUser.role == UserRole.PURCHASING_ADMIN) return true
        return currentUser.role == UserRole.PURCHASING &&
            request.assignedPurchasingStaffId == currentUser.id
    }

    private companion object {
        const val MAX_ITEM_ROWS = 50
    }
}
