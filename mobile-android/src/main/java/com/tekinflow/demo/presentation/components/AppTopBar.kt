package com.tekinflow.demo.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.AppNotification
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.navigation.LocalSyncUiState
import com.tekinflow.demo.presentation.auth.ChangePasswordUiState
import com.tekinflow.demo.presentation.auth.ChangePasswordViewModel
import com.tekinflow.demo.presentation.theme.AppSpacing
import com.tekinflow.demo.presentation.theme.BadgeShape
import com.tekinflow.demo.presentation.theme.BottomSheetShape
import com.tekinflow.demo.presentation.theme.CardShape
import com.tekinflow.demo.presentation.theme.DialogShape
import com.tekinflow.demo.presentation.theme.LocalThemeController
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.primaryGradientBrush
import com.tekinflow.demo.util.toFormattedTime
import com.tekinflow.demo.util.toFormattedDateTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    currentUser: User?,
    notifications: List<AppNotification> = emptyList(),
    onLogout: () -> Unit,
    onNotificationRead: (String) -> Unit = {},
    onNotificationClick: (AppNotification) -> Unit = {},
    canNavigateBack: Boolean = false,
    onNavigateBack: (() -> Unit)? = null
) {
    var profileExpanded by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var locallyReadNotificationIds by remember { mutableStateOf(emptySet<String>()) }
    val themeController = LocalThemeController.current
    val syncUiState = LocalSyncUiState.current
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val notificationIds = notifications.map { it.id }.toSet()
    val unreadNotifications = notifications.filter { !it.isRead && it.id !in locallyReadNotificationIds }
    val unreadCount = unreadNotifications.size
    val sheetNotifications = notifications.map { notification ->
        if (!notification.isRead && notification.id in locallyReadNotificationIds) {
            notification.copy(isRead = true)
        } else {
            notification
        }
    }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val subtitle = when {
        syncUiState.isSyncing && syncUiState.lastSyncAt == 0L -> stringResource(R.string.sync_syncing)
        syncUiState.lastSyncAt > 0L -> stringResource(
            R.string.sync_last_synced,
            syncUiState.lastSyncAt.toFormattedTime()
        )
        else -> null
    }
    val tabletSyncLabel = when {
        syncUiState.isOffline -> stringResource(R.string.sync_offline)
        syncUiState.isSyncing -> stringResource(R.string.sync_syncing_short)
        else -> stringResource(R.string.sync_realtime_online)
    }
    val showOfflineBanner = syncUiState.isOffline &&
        syncUiState.lastSyncAt > 0L &&
        (currentTime - syncUiState.lastSyncAt) >= STALE_DATA_THRESHOLD_MS
    val showFailedSyncBanner = syncUiState.failedSyncCount > 0

    LaunchedEffect(syncUiState.isOffline, syncUiState.lastSyncAt) {
        currentTime = System.currentTimeMillis()
        while (syncUiState.isOffline) {
            delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(notificationIds) {
        locallyReadNotificationIds = locallyReadNotificationIds.intersect(notificationIds)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canNavigateBack && onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacing.s1))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                subtitle?.takeUnless { isTablet }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isTablet) {
                SyncStatusChip(
                    text = tabletSyncLabel,
                    isOffline = syncUiState.isOffline,
                    isSyncing = syncUiState.isSyncing
                )
                Spacer(modifier = Modifier.width(AppSpacing.s2))
            }

            if (isTablet) {
            IconButton(
                onClick = {
                    themeController.onThemeChange(!themeController.isDarkTheme)
                }
            ) {
                Icon(
                    imageVector = if (themeController.isDarkTheme) {
                        Icons.Default.DarkMode
                    } else {
                        Icons.Default.LightMode
                    },
                    contentDescription = stringResource(R.string.theme_dark_mode),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            }

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        showNotifications = true
                        val unreadIds = unreadNotifications.map { it.id }.toSet()
                        if (unreadIds.isNotEmpty()) {
                            locallyReadNotificationIds = locallyReadNotificationIds + unreadIds
                            unreadIds.forEach(onNotificationRead)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = "Bildirimler",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 4.dp)
                ) {
                    if (unreadCount > 0) {
                        Badge(
                            modifier = Modifier
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (currentUser != null) {
                Box {
                    GradientAvatar(
                        initials = currentUser.initials(),
                        modifier = Modifier.size(48.dp).clickable { profileExpanded = true }.semantics { contentDescription = "Profil menüsü" }
                    )
                    DropdownMenu(
                        expanded = profileExpanded,
                        onDismissRequest = { profileExpanded = false }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                text = currentUser.fullName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentUser.role.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.theme_dark_mode),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (themeController.isDarkTheme) {
                                        Icons.Default.DarkMode
                                    } else {
                                        Icons.Default.LightMode
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                Switch(
                                    checked = themeController.isDarkTheme,
                                    onCheckedChange = themeController.onThemeChange
                                )
                            },
                            onClick = {
                                themeController.onThemeChange(!themeController.isDarkTheme)
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (!com.tekinflow.demo.BuildConfig.DEMO_MODE) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.change_password),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                profileExpanded = false
                                showChangePassword = true
                            }
                        )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.logout),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                profileExpanded = false
                                onLogout()
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (showOfflineBanner) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.sync_offline_stale_banner),
                    modifier = Modifier.padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showFailedSyncBanner) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = stringResource(R.string.sync_failed_count_banner, syncUiState.failedSyncCount),
                    modifier = Modifier.padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s2),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    if (showNotifications) {
        NotificationBottomSheet(
            notifications = sheetNotifications,
            onDismiss = { showNotifications = false },
            onMarkRead = onNotificationRead,
            onNotificationClick = { notification ->
                showNotifications = false
                locallyReadNotificationIds = locallyReadNotificationIds + notification.id
                if (!notification.isRead) {
                    onNotificationRead(notification.id)
                }
                onNotificationClick(notification)
            }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false }
        )
    }
}

