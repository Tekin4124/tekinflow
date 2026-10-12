package com.tekinflow.demo.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.navigation.LocalSyncUiState
import com.tekinflow.demo.presentation.components.AppSearchBar
import com.tekinflow.demo.presentation.components.AppSnackbarHost
import com.tekinflow.demo.presentation.components.AppTextField
import com.tekinflow.demo.presentation.components.FilterChipRow
import com.tekinflow.demo.presentation.components.GradientAvatar
import com.tekinflow.demo.presentation.components.GradientButton
import com.tekinflow.demo.presentation.components.AppTopBar
import com.tekinflow.demo.presentation.components.SecureScreen
import com.tekinflow.demo.presentation.components.showAppMessage
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.BottomSheetShape
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.Error
import com.tekinflow.demo.presentation.theme.SatinalmaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onLogout: () -> Unit,
    viewModel: YoneticiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    SecureScreen()
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val horizontalPadding = if (isTablet) AppSpacing.s7 else AppSpacing.s4
    val currentUser = viewModel.getCurrentUser()
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(uiState.users, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            uiState.users
        } else {
            uiState.users.filter { user ->
                listOf(user.fullName, user.username, user.role.displayName, user.licensePlate.orEmpty())
                    .joinToString(" ")
                    .lowercase()
                    .contains(query)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showAppMessage(it) }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.admin_title),
                currentUser = currentUser,
                onLogout = onLogout
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateSheet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = horizontalPadding, vertical = AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s4),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTablet) {
                AdminTabletSummary(
                    users = uiState.users,
                    modifier = Modifier
                        .widthIn(max = ADMIN_CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                )
            }

            AppSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.admin_users_title),
                modifier = Modifier
                    .widthIn(max = ADMIN_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = ADMIN_CONTENT_MAX_WIDTH)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
            ) {
                items(filteredUsers, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        currentUser = currentUser,
                        onEdit = { viewModel.openEditSheet(user) },
                        onDelete = { viewModel.deleteUser(user.id) },
                        onActivate = { viewModel.activateUser(user.id) },
                        onPermanentDelete = { viewModel.deleteUserPermanently(user.id) }
                    )
                }
                item { Spacer(modifier = Modifier.navigationBarsPadding()) }
            }
        }
    }

    if (uiState.isEditorOpen) {
        AdminEditorSheet(
            state = uiState,
            onDismiss = viewModel::closeEditor,
            onUsernameChange = viewModel::onUsernameChange,
            onFullNameChange = viewModel::onFullNameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onRoleSelected = viewModel::onRoleSelected,
            onLicensePlateChange = viewModel::onLicensePlateChange,
            onSave = viewModel::saveUser
        )
    }
}

