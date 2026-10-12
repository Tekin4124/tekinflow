package com.tekinflow.demo.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tekinflow.demo.presentation.theme.Error
import com.tekinflow.demo.presentation.theme.Info
import com.tekinflow.demo.presentation.theme.SatinalmaTheme
import com.tekinflow.demo.presentation.theme.Success

enum class AppMessageType {
    SUCCESS,
    ERROR,
    INFO
}

data class AppMessage(
    val message: String,
    val type: AppMessageType
)

private data class AppSnackbarVisuals(
    override val message: String,
    val type: AppMessageType,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short
) : SnackbarVisuals

suspend fun SnackbarHostState.showAppMessage(appMessage: AppMessage) {
    showSnackbar(
        AppSnackbarVisuals(
            message = appMessage.message,
            type = appMessage.type
        )
    )
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        AppSnackbar(data = data)
    }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val visuals = data.visuals as? AppSnackbarVisuals
    val (background, icon) = when (visuals?.type ?: AppMessageType.INFO) {
        AppMessageType.SUCCESS -> Success to Icons.Default.CheckCircle
        AppMessageType.ERROR -> Error to Icons.Default.Error
        AppMessageType.INFO -> Info to Icons.Default.Info
    }

    Snackbar(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White
            )
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppSnackbarPreview() {
    SatinalmaTheme {
        val hostState = SnackbarHostState()
        AppSnackbarHost(hostState = hostState)
    }
}