private const val STALE_DATA_THRESHOLD_MS = 5 * 60 * 1_000L
private const val TABLET_WIDTH_DP = 840

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    ChangePasswordDialogContent(
        state = state,
        onCurrentPasswordChange = viewModel::onCurrentPasswordChange,
        onNewPasswordChange = viewModel::onNewPasswordChange,
        onNewPasswordRepeatChange = viewModel::onNewPasswordRepeatChange,
        onSave = viewModel::changePassword,
        onDismiss = onDismiss
    )
}

@Composable
private fun ChangePasswordDialogContent(
    state: ChangePasswordUiState,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onNewPasswordRepeatChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var repeatPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!state.isSaving) onDismiss()
        },
        shape = DialogShape,
        title = {
            Text(
                text = stringResource(R.string.change_password),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
            ) {
                AppTextField(
                    label = stringResource(R.string.current_password),
                    value = state.currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    enabled = !state.isSaving,
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        PasswordVisibilityButton(
                            isVisible = currentPasswordVisible,
                            onClick = { currentPasswordVisible = !currentPasswordVisible }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = passwordTransformation(currentPasswordVisible)
                )
                AppTextField(
                    label = stringResource(R.string.new_password),
                    value = state.newPassword,
                    onValueChange = onNewPasswordChange,
                    enabled = !state.isSaving,
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        PasswordVisibilityButton(
                            isVisible = newPasswordVisible,
                            onClick = { newPasswordVisible = !newPasswordVisible }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = passwordTransformation(newPasswordVisible)
                )
                AppTextField(
                    label = stringResource(R.string.new_password_repeat),
                    value = state.newPasswordRepeat,
                    onValueChange = onNewPasswordRepeatChange,
                    enabled = !state.isSaving,
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        PasswordVisibilityButton(
                            isVisible = repeatPasswordVisible,
                            onClick = { repeatPasswordVisible = !repeatPasswordVisible }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = passwordTransformation(repeatPasswordVisible),
                    isError = state.errorMessage != null,
                    errorMessage = state.errorMessage
                )
                Text(
                    text = stringResource(R.string.password_policy_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            AppButton(
                text = stringResource(R.string.change_password_action),
                onClick = onSave,
                isLoading = state.isSaving,
                enabled = !state.isSaving
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSaving
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PasswordVisibilityButton(
    isVisible: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun passwordTransformation(isVisible: Boolean): VisualTransformation {
    return if (isVisible) VisualTransformation.None else PasswordVisualTransformation()
}

@Composable
private fun SyncStatusChip(
    text: String,
    isOffline: Boolean,
    isSyncing: Boolean
) {
    val containerColor = when {
        isOffline -> MaterialTheme.colorScheme.errorContainer
        isSyncing -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isOffline -> MaterialTheme.colorScheme.onErrorContainer
        isSyncing -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = BadgeShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(contentColor, CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GradientAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(primaryGradientBrush(), CircleShape)
            .padding(2.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(3.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                        )
                    ),
                    CircleShape
                )
        )
        Text(
            text = initials,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationBottomSheet(
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onMarkRead: (String) -> Unit,
    onNotificationClick: (AppNotification) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = BottomSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.notifications),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (notifications.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications_empty_title),
                    description = stringResource(R.string.notifications_empty_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AppSpacing.s4)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NotificationSheetItem(
                            notification = notification,
                            onClick = {
                                if (!notification.isRead) {
                                    onMarkRead(notification.id)
                                }
                                onNotificationClick(notification)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.size(AppSpacing.s2)) }
                }
            }
        }
    }
}

@Composable
private fun NotificationSheetItem(
    notification: AppNotification,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = CardShape,
        color = if (notification.isRead) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.s4),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.s1)
        ) {
            Text(
                text = notification.message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.SemiBold
            )
            Text(
                text = notification.timestamp.toFormattedDateTime(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppTopBarPreview() {
    SatinalmaTheme {
        AppTopBar(
            title = "Satınalma Talepleri",
            currentUser = User("1", "mehmet", "Mehmet Demir", UserRole.PURCHASING),
            notifications = listOf(
                AppNotification(
                    id = "1",
                    message = "Yeni talep atandı",
                    timestamp = System.currentTimeMillis(),
                    isRead = false,
                    targetUserId = "1"
                )
            ),
            onLogout = {}
        )
    }
}