@Composable
private fun AdminTabletSummary(
    users: List<User>,
    modifier: Modifier = Modifier
) {
    val syncUiState = LocalSyncUiState.current
    val syncValue = when {
        syncUiState.isOffline -> stringResource(R.string.sync_offline)
        syncUiState.isSyncing -> stringResource(R.string.sync_syncing_short)
        else -> stringResource(R.string.sync_realtime_online)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        AdminMetricCard(
            label = stringResource(R.string.admin_metric_total_users),
            value = users.size.toString(),
            modifier = Modifier.weight(1f)
        )
        AdminMetricCard(
            label = stringResource(R.string.admin_metric_admins),
            value = users.count { it.role == UserRole.ADMIN }.toString(),
            modifier = Modifier.weight(1f)
        )
        AdminMetricCard(
            label = stringResource(R.string.admin_metric_super_admins),
            value = users.count { it.isSuperAdmin }.toString(),
            modifier = Modifier.weight(1f)
        )
        AdminMetricCard(
            label = stringResource(R.string.admin_metric_network),
            value = syncValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AdminMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s1)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UserCard(
    user: User,
    currentUser: User?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    val canEdit = currentUser.canEdit(user)
    val canDelete = currentUser.canDelete(user)

    Surface(
        shape = CardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            GradientAvatar(initials = user.initials())

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RoleChip(
                    role = user.role,
                    plate = user.licensePlate,
                    isSuperAdmin = user.isSuperAdmin,
                    isActive = user.isActive
                )
                if (!user.isActive) {
                    Text(
                        text = "Pasif",
                        style = MaterialTheme.typography.labelMedium,
                        color = Error
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                enabled = canEdit,
                modifier = Modifier.alpha(if (canEdit) 1f else 0.35f)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            if (user.isActive) {
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.alpha(if (canDelete) 1f else 0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Error
                    )
                }
            } else {
                IconButton(
                    onClick = onActivate,
                    enabled = canDelete,
                    modifier = Modifier.alpha(if (canDelete) 1f else 0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onPermanentDelete,
                    enabled = canDelete,
                    modifier = Modifier.alpha(if (canDelete) 1f else 0.35f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Error
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleChip(
    role: UserRole,
    plate: String?,
    isSuperAdmin: Boolean = false,
    isActive: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (role == UserRole.DRIVER) Icons.Default.DirectionsCar else Icons.Default.Badge,
                contentDescription = null,
                modifier = Modifier.padding(0.dp)
            )
            Text(
                text = when {
                    isSuperAdmin -> "Süper Admin"
                    role == UserRole.DRIVER && !plate.isNullOrBlank() -> "${role.displayName} • $plate"
                    else -> role.displayName
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminEditorSheet(
    state: AdminUiState,
    onDismiss: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onFullNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRoleSelected: (UserRole) -> Unit,
    onLicensePlateChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val title = if (state.editingUserId == null) {
        stringResource(R.string.admin_add_user)
    } else {
        stringResource(R.string.admin_edit_user)
    }
    val roles = UserRole.entries.filter { it != UserRole.ADMIN }
    val selectedRoleLabel = roles.firstOrNull { it == state.selectedRole }?.displayName
        ?: UserRole.PURCHASING.displayName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = BottomSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s2),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s4)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            AppTextField(
                label = stringResource(R.string.admin_username),
                value = state.username,
                onValueChange = onUsernameChange,
                leadingIcon = Icons.Default.Person
            )
            AppTextField(
                label = stringResource(R.string.admin_full_name),
                value = state.fullName,
                onValueChange = onFullNameChange,
                leadingIcon = Icons.Default.Badge
            )
            AppTextField(
                label = stringResource(R.string.admin_password),
                value = state.password,
                onValueChange = onPasswordChange,
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = if (state.editingUserId == null) {
                    stringResource(R.string.admin_password_hint)
                } else {
                    stringResource(R.string.admin_password_optional)
                }
            )

            PasswordStrengthIndicator(password = state.password)

            FilterChipRow(
                filters = roles.map { it.displayName },
                selected = selectedRoleLabel,
                onSelect = { selected ->
                    roles.firstOrNull { it.displayName == selected }?.let(onRoleSelected)
                }
            )

            AnimatedVisibility(visible = state.selectedRole == UserRole.DRIVER) {
                AppTextField(
                    label = stringResource(R.string.admin_plate),
                    value = state.licensePlate,
                    onValueChange = onLicensePlateChange,
                    leadingIcon = Icons.Default.DirectionsCar
                )
            }

            GradientButton(
                text = if (state.editingUserId == null) {
                    stringResource(R.string.admin_add_user)
                } else {
                    stringResource(R.string.update)
                },
                onClick = onSave,
                isLoading = state.isSaving
            )

            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(password: String) {
    val labelRes = when {
        password.length >= 8 -> R.string.admin_strength_strong
        password.length >= 5 -> R.string.admin_strength_medium
        password.isNotBlank() -> R.string.admin_strength_weak
        else -> null
    }

    labelRes?.let {
        Text(
            text = stringResource(it),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val TABLET_WIDTH_DP = 840
private val ADMIN_CONTENT_MAX_WIDTH = 1180.dp

private fun User?.canEdit(target: User): Boolean {
    val current = this ?: return false
    if (current.role != UserRole.ADMIN || !current.isSuperAdmin) return false
    if (target.isSuperAdmin || target.role == UserRole.ADMIN) return false
    return true
}

private fun User?.canDelete(target: User): Boolean {
    val current = this ?: return false
    if (target.id == current.id) return false
    if (current.role != UserRole.ADMIN || !current.isSuperAdmin || target.isSuperAdmin) return false
    return true
}

@Preview(showBackground = true)
@Composable
private fun AdminPreview() {
    SatinalmaTheme {
        UserCard(
            user = User("1", "mehmet", "Mehmet Demir", UserRole.ADMIN, isSuperAdmin = true),
            currentUser = User("0", "sys", "Sistem Yöneticisi", UserRole.ADMIN, isSuperAdmin = true),
            onEdit = {},
            onDelete = {},
            onActivate = {},
            onPermanentDelete = {}
        )
    }
}
