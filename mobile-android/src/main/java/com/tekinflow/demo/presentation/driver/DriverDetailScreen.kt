package com.tekinflow.demo.presentation.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
fun DriverDetailScreen(
    requestId: String,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNotificationRequestClick: (String) -> Unit = {},
    viewModel: SoforViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
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
                title = stringResource(R.string.driver_detail_title),
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
            DriverDetailContent(
                request = request,
                selectedStatus = formState.selectedStatus,
                currentUserId = viewModel.getCurrentUser()?.id.orEmpty(),
                isSaving = formState.isSaving,
                onStatusSelected = viewModel::onStatusSelected,
                onTakeOver = { viewModel.takeOverRequest(request.id) },
                onUpdateStatus = {
                    if (formState.selectedStatus == MaterialStatus.DELIVERED) {
                        deliveredToInput = request.deliveredTo.orEmpty()
                        recipientExpanded = false
                        showDeliveredConfirm = true
                    } else {
                        viewModel.updateStatus()
                    }
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
                        viewModel.updateStatus(deliveredToInput.trim())
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
private fun DriverDetailContent(
    request: PurchaseRequest,
    selectedStatus: MaterialStatus,
    currentUserId: String,
    isSaving: Boolean,
    onStatusSelected: (MaterialStatus) -> Unit,
    onTakeOver: () -> Unit,
    onUpdateStatus: () -> Unit,
    onOpenObjection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nextStatuses = remember(request.materialStatus) {
        when (request.materialStatus) {
            MaterialStatus.IN_PROGRESS -> listOf(MaterialStatus.ON_THE_WAY)
            MaterialStatus.PURCHASED -> listOf(MaterialStatus.ON_THE_WAY)
            MaterialStatus.ON_THE_WAY -> listOf(MaterialStatus.DELIVERED)
            else -> emptyList()
        }
    }
    var statusExpanded by remember { mutableStateOf(false) }
    val isAssigned = request.assignedDriverId == currentUserId
    val isUnassigned = request.canBeTakenOverByDriver

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s4)
    ) {
        item {
            DetailCard(title = stringResource(R.string.section_actions)) {
                if (isUnassigned) {
                    AppButton(
                        text = stringResource(R.string.driver_take_over),
                        onClick = onTakeOver,
                        isLoading = isSaving
                    )
                }

                if (isAssigned) {
                    if (nextStatuses.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it }
                        ) {
                            AppTextField(
                                label = stringResource(R.string.field_material_status),
                                value = selectedStatus.displayName,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = Icons.Default.LocalShipping,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                                },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                nextStatuses.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(text = status.displayName) },
                                        onClick = {
                                            onStatusSelected(status)
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        AppButton(
                            text = stringResource(R.string.driver_update_status),
                            onClick = onUpdateStatus,
                            isLoading = isSaving
                        )
                    } else {
                        ActionNotice(
                            title = request.materialStatus.displayName,
                            message = when (request.materialStatus) {
                                MaterialStatus.DELIVERED -> stringResource(R.string.driver_delivery_completed)
                                MaterialStatus.CANCELLED -> stringResource(R.string.driver_request_cancelled)
                                else -> stringResource(R.string.driver_no_action_available)
                            }
                        )
                    }

                    // Objections can be filed at every stage, including after
                    // the request is delivered or cancelled.
                    AppButton(
                        text = stringResource(R.string.driver_object),
                        onClick = onOpenObjection,
                        variant = ButtonVariant.WARNING
                    )
                }

                if (!isAssigned && !isUnassigned) {
                    Text(
                        text = if (request.hasSpotDriverAssignment) {
                            stringResource(R.string.driver_assigned_to_spot)
                        } else {
                            stringResource(R.string.driver_assigned_to_another)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            DetailCard(title = stringResource(R.string.section_request_info)) {
                InfoRow(icon = Icons.Default.Badge, label = stringResource(R.string.field_request_number), value = request.requestNumber)
                val detailItems = request.items.filter { it.displayText.isNotBlank() }
                if (detailItems.isNotEmpty()) {
                    detailItems.forEachIndexed { index, item ->
                        InfoRow(
                            icon = Icons.Default.Inventory2,
                            label = "Kalem ${index + 1}",
                            value = item.displayText
                        )
                    }
                } else if (request.itemToPurchase.isNotBlank()) {
                    InfoRow(icon = Icons.Default.Inventory2, label = stringResource(R.string.section_request_items), value = request.itemToPurchase)
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

@Composable
private fun ActionNotice(
    title: String,
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.s3),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s1)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DriverDetailPreview() {
    SatinalmaTheme {
        DetailCard(title = "Aksiyon Bölümü") {
            Text(text = "Önizleme")
        }
    }
}
