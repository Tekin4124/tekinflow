package com.tekinflow.demo.presentation.logistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tekinflow.demo.presentation.components.AppSearchBar
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.EmptyStateView
import com.tekinflow.demo.presentation.components.RequestFilterBar
import com.tekinflow.demo.presentation.components.GradientAvatar
import com.tekinflow.demo.presentation.components.RequestCard
import com.tekinflow.demo.presentation.components.ShimmerRequestList
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.BadgeShape
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogisticsScreen(
    onRequestClick: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: SevkiyatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val filterAll = stringResource(R.string.filter_all)
    val filterCompleted = stringResource(R.string.filter_completed)
    val filterObjected = stringResource(R.string.filter_objected)
    val filterProgress = stringResource(R.string.filter_progress)
    val filterWaiting = stringResource(R.string.filter_waiting)
    val filterSpot = stringResource(R.string.filter_spot)
    val filterFleetDriver = stringResource(R.string.filter_fleet_driver)
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedFilter by remember(filterWaiting) { mutableStateOf(filterWaiting) }
    var selectedAssignmentFilter by remember(filterAll) { mutableStateOf(filterAll) }
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

    val filteredRequests = remember(uiState.requests, drivers, debouncedQuery, selectedFilter, selectedAssignmentFilter) {
        uiState.requests.filter { request ->
            val assignedDriver = drivers.firstOrNull { it.id == request.assignedDriverId }
            val queryMatches = if (debouncedQuery.isBlank()) {
                true
            } else {
                listOf(
                    request.requestNumber,
                    request.itemToPurchase,
                    request.items.joinToString(" ") { it.itemName },
                    request.companyName,
                    request.licensePlate,
                    request.spotDriverName.orEmpty(),
                    request.spotDriverCompanyName.orEmpty(),
                    assignedDriver?.fullName.orEmpty()
                ).joinToString(" ")
                    .lowercase()
                    .contains(debouncedQuery.trim().lowercase())
            }

            val isActive = request.materialStatus != MaterialStatus.DELIVERED &&
                request.materialStatus != MaterialStatus.CANCELLED
            val filterMatches = when (selectedFilter) {
                filterWaiting -> isActive && !request.hasDriverAssignment
                filterProgress -> isActive && request.hasDriverAssignment
                filterCompleted -> request.materialStatus == MaterialStatus.DELIVERED
                filterObjected -> request.hasObjection
                else -> true
            }
            val assignmentMatches = when (selectedAssignmentFilter) {
                filterFleetDriver -> request.hasFleetDriverAssignment
                filterSpot -> request.hasSpotDriverAssignment
                else -> true
            }

            queryMatches && filterMatches && assignmentMatches
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.logistics_title),
                currentUser = viewModel.getCurrentUser(),
                notifications = viewModel.getNotifications(),
                onLogout = onLogout,
                onNotificationRead = viewModel::markNotificationRead,
                onNotificationClick = { notification ->
                    viewModel.requestIdForNotification(notification)?.let(onRequestClick)
                }
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
                                    placeholder = stringResource(R.string.logistics_search_hint)
                                )
                                RequestFilterBar(
                                    primaryOptions = listOf(filterWaiting, filterProgress, filterCompleted, filterObjected, filterAll),
                                    primarySelected = selectedFilter, onPrimarySelect = { selectedFilter = it },
                                    secondaryLabel = "Atama", secondaryOptions = listOf(filterAll, filterFleetDriver, filterSpot),
                                    secondarySelected = selectedAssignmentFilter, onSecondarySelect = { selectedAssignmentFilter = it }
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
                                    icon = Icons.Default.LocalShipping,
                                    title = stringResource(R.string.logistics_empty_title),
                                    description = stringResource(R.string.logistics_empty_description),
                                    modifier = Modifier.widthIn(max = REQUEST_LIST_MAX_WIDTH)
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
                                    footerContent = {
                                        AssignmentFooter(
                                            request = request,
                                            drivers = drivers
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val REQUEST_LIST_MAX_WIDTH = 1100.dp
private const val TABLET_WIDTH_DP = 560
private val TABLET_LIST_HORIZONTAL_PADDING = 24.dp

@Composable
private fun AssignmentFooter(
    request: PurchaseRequest,
    drivers: List<User>
) {
    val driver = drivers.firstOrNull { it.id == request.assignedDriverId }
    when {
        driver != null -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
            ) {
                GradientAvatar(initials = driver.initials(), size = 30)
                Text(
                    text = driver.fullName,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        request.hasSpotDriverAssignment -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
            ) {
                GradientAvatar(initials = "SP", size = 30)
                Column {
                    Text(
                        text = request.spotDriverName?.takeIf { it.isNotBlank() }
                            ?: request.spotDriverCompanyName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.logistics_spot_vehicle_option),
                        style = MaterialTheme.typography.labelMedium
                    )
                    request.licensePlate.takeIf { it.isNotBlank() }?.let { plate ->
                        Text(
                            text = plate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        else -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = BadgeShape
            ) {
                Text(
                    text = stringResource(R.string.logistics_unassigned_chip),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogisticsFooterPreview() {
    SatinalmaTheme {
        AssignmentFooter(
            request = PurchaseRequest(
                requestNumber = "TLP-2026-001",
                itemToPurchase = "Çelik",
                companyName = "TekinFlow"
            ),
            drivers = emptyList()
        )
    }
}
