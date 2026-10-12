package com.tekinflow.demo.presentation.logistics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.components.AppMessageType
import com.tekinflow.demo.presentation.components.AppButton
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTextField
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.ButtonVariant
import com.tekinflow.demo.presentation.components.InfoRow
import com.tekinflow.demo.presentation.components.SecureScreen
import com.tekinflow.demo.presentation.components.SectionHeader
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.util.orDefault
import com.tekinflow.demo.util.toFormattedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogisticsDetailScreen(
    requestId: String,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNotificationRequestClick: (String) -> Unit = {},
    viewModel: SevkiyatViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showObjectionDialog by remember { mutableStateOf(false) }
    var showDeliveredConfirm by remember { mutableStateOf(false) }
    var objectionReason by remember { mutableStateOf("") }
    var deliveredToInput by remember { mutableStateOf("") }
    var recipientExpanded by remember { mutableStateOf(false) }
    SecureScreen()

    LaunchedEffect(requestId) {
        viewModel.loadRequest(requestId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            if (message.type == AppMessageType.SUCCESS) {
                onNavigateBack()
            } else {
                snackbarHostState.showAppMessage(message)
            }
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.logistics_detail_title),
                currentUser = viewModel.getCurrentUser(),
                notifications = viewModel.getNotifications(),
                onLogout = onLogout,
                onNotificationRead = viewModel::markNotificationRead,
                onNotificationClick = { notification ->
                    viewModel.requestIdForNotification(notification)?.let(onNotificationRequestClick)
                },
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        formState.request?.let { request ->
            LogisticsDetailContent(
                request = request,
                currentUser = viewModel.getCurrentUser(),
                drivers = drivers,
                selectedDriver = formState.selectedDriver,
                plate = formState.licensePlate,
                spotDriverName = formState.spotDriverName,
                spotDriverIdentityNumber = formState.spotDriverIdentityNumber,
                spotDriverCompanyName = formState.spotDriverCompanyName,
                spotDriverPhone = formState.spotDriverPhone,
                isSaving = formState.isSaving,
                onDriverSelected = viewModel::onDriverSelected,
                onPlateChange = viewModel::onLicensePlateChange,
                onSpotDriverNameChange = viewModel::onSpotDriverNameChange,
                onSpotDriverIdentityNumberChange = viewModel::onSpotDriverIdentityNumberChange,
                onSpotDriverCompanyNameChange = viewModel::onSpotDriverCompanyNameChange,
                onSpotDriverPhoneChange = viewModel::onSpotDriverPhoneChange,
                onSpotVehicleSelected = viewModel::onSpotVehicleSelected,
                onAssign = viewModel::assignDriver,
                onUnassign = viewModel::unassignDriver,
                onMarkInProgress = viewModel::markInProgress,
                onMarkOnTheWay = viewModel::markOnTheWay,
                onMarkDelivered = {
                    deliveredToInput = request.deliveredTo.orEmpty()
                    recipientExpanded = false
                    showDeliveredConfirm = true
                },
                onOpenObjection = { showObjectionDialog = true },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    if (showObjectionDialog) {
        AlertDialog(
            onDismissRequest = { showObjectionDialog = false; objectionReason = "" },
            title = { Text(text = stringResource(R.string.driver_object)) },
            text = {
                AppTextField(
                    label = stringResource(R.string.field_objection_reason),
                    value = objectionReason,
                    onValueChange = { objectionReason = it },
                    singleLine = false,
                    maxLines = 4
                )
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.driver_object),
                    onClick = {
                        viewModel.submitObjection(objectionReason)
                        showObjectionDialog = false
                        objectionReason = ""
                    },
                    variant = ButtonVariant.WARNING
                )
            },
            dismissButton = {
                TextButton(onClick = { showObjectionDialog = false; objectionReason = "" }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeliveredConfirm) {
        val recipientOptions = formState.recipientOptions
        AlertDialog(
            onDismissRequest = { showDeliveredConfirm = false },
            title = { Text(text = stringResource(R.string.confirm_delivered_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)) {
                    Text(text = stringResource(R.string.confirm_delivered_message))
                    val suggestions = recipientOptions.filter {
                        deliveredToInput.isBlank() || it.contains(deliveredToInput.trim(), ignoreCase = true)
                    }
                    ExposedDropdownMenuBox(
                        expanded = recipientExpanded && suggestions.isNotEmpty(),
                        onExpandedChange = { recipientExpanded = it }
                    ) {
                        AppTextField(
                            label = stringResource(R.string.field_delivered_to),
                            value = deliveredToInput,
                            onValueChange = {
                                deliveredToInput = it
                                recipientExpanded = true
                            },
                            leadingIcon = Icons.Default.Badge,
                            modifier = Modifier.menuAnchor()
                        )
                        if (suggestions.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = recipientExpanded,
                                onDismissRequest = { recipientExpanded = false }
                            ) {
                                suggestions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(text = option) },
                                        onClick = {
                                            deliveredToInput = option
                                            recipientExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                AppButton(
                    text = stringResource(R.string.confirm),
                    enabled = deliveredToInput.trim().length >= 2,
                    onClick = {
                        showDeliveredConfirm = false
                        viewModel.markDelivered(deliveredToInput.trim())
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeliveredConfirm = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogisticsDetailContent(
    request: PurchaseRequest,
    currentUser: User?,
    drivers: List<User>,
    selectedDriver: User?,
    plate: String,
    spotDriverName: String,
    spotDriverIdentityNumber: String,
    spotDriverCompanyName: String,
    spotDriverPhone: String,
    isSaving: Boolean,
    onDriverSelected: (User) -> Unit,
    onPlateChange: (String) -> Unit,
    onSpotDriverNameChange: (String) -> Unit,
    onSpotDriverIdentityNumberChange: (String) -> Unit,
    onSpotDriverCompanyNameChange: (String) -> Unit,
    onSpotDriverPhoneChange: (String) -> Unit,
    onSpotVehicleSelected: () -> Unit,
    onAssign: () -> Unit,
    onUnassign: () -> Unit,
    onMarkInProgress: () -> Unit,
    onMarkOnTheWay: () -> Unit,
    onMarkDelivered: () -> Unit,
    onOpenObjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var driverExpanded by remember { mutableStateOf(false) }
    val hasOverrideRole = currentUser?.isSuperAdmin == true ||
        currentUser?.role == UserRole.LOGISTICS_ADMIN
    val canReopenDelivered = request.materialStatus == MaterialStatus.DELIVERED &&
        !request.hasObjection &&
        hasOverrideRole
    val isClosed = request.materialStatus == MaterialStatus.DELIVERED ||
        request.materialStatus == MaterialStatus.CANCELLED
    val assignmentEnabled = !isClosed || canReopenDelivered

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s4)
    ) {
        item {
            DetailCard(title = stringResource(R.string.section_actions)) {
                ExposedDropdownMenuBox(
                    expanded = driverExpanded,
                    onExpandedChange = { if (assignmentEnabled) driverExpanded = it }
                ) {
                    AppTextField(
                        label = stringResource(R.string.field_driver),
                        value = selectedDriver?.let { "${it.fullName} - ${it.licensePlate.orEmpty()}" }
                            ?: request.assignedDriverId?.let { currentId ->
                                drivers.firstOrNull { it.id == currentId }?.let { "${it.fullName} - ${it.licensePlate.orEmpty()}" }
                            }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        enabled = assignmentEnabled,
                        leadingIcon = Icons.Default.LocalShipping,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = driverExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = driverExpanded,
                        onDismissRequest = { driverExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.logistics_spot_vehicle_option)) },
                            onClick = {
                                onSpotVehicleSelected()
                                driverExpanded = false
                            }
                        )
                        drivers.forEach { driver ->
                            DropdownMenuItem(
                                text = { Text("${driver.fullName} - ${driver.licensePlate.orEmpty()}") },
                                onClick = {
                                    onDriverSelected(driver)
                                    driverExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.logistics_spot_vehicle_hint),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AppTextField(
                    label = "${stringResource(R.string.field_spot_driver_company)} *",
                    value = spotDriverCompanyName,
                    onValueChange = onSpotDriverCompanyNameChange,
                    enabled = assignmentEnabled,
                    leadingIcon = Icons.Default.Business
                )

                AppTextField(
                    label = "${stringResource(R.string.field_spot_driver_name)} (opsiyonel)",
                    value = spotDriverName,
                    onValueChange = onSpotDriverNameChange,
                    enabled = assignmentEnabled,
                    leadingIcon = Icons.Default.Badge
                )

                AppTextField(
                    label = "${stringResource(R.string.field_spot_driver_identity)} (opsiyonel)",
                    value = spotDriverIdentityNumber,
                    onValueChange = onSpotDriverIdentityNumberChange,
                    enabled = assignmentEnabled,
                    leadingIcon = Icons.Default.Badge
                )

                AppTextField(
                    label = "${stringResource(R.string.field_spot_driver_phone)} (opsiyonel)",
                    value = spotDriverPhone,
                    onValueChange = onSpotDriverPhoneChange,
                    enabled = assignmentEnabled,
                    leadingIcon = Icons.Default.Call
                )

                AppTextField(
                    label = "${stringResource(R.string.field_license_plate)} (opsiyonel)",
                    value = plate,
                    onValueChange = onPlateChange,
                    enabled = assignmentEnabled,
                    leadingIcon = Icons.Default.DirectionsCar
                )

                AppButton(
                    text = if (request.assignedDriverId == null) {
                        stringResource(R.string.logistics_assign_driver)
                    } else {
                        stringResource(R.string.logistics_reassign_driver)
                    },
                    onClick = onAssign,
                    isLoading = isSaving,
                    enabled = assignmentEnabled
                )

                if (request.hasDriverAssignment) {
                    AppButton(
                        text = stringResource(R.string.logistics_remove_assignment),
                        onClick = onUnassign,
                        enabled = assignmentEnabled,
                        variant = ButtonVariant.SECONDARY
                    )
                }

                if (request.materialStatus == MaterialStatus.ON_THE_WAY) {
                    AppButton(
                        text = stringResource(R.string.logistics_mark_delivered),
                        onClick = onMarkDelivered,
                        isLoading = isSaving,
                        variant = ButtonVariant.SECONDARY
                    )
                }

                if (request.materialStatus == MaterialStatus.IN_PROGRESS || request.materialStatus == MaterialStatus.PURCHASED) {
                    AppButton(
                        text = stringResource(R.string.logistics_mark_on_the_way),
                        onClick = onMarkOnTheWay,
                        isLoading = isSaving,
                        variant = ButtonVariant.SECONDARY
                    )
                }

                if (canReopenDelivered) {
                    AppButton(
                        text = stringResource(R.string.logistics_reopen_in_progress),
                        onClick = onMarkInProgress,
                        isLoading = isSaving,
                        variant = ButtonVariant.SECONDARY
                    )
                    AppButton(
                        text = stringResource(R.string.logistics_mark_on_the_way),
                        onClick = onMarkOnTheWay,
                        isLoading = isSaving,
                        variant = ButtonVariant.SECONDARY
                    )
                }

                AppButton(
                    text = stringResource(R.string.driver_object),
                    onClick = onOpenObjection,
                    variant = ButtonVariant.WARNING
                )
            }
        }

        item {
            DetailCard(title = stringResource(R.string.section_request_info)) {
                InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_request_number), value = request.requestNumber)
                SectionHeader(title = stringResource(R.string.section_request_items), icon = Icons.Default.Inventory2)
                request.items.forEachIndexed { index, item ->
                    InfoRow(icon = Icons.Default.Inventory2, label = "Kalem ${index + 1}", value = item.displayText)
                }
                InfoRow(icon = Icons.Default.Business, label = stringResource(R.string.field_company_name), value = request.companyName)
                InfoRow(icon = Icons.Default.Business, label = stringResource(R.string.field_company_address), value = request.companyAddress.orDefault())
                InfoRow(icon = Icons.Default.Call, label = stringResource(R.string.field_contact_number), value = request.contactNumber.orDefault())
                request.contactPersonName?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_contact_person), value = it)
                }
                InfoRow(icon = Icons.Default.DirectionsCar, label = stringResource(R.string.field_license_plate), value = request.licensePlate.orDefault())
                request.spotDriverName?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_spot_driver_name), value = it)
                }
                request.spotDriverIdentityNumber?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_spot_driver_identity), value = it)
                }
                request.spotDriverCompanyName?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Business, label = stringResource(R.string.field_spot_driver_company), value = it)
                }
                request.spotDriverPhone?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Call, label = stringResource(R.string.field_spot_driver_phone), value = it)
                }
                InfoRow(icon = Icons.Default.CalendarMonth, label = stringResource(R.string.field_request_date), value = request.createdAt.toFormattedDateTime())
                InfoRow(icon = Icons.Default.CalendarMonth, label = stringResource(R.string.field_purchase_date), value = request.purchaseDate.orDefault())
                InfoRow(icon = Icons.Default.CalendarMonth, label = stringResource(R.string.field_actual_purchase_date), value = request.actualPurchaseDate?.toFormattedDateTime().orDefault())
                request.deliveredTo?.takeIf { it.isNotBlank() }?.let {
                    InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_delivered_to), value = it)
                }
                InfoRow(icon = Icons.Default.NoteAlt, label = stringResource(R.string.field_notes), value = request.notes.orDefault())
                InfoRow(icon = Icons.Default.LocalShipping, label = stringResource(R.string.field_material_status), value = request.materialStatus.displayName)
            }
        }

        if (request.hasObjection && !request.objectionReason.isNullOrBlank()) {
            item {
                Surface(
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.s4),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.logistics_objected_title),
                            icon = Icons.Default.WarningAmber
                        )
                        Text(
                            text = stringResource(
                                R.string.request_driver_objection,
                                request.objectionReason.orEmpty()
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            SectionHeader(title = title)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogisticsDetailPreview() {
    SatinalmaTheme {
        DetailCard(title = "Aksiyon Bölümü") {
            Text(text = "Önizleme")
        }
    }
}
