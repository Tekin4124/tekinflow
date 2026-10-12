package com.tekinflow.demo.presentation.purchasing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.components.AppButton
import com.tekinflow.demo.presentation.components.AppSearchBar
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTextField
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.ButtonVariant
import com.tekinflow.demo.presentation.components.EmptyStateView
import com.tekinflow.demo.presentation.components.RequestFilterBar
import com.tekinflow.demo.presentation.components.RequestCard
import com.tekinflow.demo.presentation.components.ShimmerRequestList
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.DialogShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.primaryGradientBrush
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasingScreen(
    onRequestClick: (String) -> Unit,
    onCreateNew: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SatinAlmaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val filterAll = stringResource(R.string.filter_all)
    val filterCompleted = stringResource(R.string.filter_completed)
    val filterOnTheWay = stringResource(R.string.filter_on_the_way)
    val filterUnassigned = stringResource(R.string.filter_unassigned)
    val filterObjected = stringResource(R.string.filter_objected)
    val filterLow = stringResource(R.string.filter_low)
    val filterNormal = stringResource(R.string.filter_normal)
    val filterHigh = stringResource(R.string.filter_high)
    val filterUrgent = stringResource(R.string.filter_urgent)
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var statusFilter by remember(filterAll) { mutableStateOf(filterAll) }
    var urgencyFilter by remember(filterAll) { mutableStateOf(filterAll) }
    var cancelTarget by remember { mutableStateOf<PurchaseRequest?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    val listHorizontalPadding = if (LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP) {
        TABLET_LIST_HORIZONTAL_PADDING
    } else {
        AppSpacing.s4
    }

    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showAppMessage(it) }
    }

    val filteredRequests = remember(uiState.requests, debouncedQuery, statusFilter, urgencyFilter) {
        uiState.requests.filter { request ->
            val queryMatches = if (debouncedQuery.isBlank()) {
                true
            } else {
                listOf(
                    request.requestNumber,
                    request.itemToPurchase,
                    request.items.joinToString(" ") { it.itemName },
                    request.companyName
                )
                    .joinToString(" ")
                    .lowercase()
                    .contains(debouncedQuery.trim().lowercase())
            }

            val statusMatches = when (statusFilter) {
                filterUnassigned -> !request.hasDriverAssignment
                filterOnTheWay -> request.hasDriverAssignment &&
                    request.materialStatus != MaterialStatus.DELIVERED &&
                    request.materialStatus != MaterialStatus.CANCELLED &&
                    !request.hasObjection
                filterCompleted -> request.materialStatus == MaterialStatus.DELIVERED
                filterObjected -> request.hasObjection
                else -> request.materialStatus != MaterialStatus.DELIVERED &&
                    request.materialStatus != MaterialStatus.CANCELLED
            }

            val urgencyMatches = when (urgencyFilter) {
                filterLow -> request.urgencyLevel == UrgencyLevel.LOW
                filterNormal -> request.urgencyLevel == UrgencyLevel.NORMAL
                filterHigh -> request.urgencyLevel == UrgencyLevel.HIGH
                filterUrgent -> request.urgencyLevel == UrgencyLevel.URGENT
                else -> true
            }

            queryMatches && statusMatches && urgencyMatches
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.purchasing_title),
                currentUser = viewModel.getCurrentUser(),
                notifications = viewModel.getNotifications(),
                onLogout = onLogout,
                onNotificationRead = viewModel::markNotificationRead,
                onNotificationClick = { notification ->
                    viewModel.requestIdForNotification(notification)?.let(onRequestClick)
                }
            )
        },
        floatingActionButton = {
            GradientFab(
                onClick = onCreateNew
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                ShimmerRequestList(modifier = Modifier.padding(AppSpacing.s4))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = listHorizontalPadding,
                        vertical = AppSpacing.s4
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = REQUEST_LIST_MAX_WIDTH)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                            ) {
                                AppSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    placeholder = stringResource(R.string.purchasing_search_hint)
                                )
                                RequestFilterBar(
                                    primaryOptions = listOf(filterAll, filterUnassigned, filterOnTheWay, filterCompleted, filterObjected),
                                    primarySelected = statusFilter, onPrimarySelect = { statusFilter = it },
                                    secondaryLabel = "Öncelik", secondaryOptions = listOf(filterAll, filterLow, filterNormal, filterHigh, filterUrgent),
                                    secondarySelected = urgencyFilter, onSecondarySelect = { urgencyFilter = it }
                                )
                            }
                        }
                    }

                    if (filteredRequests.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppSpacing.s7),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateView(
                                    icon = Icons.Default.Assignment,
                                    title = stringResource(R.string.purchasing_empty_title),
                                    description = stringResource(R.string.purchasing_empty_description),
                                    modifier = Modifier
                                        .widthIn(max = REQUEST_LIST_MAX_WIDTH)
                                        .padding(AppSpacing.s4)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(filteredRequests, key = { _, request -> request.id }) { _, request ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                RequestCard(
                                    request = request,
                                    onClick = { onRequestClick(request.id) },
                                    modifier = Modifier
                                        .widthIn(max = REQUEST_LIST_MAX_WIDTH)
                                        .fillMaxWidth(),
                                    trailingAction = if (request.materialStatus != MaterialStatus.CANCELLED && request.materialStatus != MaterialStatus.DELIVERED) {
                                        { RequestActions { cancelTarget = request } }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    cancelTarget?.let {
        CancelRequestDialog(
            reason = cancelReason,
            onReasonChange = { cancelReason = it },
            onDismiss = {
                cancelTarget = null
                cancelReason = ""
            },
            onConfirm = {
                viewModel.cancelRequest(it.id, cancelReason.ifBlank { null })
                cancelTarget = null
                cancelReason = ""
            }
        )
    }
}

private val REQUEST_LIST_MAX_WIDTH = 1100.dp
private const val TABLET_WIDTH_DP = 560
private val TABLET_LIST_HORIZONTAL_PADDING = 24.dp

@Composable
private fun GradientFab(
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(primaryGradientBrush(), CircleShape)
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.purchasing_new_request),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun CancelRequestDialog(
    reason: String,
    onReasonChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Talebi iptal et", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.cancel_request_confirm), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppTextField(label = stringResource(R.string.cancel_reason_optional), value = reason,
                    onValueChange = onReasonChange, singleLine = false, maxLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("İptal et", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PurchasingPreview() {
    SatinalmaTheme {
        GradientFab(onClick = {})
    }
}

@Composable
private fun RequestActions(onCancel: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "Talep işlemleri", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) {
            DropdownMenuItem(text = { Text("Talebi iptal et", color = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onCancel() })
        }
    }
}
