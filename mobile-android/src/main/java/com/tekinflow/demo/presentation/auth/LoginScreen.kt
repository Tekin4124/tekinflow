package com.tekinflow.demo.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tekinflow.demo.R
import com.tekinflow.demo.domain.model.User
import com.tekinflow.demo.domain.model.UserRole
import com.tekinflow.demo.presentation.theme.LocalThemeController

@Composable
fun LoginScreen(onLoginSuccess: (User) -> Unit, viewModel: GirisViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val theme = LocalThemeController.current
    LaunchedEffect(state.loggedInUser) { state.loggedInUser?.let(onLoginSuccess) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.tekinflow_mark), "TekinFlow logosu", Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TEKINFLOW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Satın alma ve sevkiyat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { theme.onThemeChange(!theme.isDarkTheme) }) {
                        Icon(if (theme.isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            if (theme.isDarkTheme) "Açık temaya geç" else "Koyu temaya geç")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Çalışma alanını seç", style = MaterialTheme.typography.headlineMedium)
                Text("Bir rol seçerek devam et.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val roles = listOf(
                    Triple(UserRole.PURCHASING, "Satın alma", Icons.Outlined.Inventory2),
                    Triple(UserRole.LOGISTICS_OFFICE, "Sevkiyat", Icons.Outlined.LocalShipping),
                    Triple(UserRole.DRIVER, "Şoför", Icons.Outlined.Navigation),
                    Triple(UserRole.ADMIN, "Yönetici", Icons.Outlined.AdminPanelSettings)
                )
                val descriptions = listOf("Talep ve tedarikçi takibi", "Şoför atama ve sevkiyat takibi", "Atanan işler ve teslimatlar", "Kullanıcılar ve işlem geçmişi")
                roles.forEachIndexed { index, (role, label, icon) ->
                    RoleEntry(label, descriptions[index], icon, !state.isLoading) {
                        viewModel.onUsernameChange(role.name.lowercase())
                        viewModel.onPasswordChange("demo")
                        viewModel.login()
                    }
                }
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Örnek veriler • Hesap gerekmez", modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoleEntry(label: String, description: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
