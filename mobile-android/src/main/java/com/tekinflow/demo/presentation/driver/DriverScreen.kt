package com.tekinflow.demo.presentation.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.PurchaseRequest
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.components.AppButton
import com.tekinflow.demo.presentation.components.AppSearchBar
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.ButtonVariant
import com.tekinflow.demo.presentation.components.EmptyStateView
import com.tekinflow.demo.presentation.components.RequestCard
import com.tekinflow.demo.presentation.components.ShimmerRequestList
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.PrimaryEnd
import com.tekinflow.demo.presentation.theme.PrimaryStart
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverScreen(
    onRequestClick: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: SoforViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    val currentUserId = viewModel.getCurrentUser()?.id.orEmpty()
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

    val assignedRequests = remember(uiState.myRequests, debouncedQuery) {
        uiState.myRequests
            .filter { request ->
                request.materialStatus != MaterialStatus.DELIVERED &&
                    request.materialStatus != MaterialStatus.CANCELLED &&
                    !request.hasObjection
            }
            .filter { request ->
                listOf(request.itemToPurchase, request.items.joinToString(" ") { it.itemName }, request.companyName)
                    .joinToString(" ")
                    .lowercase()
                    .contains(debouncedQuery.trim().lowercase())
            }
    }
    val unassignedRequests = remember(uiState.unassignedRequests, debouncedQuery) {
        uiState.unassignedRequests.filter { request ->
            listOf(request.itemToPurchase, request.items.joinToString(" ") { it.itemName }, request.companyName)
                .joinToString(" ")
                .lowercase()
                .contains(debouncedQuery.trim().lowercase())
        }
    }
    val completedRequests = remember(uiState.allRequests, currentUserId, debouncedQuery) {
        uiState.allRequests.filter { request ->
            request.assignedDriverId == currentUserId &&
                request.materialStatus == MaterialStatus.DELIVERED
        }.filter { request ->
            listOf(request.itemToPurchase, request.items.joinToString(" ") { it.itemName }, request.companyName)
                .joinToString(" ")
                .lowercase()
                .contains(debouncedQuery.trim().lowercase())
        }
    }
    val objectionRequests = remember(uiState.allRequests, currentUserId, debouncedQuery) {
        uiState.allRequests.filter { request ->
            request.assignedDriverId == currentUserId &&
                request.hasObjection
        }.filter { request ->
            listOf(request.itemToPurchase, request.items.joinToString(" ") { it.itemName }, request.companyName)
                .joinToString(" ")
                .lowercase()
                .contains(debouncedQuery.trim().lowercase())
        }
    }
    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.driver_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = listHorizontalPadding, vertical = AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.driver_search_hint)
            )

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                            .padding(horizontal = 24.dp)
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(listOf(PrimaryStart, PrimaryEnd)),
                                MaterialTheme.shapes.small
                            )
                    )
                }
            ) {
                listOf(
                    stringResource(R.string.driver_tab_assigned),
                    stringResource(R.string.driver_tab_unassigned),
                    stringResource(R.string.driver_tab_completed),
                    stringResource(R.string.driver_tab_objected)
                ).forEachIndexed { index, title ->
                    Tab(
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val requests = when (page) {
                    0 -> assignedRequests
                    1 -> unassignedRequests
                    2 -> completedRequests
                    else -> objectionRequests
                }
                val listState = rememberLazyListState()
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        uiState.isLoading -> {
                            ShimmerRequestList(modifier = Modifier.padding(top = AppSpacing.s3))
                        }

                        requests.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyStateView(
                                    icon = if (page == 0) Icons.Default.DirectionsCar else Icons.Default.LocalShipping,
                                    title = when (page) {
                                        0 -> stringResource(R.string.driver_empty_assigned_title)
                                        1 -> stringResource(R.string.driver_empty_unassigned_title)
                                        2 -> stringResource(R.string.driver_empty_completed_title)
                                        else -> stringResource(R.string.driver_empty_objected_title)
                                    },
                                    description = when (page) {
                                        0 -> stringResource(R.string.driver_empty_assigned_description)
                                        1 -> stringResource(R.string.driver_empty_unassigned_description)
                                        2 -> stringResource(R.string.driver_empty_completed_description)
                                        else -> stringResource(R.string.driver_empty_objected_description)
                                    },
                                    modifier = Modifier.widthIn(max = REQUEST_LIST_MAX_WIDTH)
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(bottom = AppSpacing.s7),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                            ) {
                                itemsIndexed(requests, key = { _, request -> request.id }) { _, request ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RequestCard(
                                            request = request,
                                            onClick = {
                                                if (page == 0) viewModel.markAsSeen(request.id)
                                                onRequestClick(request.id)
                                            },
                                            modifier = Modifier
                                                .widthIn(max = REQUEST_LIST_MAX_WIDTH)
                                                .fillMaxWidth(),
                                            showNewBadge = page == 0 && currentUserId !in request.seenByUserIds,
                                            showObjection = false,
                                            footerContent = if (page == 1) {
                                                {
                                                    AppButton(
                                                        text = stringResource(R.string.driver_take_over),
                                                        onClick = { viewModel.takeOverRequest(request.id) },
                                                        variant = ButtonVariant.SECONDARY
                                                    )
                                                }
                                            } else {
                                                null
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
    }
}

private val REQUEST_LIST_MAX_WIDTH = 1100.dp
private const val TABLET_WIDTH_DP = 560
private val TABLET_LIST_HORIZONTAL_PADDING = 24.dp

@Preview(showBackground = true)
@Composable
private fun DriverScreenPreview() {
    SatinalmaTheme {
        RequestCard(
            request = PurchaseRequest(
                requestNumber = "TLP-2026-001",
                itemToPurchase = "Çelik Profil",
                companyName = "TekinFlow"
            ),
            onClick = {}
        )
    }
}
