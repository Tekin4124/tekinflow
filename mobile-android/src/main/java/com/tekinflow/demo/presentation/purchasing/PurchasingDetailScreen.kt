package com.tekinflow.demo.presentation.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.PurchaseRequestItem
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.presentation.components.AppButton
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTextField
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.ButtonVariant
import com.tekinflow.demo.presentation.components.GradientButton
import com.tekinflow.demo.presentation.components.SecureScreen
import com.tekinflow.demo.presentation.components.SectionHeader
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.util.Constants
import com.tekinflow.demo.util.toFormattedDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasingDetailScreen(
    requestId: String,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onNotificationRequestClick: (String) -> Unit = {},
    viewModel: SatinAlmaViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isNewRequest = requestId == Constants.NEW_REQUEST_ID
    var showDatePicker by remember { mutableStateOf(false) }
    SecureScreen()

    LaunchedEffect(requestId) {
        viewModel.loadRequestForEdit(requestId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showAppMessage(message)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = if (isNewRequest) {
                    stringResource(R.string.purchasing_create_title)
                } else {
                    stringResource(R.string.purchasing_edit_title)
                },
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
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                GradientButton(
                    text = if (isNewRequest) {
                        stringResource(R.string.save)
                    } else {
                        stringResource(R.string.update)
                    },
                    onClick = {
                        viewModel.saveRequest(requestId) {
                            onNavigateBack()
                        }
                    },
                    isLoading = formState.isSaving,
                    enabled = isNewRequest || formState.canEdit,
                    modifier = Modifier
                        .padding(AppSpacing.s4)
                        .navigationBarsPadding()
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s6)
        ) {
            item {
                DetailSection(title = stringResource(R.string.section_request_info)) {
                    AppTextField(
                        label = stringResource(R.string.field_request_number),
                        value = formState.requestNumber,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = Icons.Default.Inventory2
                    )
                    if (!isNewRequest && !formState.canEdit) {
                        Surface(
                            shape = CardShape,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.purchasing_read_only_notice,
                                    formState.purchasingOwnerName.ifBlank {
                                        stringResource(R.string.unknown_user)
                                    }
                                ),
                                modifier = Modifier.padding(AppSpacing.s3),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    RequestItemsEditor(
                        items = formState.items,
                        enabled = formState.canEdit,
                        onNameChange = viewModel::onItemNameChange,
                        onQuantityChange = viewModel::onItemQuantityChange,
                        onUnitChange = viewModel::onItemUnitChange,
                        onPackageCountChange = viewModel::onItemPackageCountChange,
                        onDimensionsNoteChange = viewModel::onItemDimensionsNoteChange,
                        onAdd = viewModel::addItemRow,
                        onRemove = viewModel::removeItemRow
                    )
                }
            }

            item {
                DetailSection(title = stringResource(R.string.section_company_info)) {
                    AppTextField(
                        label = stringResource(R.string.field_company_name),
                        value = formState.companyName,
                        onValueChange = viewModel::onCompanyNameChange,
                        enabled = formState.canEdit,
                        leadingIcon = Icons.Default.Business,
                        isError = formState.companyError != null,
                        errorMessage = formState.companyError
                    )
                    AppTextField(
                        label = stringResource(R.string.field_company_address),
                        value = formState.companyAddress,
                        onValueChange = viewModel::onCompanyAddressChange,
                        enabled = formState.canEdit,
                        leadingIcon = Icons.Default.Business,
                        singleLine = false,
                        maxLines = 2
                    )
                    AppTextField(
                        label = stringResource(R.string.field_contact_number),
                        value = formState.contactNumber,
                        onValueChange = viewModel::onContactNumberChange,
                        enabled = formState.canEdit,
                        leadingIcon = Icons.Default.Phone,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        )
                    )
                    AppTextField(
                        label = stringResource(R.string.field_contact_person),
                        value = formState.contactPersonName,
                        onValueChange = viewModel::onContactPersonNameChange,
                        enabled = formState.canEdit,
                        leadingIcon = Icons.Default.Person
                    )
                }
            }

            item {
                DetailSection(title = stringResource(R.string.section_planning)) {
                    Text(
                        text = stringResource(R.string.field_urgency_level),
                        style = MaterialTheme.typography.labelLarge
                    )
                    ChoicePicker("Öncelik", formState.urgencyLevel, UrgencyLevel.entries.toList(), formState.canEdit,
                        { it.displayName }, viewModel::onUrgencyLevelChange)
                    AppTextField(
                        label = stringResource(R.string.field_purchase_date),
                        value = formState.purchaseDate,
                        onValueChange = {},
                        readOnly = true,
                        enabled = formState.canEdit,
                        placeholder = stringResource(R.string.date_placeholder),
                        leadingIcon = Icons.Default.CalendarMonth,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        },
                        onFieldClick = if (formState.canEdit) {
                            { showDatePicker = true }
                        } else {
                            null
                        }
                    )
                }
            }

            item {
                DetailSection(title = stringResource(R.string.section_additional_info)) {
                    AppTextField(
                        label = stringResource(R.string.field_notes),
                        value = formState.notes,
                        onValueChange = viewModel::onNotesChange,
                        enabled = formState.canEdit,
                        leadingIcon = Icons.Default.NoteAlt,
                        singleLine = false,
                        maxLines = 3
                    )
                    if (!isNewRequest) {
                        AppTextField(
                            label = stringResource(R.string.field_material_status),
                            value = formState.materialStatus.displayName,
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = Icons.Default.LocalShipping
                        )
                        formState.requestDate?.let { requestDate ->
                            AppTextField(
                                label = stringResource(R.string.field_request_date),
                                value = requestDate.toFormattedDateTime(),
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = Icons.Default.CalendarMonth
                            )
                        }
                        formState.actualPurchaseDate?.let { actualDate ->
                            AppTextField(
                                label = stringResource(R.string.field_actual_purchase_date),
                                value = actualDate.toFormattedDateTime(),
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = Icons.Default.CalendarMonth
                            )
                        }
                        if (formState.deliveredTo.isNotBlank()) {
                            AppTextField(
                                label = stringResource(R.string.field_delivered_to),
                                value = formState.deliveredTo,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = Icons.Default.Person
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PurchaseDatePicker(
            currentValue = formState.purchaseDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.onPurchaseDateChange(it)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun RequestItemsEditor(
    items: List<PurchaseRequestItem>,
    enabled: Boolean,
    onNameChange: (Int, String) -> Unit,
    onQuantityChange: (Int, String) -> Unit,
    onUnitChange: (Int, MaterialUnit) -> Unit,
    onPackageCountChange: (Int, String) -> Unit,
    onDimensionsNoteChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)) {
        Text(
            text = stringResource(R.string.section_request_items),
            style = MaterialTheme.typography.labelLarge
        )
        items.forEachIndexed { index, item ->
            Surface(
                shape = CardShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(AppSpacing.s3),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                ) {
                    AppTextField(
                        label = stringResource(R.string.field_item_name),
                        value = item.itemName,
                        onValueChange = { onNameChange(index, it) },
                        enabled = enabled,
                        leadingIcon = Icons.Default.Inventory2
                    )
                    AppTextField(
                        label = stringResource(R.string.field_item_quantity),
                        value = item.quantity,
                        onValueChange = { onQuantityChange(index, it) },
                        enabled = enabled,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                    UnitPicker(item.unit, enabled) { onUnitChange(index, it) }
                    AppTextField(
                        label = stringResource(R.string.field_package_count),
                        value = item.packageCount.takeIf { it > 0 }?.toString().orEmpty(),
                        onValueChange = { onPackageCountChange(index, it) },
                        enabled = enabled,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                    AppTextField(
                        label = stringResource(R.string.field_dimensions_note),
                        value = item.dimensionsNote,
                        onValueChange = { onDimensionsNoteChange(index, it) },
                        enabled = enabled
                    )
                    if (items.size > 1) {
                        AppButton(
                            text = stringResource(R.string.remove_item_row),
                            onClick = { onRemove(index) },
                            enabled = enabled,
                            variant = ButtonVariant.SECONDARY
                        )
                    }
                }
            }
        }
        AppButton(
            text = stringResource(R.string.add_item_row),
            onClick = onAdd,
            enabled = enabled,
            variant = ButtonVariant.SECONDARY
        )
    }
}

@Composable
private fun DetailSection(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDatePicker(
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR")) }
    val selectedMillis = remember(currentValue) {
        runCatching { formatter.parse(currentValue)?.time }.getOrNull()
    }
    val today = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis >= today
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onConfirm(formatter.format(Date(it)))
                    } ?: onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.date_select_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Preview(showBackground = true)
@Composable
private fun PurchasingDetailPreview() {
    SatinalmaTheme {
        DetailSection(title = "Talep Bilgileri") {
            Text(text = "Önizleme")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPicker(unit: MaterialUnit, enabled: Boolean, onSelect: (MaterialUnit) -> Unit) {
    ChoicePicker("Birim", unit, MaterialUnit.entries.toList(), enabled, { it.displayName }, onSelect)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoicePicker(label: String, selected: T, options: List<T>, enabled: Boolean, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(value = labelOf(selected), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor())
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
